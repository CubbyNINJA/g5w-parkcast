"""The S3 evaluator: reads a stick copy of the receiver's logs on the PC (AIR-071, AIR-075, AIR-076, AIR-079).

A run folder is `NEMESIS-diag/airplay-receiver/<timestamp>/` as the log-copy script leaves it: the receiver's
`*.log` files, the owner's `owner-note.txt` and `run-checklist.txt`. Raw logs only ever go under `runs/`; the
committed summary carries the verdict, the device model, the iOS version, the build ids and the checklist.

Cites are 1-based line numbers over the folder's `*.log` files read in name order, plus `owner-note.txt`
when the note decided a verdict."""

from __future__ import annotations

import argparse
import re
import shutil
import sys
from pathlib import Path
from types import SimpleNamespace

DEADLINE_MS = 600_000
CHECKLIST_KEYS = ("parkcast listed", "letterboxed", "sound on unit", "stayed behind other app",
                  "nothing on cluster or hud", "carplay after exit")
NOTE = "owner-note.txt"
CHECKLIST = "run-checklist.txt"
OFFLINE_SENTENCE = "This is a synthetic fixture result and not evidence about any iPhone."
RUN_LEVEL = ("parkcast", "carplay listener", "port busy", "advertise ", "hotspot address missing", "mdnsd up")

PREFIX = re.compile(r"^(\d+) (.*)$")
SESSION = re.compile(r"^session (\d+) (.*)$")
ACCEPTED = re.compile(r"^connection accepted model=(.*)$")
CODEC = re.compile(r"^codec (h264|h265) (\d+)x(\d+)$")
END = re.compile(r"^end reason=(.*?) withheld=\d+$")
DECODER_CONFIGURED = re.compile(r"^decoder \S+ configured$")
NOTE_LINE = re.compile(r"\s*picture\s*:\s*(yes|no)\s*", re.IGNORECASE)


# --- Reading the log (AIR-071, AIR-075(b), AIR-076(h)) ------------------------------------------------------

def _records(run_dir: Path):
    """[(line, ms or None, text)] over every *.log in name order, and whether the data ends with LF."""
    files = sorted(p for p in run_dir.glob("*.log") if p.is_file())
    if not files:
        raise OSError("no log file")
    data = b"".join(p.read_bytes() for p in files)
    text = data.decode("utf-8", errors="replace")
    lines = text.split("\n")
    ends_lf = text.endswith("\n")
    if ends_lf:
        lines.pop()
    out = []
    for i, line in enumerate(lines, 1):
        line = line[:-1] if line.endswith("\r") else line
        m = PREFIX.match(line)
        out.append((i, int(m.group(1)), m.group(2)) if m else (i, None, line))
    return out, ends_lf


def _limitation(records, ends_lf) -> str | None:
    texts = [t for _, _, t in records if t.strip()]
    if not any(t.startswith("parkcast start") for t in texts):
        return "no start marker"
    if not ends_lf or not texts[-1].startswith("parkcast stop"):
        return "truncated"
    return None


def _start_fields(text: str) -> dict:
    fields = {}
    for key in ("app", "commit", "uxplay", "product", "require_park"):
        m = re.search(rf"(?:^| ){key}=(\S*)", text)
        if m:
            fields[key] = m.group(1)
    return fields


def _parse(run_dir):
    """The log read into sessions, with each line's owner (AIR-076(h): UxPlay lines go to the latest open session)."""
    run_dir = Path(run_dir)
    try:
        records, ends_lf = _records(run_dir)
    except OSError:
        return SimpleNamespace(records=[], limitation="unreadable", sessions=[], owner={}, starts=[], stops=[])
    sessions, owner, starts, stops = [], {}, [], []
    by_number: dict[int, SimpleNamespace] = {}
    open_session = None
    for line, ms, text in records:
        if text.startswith("parkcast start"):
            starts.append((line, ms, text))
            by_number = {}
            open_session = None
            owner[line] = None
            continue
        if text.startswith("parkcast stop"):
            stops.append((line, ms, text))
            owner[line] = None
            continue
        m = SESSION.match(text)
        if m:
            n, rest = int(m.group(1)), m.group(2)
            acc = ACCEPTED.match(rest)
            if acc:
                s = SimpleNamespace(number=n, model=acc.group(1), markers=["connection accepted"], end_reason=None,
                                    lines=[(line, ms, text)], ended=False)
                sessions.append(s)
                by_number[n] = s
                open_session = s
                owner[line] = s
                continue
            s = by_number.get(n)
            owner[line] = s
            if s is None:
                continue
            s.lines.append((line, ms, text))
            end = END.match(rest)
            if end:
                s.markers.append("session end")
                s.end_reason = end.group(1)
                s.ended = True
                if open_session is s:
                    open_session = next((o for o in reversed(sessions) if not o.ended and o in by_number.values()),
                                        None)
            else:
                s.markers.append(rest)
            continue
        if text.startswith(RUN_LEVEL):
            owner[line] = None
            continue
        owner[line] = open_session
        if open_session is None:
            continue
        open_session.lines.append((line, ms, text))
        if text.startswith("Mirroring initialized successfully"):
            open_session.markers.append("mirroring initialized")
        elif DECODER_CONFIGURED.match(text):
            open_session.markers.append("decoder configured")
    return SimpleNamespace(records=records, limitation=_limitation(records, ends_lf), sessions=sessions, owner=owner,
                           starts=starts, stops=stops)


def read_log(run_dir):
    p = _parse(run_dir)
    start = _start_fields(p.starts[0][2]) if p.starts else None
    stop = re.search(r"reason=(.*)$", p.stops[-1][2]) if p.stops else None
    sessions = [SimpleNamespace(number=s.number, model=s.model, markers=list(s.markers), end_reason=s.end_reason)
                for s in p.sessions]
    return SimpleNamespace(complete=p.limitation is None, limitation=p.limitation, start=start,
                           stop_reason=stop.group(1) if stop else None, sessions=sessions)


# --- The owner's note and checklist (AIR-076(e), AIR-079(e), (f)) -------------------------------------------

def read_note(run_dir) -> str | None:
    """`yes`, `no`, or None for a missing note. Only `owner-note.txt` in the run's own folder counts."""
    try:
        data = (Path(run_dir) / NOTE).read_bytes()
    except OSError:
        return None
    if data.endswith(b"\r\n"):
        data = data[:-2]
    elif data.endswith(b"\n"):
        data = data[:-1]
    if b"\n" in data:
        return None
    try:
        m = NOTE_LINE.fullmatch(data.decode("utf-8"))
    except UnicodeDecodeError:
        return None
    return m.group(1).lower() if m else None


def read_checklist(run_dir):
    values = {k: "not recorded" for k in CHECKLIST_KEYS}
    malformed: list[str] = []
    try:
        text = (Path(run_dir) / CHECKLIST).read_bytes().decode("utf-8", errors="replace")
    except OSError:
        return SimpleNamespace(values=values, malformed=malformed)
    seen: dict[str, list[str]] = {k: [] for k in CHECKLIST_KEYS}
    for line in text.splitlines():
        if not line.strip():
            continue
        key, sep, value = line.partition(":")
        key = key.strip().lower()
        if not sep or key not in seen:
            malformed.append(line.strip())
            continue
        seen[key].append(value.strip().lower())
    for key, found in seen.items():
        if len(found) == 1 and found[0] in ("yes", "no"):
            values[key] = found[0]
    return SimpleNamespace(values=values, malformed=malformed)


# --- Verdicts (AIR-075(b), AIR-076) -------------------------------------------------------------------------

def _verdict(number, verdict, reason, cites, limitation=None):
    return SimpleNamespace(number=number, verdict=verdict, reason=reason, cites=list(cites), limitation=limitation)


def _timing(p):
    """(launch, deadline, endpoint, termination); the endpoint is the first end marker before the deadline that
    closes the run's observed app start, else the deadline."""
    launch = p.starts[0][1] if p.starts else None
    if launch is None:
        return SimpleNamespace(launch_ms=None, deadline_ms=None, endpoint_ms=None, termination=None)
    deadline = launch + DEADLINE_MS
    for line, ms, _ in p.stops:
        if ms is None or not launch <= ms < deadline:
            continue
        restarted = any(sl > line and (sms is None or sms < deadline) for sl, sms, _ in p.starts)
        if not restarted:
            return SimpleNamespace(launch_ms=launch, deadline_ms=deadline, endpoint_ms=ms, termination="end marker")
    return SimpleNamespace(launch_ms=launch, deadline_ms=deadline, endpoint_ms=deadline, termination="timeout")


def _judge_session(s, record, note, frames_in_run):
    def counted(ms):
        return ms is not None and ms < record.deadline_ms and ms <= record.endpoint_ms

    lines = [(line, text) for line, ms, text in s.lines if counted(ms)]
    frame = next((line for line, t in lines if t == f"session {s.number} first frame rendered"), None)
    codec = next(((line, CODEC.match(t.split(" ", 2)[2])) for line, t in lines
                  if t.startswith(f"session {s.number} codec ") and CODEC.match(t.split(" ", 2)[2])), None)
    decoder = next((line for line, t in lines if t == "decoder OMX.TCC.avcdec configured"), None)
    withheld = any(t == f"session {s.number} first frame withheld by gate" for _, t in lines)

    for line, t in lines:
        before_frame = frame is None or line < frame
        if t.startswith("decoder unavailable"):
            return _verdict(s.number, "NO-GO", "decoder unavailable", [line])
        if t.startswith("decoder error") and before_frame:
            return _verdict(s.number, "NO-GO", "decoder error", [line])
        if t.startswith("Mirroring not initialized at SETUP"):
            return _verdict(s.number, "NO-GO", "mirroring not initialized", [line])
    if codec and codec[1].group(1) == "h265":
        return _verdict(s.number, "NO-GO", "codec h265", [codec[0]])
    if frame is not None:
        if note == "no":
            return _verdict(s.number, "NO-GO", "owner saw no picture", [NOTE])
        if note is None:
            return _verdict(s.number, "INCONCLUSIVE", "owner note missing", [])
        if frames_in_run > 1:
            return _verdict(s.number, "INCONCLUSIVE", "owner note ambiguous", [frame, NOTE])
        if codec and decoder is not None:
            return _verdict(s.number, "GO", "picture seen", [codec[0], decoder, frame, NOTE])
        return _verdict(s.number, "INCONCLUSIVE", "codec or decoder record missing", [frame])
    if withheld:
        return _verdict(s.number, "INCONCLUSIVE", "gate never permitted video", [])
    return _verdict(s.number, "INCONCLUSIVE", "timeout", [])


def _run_reasons(records) -> list[str]:
    reasons = []
    for marker, reason in (("port busy", "port busy"), ("advertise failed", "advertise failed"),
                           ("carplay listener present", "carplay present")):
        if any(t.startswith(marker) for _, _, t in records):
            reasons.append(reason)
    return reasons


def judge_run_folder(run_dir):
    run_dir = Path(run_dir)
    p = _parse(run_dir)
    record = _timing(p)
    line_session = {ms: (p.owner[line].number if p.owner.get(line) else None)
                    for line, ms, _ in p.records if ms is not None}
    run_reasons = _run_reasons(p.records)
    limitation = p.limitation
    if limitation is None and p.starts and p.starts[0][1] is None:
        limitation = "start marker has no uptime prefix"
    if limitation is not None:
        sessions = [_verdict(s.number, "INCONCLUSIVE", "evidence error", [], limitation) for s in p.sessions]
        return SimpleNamespace(verdict="INCONCLUSIVE", reason="evidence error", cites=[], limitation=limitation,
                               sessions=sessions, run_reasons=run_reasons, record=record, line_session=line_session)

    note = read_note(run_dir)
    frame_texts = {f"session {s.number} first frame rendered" for s in p.sessions}
    frames_in_run = sum(
        1 for s in p.sessions
        if any(t == f"session {s.number} first frame rendered" and t in frame_texts and ms is not None
               and ms < record.deadline_ms and ms <= record.endpoint_ms for _, ms, t in s.lines))
    sessions = [_judge_session(s, record, note, frames_in_run) for s in p.sessions]

    if not sessions:
        verdict, reason = "INCONCLUSIVE", "no iPhone connected"
    elif any(s.verdict == "GO" for s in sessions):
        verdict, reason = "GO", "picture seen"
    elif any(s.verdict == "NO-GO" for s in sessions):
        verdict, reason = "NO-GO", next(s.reason for s in sessions if s.verdict == "NO-GO")
    else:
        verdict, reason = "INCONCLUSIVE", sessions[0].reason
    if run_reasons:
        verdict, reason = "INCONCLUSIVE", ", ".join(run_reasons)
    cites: list = []
    for s in sessions:
        if s.verdict == verdict:
            cites += [c for c in s.cites if c not in cites]
    return SimpleNamespace(verdict=verdict, reason=reason, cites=cites, limitation=None, sessions=sessions,
                           run_reasons=run_reasons, record=record, line_session=line_session)


# --- The summary and the result (AIR-075) -------------------------------------------------------------------

def _summary(name, tier, ios, judgement, start, models, checklist) -> str:
    start = start or {}
    head_unit = f"Head unit: firmware build {start.get('product') or 'not recorded'}."
    if tier == "offline":
        head_unit += " " + OFFLINE_SENTENCE
        check_line = "Checklist: not run (offline)"
    else:
        check_line = "Checklist: " + "; ".join(f"{k}={checklist.values[k]}" for k in CHECKLIST_KEYS)
    lines = [
        f"# AirPlay receiver S3 result ({name})",
        f"Tier: {tier}",
        f"Verdict: {judgement.verdict} ({judgement.reason})",
        f"Sessions: {', '.join(s.verdict for s in judgement.sessions) or 'none'}",
        f"Device model: {', '.join(models) or 'unknown'}",
        f"iOS: {ios or 'missing'}",
        f"UxPlay build commit: {start.get('uxplay') or 'not recorded'}",
        head_unit,
        "Firewall: not applicable on the head unit.",
        check_line,
    ]
    return "\n".join(lines) + "\n"


def read_run_folder(run_dir, tier: str = "unit", ios: str | None = None):
    run_dir = Path(run_dir)
    judgement = judge_run_folder(run_dir)
    log = read_log(run_dir)
    checklist = read_checklist(run_dir)
    models = sorted({s.model for s in log.sessions if s.model})
    summary = _summary(run_dir.name, tier, ios, judgement, log.start, models, checklist)
    return SimpleNamespace(summary=summary, judgement=judgement, checklist=checklist.values,
                           malformed=list(checklist.malformed), note_missing=read_note(run_dir) is None)


def write_result(run_dir, root, tier: str = "unit", ios: str | None = None) -> Path:
    """Copies the raw files under root/runs/ and writes only the redacted summary to root/docs/evidence/."""
    run_dir, root = Path(run_dir), Path(root)
    result = read_run_folder(run_dir, tier=tier, ios=ios)
    raw = root / "runs" / "NEMESIS-diag" / "airplay-receiver" / run_dir.name
    raw.mkdir(parents=True, exist_ok=True)
    for src in sorted(run_dir.glob("*.log")) + [run_dir / NOTE, run_dir / CHECKLIST]:
        if src.is_file():
            shutil.copyfile(src, raw / src.name)
    dest = root / "docs" / "evidence"
    dest.mkdir(parents=True, exist_ok=True)
    path = dest / f"{run_dir.name}-summary.md"
    path.write_text(result.summary, encoding="utf-8", newline="\n")
    return path


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Judge an S3 stick copy and write its redacted summary.")
    ap.add_argument("run_dir", help="the NEMESIS-diag/airplay-receiver/<timestamp>/ folder copied from the stick")
    ap.add_argument("--root", default=".", help="the repository root (raw files go under its runs/)")
    ap.add_argument("--tier", default="unit", choices=("unit", "offline"))
    ap.add_argument("--ios", help="the iPhone's iOS version with its build, e.g. 18.0 (22A0000)")
    args = ap.parse_args(argv)
    path = write_result(args.run_dir, args.root, tier=args.tier, ios=args.ios)
    print(path)
    for line in read_run_folder(args.run_dir).malformed:
        print(f"malformed checklist line: {line}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
