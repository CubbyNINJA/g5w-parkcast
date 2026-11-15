"""Steps for 02_firewall.feature and 03_runner.feature (AIR-003 to AIR-007, AIR-028, AIR-030).

Launch and rule checks read what the fakes actually received (launcher.argv, the fw.create
event's copy of the rule), then check that the run record agrees with it.
"""

from __future__ import annotations

import dataclasses

from pytest_bdd import given, parsers, then, when

from steps.fakes import FakeLauncher, s1
from steps.world import (CODEC_H264, CONNECT, EXPECTED_FLAGS, FORBIDDEN_FLAGS, LOST, host_path,
                         make_verified_build, plan_run, read_json, rec, run_dir)

Q = r'"(?P<{}>[^"]*)"'
N = r"(?P<{}>\d+)"


def _owned_rule(w, n: int):
    fw = s1("firewall")
    return fw.Rule(name=fw.RULE_NAME, program=str(w.build_root / f"old-{n}" / "build" / "uxplay.exe"),
                   direction="in", action="allow", profiles=("Public",), ports=None)


def _created_events(w) -> list[dict]:
    return w.events.of("fw.create")


def _run_rule_id(w):
    created = _created_events(w)
    assert created, "the run never created its rule"
    return created[0]["id"]


def _removed_then_listed_absent(w, rule_id: str) -> int:
    """The rule was removed, and a later listing (taken from the fake, not the runner) shows it gone."""
    removed = w.events.first("fw.remove", id=rule_id)
    assert removed >= 0, f"{rule_id} was never removed"
    later = [e for e in w.events[removed + 1:] if e["kind"] == "fw.list"]
    assert later and rule_id not in later[0]["ids"], "removal wasn't checked by listing again"
    return w.events.index(later[0])


def _argv(w) -> list[str]:
    """What the fake launcher was actually given; the run record must agree with it."""
    assert w.launcher.argv is not None, "UxPlay was never launched"
    argv = list(w.launcher.argv)
    assert rec(w).command == argv, "the recorded command isn't the one launched"
    return argv


def _contains(cmd: list[str], words) -> bool:
    seq = list(words)
    return any(cmd[i:i + len(seq)] == seq for i in range(len(cmd)))


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


@when("a test run starts")
@when("the runner launches the receiver")
@when("the runner attempts to launch the receiver")
@when("a test run creates its firewall rule")
def _(world):
    plan_run(world)


@then("UxPlay is never launched")
def _(world):
    assert not world.events.of("proc.start", "proc.create.failed")
    assert world.launcher.argv is None


@then("no firewall rule is created")
@then("no new rule is created")
def _(world):
    assert not world.events.of("fw.create", "fw.create.failed")


@then("cleanup runs")
def _(world):
    assert rec(world).cleanup_ran
    listed = world.events.of("fw.list")
    assert listed, "cleanup never looked at the firewall"
    fw = s1("firewall")
    assert not [r for r in listed[-1]["rules"] if fw.is_owned(fw.Rule(**r), world.build_root)]


# --- 02 firewall ----------------------------------------------------------------------------

@given(parsers.re("the build root recorded in the provenance is " + Q.format("root")))
def _(world, root):
    world.build_root = host_path(world, root)
    world.work_root = world.build_root


@given(parsers.re("the runner owns a rule only if it is named " + Q.format("name")
                  + " and its program is a uxplay.exe under that root"))
def _(world, name):
    fw = s1("firewall")
    assert fw.RULE_NAME == name
    mine = _owned_rule(world, 0)
    assert fw.is_owned(mine, world.build_root)
    assert not fw.is_owned(dataclasses.replace(mine, name=name + "-other"), world.build_root)
    assert not fw.is_owned(dataclasses.replace(mine, program="C:\\Tools\\uxplay.exe"), world.build_root)
    assert not fw.is_owned(dataclasses.replace(mine, program=str(world.build_root / "build" / "other.exe")),
                           world.build_root)


@given(parsers.re("the firewall holds " + N.format("count") + " owned rules? left by (?:earlier runs|an earlier run)"))
def _(world, count):
    world.stale = [world.firewall.seed(_owned_rule(world, i)) for i in range(int(count))]


@given(parsers.re("the firewall holds an unrelated rule named " + Q.format("name")))
def _(world, name):
    fw = s1("firewall")
    world.unrelated = world.firewall.seed(fw.Rule(name=name, program="C:\\Program Files\\Steam\\steam.exe",
                                                  direction="in", action="allow", profiles=("Public", "Private"),
                                                  ports=None))
    world.unrelated_fields = dataclasses.asdict(world.unrelated)


@given("removing firewall rules fails")
def _(world):
    world.firewall.fail_remove = True


@given("removing a rule reports success but leaves it in the next listing")
def _(world):
    world.firewall.remove_no_effect = True


@given(parsers.re("the firewall holds a rule named " + Q.format("name") + " for the program " + Q.format("program")))
def _(world, name, program):
    fw = s1("firewall")
    world.foreign = world.firewall.seed(fw.Rule(name=name, program=program, direction="in", action="allow",
                                                profiles=("Public",), ports=None))
    world.foreign_fields = dataclasses.asdict(world.foreign)


@given(parsers.re("the active network profile is " + Q.format("profile")))
def _(world, profile):
    world.firewall.profile = profile


@given("creating firewall rules fails")
def _(world):
    world.firewall.fail_create = True


@given("a test run whose firewall rule was created")
def _(world):
    make_verified_build(world)


@then("both owned rules are removed, and their removal checked, before the run's rule is created")
def _(world):
    created = world.events.first("fw.create")
    assert created >= 0
    for rule in world.stale:
        checked = _removed_then_listed_absent(world, rule.id)
        assert checked < created, f"{rule.id}: removal not verified before the run's rule was created"
        assert rule.id not in world.firewall.rules


@then(parsers.re("the rule named " + Q.format("name") + " is unchanged"))
def _(world, name):
    assert world.unrelated.name == name
    current = world.firewall.rules.get(world.unrelated.id)
    assert current is not None and dataclasses.asdict(current) == world.unrelated_fields
    assert world.events.first("fw.remove", id=world.unrelated.id) == -1


@then(parsers.re("the run stops naming the remaining rule " + Q.format("name")))
def _(world, name):
    assert not rec(world).started
    assert any(name in e for e in world.record.errors)


@then(parsers.re("the run stops naming the remaining rule " + Q.format("name") + " and its program"))
def _(world, name):
    assert not rec(world).started
    text = " ".join(world.record.errors)
    assert name in text and world.stale[0].program in text


@then(parsers.re("the run stops naming that rule and its program " + Q.format("program")))
def _(world, program):
    assert not rec(world).started
    text = " ".join(world.record.errors)
    assert s1("firewall").RULE_NAME in text and program in text


@then("that rule is unchanged")
def _(world):
    current = world.firewall.rules.get(world.foreign.id)
    assert current is not None and dataclasses.asdict(current) == world.foreign_fields
    assert world.events.first("fw.remove", id=world.foreign.id) == -1


@then("the run creates exactly one rule")
def _(world):
    assert len(_created_events(world)) == 1


@then(parsers.re("that rule is inbound and allows only the program " + Q.format("program")))
def _(world, program):
    asked = _created_events(world)[0]["asked"]
    assert (asked["direction"], asked["action"]) == ("in", "allow")
    assert asked["program"] == str(host_path(world, program))
    recorded = rec(world).rule
    assert recorded is not None and recorded.id == _created_events(world)[0]["id"]
    for field in ("name", "program", "direction", "action", "ports"):
        assert getattr(recorded, field) == asked[field], field
    assert tuple(recorded.profiles) == asked["profiles"]


@then(parsers.re("that rule applies to the " + Q.format("profile") + " profile"))
def _(world, profile):
    assert _created_events(world)[0]["asked"]["profiles"] == (profile,)


@then("that rule names no port or port range")
def _(world):
    assert _created_events(world)[0]["asked"]["ports"] is None


@then("the run reports that the firewall rule could not be created")
def _(world):
    assert world.events.of("fw.create.failed")
    assert any("firewall rule" in e.lower() and "creat" in e.lower() for e in rec(world).errors)


@when(parsers.re("the run ends by (?P<ending>the owner ending the test|the 10-minute timeout|UxPlay failing to start"
                 "|UxPlay exiting mid-test|a keyboard interrupt)"))
def _(world, ending):
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
    world.ending = ending
    plan_run(world)


@then("the run's rule is removed")
def _(world):
    assert world.executed
    assert _run_rule_id(world) not in world.firewall.rules


@then("the removal is checked by listing the firewall rules again")
def _(world):
    _removed_then_listed_absent(world, _run_rule_id(world))


@then(parsers.re("the run reports the residual rule " + Q.format("name") + " and its program path"))
def _(world, name):
    r = rec(world)
    created = _created_events(world)[0]
    assert r.residual_rule is not None and r.residual_rule.id == created["id"] and r.residual_rule.name == name
    text = " ".join(r.errors)
    assert name in text and created["asked"]["program"] in text


@then("the run does not report a clean cleanup")
def _(world):
    assert rec(world).cleanup_clean is False


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


@then("the run created its firewall rule before the capture failed")
def _(world):
    created, started = world.events.first("fw.create"), world.events.first("proc.start")
    assert 0 <= created < started


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
        removed = world.events.first("fw.remove", id=_run_rule_id(world))
        assert 0 <= exited < removed, "the process must have exited before cleanup"


@then("the temporary firewall rule is removed")
def _(world):
    assert _run_rule_id(world) not in world.firewall.rules


@then("its removal is verified")
def _(world):
    _removed_then_listed_absent(world, _run_rule_id(world))
    assert rec(world).cleanup_clean is True


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


@then("firewall cleanup starts only after UxPlay has stopped")
def _(world):
    exited = world.events.first("proc.exit")
    cleanup = world.events.first("fw.remove", id=_run_rule_id(world))
    assert 0 <= exited < cleanup, "cleanup began before UxPlay had actually exited"


@then(parsers.re("the log's last line is the runner's end marker naming " + Q.format("reason")))
def _(world, reason):
    text = (run_dir(world) / "uxplay.log").read_text(encoding="utf-8")
    assert text.endswith("\n")
    last = text.splitlines()[-1]
    logfile = s1("logfile")
    assert logfile.is_end_marker(last) and reason in last
    exit_event = world.events.of("proc.exit")[0]
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


@given("the UxPlay process ignores the stop request")
def _(world):
    world.launcher.ignores_stop = True


@then("the run reports the UxPlay process that is still running, with its process ID")
def _(world):
    proc = world.launcher.procs[0]
    assert proc.alive
    assert rec(world).residual_pid == proc.pid
    assert any(str(proc.pid) in e for e in world.record.errors)


@then("cleanup still removes the firewall rule")
def _(world):
    _removed_then_listed_absent(world, _run_rule_id(world))
