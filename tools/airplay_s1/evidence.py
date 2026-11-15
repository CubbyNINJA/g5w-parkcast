"""What a run keeps, what it publishes, and what it claims (AIR-012 to AIR-014; OD-11, OD-15, OD-21).

The whole record stays in the run's folder under runs/. A summary carries only the verdict, the
device model, the iOS version and the tier: never client names, deviceIDs or addresses. Nothing
showing the phone's screen is ever written."""

from __future__ import annotations

import dataclasses
import json
import os
import re
from pathlib import Path

OWNER_ENTERED = "owner-entered"
FULL_IOS = re.compile(r"^(?P<version>\d+(?:\.\d+){1,2})\s+\((?P<build>[0-9A-Za-z]+)\)$")
VERSION_ONLY = re.compile(r"^\d+(?:\.\d+){1,2}$")
QUESTION = ("Enter the iPhone's full iOS version with its build number, exactly as shown in "
            "Settings > General > About (for example 27.0 (24A5260a)):")
SCREEN_SUFFIXES = {".h264", ".h265", ".hevc", ".264", ".265", ".mp4", ".mov", ".mkv", ".ts", ".avi", ".png", ".jpg",
                   ".jpeg", ".bmp", ".gif", ".webp", ".tif", ".tiff", ".yuv", ".raw", ".aac", ".alac", ".pcm", ".wav"}
SCREEN_PREFIXES = ("videodump", "audiodump", "screenshot", "frame")


@dataclasses.dataclass
class Evidence:
    ios: str | None
    ios_provenance: str | None
    missing: list[str]
    incomplete: bool


def _write_json(path: Path, data) -> None:
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(data, indent=2), encoding="utf-8")
    os.replace(tmp, path)


def _ios(owner, known: dict | None) -> tuple[str | None, str | None, list[str]]:
    """The exact iOS version and build, asking the owner unless both are already known (OD-21)."""
    known = known or {}
    version, build = known.get("version"), known.get("build")
    if version and build:
        return f"{version} ({build})", known.get("provenance"), []
    answer = owner.ask(QUESTION)
    answer = answer.strip() if isinstance(answer, str) else None
    if answer and FULL_IOS.match(answer):
        return answer, OWNER_ENTERED, []
    if answer and VERSION_ONLY.match(answer):
        return answer, OWNER_ENTERED, ["ios build"]
    missing = ([] if version else ["ios version"]) + ["ios build"]
    return version, None, missing


def write(run_dir, *, record: dict, report, judgement, owner, ios: dict | None = None) -> Evidence:
    run_dir = Path(run_dir)
    run_dir.mkdir(parents=True, exist_ok=True)
    value, provenance, missing = _ios(owner, ios)
    _write_json(run_dir / "report.json", report.to_dict())
    _write_json(run_dir / "verdict.json", judgement.to_dict())
    run = dict(record)
    run.update(ios={"value": value, "provenance": provenance}, notes=dict(judgement.notes),
               evidence_complete=not missing, missing=list(missing))
    _write_json(run_dir / "run.json", run)
    return Evidence(ios=value, ios_provenance=provenance, missing=list(missing), incomplete=bool(missing))


def summary(run_dir, tier: str) -> str:
    run_dir = Path(run_dir)
    run = json.loads((run_dir / "run.json").read_text(encoding="utf-8"))
    verdict = json.loads((run_dir / "verdict.json").read_text(encoding="utf-8"))
    report = json.loads((run_dir / "report.json").read_text(encoding="utf-8"))
    models = sorted({s["model"] for s in report["sessions"] if s.get("model")})
    ios = (run.get("ios") or {}).get("value") or "missing"
    lines = [
        f"# AirPlay receiver S1 result ({run_dir.name})",
        "",
        f"Tier: {tier}",
        f"Verdict: {verdict['verdict']} ({verdict['reason']})",
        f"Sessions: {', '.join(s['verdict'] for s in verdict['sessions']) or 'none'}",
        f"Device model: {', '.join(models) or 'unknown'}",
        f"iOS: {ios}",
        f"UxPlay build commit: {run.get('commit') or 'not recorded'}",
        "Head unit: untested. This result says nothing about decoding, networking or playback on the head unit.",
    ]
    if tier == "offline":
        lines.append("This is a synthetic fixture result. It is not evidence about any iPhone.")
    return "\n".join(lines) + "\n"


def write_summary(dest_dir, run_dir, tier: str) -> Path:
    dest = Path(dest_dir)
    dest.mkdir(parents=True, exist_ok=True)
    path = dest / f"{Path(run_dir).name}-summary.md"
    path.write_text(summary(run_dir, tier), encoding="utf-8", newline="\n")
    return path


def screen_content(dirs) -> list[Path]:
    """Video, image and stream-dump files under the given folders (AIR-013)."""
    found = []
    for d in dirs:
        d = Path(d)
        if not d.exists():
            continue
        for f in d.rglob("*"):
            if f.is_file() and (f.suffix.lower() in SCREEN_SUFFIXES or f.name.lower().startswith(SCREEN_PREFIXES)):
                found.append(f)
    return sorted(found)


def latest_run(runs_root) -> Path:
    runs = [p for p in Path(runs_root).iterdir() if p.is_dir() and (p / "run.json").is_file()]
    if not runs:
        raise FileNotFoundError(f"no run under {runs_root}")
    return max(runs, key=lambda p: (p / "run.json").stat().st_mtime)
