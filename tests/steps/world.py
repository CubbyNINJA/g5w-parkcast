"""Shared state and helpers for the S1 step definitions.

`world` holds one scenario's fakes and results. Paths written in the scenarios under
C:\\uxplay-s1\\ map into the scenario's temporary directory. Log excerpts are wrapped into
complete logs as specs/README.md ("Log excerpts") says: start marker and banner before, end
marker and final newline after, so excerpt line N is log line N + 2.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path, PureWindowsPath
from types import SimpleNamespace

import pytest

from steps.fakes import (Events, FakeClock, FakeCompiler, FakeFirewall, FakeLauncher, FakeOwner,
                         FakeUpstream, s1)

PROJECT = Path(__file__).resolve().parents[2]
REPO = PROJECT.parents[1]
BANNER = "UxPlay 1.74: An Open-Source AirPlay mirroring and audio-streaming server."
WRAP_OFFSET = 2
FIXTURE_ROOT = "C:\\uxplay-s1\\"
COMMIT = "05b0275cb1b726592700e47a6e0307544b3d3a52"

CONNECT = "connection request from Fixture iPhone (iPhone17,1) with deviceID = AA:BB:CC:DD:EE:FF"
LOST = "*** ERROR lost connection with client (network problem?)"
SETUP = "Mirroring initialized successfully"
ACCEPT = "raop_rtp_mirror accepting client"
ARRIVE = ("raop_rtp_mirror width_source = 1920.000000 height_source = 886.000000 x_offset = 0.000000 "
          "y_offset = 0.000000 width = 1920.000000 height = 886.000000")
CODEC_H264 = "*** codec is h264: geometry is (w,h)=(1920,886) (source); (x,y)=(0,0); (w,h)=(1920,886)"
HEVC_REFUSAL = [
    "raop_rtp_mirror: received type 0x01 packet with no payload:",
    "this indicates non-h264 video but Airplay features bit 42 (SupportsScreenMultiCodec) is not set",
    'use startup option "-h265" to set this bit and support h265 (4K) video',
]
AUDIO_ONLY = "ct=2 spf=352 usingScreen=0 isMedia=1  audioFormat=0x40000"
FEATURES = 'register_dnssd: advertised AirPlay service with "Features" code = 0x5A7FFEE6'

# One session's excerpt, by the verdict it should get (05_verdict.feature).
SESSION = {
    "GO": [CONNECT, SETUP, ACCEPT, ARRIVE, CODEC_H264],
    "NO-GO": [CONNECT, SETUP, ACCEPT, ARRIVE, *HEVC_REFUSAL],
    "INCONCLUSIVE": [CONNECT, SETUP, ACCEPT, ARRIVE],
}


@pytest.fixture
def world(tmp_path, monkeypatch):
    events = Events()
    clock = FakeClock(events)
    w = SimpleNamespace(
        tmp=tmp_path, events=events, clock=clock,
        firewall=FakeFirewall(events), launcher=FakeLauncher(events, clock), owner=FakeOwner(),
        upstream=FakeUpstream(events), compiler=FakeCompiler(events),
        work_root=tmp_path / "uxplay-s1", runs_root=tmp_path / "runs", cwd=tmp_path / "cwd",
        build_root=tmp_path / "uxplay-s1", provenance=None, record=None, interrupted=False,
        log=None, report=None, judgement=None, notes={}, end=None, command=None, ios=None,
        planned=False, block_log=False, unrelated=None,
    )
    w.cwd.mkdir()
    monkeypatch.chdir(w.cwd)
    return w


def host_path(w, written: str) -> Path:
    """Map a scenario path under C:\\uxplay-s1\\ into the scenario's temporary directory."""
    if written.lower().startswith(FIXTURE_ROOT.lower()):
        rest = PureWindowsPath(written[len(FIXTURE_ROOT):]).parts
        return w.tmp.joinpath("uxplay-s1", *rest)
    return Path(written)


def make_verified_build(w, exe: Path | None = None):
    """A built uxplay.exe and its provenance, saved where the runner looks for it."""
    exe = exe or w.work_root / "build" / "uxplay.exe"
    exe.parent.mkdir(parents=True, exist_ok=True)
    exe.write_bytes(b"MZ fake uxplay 1.74")
    build = s1("build")
    w.provenance = build.Provenance(
        commit=COMMIT, version="1.74", build_root=str(w.build_root), source_dir=str(w.work_root / "src"),
        build_dir=str(exe.parent), executable=str(exe), sha256=hashlib.sha256(exe.read_bytes()).hexdigest(),
        dependencies={"mingw-w64-ucrt-x86_64-gstreamer": "1.28.9"}, commands=["cmake -G Ninja ..", "ninja"])
    build.save_provenance(w.provenance, w.work_root)
    return w.provenance


def run_dir(w) -> Path:
    return w.runs_root / "fixture-run"


def run(w):
    """Run the runner once, with whatever the scenario's Given and When steps set up."""
    if w.record is not None or w.interrupted:
        return w.record
    if w.provenance is None:
        make_verified_build(w)
    rd = run_dir(w)
    if w.block_log:
        rd.mkdir(parents=True, exist_ok=True)
        (rd / "uxplay.log").mkdir()
    try:
        w.record = s1("runner").run(w.provenance, rd, firewall=w.firewall, launcher=w.launcher,
                                    clock=w.clock, owner=w.owner, timeout=600)
    except KeyboardInterrupt:
        w.interrupted = True
        w.record = None
    return w.record


def wrap(excerpt: list[str]) -> str:
    logfile = s1("logfile")
    return "\n".join([logfile.start_marker(0.0), BANNER, *excerpt, logfile.end_marker("owner ended", 600.0)]) + "\n"


def write_log(w, text: str | bytes, name: str = "uxplay.log") -> Path:
    path = w.tmp / "logs" / name
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(text, str):
        path.write_text(text, encoding="utf-8", newline="\n")
    else:
        path.write_bytes(text)
    w.log = path
    return path


def sessions_excerpt(verdicts: list[str]) -> list[str]:
    lines: list[str] = []
    for i, v in enumerate(verdicts):
        if i:
            lines.append(LOST)
        lines += SESSION[v]
    return lines


def analyse(w):
    if w.report is None:
        w.report = s1("analyser").analyse(w.log, command=w.command)
    return w.report


def judge(w, notes=None):
    end = w.end or (None, None)
    return s1("evaluator").judge(analyse(w), notes=dict(w.notes if notes is None else notes),
                                 ended_at=end[0], end_reason=end[1])


def log_line(w, n: int) -> int:
    """Excerpt line n as a line number in the wrapped log."""
    return n + WRAP_OFFSET


def read_json(path: Path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def snapshot(*roots: Path) -> dict[str, tuple[int, int]]:
    """Every file under the roots with its size and modification time."""
    out = {}
    for root in roots:
        if root.exists():
            for p in sorted(root.rglob("*")):
                if p.is_file():
                    st = p.stat()
                    out[str(p)] = (st.st_size, st.st_mtime_ns)
                elif p.is_dir():
                    out[str(p) + "/"] = (0, 0)
    return out
