"""The runner's start and end markers in a run's log (AIR-028)."""

from __future__ import annotations

START = "# airplay-s1 runner start"
END = "# airplay-s1 runner end"


def _t(t: float) -> str:
    return f"{t:.3f}"


def start_marker(t: float) -> str:
    return f"{START} t={_t(t)}"


def end_marker(reason: str, t: float) -> str:
    return f"{END} reason={reason} t={_t(t)}"


def is_start_marker(line: str) -> bool:
    return line.startswith(START + " ")


def is_end_marker(line: str) -> bool:
    return line.startswith(END + " ")
