"""The runner's start and end markers in a run's log (AIR-028)."""

from __future__ import annotations

START = "# airplay-s1 runner start"
END = "# airplay-s1 runner end"


def _t(t: float) -> str:
    return f"{t:.3f}"


def start_marker(t: float) -> str:
    return f"{START} t={_t(t)}"


def end_marker(reason: str, t: float, pid: int | None = None) -> str:
    """The end marker. `pid` names a UxPlay process that was still running at the end (OD-42)."""
    marker = f"{END} reason={reason} t={_t(t)}"
    return marker if pid is None else f"{marker} still-running-pid={pid}"


def is_start_marker(line: str) -> bool:
    return line.startswith(START + " ")


def is_end_marker(line: str) -> bool:
    return line.startswith(END + " ")
