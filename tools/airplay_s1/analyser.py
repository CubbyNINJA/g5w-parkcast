"""Turn a UxPlay 1.74 log into observations tied to the lines they came from (AIR-008, AIR-009,
AIR-028, AIR-029). Message texts are in docs/evidence/UXPLAY_LOG_SIGNATURES.md. Matching looks for
the message anywhere in a line, so a logger prefix doesn't matter. Anything not logged stays
unknown (None)."""

from __future__ import annotations

import dataclasses
import re
from pathlib import Path

from airplay_s1 import logfile

BANNER = re.compile(r"UxPlay \S+: An Open-Source AirPlay mirroring and audio-streaming server\.")
CONNECT = re.compile(r"connection request from (?P<name>.*) \((?P<model>[^()]*)\) with deviceID = (?P<device>\S+)")
ARRIVE = re.compile(r"raop_rtp_mirror width_source = \S+")
CODEC = re.compile(r"\*\*\* codec is (?P<codec>h264|h265): geometry is \(w,h\)=\((?P<w>\d+),(?P<h>\d+)\) \(source\)")
HEVC_REFUSAL = re.compile(r"raop_rtp_mirror: received type 0x01 packet with no payload:")
HEVC_UNASKED = re.compile(r"video is h265 but the -h265 option was not used")
AUDIO = re.compile(r"ct=(?P<ct>\d+) spf=\d+ usingScreen=\d+ isMedia=\d+\s+audioFormat=(?P<fmt>0x[0-9a-fA-F]+)")
SIZE = re.compile(r"^(?P<w>\d+)x(?P<h>\d+)(?:@\d+)?$")
CODEC_NAMES = {"h264": "H.264", "h265": "HEVC"}


@dataclasses.dataclass
class Observation:
    value: object
    line: int
    message: str

    def to_dict(self) -> dict:
        value = list(self.value) if isinstance(self.value, tuple) else self.value
        return {"value": value, "line": self.line, "message": self.message}


@dataclasses.dataclass
class Session:
    start_line: int
    model: str | None = None
    connection: Observation | None = None
    arrived: Observation | None = None
    started: Observation | None = None
    codec: Observation | None = None
    resolution: Observation | None = None
    audio_format: Observation | None = None
    hevc_request: Observation | None = None
    hevc_stream: Observation | None = None
    ios_version: str | None = None  # UxPlay doesn't log it; the owner enters it (OD-11)

    OBSERVATIONS = ("connection", "arrived", "started", "codec", "resolution", "audio_format", "hevc_request",
                    "hevc_stream")

    def to_dict(self) -> dict:
        out = {"start_line": self.start_line, "model": self.model, "ios_version": self.ios_version}
        for name in self.OBSERVATIONS:
            obs = getattr(self, name)
            out[name] = obs.to_dict() if obs else None
        return out


@dataclasses.dataclass
class Report:
    complete: bool
    limitation: str | None
    lines_read: int
    sessions: list[Session]
    advertised_size: tuple[int, int] | None = None
    advertised_source: str | None = None

    def to_dict(self) -> dict:
        return {"complete": self.complete, "limitation": self.limitation, "lines_read": self.lines_read,
                "advertised_size": list(self.advertised_size) if self.advertised_size else None,
                "advertised_source": self.advertised_source,
                "sessions": [s.to_dict() for s in self.sessions]}


def _advertised(command) -> tuple[int, int] | None:
    if not command:
        return None
    args = list(command)
    for i, a in enumerate(args[:-1]):
        if a == "-s":
            m = SIZE.match(args[i + 1])
            if m:
                return int(m["w"]), int(m["h"])
    return None


def _read(path: Path) -> tuple[list[str | None], bool, bool]:
    """The log's lines (None where a line can't be decoded), whether it could be read at all, and
    whether it ends with a newline."""
    try:
        data = Path(path).read_bytes()
    except OSError:
        return [], False, False
    ends_with_newline = data.endswith(b"\n")
    rows: list[str | None] = []
    for raw in data.split(b"\n")[:-1] if ends_with_newline else data.split(b"\n"):
        raw = raw[:-1] if raw.endswith(b"\r") else raw
        try:
            rows.append(raw.decode("utf-8"))
        except UnicodeDecodeError:
            rows.append(None)
    if rows == [""] and not data:
        rows = []
    return rows, True, ends_with_newline


def _limitation(rows: list[str | None], readable: bool, ends_with_newline: bool) -> str | None:
    """AIR-028(c): the first limitation that applies, in order."""
    if not readable or any(r is None for r in rows):
        return "unreadable"
    if not rows or not logfile.is_start_marker(rows[0]):
        return "no start marker"
    if not any(BANNER.search(r) for r in rows):
        return "no UxPlay banner"
    if not logfile.is_end_marker(rows[-1]) or not ends_with_newline:
        return "truncated"
    return None


def _observe(session: Session, n: int, text: str) -> None:
    def first(name: str, value, match: re.Match) -> None:
        if getattr(session, name) is None:
            setattr(session, name, Observation(value, n, match.group(0)))

    if m := ARRIVE.search(text):
        first("arrived", True, m)
    if m := CODEC.search(text):
        name = CODEC_NAMES[m["codec"]]
        first("started", True, m)
        first("codec", name, m)
        first("resolution", (int(m["w"]), int(m["h"])), m)
        if name == "HEVC":
            first("hevc_stream", True, m)
    if m := HEVC_REFUSAL.search(text):
        first("hevc_request", True, m)
    if m := HEVC_UNASKED.search(text):
        first("hevc_stream", True, m)
    if m := AUDIO.search(text):
        first("audio_format", f"ct={m['ct']} audioFormat={m['fmt']}", m)


def analyse(log_path, command=None) -> Report:
    rows, readable, ends_with_newline = _read(Path(log_path))
    sessions: list[Session] = []
    for n, text in enumerate(rows, 1):
        if text is None:
            continue
        if m := CONNECT.search(text):
            sessions.append(Session(start_line=n, model=m["model"], connection=Observation(m["model"], n, m.group(0))))
            continue
        if sessions:
            _observe(sessions[-1], n, text)
    limitation = _limitation(rows, readable, ends_with_newline)
    size = _advertised(command)
    return Report(complete=limitation is None, limitation=limitation, lines_read=len(rows), sessions=sessions,
                  advertised_size=size, advertised_source="launch configuration" if size else None)
