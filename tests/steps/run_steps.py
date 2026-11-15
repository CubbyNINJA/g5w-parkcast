"""Steps for 02_firewall.feature and 03_runner.feature (AIR-003 to AIR-007, AIR-028, AIR-030)."""

from __future__ import annotations

import dataclasses

from pytest_bdd import given, parsers, then, when

from steps.fakes import s1
from steps.world import BANNER, CONNECT, CODEC_H264, LOST, host_path, make_verified_build, read_json, run, run_dir

Q = r'"(?P<{}>[^"]*)"'
N = r"(?P<{}>\d+)"


def _owned_rule(w, n: int):
    fw = s1("firewall")
    return fw.Rule(name=fw.RULE_NAME, program=str(w.build_root / f"old-{n}" / "build" / "uxplay.exe"),
                   direction="in", action="allow", profiles=("Public",), ports=None)


def _run_rule(w):
    """The rule this run created (the first fw.create event), or None."""
    created = w.events.of("fw.create")
    return created[0]["id"] if created else None


def _removed_and_checked(w, rule_id: str) -> None:
    assert rule_id not in w.firewall.rules
    removed = w.events.last("fw.remove", id=rule_id)
    assert removed >= 0, f"{rule_id} was never removed"
    assert any(e["kind"] == "fw.list" for e in w.events[removed + 1:]), "removal wasn't checked by listing again"


def _command(w) -> list[str]:
    run(w)
    return w.record.command


def _contains(cmd: list[str], words: str) -> bool:
    seq = words.split()
    return any(cmd[i:i + len(seq)] == seq for i in range(len(cmd)))


# --- shared ---------------------------------------------------------------------------------

@given(parsers.re("the verified build at " + Q.format("path")))
def _(world, path):
    make_verified_build(world, host_path(world, path))


@given("the verified build and a newly created temporary firewall rule")
def _(world):
    make_verified_build(world)


@given("a controlled clock")
def _(world):
    assert world.clock.now() == 0


@when("a test run starts")
@when("the runner launches the receiver")
@when("the runner attempts to launch the receiver")
@when("a test run creates its firewall rule")
def _(world):
    world.planned = True


@then("UxPlay is never launched")
def _(world):
    run(world)
    assert not world.events.of("proc.start")


@then("no firewall rule is created")
@then("no new rule is created")
def _(world):
    if world.planned:
        run(world)
    assert not world.events.of("fw.create")


@then("cleanup runs")
def _(world):
    run(world)
    assert world.record.cleanup_ran
    fw = s1("firewall")
    assert not [r for r in world.firewall.rules.values() if fw.is_owned(r, world.build_root)]


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


@given(parsers.re("the firewall holds " + N.format("count") + " owned rules? left by (?:earlier runs|an earlier run)"))
def _(world, count):
    world.stale = [world.firewall.seed(_owned_rule(world, i)) for i in range(int(count))]


@given(parsers.re("the firewall holds an unrelated rule named " + Q.format("name")))
def _(world, name):
    fw = s1("firewall")
    world.unrelated = world.firewall.seed(fw.Rule(name=name, program="C:\\Program Files\\Steam\\steam.exe",
                                                  direction="in", action="allow", profiles=("Public", "Private"),
                                                  ports=None))


@given("removing firewall rules fails")
def _(world):
    world.firewall.fail_remove = True


@given(parsers.re("the firewall holds a rule named " + Q.format("name") + " for the program " + Q.format("program")))
def _(world, name, program):
    fw = s1("firewall")
    world.foreign = world.firewall.seed(fw.Rule(name=name, program=program, direction="in", action="allow",
                                                profiles=("Public",), ports=None))


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
    run(world)
    created = world.events.first("fw.create")
    assert created >= 0
    for rule in world.stale:
        removed = world.events.first("fw.remove", id=rule.id)
        assert 0 <= removed < created, f"{rule.id} not removed before creation"
        assert rule.id not in world.firewall.rules
    last_removal = max(world.events.first("fw.remove", id=r.id) for r in world.stale)
    assert any(e["kind"] == "fw.list" for e in world.events[last_removal + 1:created])


@then(parsers.re("the rule named " + Q.format("name") + " is unchanged"))
def _(world, name):
    run(world)
    assert world.firewall.rules.get(world.unrelated.id) == world.unrelated


@then(parsers.re("the run stops naming the remaining rule " + Q.format("name")))
def _(world, name):
    run(world)
    assert not world.record.started
    assert any(name in e for e in world.record.errors)


@then(parsers.re("the run stops naming that rule and its program " + Q.format("program")))
def _(world, program):
    run(world)
    assert not world.record.started
    text = " ".join(world.record.errors)
    assert s1("firewall").RULE_NAME in text and program in text


@then("that rule is unchanged")
def _(world):
    assert world.firewall.rules.get(world.foreign.id) == world.foreign
    assert not world.events.of("fw.remove") or all(e["id"] != world.foreign.id for e in world.events.of("fw.remove"))


@then("the run creates exactly one rule")
def _(world):
    run(world)
    assert len(world.events.of("fw.create")) == 1


@then(parsers.re("that rule is inbound and allows only the program " + Q.format("program")))
def _(world, program):
    rule = world.created_rule = _created(world)
    assert rule.direction == "in" and rule.action == "allow"
    assert rule.program == str(host_path(world, program))


@then(parsers.re("that rule applies to the " + Q.format("profile") + " profile"))
def _(world, profile):
    assert tuple(_created(world).profiles) == (profile,)


@then("that rule names no port or port range")
def _(world):
    assert _created(world).ports is None


def _created(w):
    """The rule as the run created it (taken from the run record, since cleanup removes it)."""
    run(w)
    return w.record.rule


@then("the run reports that the firewall rule could not be created")
def _(world):
    run(world)
    assert any("firewall rule" in e.lower() and "creat" in e.lower() for e in world.record.errors)


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
    run(world)


@then("the run's rule is removed")
def _(world):
    rule_id = _run_rule(world)
    assert rule_id, "the run never created its rule"
    assert rule_id not in world.firewall.rules


@then("the removal is checked by listing the firewall rules again")
def _(world):
    _removed_and_checked(world, _run_rule(world))


@then(parsers.re("the run reports the residual rule " + Q.format("name") + " and its program path"))
def _(world, name):
    rec = world.record
    assert rec.residual_rule is not None and rec.residual_rule.name == name
    text = " ".join(rec.errors)
    assert name in text and rec.residual_rule.program in text


@then("the run does not report a clean cleanup")
def _(world):
    assert world.record.cleanup_clean is False


# --- 03 runner ------------------------------------------------------------------------------

@then(parsers.re("the command runs " + Q.format("path")))
def _(world, path):
    assert _command(world)[0] == str(host_path(world, path))


@then(parsers.re("the command includes " + Q.format("a") + " and " + Q.format("b")))
def _(world, a, b):
    cmd = _command(world)
    assert _contains(cmd, a) and _contains(cmd, b), cmd


@then(parsers.re("the command includes " + Q.format("a")))
def _(world, a):
    assert _contains(_command(world), a), world.record.command


@then(parsers.re("the command does not include " + Q.format("a")))
def _(world, a):
    assert a not in _command(world)


@then("the effective command is recorded in the run")
def _(world):
    assert read_json(run_dir(world) / "run.json")["command"] == _command(world)


@then("the run's log starts with the runner's start marker, written before UxPlay was launched")
def _(world):
    run(world)
    first = world.launcher.log_at_start.splitlines()
    assert first and s1("logfile").is_start_marker(first[0])


@then("UxPlay's output is captured to the run's log after the start marker")
def _(world):
    lines = (run_dir(world) / "uxplay.log").read_text(encoding="utf-8").splitlines()
    assert s1("logfile").is_start_marker(lines[0])
    assert BANNER in lines[1:]


@given(parsers.re("the build at " + Q.format("path") + " whose SHA-256 differs from its provenance"))
def _(world, path):
    exe = host_path(world, path)
    make_verified_build(world, exe)
    exe.write_bytes(b"MZ something else")


@then("the launch is refused naming the SHA-256 mismatch")
def _(world):
    run(world)
    assert not world.record.started
    assert any("SHA-256" in e for e in world.record.errors)


@given(parsers.re("UxPlay exits at once with code " + N.format("code")))
def _(world, code):
    world.launcher.exit_at_once = int(code)


@then(parsers.re("the run reports that UxPlay failed to start, with exit code " + N.format("code")))
def _(world, code):
    run(world)
    assert world.record.reason == "failed to start"
    assert world.record.exit_code == int(code)


@given("diagnostic log capture can't be established")
def _(world):
    world.block_log = True


@then("the run reports the diagnostic capture failure")
def _(world):
    run(world)
    assert world.record.reason == "capture failed"
    assert any("capture" in e.lower() for e in world.record.errors)


@then("no UxPlay process launched by the run remains running")
def _(world):
    run(world)
    assert not [p for p in world.launcher.procs if p.alive]


@then("the temporary firewall rule is removed")
def _(world):
    rule_id = _run_rule(world)
    assert rule_id and rule_id not in world.firewall.rules


@then("its removal is verified")
def _(world):
    _removed_and_checked(world, _run_rule(world))
    assert world.record.cleanup_clean is True


@then("the run is not reported as successfully started")
def _(world):
    assert world.record.started is False


@then(parsers.re("the owner is told to open Control Center, tap Screen Mirroring and choose " + Q.format("name")))
def _(world, name):
    run(world)
    text = "\n".join(world.owner.messages)
    for words in ("Control Center", "Screen Mirroring", name):
        assert words in text, words


@then("the owner is told how to end the test")
def _(world):
    assert any("Enter" in m and "end" in m.lower() for m in world.owner.messages)


@when(parsers.re("the runner launches the receiver at second 0"))
def _(world):
    world.owner.end_at = None
    world.launcher.lines.append((1, BANNER))


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


@then(parsers.re("UxPlay is stopped at second " + N.format("t") + " with the reason " + Q.format("reason")))
def _(world, t, reason):
    run(world)
    stops = world.events.of("proc.stop")
    assert stops and stops[0]["t"] == int(t)
    assert world.record.ended_at == int(t) and world.record.reason == reason


@then("firewall cleanup starts only after UxPlay has stopped")
def _(world):
    run(world)
    stopped = world.events.first("proc.stop")
    cleanup = world.events.first("fw.remove", id=_run_rule(world))
    assert 0 <= stopped < cleanup


@then(parsers.re("the log's last line is the runner's end marker naming " + Q.format("reason")))
def _(world, reason):
    text = (run_dir(world) / "uxplay.log").read_text(encoding="utf-8")
    assert text.endswith("\n")
    last = text.splitlines()[-1]
    assert s1("logfile").is_end_marker(last) and reason in last


@then(parsers.re("the run records the launch (?:time|at second " + N.format("launch") + "), the deadline at second "
                 + N.format("deadline") + " and the end at second " + N.format("end")))
def _(world, deadline, end, launch=None):
    run(world)
    rec = world.record
    assert rec.launched_at == int(launch or 0)
    assert rec.deadline == rec.launched_at + 600 == int(deadline)
    assert rec.ended_at == int(end)
    saved = read_json(run_dir(world) / "run.json")
    assert (saved["launched_at"], saved["deadline"], saved["ended_at"]) == (rec.launched_at, rec.deadline, rec.ended_at)


@then(parsers.re("the run records the deadline at second " + N.format("deadline") + " and the end at second " + N.format("end")))
def _(world, deadline, end):
    run(world)
    assert world.record.deadline == int(deadline) and world.record.ended_at == int(end)


@given("the UxPlay process ignores the stop request")
def _(world):
    world.launcher.ignores_stop = True


@then("the run reports the UxPlay process that is still running, with its process ID")
def _(world):
    run(world)
    pid = world.launcher.procs[0].pid
    assert world.record.residual_pid == pid
    assert any(str(pid) in e for e in world.record.errors)


@then("cleanup still removes the firewall rule")
def _(world):
    rule_id = _run_rule(world)
    assert rule_id and rule_id not in world.firewall.rules
