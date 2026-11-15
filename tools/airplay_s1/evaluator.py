"""Session and run verdicts from an analyser report (AIR-009 to AIR-011; OD-12, OD-13, OD-17 to
OD-19, OD-25, OD-26). Owner notes are kept but never change a verdict (OD-09, OD-10)."""

from __future__ import annotations

import dataclasses

GO, NO_GO, INCONCLUSIVE = "GO", "NO-GO", "INCONCLUSIVE"
EVIDENCE_ERROR = "evidence error"


@dataclasses.dataclass
class SessionVerdict:
    verdict: str
    reason: str
    limitation: str | None
    cites: list[int]

    def to_dict(self) -> dict:
        return dataclasses.asdict(self)


@dataclasses.dataclass
class Judgement:
    verdict: str
    reason: str
    limitation: str | None
    cites: list[int]
    sessions: list[SessionVerdict]
    notes: dict
    examined_to_line: int
    endpoint: float | None

    def to_dict(self) -> dict:
        return {"verdict": self.verdict, "reason": self.reason, "limitation": self.limitation, "cites": self.cites,
                "sessions": [s.to_dict() for s in self.sessions], "notes": dict(self.notes),
                "examined_to_line": self.examined_to_line, "endpoint": self.endpoint}


def _num(t) -> str:
    return str(int(t)) if float(t).is_integer() else f"{t:.1f}"


def _extent(report, ended_at) -> str:
    end = f"ended at second {_num(ended_at)}" if ended_at is not None else "observation endpoint not recorded"
    return f"log read to line {report.lines_read}; run {end}"


def _session(s, report, ended_at) -> SessionVerdict:
    if s.codec is not None and s.codec.value == "H.264":
        reason, cites = "H.264 mirroring started", [s.codec.line]
        if s.hevc_request is not None:
            reason += f" (after an earlier HEVC request at line {s.hevc_request.line})"
            cites = [s.hevc_request.line, s.codec.line]
        return SessionVerdict(GO, reason, None, cites)
    if s.hevc_request is not None:
        return SessionVerdict(NO_GO, "HEVC requested", None, [s.hevc_request.line])
    if s.hevc_stream is not None:
        return SessionVerdict(NO_GO, "HEVC stream started, not H.264", None, [s.hevc_stream.line])
    if s.arrived is not None:
        return SessionVerdict(INCONCLUSIVE, EVIDENCE_ERROR, "codec not identified", [s.arrived.line])
    return SessionVerdict(NO_GO, f"connected but no H.264 mirroring ({_extent(report, ended_at)})", None,
                          [s.connection.line])


def judge(report, notes=None, ended_at=None, end_reason=None) -> Judgement:
    sessions = [_session(s, report, ended_at) for s in report.sessions]

    def decided(verdict: str, reason: str, limitation=None) -> Judgement:
        cites = sorted({n for s in sessions if s.verdict == verdict for n in s.cites}) if limitation is None else []
        return Judgement(verdict, reason, limitation, cites, sessions, dict(notes or {}), report.lines_read, ended_at)

    if not report.complete:
        return decided(INCONCLUSIVE, EVIDENCE_ERROR, report.limitation)
    if not sessions:
        return decided(INCONCLUSIVE, "no iPhone connected")
    if any(s.verdict == GO for s in sessions):
        return decided(GO, "H.264 mirroring started in at least one complete session")
    if any(s.verdict == NO_GO for s in sessions):
        return decided(NO_GO, "no session started H.264 mirroring")
    return decided(INCONCLUSIVE, "every session was inconclusive")
