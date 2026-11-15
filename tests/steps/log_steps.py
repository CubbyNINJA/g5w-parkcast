"""Steps for 04_analyser.feature and 05_verdict.feature (AIR-008 to AIR-011, AIR-028, AIR-029)."""

from __future__ import annotations

import re

from pytest_bdd import given, parsers, then, when

from steps.fakes import s1
from steps.world import (ACCEPT, ARRIVE, AUDIO_ONLY, BANNER, CODEC_H264, CONNECT, DECISIVE, FEATURES, SESSION, SETUP,
                         WRAP_OFFSET, analyse, judge, log_line, sessions_excerpt, wrap, write_log)

Q = r'"(?P<{}>[^"]*)"'
N = r"(?P<{}>\d+)"
VERDICT = r"(?P<{}>GO|NO-GO|INCONCLUSIVE)"

# Each one-session fixture, with the excerpt lines its verdict must cite.
ONE_SESSION = {
    "H.264 mirroring started": (SESSION["GO"], [5]),
    "the iPhone requested HEVC": (SESSION["NO-GO"], [5]),
    "no video stream arrived": ([CONNECT], [1]),
    "only mirroring setup and the connection were logged": ([CONNECT, SETUP, ACCEPT], [1]),
    "only audio played": ([CONNECT, AUDIO_ONLY], [1]),
    "the iPhone requested HEVC and then H.264 mirroring started": ([*SESSION["NO-GO"], ARRIVE, CODEC_H264], [5, 9]),
    "a video stream arrived and no codec line followed": (SESSION["INCONCLUSIVE"], [4]),
}
WHOLE_LOG = {
    "no iPhone ever connected": [],
    "an iPhone connected but sent nothing else": [CONNECT],
    "only audio played": [CONNECT, AUDIO_ONLY],
    "the receiver advertised H.264 and no iPhone connected": [FEATURES],
}


def _session(w, i: int = 1):
    sessions = analyse(w).sessions
    assert len(sessions) >= i, f"only {len(sessions)} sessions"
    return sessions[i - 1]


def _obs_at(obs, n: int, w) -> None:
    assert obs is not None, "not observed"
    assert obs.line == log_line(w, n), f"observed at log line {obs.line}, expected excerpt line {n}"


def _judgement(w):
    if w.judgement is None:
        w.judgement = judge(w)
    return w.judgement


def _lines(w) -> list[str]:
    return w.log.read_text(encoding="utf-8", errors="replace").splitlines()


# --- 04 analyser: logs ----------------------------------------------------------------------

@given("a UxPlay log excerpt with these lines:")
def _(world, docstring):
    write_log(world, wrap(docstring.splitlines()))


@given(parsers.re(r"the receiver advertised (?P<w>\d+)x(?P<h>\d+) at (?P<fps>\d+) fps"))
def _(world, w, h, fps):
    world.command = ["uxplay.exe", "-n", "HU-Test", "-nh", "-s", f"{w}x{h}@{fps}", "-d"]


DAMAGE = {
    "a log file that can't be read": None,
    "a log that isn't valid UTF-8 text": "utf8",
    "a log without the runner's start marker": "no start",
    "a log with no UxPlay version banner": "no banner",
    "a log without the runner's end marker": "no end",
    "a log whose last line has no newline": "no newline",
    "a log with neither the banner nor the end marker": "no banner, no end",
    "a log with neither the start marker nor the end marker": "no start, no end",
}


def damaged(w, kind, lines: list[str]):
    text = wrap(lines)
    rows = text.splitlines()
    if kind is None:
        path = w.tmp / "logs" / "uxplay.log"
        path.mkdir(parents=True)
        w.log = path
        return
    if kind == "utf8":
        # An unreadable line of its own, after the session and before the end marker, so the
        # session's own lines stay intact.
        head, end = text.rstrip("\n").rsplit("\n", 1)
        write_log(w, head.encode("utf-8") + b"\n\xff\xfe unreadable bytes\n" + end.encode("utf-8") + b"\n")
        return
    if "no start" in kind:
        rows = rows[1:]
    if "no banner" in kind:
        rows = [r for r in rows if r != BANNER]
    if "no end" in kind:
        rows = rows[:-1]
    out = "\n".join(rows) + "\n"
    if kind == "no newline":
        out = out[:-1]
    write_log(w, out)


@given(parsers.re("(?P<what>" + "|".join(re.escape(k) for k in DAMAGE) + ")"))
def _(world, what):
    damaged(world, DAMAGE[what], [CONNECT])


@when("the analyser reads the log")
def _(world):
    analyse(world)


# --- 04 analyser: observations --------------------------------------------------------------

@then(parsers.re("an iPhone connection is observed at line " + N.format("n") + " with model " + Q.format("model")))
def _(world, n, model):
    s = _session(world)
    _obs_at(s.connection, int(n), world)
    assert s.model == model


@then(parsers.re("a mirroring video stream arriving is observed at line " + N.format("n")))
def _(world, n):
    _obs_at(_session(world).arrived, int(n), world)


@then("a mirroring video stream arriving is not observed")
def _(world):
    assert _session(world).arrived is None


@then(parsers.re("mirroring video started is observed at line " + N.format("n")))
def _(world, n):
    _obs_at(_session(world).started, int(n), world)


@then("mirroring video started is not observed")
def _(world):
    assert _session(world).started is None


@then(parsers.re("the video codec is " + Q.format("codec") + ", observed at line " + N.format("n")))
def _(world, codec, n):
    s = _session(world)
    _obs_at(s.codec, int(n), world)
    assert s.codec.value == codec


@then("the video codec is unknown")
def _(world):
    assert _session(world).codec is None


@then(parsers.re(r"the stream resolution is (?P<w>\d+)x(?P<h>\d+), observed at line " + N.format("n")))
def _(world, w, h, n):
    s = _session(world)
    _obs_at(s.resolution, int(n), world)
    assert s.resolution.value == (int(w), int(h))


@then("the stream resolution is unknown")
def _(world):
    assert _session(world).resolution is None


@then(parsers.re("the audio format is " + Q.format("fmt") + ", observed at line " + N.format("n")))
def _(world, fmt, n):
    s = _session(world)
    _obs_at(s.audio_format, int(n), world)
    assert s.audio_format.value == fmt


@then("the iOS version is unknown")
def _(world):
    assert _session(world).ios_version is None


@then(parsers.re("an HEVC request is observed at line " + N.format("n")))
def _(world, n):
    _obs_at(_session(world).hevc_request, int(n), world)


@then(parsers.re("an HEVC stream is observed at line " + N.format("n")))
def _(world, n):
    _obs_at(_session(world).hevc_stream, int(n), world)


@then("the audio format is unknown")
def _(world):
    assert _session(world).audio_format is None


@then("no HEVC stream is observed")
def _(world):
    assert _session(world).hevc_stream is None


@then(parsers.re(r"the advertised display size is (?P<w>\d+)x(?P<h>\d+), from the launch configuration"))
def _(world, w, h):
    report = analyse(world)
    assert report.advertised_size == (int(w), int(h))
    assert report.advertised_source == "launch configuration"
    assert _session(world).resolution.value != report.advertised_size


@then(parsers.re(N.format("count") + " sessions are found, starting at lines " + N.format("a") + " and " + N.format("b")))
def _(world, count, a, b):
    sessions = analyse(world).sessions
    assert len(sessions) == int(count)
    assert [s.start_line for s in sessions] == [log_line(world, int(a)), log_line(world, int(b))]


@then(parsers.re("session " + N.format("i") + "'s video codec is " + Q.format("codec") + ", observed at line " + N.format("n")))
def _(world, i, codec, n):
    s = _session(world, int(i))
    _obs_at(s.codec, int(n), world)
    assert s.codec.value == codec


@then(parsers.re("session " + N.format("i") + "'s video codec is unknown"))
def _(world, i):
    assert _session(world, int(i)).codec is None


@then("the report records the log as complete")
def _(world):
    report = analyse(world)
    assert report.complete is True
    assert report.lines_read == len(_lines(world))


@then("the report names no evidence error")
def _(world):
    assert analyse(world).limitation is None


@then(parsers.re("the report flags an evidence error naming " + Q.format("limitation")))
def _(world, limitation):
    report = analyse(world)
    assert report.complete is False
    assert report.limitation == limitation


# --- 05 verdicts ----------------------------------------------------------------------------

@given(parsers.re("a complete log with one session in which (?P<what>.+)"))
def _(world, what):
    lines, cites = ONE_SESSION[what]
    world.required_cites = [log_line(world, n) for n in cites]
    write_log(world, wrap(lines))


@given(parsers.re("a complete log in which (?P<what>.+)"))
def _(world, what):
    write_log(world, wrap(WHOLE_LOG[what]))


@given(parsers.re("a run with a complete readable log whose sessions have verdicts (?P<sessions>.+)"))
def _(world, sessions):
    world.expected = [v.strip() for v in sessions.split(",")]
    write_log(world, wrap(sessions_excerpt(world.expected)))


@given(parsers.re("a run containing a complete session whose verdict is " + VERDICT.format("verdict")))
def _(world, verdict):
    world.session_lines = SESSION[verdict]
    world.session_verdict = verdict


@given(parsers.re("the log as a whole is (?P<damage>truncated|partly unreadable)"))
def _(world, damage):
    # First prove the undamaged log really gives that session the stated verdict.
    write_log(world, wrap(world.session_lines), name="clean.log")
    clean = s1("evaluator").judge(s1("analyser").analyse(world.log), notes={}, ended_at=None, end_reason=None)
    assert [s.verdict for s in clean.sessions] == [world.session_verdict]
    world.report = None
    damaged(world, "no end" if damage == "truncated" else "utf8", world.session_lines)


@given(parsers.re("a complete synthetic run with one session whose verdict is " + VERDICT.format("verdict")))
def _(world, verdict):
    write_log(world, wrap(SESSION[verdict]))


@given(parsers.re("the run ended at second " + N.format("t") + " by the timeout"))
def _(world, t):
    world.end = (int(t), "timeout")


@when("the evaluator judges the run")
def _(world):
    _judgement(world)


@when(parsers.re("only the " + Q.format("note") + " observation changes from " + Q.format("before") + " to " + Q.format("after")))
def _(world, note, before, after):
    world.before = judge(world, notes={note: before})
    world.judgement = judge(world, notes={note: after})
    world.changed = (note, after)


@then(parsers.re("the session's verdict is " + VERDICT.format("verdict") + " with the reason " + Q.format("reason")))
def _(world, verdict, reason):
    j = _judgement(world)
    assert len(j.sessions) == 1
    assert j.sessions[0].verdict == verdict
    assert j.sessions[0].reason.startswith(reason), j.sessions[0].reason


@then(parsers.re("the run's verdict is " + VERDICT.format("verdict") + " with the reason " + Q.format("reason")))
def _(world, verdict, reason):
    j = _judgement(world)
    assert (j.verdict, j.reason) == (verdict, reason)
    if reason == "evidence error":
        _no_fabricated_cites(world, j.cites)


@then(parsers.re("the run's verdict is " + VERDICT.format("verdict")))
def _(world, verdict):
    assert _judgement(world).verdict == verdict


@then("the run's verdict is not GO")
def _(world):
    assert _judgement(world).verdict != "GO"


def _span(world, i: int) -> range:
    """The log lines that belong to session i (1-based): from its start to the next session's start."""
    sessions = analyse(world).sessions
    end = sessions[i].start_line if i < len(sessions) else len(_lines(world))
    return range(sessions[i - 1].start_line, end + (0 if i < len(sessions) else 1))


def _no_fabricated_cites(world, cites) -> None:
    raw = world.log.read_bytes().split(b"\n")
    for n in cites:
        assert 1 <= n <= len(raw), f"line {n} doesn't exist"
        raw[n - 1].decode("utf-8")  # a cited line must be readable


@then("the verdict cites the log lines it rests on")
def _(world):
    j = _judgement(world)
    assert len(j.sessions) == 1
    cites = j.sessions[0].cites
    for n in world.required_cites:
        assert n in cites, f"the deciding line {n} isn't cited ({cites})"
    assert set(cites) <= set(_span(world, 1)), cites
    assert set(j.cites) <= set(cites) and j.cites, "the run verdict must cite the lines its session rests on"


@then("the reason notes the earlier HEVC request")
def _(world):
    sv = _judgement(world).sessions[0]
    assert "HEVC" in sv.reason
    assert _session(world).hevc_request.line in sv.cites


@then(parsers.re("the limitation " + Q.format("limitation") + " is named"))
def _(world, limitation):
    j = _judgement(world)
    assert limitation in [j.limitation, *(s.limitation for s in j.sessions)]


@then("no session verdict is listed")
def _(world):
    assert _judgement(world).sessions == []


@then("every session's verdict is listed unchanged")
def _(world):
    assert [s.verdict for s in _judgement(world).sessions] == world.expected


@then(parsers.re("the session verdict remains " + VERDICT.format("verdict")))
def _(world, verdict):
    assert world.before.sessions[0].verdict == world.judgement.sessions[0].verdict == verdict


@then(parsers.re("the run verdict remains " + VERDICT.format("verdict")))
def _(world, verdict):
    assert world.before.verdict == world.judgement.verdict == verdict


@then("the updated observation is retained")
def _(world):
    note, after = world.changed
    assert world.judgement.notes[note] == after


@then(parsers.re("the reason states the log was read to its last line and the run ended at second " + N.format("t")))
def _(world, t):
    j = _judgement(world)
    total = len(_lines(world))
    assert j.examined_to_line == total
    assert j.endpoint == int(t)
    reason = j.sessions[0].reason
    assert re.search(rf"\bline {total}\b", reason) and re.search(rf"\bsecond {t}\b", reason), reason


@then("the verdict cites only the connection line, not the setup lines")
def _(world):
    assert _judgement(world).sessions[0].cites == [log_line(world, 1)]
