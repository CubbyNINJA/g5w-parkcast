"""Steps for 03_runner.feature, and the run steps shared with 02 and 08 (AIR-006, AIR-007, AIR-013,
AIR-028).

Launch checks read what the fake launcher actually received (launcher.argv), then check that the
run record agrees with it. The runner takes no firewall backend (OD-31).
"""

from __future__ import annotations

from pytest_bdd import given, parsers, then, when

from steps.fakes import FakeLauncher, s1
from steps.world import (CODEC_H264, CONNECT, EXPECTED_FLAGS, FORBIDDEN_FLAGS, LOST, host_path,
                         make_verified_build, plan_run, read_json, rec, run_dir)

Q = r'"(?P<{}>[^"]*)"'
N = r"(?P<{}>\d+)"


def _argv(w) -> list[str]:
    """What the fake launcher was actually given; the run record must agree with it."""
    assert w.launcher.argv is not None, "UxPlay was never launched"
    argv = list(w.launcher.argv)
    assert rec(w).command == argv, "the recorded command isn't the one launched"
    return argv


def _contains(cmd: list[str], words) -> bool:
    seq = list(words)
    return any(cmd[i:i + len(seq)] == seq for i in range(len(cmd)))


def reminders(w) -> list[int]:
    """Indexes of owner messages that remind the owner to remove the firewall allowance."""
    return [i for i, e in enumerate(w.events) if e["kind"] == "owner.tell" and "Allowed apps" in e["text"]]


# --- shared ---------------------------------------------------------------------------------

@given(parsers.re("the verified build at " + Q.format("path")))
def _(world, path):
    make_verified_build(world, host_path(world, path))


@given("the verified build and writable run outputs")
def _(world):
    make_verified_build(world)
    run_dir(world).parent.mkdir(parents=True, exist_ok=True)


@given("a controlled clock")
def _(world):
    assert world.clock.now() == 0


@when("the runner launches the receiver")
@when("the runner attempts to launch the receiver")
def _(world):
    plan_run(world)


@then("UxPlay is never launched")
def _(world):
    assert not world.events.of("proc.start", "proc.create.failed")
    assert world.launcher.argv is None


def set_ending(world, ending: str) -> None:
    """Set up the fakes so the run ends the given way."""
    owner, launcher = world.owner, world.launcher
    owner.end_at = None
    if ending == "the owner ending the test":
        owner.end_at = 30
    elif ending == "UxPlay failing to start":
        launcher.exit_at_once = 1
    elif ending == "UxPlay exiting mid-test":
        launcher.exit_at = 100
    elif ending == "a keyboard interrupt":
        def interrupt():
            raise KeyboardInterrupt
        world.clock.at(50, interrupt)
    elif ending == "UxPlay's output failing to be captured":
        launcher.capture_fails = True
    world.ending = ending


ENDINGS = ("the owner ending the test|the 10-minute timeout|UxPlay failing to start|UxPlay exiting mid-test"
           "|a keyboard interrupt")


@when(parsers.re("the run ends by (?P<ending>" + ENDINGS + ")"))
def _(world, ending):
    set_ending(world, ending)
    plan_run(world)


@given(parsers.re("the run will end by (?P<ending>" + ENDINGS + "|UxPlay's output failing to be captured)"))
def _(world, ending):
    set_ending(world, ending)


@given("the UxPlay process ignores the stop request")
def _(world):
    world.launcher.ignores_stop = True


@then("the run reports the UxPlay process that is still running, with its process ID")
def _(world):
    proc = world.launcher.procs[0]
    assert proc.alive
    assert rec(world).residual_pid == proc.pid
    assert any(str(proc.pid) in e for e in world.record.errors)


# --- 03 runner ------------------------------------------------------------------------------

@then(parsers.re("the command runs " + Q.format("path")))
def _(world, path):
    assert _argv(world)[0] == str(host_path(world, path))


@then(parsers.re("the command includes " + Q.format("a") + " and " + Q.format("b")))
def _(world, a, b):
    cmd = _argv(world)
    assert _contains(cmd, a.split()) and _contains(cmd, b.split()), cmd


@then(parsers.re("the command includes " + Q.format("a")))
def _(world, a):
    assert _contains(_argv(world), a.split()), world.launcher.argv


@then(parsers.re("the command does not include " + Q.format("a")))
def _(world, a):
    assert a not in _argv(world)


@then("the effective command is recorded in the run")
def _(world):
    argv = _argv(world)
    assert read_json(run_dir(world) / "run.json")["command"] == argv
    for flags in EXPECTED_FLAGS:
        assert _contains(argv, flags), flags
    assert not set(argv) & FORBIDDEN_FLAGS


@then("the run's log starts with the runner's start marker, written before UxPlay was launched")
def _(world):
    rec(world)
    at_launch = world.launcher.log_at_start.splitlines()
    assert len(at_launch) == 1 and s1("logfile").is_start_marker(at_launch[0])


@then("UxPlay's output is captured to the run's log after the start marker")
def _(world):
    lines = (run_dir(world) / "uxplay.log").read_text(encoding="utf-8").splitlines()
    emitted = world.launcher.emitted
    assert emitted[:2] == [FakeLauncher.BANNER, FakeLauncher.DIAGNOSTIC], emitted
    assert lines[1:1 + len(emitted)] == emitted
    assert not world.events.of("log.write.after.close")


@given(parsers.re("the build at " + Q.format("path") + " whose SHA-256 differs from its provenance"))
def _(world, path):
    exe = host_path(world, path)
    make_verified_build(world, exe)
    exe.write_bytes(b"MZ something else")


@then("the launch is refused naming the SHA-256 mismatch")
def _(world):
    assert not rec(world).started
    assert world.record.reason == "refused"
    assert any("SHA-256" in e for e in world.record.errors)


@given(parsers.re("UxPlay exits at once with code " + N.format("code")))
def _(world, code):
    world.launcher.exit_at_once = int(code)


@then(parsers.re("the run reports that UxPlay failed to start, with exit code " + N.format("code")))
def _(world, code):
    assert rec(world).reason == "failed to start"
    assert world.record.exit_code == int(code)
    assert world.record.started is False


@given("UxPlay starts but its output can't be attached to the run's log")
def _(world):
    world.launcher.capture_fails = True


@given("creating the UxPlay process raises an operating-system error")
def _(world):
    world.launcher.create_fails = True


@then("the run reports the diagnostic capture failure")
def _(world):
    assert rec(world).reason == "capture failed"
    assert any("capture" in e.lower() for e in world.record.errors)


@then("the run reports the process-creation error")
def _(world):
    assert world.events.of("proc.create.failed")
    assert rec(world).reason == "failed to start"
    assert any("process" in e.lower() for e in world.record.errors)


@then("no UxPlay process launched by the run remains running")
def _(world):
    rec(world)
    assert not [p for p in world.launcher.procs if p.alive]
    for p in world.launcher.procs:
        exited = world.events.first("proc.exit", pid=p.pid)
        assert exited >= 0
        for r in reminders(world):
            assert exited < r, "the reminder came before the process had exited"


@then("the run is not reported as successfully started")
def _(world):
    assert rec(world).started is False


@then(parsers.re("the owner is told to open Control Center, tap Screen Mirroring and choose " + Q.format("name")))
def _(world, name):
    rec(world)
    text = "\n".join(world.owner.messages)
    for words in ("Control Center", "Screen Mirroring", name):
        assert words in text, words


@then("the owner is told how to end the test")
def _(world):
    assert any("Enter" in m and "end" in m.lower() for m in world.owner.messages)


@when("the runner launches the receiver at second 0")
def _(world):
    world.owner.end_at = None
    plan_run(world)


@when(parsers.re("an iPhone connects at second " + N.format("a") + " and reconnects at second " + N.format("b")))
def _(world, a, b):
    world.launcher.lines += [(int(a), CONNECT), (int(b) - 1, LOST), (int(b), CONNECT)]


@when(parsers.re("an iPhone connects at second " + N.format("a")))
def _(world, a):
    world.launcher.lines.append((int(a), CONNECT))


@when(parsers.re("mirroring video starts at second " + N.format("t")))
def _(world, t):
    world.launcher.lines.append((int(t), CODEC_H264))


@when(parsers.re("the owner ends the test at second " + N.format("t")))
def _(world, t):
    world.owner.end_at = int(t)


@when("the owner ends the test")
def _(world):
    world.owner.end_at = 5
    plan_run(world)


@then(parsers.re("UxPlay is stopped at second " + N.format("t") + " with the reason " + Q.format("reason")))
def _(world, t, reason):
    stops = world.events.of("proc.stop")
    assert stops and stops[0]["t"] == int(t)
    assert rec(world).ended_at == int(t) and world.record.reason == reason


@then("the firewall reminder comes only after UxPlay has stopped")
def _(world):
    exited = world.events.first("proc.exit")
    found = reminders(world)
    assert found, "the owner was never reminded"
    assert 0 <= exited < found[0], "the reminder came before UxPlay had actually exited"


@then(parsers.re("the log's last line is the runner's end marker naming " + Q.format("reason")))
def _(world, reason):
    text = (run_dir(world) / "uxplay.log").read_text(encoding="utf-8")
    assert text.endswith("\n")
    last = text.splitlines()[-1]
    logfile = s1("logfile")
    assert logfile.is_end_marker(last) and reason in last
    # A UxPlay that ignored the stop request never exits (OD-42), so there's no exit to order against.
    exits = world.events.of("proc.exit")
    exit_event = exits[0] if exits else {"log_at_exit": ""}
    assert not any(logfile.is_end_marker(l) for l in exit_event["log_at_exit"].splitlines()), \
        "the end marker was written before UxPlay had exited"


@then(parsers.re("the run records the launch (?:time|at second " + N.format("launch") + "), the deadline at second "
                 + N.format("deadline") + " and the end at second " + N.format("end")))
def _(world, deadline, end, launch=None):
    r = rec(world)
    started = world.events.of("proc.start")[0]["t"]
    assert r.launched_at == started == int(launch or 0)
    assert r.deadline == r.launched_at + 600 == int(deadline)
    assert r.ended_at == int(end)
    saved = read_json(run_dir(world) / "run.json")
    assert (saved["launched_at"], saved["deadline"], saved["ended_at"]) == (r.launched_at, r.deadline, r.ended_at)


@then(parsers.re("the run records the deadline at second " + N.format("deadline") + " and the end at second " + N.format("end")))
def _(world, deadline, end):
    r = rec(world)
    assert r.deadline == int(deadline) and r.ended_at == int(end)
    saved = read_json(run_dir(world) / "run.json")
    assert (saved["deadline"], saved["ended_at"]) == (r.deadline, r.ended_at)
