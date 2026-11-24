"""S3 tool step definitions: eval area of 14_receiver_tools.feature (docs/design/S3_RECEIVER.md).

AIR-071, AIR-075 to AIR-077 and AIR-079. A scenario builds one run's stick copy as records in
`rworld.eval_recs` (uptime ms and text, or a raw line), plus the owner's note and the run checklist. The
folder is written only when a When or Then needs it, at `<tmp>/eval/NEMESIS-diag/airplay-receiver/<timestamp>/`.
Steps shared with S1's verdict feature (`the evaluator judges the run`, the run verdict, the limitation) read
`world.judgement`, which these Givens set to a lazy proxy over `airplay_s3.evaluator.judge_run_folder`.
"""

from __future__ import annotations

import importlib
import re
import subprocess
from pathlib import Path

import pytest
from pytest_bdd import given, parsers, then, when

from steps.receiver_toolbox import REPO, RUN_PARENT, TIMESTAMP

Q = r'"(?P<{}>[^"]*)"'
N = r"(?P<{}>\d+)"
VERDICT = r"(?P<{}>GO|NO-GO|INCONCLUSIVE)"

CHECKLIST_KEYS = ("parkcast listed", "letterboxed", "sound on unit", "stayed behind other app",
                  "nothing on cluster or hud", "carplay after exit")
APP = "0.1.0"
COMMIT = "0123456789abcdef0123456789abcdef01234567"
UXPLAY = "05b0275cb1b726592700e47a6e0307544b3d3a52"
PRODUCT = "CVEV.CAN.0000.V015.011.260206"
MODEL = "iPhone15,2"
START = f"parkcast start app={APP} commit={COMMIT} uxplay={UXPLAY} product={PRODUCT} require_park=true"
STOP = "parkcast stop reason=owner exit"
NOTE_YES = b"picture: yes\n"


def _ev():
    return importlib.import_module("airplay_s3.evaluator")


# --- The fixture model -----------------------------------------------------------------------------------


def _st(rworld):
    d = rworld.__dict__
    d.setdefault("eval_recs", [])
    d.setdefault("eval_note", NOTE_YES)
    d.setdefault("eval_note_where", "run")
    d.setdefault("eval_checklist", None)
    d.setdefault("eval_checklist_where", "run")
    d.setdefault("eval_tier", "unit")
    d.setdefault("eval_ios", None)
    d.setdefault("eval_sessions", 0)
    return rworld


def _rec(ms, text, raw=None):
    return {"ms": ms, "text": text, "raw": raw}


def _session_recs(n, at, frame_ms=None, mirroring=True, model=MODEL):
    """One session that would be GO: accepted at `at` ms, its first frame at `frame_ms` (default at + 4 s)."""
    frame_ms = at + 4000 if frame_ms is None else frame_ms
    recs = [_rec(at, f"session {n} connection accepted model={model}")]
    if mirroring:
        recs.append(_rec(at + 1000, "Mirroring initialized successfully"))
    recs += [_rec(at + 2000, f"session {n} codec h264 1920x720"),
             _rec(at + 3000, "decoder OMX.TCC.avcdec configured"),
             _rec(frame_ms, f"session {n} first frame rendered"),
             _rec(frame_ms + 1000, f"session {n} end reason=iphone disconnected withheld=0")]
    return recs


def _set_run(rworld, recs, sessions, world=None):
    _st(rworld).eval_recs = recs
    rworld.eval_sessions = sessions
    if world is not None:
        _bind(rworld, world)


def _go_run(rworld, world=None, frame_ms=None):
    recs = _session_recs(1, 1000, frame_ms)
    stop_ms = max(recs[-1]["ms"] + 1000, 7000)
    _set_run(rworld, [_rec(0, START), *recs, _rec(stop_ms, STOP)], 1, world)


def _two_go_runs(rworld, world):
    recs = [_rec(0, START), *_session_recs(1, 1000), *_session_recs(2, 11000), _rec(20000, STOP)]
    _set_run(rworld, recs, 2, world)


def _add(rworld, ms, text, raw=None):
    """Insert a record before the first one that is later than it (records stay in uptime order)."""
    recs = _st(rworld).eval_recs
    at = next((i for i, r in enumerate(recs) if r["ms"] > ms), len(recs))
    recs.insert(at, _rec(ms, text, raw))


def _find(rworld, pred):
    hits = [r for r in rworld.eval_recs if pred(r["text"])]
    assert hits, "the fixture has no such record"
    return hits


def _stop(rworld):
    return _find(rworld, lambda t: t.startswith("parkcast stop"))[-1]


def _render(rworld) -> bytes:
    lines = [r["raw"] if r["raw"] is not None else f"{r['ms']} {r['text']}" for r in rworld.eval_recs]
    end = "" if getattr(rworld, "eval_no_final_lf", False) else "\n"
    return ("\n".join(lines) + end).encode("utf-8")


def _place(run_dir: Path, name: str, data, where: str) -> None:
    if where == "unreadable":
        (run_dir / name).mkdir()  # a folder by that name can't be read as a file on any OS
    elif data is None:
        return
    elif where == "run":
        (run_dir / name).write_bytes(data)
    elif where == "outside":
        (run_dir.parent / name).write_bytes(data)
    else:
        raise AssertionError(where)


def _run_dir(rworld) -> Path:
    _st(rworld)
    if getattr(rworld, "eval_dir", None) is None:
        run_dir = rworld.tmp / "eval" / RUN_PARENT / TIMESTAMP
        run_dir.mkdir(parents=True)
        (run_dir / "receiver.log").write_bytes(_render(rworld))
        _place(run_dir, "owner-note.txt", rworld.eval_note, rworld.eval_note_where)
        _place(run_dir, "run-checklist.txt", rworld.eval_checklist, rworld.eval_checklist_where)
        rworld.eval_dir = run_dir
    return rworld.eval_dir


def _log_lines(rworld) -> list[str]:
    return (_run_dir(rworld) / "receiver.log").read_text(encoding="utf-8").split("\n")[:-1]


class _LazyJudgement:
    """Stands in for `world.judgement`: judges the stick copy the first time a step reads it."""

    def __init__(self, rworld, world):
        self.__dict__.update(_rworld=rworld, _world=world, _j=None)

    def real(self):
        if self.__dict__["_j"] is None:
            run_dir = _run_dir(self.__dict__["_rworld"])
            self.__dict__["_world"].log = run_dir / "receiver.log"
            self.__dict__["_j"] = _ev().judge_run_folder(run_dir)
        return self.__dict__["_j"]

    def __getattr__(self, name):
        if name.startswith("__"):
            raise AttributeError(name)
        return getattr(self.real(), name)


def _bind(rworld, world):
    rworld.eval_proxy = _LazyJudgement(rworld, world)
    world.judgement = rworld.eval_proxy


def _j(rworld):
    return rworld.eval_proxy.real()


def _checklist_bytes(fixture: str):
    if fixture == "absent":
        return None
    if fixture == 'all six keys "yes"':
        lines = [f"{k}: yes" for k in CHECKLIST_KEYS]
    elif fixture == 'all six keys "no"':
        lines = [f"{k}: no" for k in CHECKLIST_KEYS]
    elif fixture == 'all six keys "yes" except "sound on unit: no"':
        lines = [f"{k}: {'no' if k == 'sound on unit' else 'yes'}" for k in CHECKLIST_KEYS]
    elif fixture == 'all six keys "yes" plus the lines "secret text: yes" and "free text"':
        lines = [f"{k}: yes" for k in CHECKLIST_KEYS] + ["secret text: yes", "free text"]
    elif fixture.startswith('"'):
        lines = re.findall(r'"([^"]*)"', fixture)
    else:
        raise AssertionError(f"unknown checklist fixture {fixture!r}")
    return ("\n".join(lines) + "\n").encode("utf-8")


# --- AIR-071: reading the markers -------------------------------------------------------------------------


@given("a stick copy whose log is:")
def eval_stick_copy_docstring(rworld, world, docstring):
    lines = docstring.splitlines()
    _set_run(rworld, [_rec(i * 1000, t) for i, t in enumerate(lines)], 1, world)


@given(parsers.re("a complete stick copy whose session 1 ends with " + Q.format("end")))
def eval_complete_copy_ending(rworld, world, end):
    _go_run(rworld, world)
    _find(rworld, lambda t: t.startswith("session 1 end "))[0]["text"] = end


@given(parsers.re("whose end marker is " + Q.format("stop")))
def eval_whose_end_marker(rworld, stop):
    _stop(rworld)["text"] = stop


@when("the evaluator reads the log")
def eval_reads_log(rworld):
    rworld.eval_read = _ev().read_log(_run_dir(rworld))


@then("the log is complete")
def eval_log_complete(rworld):
    assert rworld.eval_read.complete is True, rworld.eval_read.limitation
    assert rworld.eval_read.limitation is None


@then(parsers.re("the start marker gives app " + Q.format("app") + ", commit " + Q.format("commit") + ", UxPlay commit "
                 + Q.format("uxplay") + ", product " + Q.format("product") + " and parking flag " + Q.format("park")))
def eval_start_marker_fields(rworld, app, commit, uxplay, product, park):
    assert rworld.eval_read.start == {"app": app, "commit": commit, "uxplay": uxplay, "product": product,
                                      "require_park": park}


@then('session 1\'s markers are read in this order: connection accepted, mirroring initialized, codec h264 1920x720, '
      'decoder configured, first frame withheld by gate, first frame rendered, session end with reason '
      '"iphone disconnected"')
def eval_session_marker_order(rworld):
    s = rworld.eval_read.sessions[0]
    assert s.number == 1
    assert s.markers == ["connection accepted", "mirroring initialized", "codec h264 1920x720", "decoder configured",
                         "first frame withheld by gate", "first frame rendered", "session end"], s.markers
    assert s.end_reason == "iphone disconnected"


@then(parsers.re("session 1's end reason is " + Q.format("reason")))
def eval_session_end_reason(rworld, reason):
    assert rworld.eval_read.sessions[0].end_reason == reason


@then(parsers.re("the end marker gives the stop reason " + Q.format("reason")))
def eval_stop_reason(rworld, reason):
    assert rworld.eval_read.stop_reason == reason


# --- AIR-075: where things are written, and the summary -----------------------------------------------------


@given("a synthetic stick copy of one run")
def eval_synthetic_copy(rworld, world):
    _go_run(rworld, world)


@given("a synthetic stick copy of one run judged as a unit run")
def eval_synthetic_copy_unit(rworld, world):
    _go_run(rworld, world)
    rworld.eval_tier = "unit"


@given("a synthetic log fixture judged by the offline suite")
def eval_offline_fixture(rworld, world):
    _go_run(rworld, world)
    rworld.eval_tier = "offline"


def _tree(root: Path) -> dict:
    return {p: p.read_bytes() for p in root.rglob("*") if p.is_file()} if root.exists() else {}


@when("the evaluator judges it")
def eval_judges_it(rworld):
    run_dir = _run_dir(rworld)
    out = rworld.tmp / "out"
    out.mkdir()
    _ev().write_result(run_dir, out, tier=rworld.eval_tier, ios=rworld.eval_ios)
    rworld.eval_out = out
    rworld.eval_written = _tree(out)
    assert rworld.eval_written, "the evaluator wrote nothing"


def _raw_lines(rworld) -> list[bytes]:
    lines = []
    for line in _log_lines(rworld):
        lines.append(line.encode("utf-8"))
        text = line.split(" ", 1)[-1]
        if len(text) >= 12:
            lines.append(text.encode("utf-8"))
    return lines


@then(parsers.re("every raw log it writes is under " + Q.format("folder")))
def eval_raw_logs_under(rworld, folder):
    whole = _render(rworld)
    raw_files = [p for p, b in rworld.eval_written.items() if b == whole]
    assert raw_files, "no raw log was kept"
    for p in raw_files:
        assert p.relative_to(rworld.eval_out).as_posix().startswith(folder), p


@then(parsers.re(Q.format("cmd") + " reports " + Q.format("folder") + " as ignored"))
def eval_git_ignores(cmd, folder):
    assert cmd == "git check-ignore"
    res = subprocess.run(["git", "check-ignore", "-q", folder], cwd=REPO, capture_output=True)
    assert res.returncode == 0, f"{folder} is not ignored ({res.returncode})"


@then(parsers.re("nothing it writes outside " + Q.format("folder") + " contains a raw log line"))
def eval_nothing_raw_outside(rworld, folder):
    raw = _raw_lines(rworld)
    outside = {p: b for p, b in rworld.eval_written.items()
               if not p.relative_to(rworld.eval_out).as_posix().startswith(folder)}
    assert outside, "the evaluator wrote nothing outside the runs folder"
    for p, data in outside.items():
        hits = [line for line in raw if line in data]
        assert hits == [], (p, hits)


@when(parsers.re("the evaluator writes the summary to " + Q.format("dest")))
def eval_writes_summary_to(rworld, dest):
    out = rworld.tmp / "out"
    _ev().write_result(_run_dir(rworld), out, tier=rworld.eval_tier, ios=rworld.eval_ios)
    path = out / dest / f"{TIMESTAMP}-summary.md"
    assert path.is_file(), sorted(p.relative_to(out).as_posix() for p in out.rglob("*"))
    rworld.eval_summary = path.read_bytes().decode("utf-8")


@when("the evaluator writes the summary")
def eval_writes_summary(rworld):
    rworld.eval_summary = _ev().read_run_folder(_run_dir(rworld), tier=rworld.eval_tier, ios=rworld.eval_ios).summary


def _summary(rworld) -> list[str]:
    text = rworld.eval_summary
    assert text.endswith("\n") and "\r" not in text, repr(text)
    return text[:-1].split("\n")


@then("the summary has these S1 summary fields, in order:")
def eval_summary_fields(rworld, datatable):
    fields = [row[0].replace("...", "").strip() for row in datatable[1:]]
    lines = _summary(rworld)
    assert len(lines) >= len(fields), lines
    for field, line in zip(fields, lines):
        assert line.startswith(field), (field, line)
    rworld.eval_fields = fields


@then(parsers.re("(?:it|the summary) has exactly one line starting " + Q.format("prefix")))
def eval_one_line_starting(rworld, prefix):
    hits = [line for line in _summary(rworld) if line.startswith(prefix)]
    assert len(hits) == 1, hits
    rworld.eval_line = hits[0]


@then(parsers.re("the line after " + Q.format("before") + " is the " + Q.format("prefix")
                 + " line, and it is the summary's last line"))
def eval_line_after_is_last(rworld, before, prefix):
    lines = _summary(rworld)
    i = next(i for i, line in enumerate(lines) if line.startswith(before))
    assert i + 1 == len(lines) - 1, lines
    assert lines[i + 1].startswith(prefix), lines[i + 1]


@then(parsers.re("the line after " + Q.format("before") + " is exactly " + Q.format("line")
                 + ", and it is the summary's last line"))
def eval_line_after_is_exactly(rworld, before, line):
    lines = _summary(rworld)
    i = next(i for i, x in enumerate(lines) if x.startswith(before))
    assert lines[i + 1:] == [line], lines[i:]


@then("the summary has no blank line")
def eval_no_blank_line(rworld):
    assert all(line.strip() for line in _summary(rworld)), rworld.eval_summary


@then("the summary has no other line")
def eval_no_other_line(rworld):
    lines = _summary(rworld)
    assert len(lines) == len(rworld.eval_fields) + 1, lines
    assert lines[-1].startswith("Checklist: ")


@then(parsers.re("that line is " + Q.format("line")))
def eval_that_line_is(rworld, line):
    assert rworld.eval_line == line


@then(parsers.re("neither " + Q.format("a") + " nor " + Q.format("b") + " appears anywhere in the summary"))
def eval_neither_appears(rworld, a, b):
    assert a not in rworld.eval_summary and b not in rworld.eval_summary


@then(parsers.re("the summary states " + Q.format("line")))
def eval_summary_states(rworld, line):
    assert line in _summary(rworld), rworld.eval_summary


@then(parsers.re("it never states " + Q.format("text")))
def eval_never_states(rworld, text):
    assert text not in rworld.eval_summary


@then("it says the result is a synthetic fixture result and not evidence about any iPhone")
def eval_says_synthetic(rworld):
    text = rworld.eval_summary
    assert "synthetic fixture result" in text and "not evidence about any iPhone" in text, text


def _field(rworld, prefix) -> str:
    hits = [line for line in _summary(rworld) if line.startswith(prefix)]
    assert len(hits) == 1, hits
    return hits[0]


@then(parsers.re("it states the firmware build " + Q.format("build")))
def eval_states_build(rworld, build):
    assert build in _field(rworld, "Head unit:")


@then(parsers.re("it states the model " + Q.format("model")))
def eval_states_model(rworld, model):
    assert model in _field(rworld, "Device model:").split(": ", 1)[1].split(", ")


@then(parsers.re("it states the iOS version " + Q.format("ios") + " exactly as entered"))
def eval_states_ios(rworld, ios):
    assert _field(rworld, "iOS:") == f"iOS: {ios}"


@then(parsers.re("it states the UxPlay commit " + Q.format("commit")))
def eval_states_uxplay(rworld, commit):
    assert _field(rworld, "UxPlay build commit:") == f"UxPlay build commit: {commit}"


@given(parsers.re("its start marker names product " + Q.format("product") + " and UxPlay commit " + Q.format("uxplay")))
def eval_start_names(rworld, product, uxplay):
    rec = _find(rworld, lambda t: t.startswith("parkcast start"))[0]
    rec["text"] = f"parkcast start app={APP} commit={COMMIT} uxplay={uxplay} product={product} require_park=true"


@given(parsers.re("its session names the model " + Q.format("model")))
def eval_session_model(rworld, model):
    rec = _find(rworld, lambda t: " connection accepted model=" in t)[0]
    rec["text"] = rec["text"].split(" model=")[0] + f" model={model}"


@given(parsers.re("the owner entered the iOS version " + Q.format("ios")))
def eval_owner_ios(rworld, ios):
    rworld.eval_ios = ios


# --- AIR-075(b), AIR-076: verdicts --------------------------------------------------------------------------


@given(parsers.re("a damaged stick log with two sessions that would be GO and (?P<damage>no start marker|no end marker|no final newline)"))
def eval_damaged_log(rworld, world, damage):
    _two_go_runs(rworld, world)
    if damage == "no final newline":
        rworld.eval_no_final_lf = True
        return
    prefix = "parkcast start" if damage == "no start marker" else "parkcast stop"
    rworld.eval_recs = [r for r in rworld.eval_recs if not r["text"].startswith(prefix)]


@given("a receiver log excerpt with these lines:")
def eval_excerpt(rworld, world, docstring):
    lines = docstring.splitlines()
    recs = [_rec(0, START), *(_rec(i * 1000, t) for i, t in enumerate(lines, 1)), _rec((len(lines) + 1) * 1000, STOP)]
    _set_run(rworld, recs, sum(" connection accepted" in t for t in lines), world)


@given(parsers.re("the owner's note says " + Q.format("note")))
def eval_note_says(rworld, note):
    _st(rworld).eval_note = (note + "\n").encode("utf-8")
    rworld.eval_note_where = "run"


@then(parsers.re("the session's verdict is " + VERDICT.format("verdict")))
def eval_session_verdict(rworld, verdict):
    j = _j(rworld)
    assert len(j.sessions) == 1, [s.verdict for s in j.sessions]
    assert j.sessions[0].verdict == verdict, (j.sessions[0].verdict, j.sessions[0].reason)


@then("the session's verdict is not GO")
def eval_session_verdict_not_go(rworld):
    j = _j(rworld)
    assert len(j.sessions) == 1, [s.verdict for s in j.sessions]
    assert j.sessions[0].verdict != "GO", (j.sessions[0].verdict, j.sessions[0].reason)


def _line_numbers(rworld, pred) -> list[int]:
    return [i for i, line in enumerate(_log_lines(rworld), 1) if pred(line.split(" ", 1)[-1])]


@then('the verdict cites the log lines it rests on and "owner-note.txt"')
def eval_cites_lines_and_note(rworld):
    j = _j(rworld)
    cites = j.sessions[0].cites
    assert "owner-note.txt" in cites and "owner-note.txt" in j.cites, (cites, j.cites)
    for marker in (" codec h264 ", " decoder OMX.TCC.avcdec configured ", " first frame rendered "):
        lines = _line_numbers(rworld, lambda t, m=marker: m in f" {t} ")
        assert lines and set(lines) <= set(cites), (marker, lines, cites)
    total = len(_log_lines(rworld))
    assert all(c == "owner-note.txt" or (isinstance(c, int) and 1 <= c <= total) for c in cites), cites


@then(parsers.re("the verdict cites the line " + Q.format("line")))
def eval_cites_line(rworld, line):
    cites = _j(rworld).sessions[0].cites
    lines = _line_numbers(rworld, lambda t: t == line)
    if lines:
        assert set(lines) & set(cites), (line, lines, cites)
    else:
        note = (_run_dir(rworld) / "owner-note.txt").read_bytes().decode("utf-8").strip()
        assert note == line, "the cited line is in neither the log nor the owner's note"
        assert "owner-note.txt" in cites, cites


@given("a session that would be GO")
def eval_go_session(rworld, world):
    _go_run(rworld, world)


@given("a session that would be GO, apart from its note")
def eval_go_session_no_note(rworld, world):
    _go_run(rworld, world)
    rworld.eval_note = None


@given('the run\'s "<timestamp>/" folder has no "owner-note.txt"')
def eval_no_owner_note(rworld):
    _st(rworld).eval_note = None
    rworld.eval_note_where = "run"


NOTE_FIXTURES = {
    '"picture: yes" with one trailing LF': (b"picture: yes\n", "run"),
    '" Picture: YES " with one trailing CRLF': (b" Picture: YES \r\n", "run"),
    '"picture: no" with one trailing LF': (b"picture: no\n", "run"),
    "an empty file": (b"", "run"),
    'the two lines "picture: yes" and "picture: no"': (b"picture: yes\npicture: no\n", "run"),
    'the two lines "picture:" and "yes"': (b"picture:\nyes\n", "run"),
    '"picture: maybe"': (b"picture: maybe", "run"),
    "an unreadable file": (None, "unreadable"),
    '"picture: yes" in "NEMESIS-diag/airplay-receiver/", outside the "<timestamp>/" folder': (NOTE_YES, "outside"),
}


@given(parsers.re("the owner's note fixture is (?P<fixture>.+)"))
def eval_note_fixture(rworld, fixture):
    _st(rworld).eval_note, rworld.eval_note_where = NOTE_FIXTURES[fixture]


@given('its "run-checklist.txt" holds the line "picture: yes"')
def eval_checklist_picture(rworld):
    _st(rworld).eval_checklist = b"picture: yes\n"


@given(parsers.re('(?:its|the run\'s) "run-checklist.txt" is (?P<fixture>.+)'))
def eval_checklist_is(rworld, fixture):
    _st(rworld).eval_checklist = _checklist_bytes(fixture)


def _swap_letterboxed(new):
    return lambda ls: [new if x == "letterboxed: yes" else x for x in ls]


LETTERBOXED = {
    '"letterboxed: yes" is unchanged': lambda ls: ls,
    'the line is " Letterboxed : NO " and every line ends in CRLF': _swap_letterboxed(" Letterboxed : NO "),
    'the line is "letterboxed:"': _swap_letterboxed("letterboxed:"),
    'the line is "letterboxed: maybe"': _swap_letterboxed("letterboxed: maybe"),
    '"letterboxed: yes" appears on two lines': lambda ls: ls + ["letterboxed: yes"],
    'there is no "letterboxed" line': lambda ls: [x for x in ls if not x.startswith("letterboxed")],
}


@given(parsers.re('its "run-checklist.txt" in the run\'s "<timestamp>/" folder holds all six keys "yes" except that '
                  '(?P<change>.+)'))
def eval_checklist_letterboxed(rworld, change):
    lines = LETTERBOXED[change]([f"{k}: yes" for k in CHECKLIST_KEYS])
    eol = "\r\n" if "CRLF" in change else "\n"
    _st(rworld).eval_checklist = "".join(x + eol for x in lines).encode("utf-8")
    rworld.eval_checklist_where = "run"


@given('its "run-checklist.txt" holds all six keys "yes" plus the lines "secret text: yes" and "free text"')
def eval_checklist_extra_lines(rworld):
    _st(rworld).eval_checklist = _checklist_bytes(
        'all six keys "yes" plus the lines "secret text: yes" and "free text"')


@given(parsers.re("the run's checklist is (?P<fixture>.+)"))
def eval_checklist_misplaced(rworld, fixture):
    _st(rworld)
    if fixture == 'an unreadable "run-checklist.txt" in the run\'s "<timestamp>/" folder':
        rworld.eval_checklist, rworld.eval_checklist_where = None, "unreadable"
    elif fixture == ('a "run-checklist.txt" with all six keys "yes" in "NEMESIS-diag/airplay-receiver/", outside the '
                     '"<timestamp>/" folder'):
        rworld.eval_checklist, rworld.eval_checklist_where = _checklist_bytes('all six keys "yes"'), "outside"
    else:
        raise AssertionError(fixture)


@when("the evaluator reads the checklist")
def eval_reads_checklist(rworld):
    rworld.eval_cl = _ev().read_checklist(_run_dir(rworld))


@then(parsers.re("it reports " + Q.format("key") + " as " + Q.format("value")))
def eval_reports_key(rworld, key, value):
    assert rworld.eval_cl.values[key] == value, rworld.eval_cl.values
    rworld.eval_named_key = key


@then(parsers.re("it reports every other key as " + Q.format("value")))
def eval_reports_others(rworld, value):
    others = {k: v for k, v in rworld.eval_cl.values.items() if k != rworld.eval_named_key}
    assert others == {k: value for k in CHECKLIST_KEYS if k != rworld.eval_named_key}, others


@then(parsers.re("it reports every one of the six keys as " + Q.format("value")))
def eval_reports_all(rworld, value):
    assert rworld.eval_cl.values == {k: value for k in CHECKLIST_KEYS}, rworld.eval_cl.values


@then("it names both added lines as malformed")
def eval_names_malformed(rworld):
    assert list(rworld.eval_cl.malformed) == ["secret text: yes", "free text"], rworld.eval_cl.malformed


@given('a receiver log excerpt with two sessions, each with "connection accepted", a "codec h264" line, '
       '"decoder OMX.TCC.avcdec configured" and "first frame rendered"')
def eval_two_rendered_sessions(rworld, world):
    recs = [_rec(0, START), *_session_recs(1, 1000, mirroring=False), *_session_recs(2, 11000, mirroring=False),
            _rec(20000, STOP)]
    _set_run(rworld, recs, 2, world)


@then(parsers.re("(?P<which>each|every) session's verdict is " + VERDICT.format("verdict") + " with the reason "
                 + Q.format("reason")))
def eval_each_session_verdict(rworld, which, verdict, reason):
    j = _j(rworld)
    assert len(j.sessions) == rworld.eval_sessions >= 2, [s.verdict for s in j.sessions]
    for s in j.sessions:
        assert (s.verdict, s.reason) == (verdict, reason), (s.number, s.verdict, s.reason)


@then("neither session is GO")
def eval_neither_go(rworld):
    sessions = _j(rworld).sessions
    assert len(sessions) == 2 and all(s.verdict != "GO" for s in sessions)


@given(parsers.re("the run's logs also contain " + Q.format("record")))
def eval_logs_also_contain(rworld, record):
    _add(rworld, 500, record)


@given('the run\'s logs contain no "carplay listener" record')
def eval_logs_no_carplay(rworld):
    _st(rworld).eval_recs = [r for r in rworld.eval_recs if "carplay listener" not in r["text"]]


@given(parsers.re("the run's logs contain " + Q.format("a") + " and " + Q.format("b")))
def eval_logs_contain_two(rworld, a, b):
    _add(rworld, 500, a)
    _add(rworld, 600, b)


@then("no CarPlay reason is recorded")
def eval_no_carplay_reason(rworld):
    j = _j(rworld)
    assert "carplay present" not in j.run_reasons and "carplay" not in j.reason.lower(), (j.run_reasons, j.reason)


@then(parsers.re("the record names both " + Q.format("a") + " and " + Q.format("b")))
def eval_record_names_both(rworld, a, b):
    j = _j(rworld)
    assert a in j.run_reasons and b in j.run_reasons, j.run_reasons
    assert a in j.reason and b in j.reason, j.reason


# --- AIR-076: timing ----------------------------------------------------------------------------------------


@given(parsers.re(r"a session that would be GO, with its first frame rendered at (?P<t>\d+(?:\.\d+)?) s"))
def eval_go_frame_at(rworld, world, t):
    _go_run(rworld, world, frame_ms=round(float(t) * 1000))


@given(parsers.re("the end marker at " + N.format("t") + " s"))
def eval_end_marker_at(rworld, t):
    stop = _stop(rworld)
    rworld.eval_recs.remove(stop)
    _add(rworld, int(t) * 1000, stop["text"], stop["raw"])


@given('a receiver log excerpt in which session 1 is accepted at 10 s with a "codec h264" line and '
       '"decoder OMX.TCC.avcdec configured", and no first frame or session end before 600 s')
def eval_open_session(rworld, world):
    recs = [_rec(0, START), _rec(10000, f"session 1 connection accepted model={MODEL}"),
            _rec(11000, "Mirroring initialized successfully"), _rec(12000, "session 1 codec h264 1920x720"),
            _rec(13000, "decoder OMX.TCC.avcdec configured"), _rec(620000, STOP)]
    _set_run(rworld, recs, 1, world)


def _record(rworld):
    return _j(rworld).record


@then(parsers.re("the record names the launch time at " + N.format("launch") + " s and the deadline at "
                 + N.format("deadline") + " s"))
def eval_record_launch_deadline(rworld, launch, deadline):
    r = _record(rworld)
    assert (r.launch_ms, r.deadline_ms) == (int(launch) * 1000, int(deadline) * 1000), r


@then(parsers.re("the record names the actual endpoint at " + N.format("end") + " s and the termination reason "
                 + Q.format("reason")))
def eval_record_endpoint(rworld, end, reason):
    r = _record(rworld)
    assert (r.endpoint_ms, r.termination) == (int(end) * 1000, reason), r


@then(parsers.re("the record names the launch, the deadline at " + N.format("deadline") + " s, the endpoint and the "
                 "reason " + Q.format("reason")))
def eval_record_all(rworld, deadline, reason):
    r = _record(rworld)
    assert r.launch_ms == 0 and r.deadline_ms == int(deadline) * 1000, r
    assert r.endpoint_ms == r.deadline_ms and r.termination == reason, r


@then(parsers.re("the record names the deadline at " + N.format("deadline") + " s and the actual endpoint at "
                 + N.format("end") + " s, the end marker"))
def eval_record_end_marker(rworld, deadline, end):
    r = _record(rworld)
    assert (r.deadline_ms, r.endpoint_ms, r.termination) == (int(deadline) * 1000, int(end) * 1000, "end marker"), r


@given(parsers.re("a stick copy with two app starts, the second start marker at " + N.format("t") + " s"))
def eval_two_app_starts(rworld, world, t):
    ms = int(t) * 1000
    recs = [_rec(0, START), _rec(ms - 1000, "parkcast stop reason=destroy"), _rec(ms, START)]
    _set_run(rworld, recs, 0, world)


@given(parsers.re(r"a session in the second app start that would be GO, with its first frame rendered at "
                  r"(?P<t>\d+(?:\.\d+)?) s"))
def eval_second_start_session(rworld, t):
    second = [r for r in rworld.eval_recs if r["text"].startswith("parkcast start")][1]["ms"]
    frame = round(float(t) * 1000)
    rworld.eval_recs += [*_session_recs(1, second + 1000, frame), _rec(max(frame + 2000, 700000), STOP)]
    rworld.eval_sessions = 1


@then(parsers.re("the deadline is at " + N.format("deadline") + " s, counted from the first start marker"))
def eval_deadline_first_start(rworld, deadline):
    r = _record(rworld)
    assert (r.launch_ms, r.deadline_ms) == (0, int(deadline) * 1000), r


PREFIXES = {
    "missing": "",
    '"12x000 " (a letter among the digits)': "12x000 ",
    '"120000" with no space after the digits': "120000",
}


@given(parsers.re('the "first frame rendered" record\'s prefix is (?P<prefix>missing|"12x000 " \\(a letter among the '
                  'digits\\)|"120000" with no space after the digits)'))
def eval_frame_prefix(rworld, prefix):
    rec = _find(rworld, lambda t: t.endswith(" first frame rendered"))[0]
    rec["raw"] = PREFIXES[prefix] + rec["text"]


@given("a stick copy with two sessions that would each be GO")
def eval_two_go_sessions(rworld, world):
    _two_go_runs(rworld, world)


@given('the run\'s first "parkcast start" record has no uptime prefix')
def eval_start_unprefixed(rworld):
    rec = _find(rworld, lambda t: t.startswith("parkcast start"))[0]
    rec["raw"] = rec["text"]


@given(parsers.re("a receiver log in which session 1 is accepted at " + N.format("a") + " s and logs its end record at "
                  + N.format("e") + " s"))
def eval_session_accepted_and_ended(rworld, world, a, e):
    recs = [_rec(0, START), _rec(int(a) * 1000, f"session 1 connection accepted model={MODEL}"),
            _rec(int(e) * 1000, "session 1 end reason=iphone disconnected withheld=0"), _rec(70000, STOP)]
    _set_run(rworld, recs, 1, world)


@given(parsers.re("session 2 is accepted at " + N.format("a") + " s"))
def eval_session_two_accepted(rworld, a):
    _add(rworld, int(a) * 1000, f"session 2 connection accepted model={MODEL}")
    rworld.eval_sessions = 2


@given(parsers.re("the UxPlay line " + Q.format("line") + " at " + N.format("a") + " s, and again at "
                  + N.format("b") + " s"))
def eval_uxplay_line_twice(rworld, line, a, b):
    _add(rworld, int(a) * 1000, line)
    _add(rworld, int(b) * 1000, line)


@then(parsers.re("the line at " + N.format("t") + " s belongs to no session"))
def eval_line_no_session(rworld, t):
    owners = _j(rworld).line_session
    assert int(t) * 1000 in owners and owners[int(t) * 1000] is None, owners


@then(parsers.re("the line at " + N.format("t") + " s belongs to session " + N.format("n")))
def eval_line_session(rworld, t, n):
    assert _j(rworld).line_session.get(int(t) * 1000) == int(n), _j(rworld).line_session


# --- AIR-079(g): the first on-unit run's procedure (@host) ---------------------------------------------------

PROCEDURE_HEADING = re.compile(r"^#+ .*first on-unit S3 run", re.IGNORECASE)


@given("the procedure for the first on-unit S3 run in the repository")
def eval_procedure(rworld):
    found = []
    for doc in sorted((REPO / "docs").rglob("*.md")):
        lines = doc.read_text(encoding="utf-8").splitlines()
        for i, line in enumerate(lines):
            if PROCEDURE_HEADING.match(line):
                found.append((doc, lines[i + 1:]))
    if len(found) != 1:
        pytest.fail(f"expected one procedure for the first on-unit S3 run under docs/, found {len(found)}")
    rworld.eval_procedure = found[0][1]


@when("its steps are read")
def eval_procedure_steps(rworld):
    steps = []
    for line in rworld.eval_procedure:
        if line.startswith("#"):
            break
        m = re.match(r"^\d+\.\s+(.*)$", line)
        if m:
            steps.append(m.group(1))
        elif steps and line.startswith("   ") and line.strip():
            steps[-1] += " " + line.strip()
    assert steps, "the procedure has no numbered steps"
    rworld.eval_last_step = steps[-1]


@then("its last step tells the owner to exit ParkCast and confirm wireless CarPlay connects as it did before the run")
def eval_last_step_exit(rworld):
    step = rworld.eval_last_step.lower()
    assert "exit parkcast" in step and "wireless carplay" in step and "before the run" in step, rworld.eval_last_step


@then(parsers.re("that step records " + Q.format("yes") + " or " + Q.format("no") + " in " + Q.format("file")))
def eval_last_step_records(rworld, yes, no, file):
    step = rworld.eval_last_step
    assert yes in step and no in step and file in step, step


@then(parsers.re("that step says not to record it in " + Q.format("file")))
def eval_last_step_not_note(rworld, file):
    step = rworld.eval_last_step
    assert file in step and re.search(r"\bnot\b", step), step
