"""@host and @pc steps for 02_firewall.feature and 06_evidence.feature. They use the real
backends: Windows Firewall, the real UxPlay build, this PC's clock and the owner at the console.
Skipped unless --run-host or --run-pc is given (tests/conftest.py)."""

from __future__ import annotations

import os
import signal
import time
from pathlib import Path

from pytest_bdd import given, parsers, then, when

from steps.fakes import FakeOwner, s1
from steps.world import PROJECT, read_json

Q = r'"(?P<{}>[^"]*)"'


class RecordingLauncher:
    """The real launcher, keeping the process so a step can end it from outside the runner."""

    def __init__(self):
        self.inner = s1("runner").SubprocessLauncher()
        self.procs = []

    def start(self, argv, log):
        proc = self.inner.start(argv, log)
        self.procs.append(proc)
        return proc


def _lists(w) -> dict[str, list[dict]]:
    return {k: read_json(w.host_run_dir / f"firewall-{k}.json") for k in ("before", "during", "after")}


def _owned(rows: list[dict], build_root: str) -> list[dict]:
    fw = s1("firewall")
    return [r for r in rows if fw.is_owned(fw.Rule(**{k: v for k, v in r.items() if k != "id"}, id=r.get("id")), build_root)]


def _host_run(w, owner) -> None:
    runner = s1("runner")
    w.host_run_dir = PROJECT / "runs" / time.strftime("host-%Y%m%d-%H%M%S")
    w.record = runner.run(w.provenance, w.host_run_dir, firewall=w.real_fw, launcher=w.real_launcher,
                          clock=runner.SystemClock(), owner=owner, timeout=600)


@given("this PC's Windows Firewall")
def _(world):
    world.real_fw = s1("firewall").WindowsFirewall()
    world.real_launcher = RecordingLauncher()
    world.provenance = s1("build").load_verified(s1("build").DEFAULT_WORK_ROOT)
    world.build_root = world.provenance.build_root


@given("an owned rule deliberately left from an earlier run")
def _(world):
    fw = s1("firewall")
    world.leftover = world.real_fw.create(fw.Rule(
        name=fw.RULE_NAME, program=str(Path(world.build_root) / "stale" / "uxplay.exe"), direction="in",
        action="allow", profiles=(world.real_fw.active_profile(),), ports=None))


@when("a test run starts and the owner cancels it at once")
def _(world):
    owner = FakeOwner()
    owner.end_at = None
    owner.on_poll = lambda now: owner.end_at is None and setattr(owner, "end_at", now + 3)
    _host_run(world, owner)


@when("a test run starts and its UxPlay process is killed from outside the runner")
def _(world):
    owner = FakeOwner()
    owner.end_at = None
    started = {}

    def poll(now):
        procs = world.real_launcher.procs
        if procs and "at" not in started:
            started["at"] = now
        elif procs and now - started["at"] >= 3 and "killed" not in started:
            started["killed"] = True
            os.kill(procs[0].pid, signal.SIGTERM)
    owner.on_poll = poll
    _host_run(world, owner)


@then("the firewall rule lists from before, during and after the run are saved")
def _(world):
    lists = _lists(world)
    assert all(isinstance(v, list) for v in lists.values())


@then("they show the leftover rule removed before the run's rule was created")
def _(world):
    lists = _lists(world)
    ids_before = {r["id"] for r in lists["before"]}
    ids_during = {r["id"] for r in lists["during"]}
    assert world.leftover.id in ids_before and world.leftover.id not in ids_during


@then("they show the run's rule inbound, for only the built uxplay.exe and on the Public profile while the run was active")
def _(world):
    mine = _owned(_lists(world)["during"], world.build_root)
    assert len(mine) == 1
    rule = mine[0]
    assert rule["direction"] == "in" and rule["action"] == "allow"
    assert rule["program"] == world.provenance.executable
    assert rule["profiles"] == ["Public"] and rule["ports"] is None


@then("they show no owned rule after the run")
@then("they show the run's rule while the run was active and no owned rule after it")
def _(world):
    lists = _lists(world)
    assert len(_owned(lists["during"], world.build_root)) == 1
    assert _owned(lists["after"], world.build_root) == []


@then("they show every rule the runner doesn't own unchanged")
def _(world):
    lists = _lists(world)
    others = [{r["id"]: r for r in lists[k] if not _owned([r], world.build_root)} for k in ("before", "during", "after")]
    assert others[0] == others[1] == others[2]


@then("the run reports UxPlay exiting mid-test")
def _(world):
    assert world.record.reason == "uxplay exited"


# --- @pc: the live test with the owner's iPhone ----------------------------------------------

@given("the verified build and this PC's network")
def _(world):
    world.provenance = s1("build").load_verified(s1("build").DEFAULT_WORK_ROOT)


@given("the owner's iPhone on iOS 27 on the same network")
def _(world):
    pass  # the owner's own setup; the runner asks for the exact version and build afterwards


@when(parsers.re("the runner launches " + Q.format("name") + " and the owner starts Screen Mirroring to it"))
def _(world, name):
    world.live_rc = s1("cli").main(["run"])


def _latest(world) -> Path:
    if getattr(world, "live_dir", None) is None:
        world.live_dir = s1("evidence").latest_run(PROJECT / "runs")
    return world.live_dir


@then("the recorded launch command is the one the offline launch scenarios specify")
def _(world):
    saved = read_json(_latest(world) / "run.json")["command"]
    assert saved == s1("runner").build_command(world.provenance.executable)


@then(parsers.re("the run records the launch time, the deadline 600 seconds later, the end time and the reason " + Q.format("reason")))
def _(world, reason):
    rec = read_json(_latest(world) / "run.json")
    assert rec["deadline"] == rec["launched_at"] + 600
    assert rec["launched_at"] <= rec["ended_at"] <= rec["deadline"]
    assert rec["reason"] == reason


@then("no UxPlay process launched by the run is still running")
def _(world):
    rec = read_json(_latest(world) / "run.json")
    assert rec["residual_pid"] is None
    assert not s1("runner").process_running(rec["pid"])


@then("no rule the runner owns remains in the firewall")
def _(world):
    fw = s1("firewall")
    root = world.provenance.build_root
    assert not [r for r in fw.WindowsFirewall().list_rules() if fw.is_owned(r, root)]


@then("the log starts with the runner's start marker and ends with its end marker and a newline")
def _(world):
    text = (_latest(world) / "uxplay.log").read_text(encoding="utf-8")
    lines = text.splitlines()
    logfile = s1("logfile")
    assert text.endswith("\n") and logfile.is_start_marker(lines[0]) and logfile.is_end_marker(lines[-1])


@then(parsers.re("the owner's sound note and the iOS version with its build, marked " + Q.format("mark") + ", are recorded"))
def _(world, mark):
    rec = read_json(_latest(world) / "run.json")
    assert rec["notes"].get("sound played") in ("yes", "no")
    assert rec["ios"]["provenance"] == mark and "(" in rec["ios"]["value"]


@then(parsers.re("the live run's folder under \"runs/\" holds the log, the analyser report, every session verdict and the run verdict"))
def _(world):
    d = _latest(world)
    assert (d / "uxplay.log").is_file() and (d / "report.json").is_file()
    assert "sessions" in read_json(d / "verdict.json")


@then("the live run's folder, the build tree and the working directory hold no video, image or stream dump file")
def _(world):
    places = [_latest(world), Path(world.provenance.build_dir), Path.cwd()]
    assert s1("evidence").screen_content(places) == []


@given(parsers.re('the most recent live run under "runs/"(?P<agree> whose saved results agree with its own log)?'))
def _(world, agree=None):
    world.provenance = s1("build").load_verified(s1("build").DEFAULT_WORK_ROOT)
    d = _latest(world)
    if agree:
        _rederive(world)
        assert world.rederived_report == read_json(d / "report.json")
        assert world.rederived_verdict == read_json(d / "verdict.json")


def _rederive(world):
    d = _latest(world)
    rec = read_json(d / "run.json")
    report = s1("analyser").analyse(d / "uxplay.log", command=rec["command"])
    judgement = s1("evaluator").judge(report, notes=rec["notes"], ended_at=rec["ended_at"], end_reason=rec["reason"])
    world.rederived = (report, judgement)
    world.rederived_report, world.rederived_verdict = report.to_dict(), judgement.to_dict()


@when("its retained log is analysed and judged again")
def _(world):
    _rederive(world)


@then("every observation in the saved report matches the one re-derived from the log")
def _(world):
    assert world.rederived_report == read_json(_latest(world) / "report.json")


@then("every cited line number holds the message the observation names")
def _(world):
    lines = (_latest(world) / "uxplay.log").read_text(encoding="utf-8").splitlines()
    for session in world.rederived_report["sessions"]:
        for key, obs in session.items():
            if isinstance(obs, dict) and obs.get("line"):
                assert obs["message"] in lines[obs["line"] - 1], (key, obs)


@then("every saved session verdict and reason matches the re-derived one")
def _(world):
    saved = read_json(_latest(world) / "verdict.json")["sessions"]
    assert [(s["verdict"], s["reason"]) for s in saved] == [(s["verdict"], s["reason"]) for s in world.rederived_verdict["sessions"]]


@then("the saved run verdict matches the re-derived one")
def _(world):
    assert read_json(_latest(world) / "verdict.json")["verdict"] == world.rederived_verdict["verdict"]


@then("the saved run verdict is GO")
def _(world):
    assert read_json(_latest(world) / "verdict.json")["verdict"] == "GO"


@then(parsers.re("a GO session cites a " + Q.format("text") + " line in the retained log"))
def _(world, text):
    lines = (_latest(world) / "uxplay.log").read_text(encoding="utf-8").splitlines()
    go = [s for s in read_json(_latest(world) / "verdict.json")["sessions"] if s["verdict"] == "GO"]
    assert any(text in lines[n - 1] for s in go for n in s["cites"])


@then(parsers.re('the summary names the tier "pc", the build\'s commit, the iOS version with its build, and says the head unit is untested'))
def _(world):
    d = _latest(world)
    text = s1("evidence").summary(d, tier="pc")
    rec = read_json(d / "run.json")
    assert "pc" in text and world.provenance.commit in text and rec["ios"]["value"] in text
    assert "untested" in text.lower()
