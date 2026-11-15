"""@host and @pc steps for 02_firewall.feature and 06_evidence.feature.

They use the real backends: Windows Firewall, the real UxPlay build, this PC's clock and the owner
at the console. They're skipped unless --run-host or --run-pc is given (tests/conftest.py).

The evidence doesn't rest on what the runner wrote about itself:
- The firewall backend is wrapped in a test-owned recorder, and the steps also list the rules
  themselves through PowerShell before and after the run.
- A live scenario uses only the one run folder its own command created. Later scenarios use
  that same folder, or else the newest run folder recorded as a complete pc run.
"""

from __future__ import annotations

import json
import os
import re
import signal
import subprocess
import time
from pathlib import Path

from pytest_bdd import given, parsers, then, when

from steps.fakes import Events, FakeOwner, rule_fields, s1
from steps.world import PROJECT, read_json

Q = r'"(?P<{}>[^"]*)"'
RUNS = PROJECT / "runs"
LIVE = {}  # the run folder made by this session's live scenario, shared by the later @pc scenarios
IOS_FULL = re.compile(r"^27(\.\d+){1,2} \([0-9A-Za-z]+\)$")
KNOWN_MESSAGES = ("connection request from", "Client identified as User-Agent:", "raop_rtp_mirror width_source =",
                  "*** codec is", "raop_rtp_mirror: received type 0x01 packet with no payload:",
                  "video is h265 but the -h265 option was not used", "audioFormat=0x")
EXPECTED_FLAGS = [("-n", "HU-Test"), ("-nh",), ("-s", "1920x720@60"), ("-d",)]
FORBIDDEN_FLAGS = {"-h265", "-vdmp", "-admp", "-vrtp", "-artp"}


class RecordingFirewall:
    """The real firewall backend, with every call and every listing recorded by the test."""

    def __init__(self, events: Events):
        self.inner = s1("firewall").WindowsFirewall()
        self.events = events

    def active_profile(self):
        return self.inner.active_profile()

    def list_rules(self):
        rules = self.inner.list_rules()
        self.events.add("fw.list", ids=tuple(r.id for r in rules), rules=tuple(rule_fields(r) for r in rules))
        return rules

    def create(self, rule):
        made = self.inner.create(rule)
        self.events.add("fw.create", id=made.id, asked=rule_fields(rule))
        return made

    def remove(self, rule_id):
        self.events.add("fw.remove", id=rule_id)
        return self.inner.remove(rule_id)


class RecordingLauncher:
    """The real launcher, keeping the process so a step can end it from outside the runner."""

    def __init__(self, events: Events):
        self.inner = s1("runner").SubprocessLauncher()
        self.events = events
        self.procs = []
        self.argv = None

    def start(self, argv, log):
        self.argv = tuple(argv)
        proc = self.inner.start(argv, log)
        self.procs.append(proc)
        self.events.add("proc.start", pid=proc.pid, argv=tuple(argv))
        return proc


def _powershell_rules() -> list[dict]:
    """Every firewall rule as Windows itself lists it, independent of the code under test."""
    script = ("Get-NetFirewallRule | Select-Object Name,DisplayName,Enabled,Direction,Action,Profile | "
              "ConvertTo-Json -Compress")
    out = subprocess.run(["powershell", "-NoProfile", "-Command", script], capture_output=True, text=True,
                         check=True, timeout=300).stdout
    rows = json.loads(out) if out.strip() else []
    return [{k: str(v) for k, v in r.items()} for r in (rows if isinstance(rows, list) else [rows])]


def _others(rows: list[dict]) -> list[dict]:
    return sorted((r for r in rows if r["DisplayName"] != s1("firewall").RULE_NAME), key=lambda r: r["Name"])


def _owned(rules, build_root) -> list:
    fw = s1("firewall")
    return [r for r in rules if fw.is_owned(fw.Rule(**r) if isinstance(r, dict) else r, build_root)]


def _host_run(w, owner) -> None:
    w.events = Events()
    w.real_fw = RecordingFirewall(w.events)
    w.real_launcher = RecordingLauncher(w.events)
    w.ps_before = _powershell_rules()
    w.host_run_dir = RUNS / time.strftime("host-%Y%m%d-%H%M%S")
    w.record = s1("runner").run(w.provenance, w.host_run_dir, firewall=w.real_fw, launcher=w.real_launcher,
                                clock=s1("runner").SystemClock(), owner=owner, timeout=600)
    w.ps_after = _powershell_rules()


@given("this PC's Windows Firewall")
def _(world):
    world.provenance = s1("build").load_verified(s1("build").DEFAULT_WORK_ROOT)
    world.build_root = world.provenance.build_root


@given("an owned rule deliberately left from an earlier run")
def _(world):
    fw = s1("firewall")
    world.leftover = fw.WindowsFirewall().create(fw.Rule(
        name=fw.RULE_NAME, program=str(Path(world.build_root) / "stale" / "uxplay.exe"), direction="in",
        action="allow", profiles=("Public",), ports=None))


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
    seen = {}

    def poll(now):
        procs = world.real_launcher.procs
        if procs and "at" not in seen:
            seen["at"] = now
        elif procs and now - seen["at"] >= 3 and "killed" not in seen:
            seen["killed"] = True
            os.kill(procs[0].pid, signal.SIGTERM)
        if "killed" in seen and now - seen["at"] > 60:
            owner.end_at = now  # safety net: never leave the receiver running if the runner misses the exit
    owner.on_poll = poll
    world.killed = seen
    _host_run(world, owner)


@then("the firewall rule lists from before, during and after the run are saved")
def _(world):
    observed = [e["rules"] for e in world.events.of("fw.list")]
    saved = {k: read_json(world.host_run_dir / f"firewall-{k}.json") for k in ("before", "during", "after")}
    for name, rows in saved.items():
        assert any(_same(rows, seen) for seen in observed), f"firewall-{name}.json isn't a listing the backend returned"
    world.saved_lists = saved


def _same(saved_rows, observed_rows) -> bool:
    def key(r):
        return (r["id"], r["name"], r["program"])
    return sorted(map(key, saved_rows)) == sorted(map(key, observed_rows))


@then("they show the leftover rule removed before the run's rule was created")
def _(world):
    created = world.events.first("fw.create")
    removed = world.events.first("fw.remove", id=world.leftover.id)
    assert 0 <= removed < created
    checks = [e for e in world.events[removed + 1:created] if e["kind"] == "fw.list"]
    assert checks and world.leftover.id not in checks[-1]["ids"]


@then("they show the run's rule inbound, for only the built uxplay.exe and on the Public profile while the run was active")
def _(world):
    asked = world.events.of("fw.create")[0]["asked"]
    assert (asked["direction"], asked["action"], asked["ports"]) == ("in", "allow", None)
    assert asked["program"] == world.provenance.executable
    assert asked["profiles"] == ("Public",)
    during = _owned(world.saved_lists["during"], world.build_root)
    assert len(during) == 1 and during[0]["program"] == world.provenance.executable


@then("they show no owned rule after the run")
@then("they show the run's rule while the run was active and no owned rule after it")
def _(world):
    assert len(world.events.of("fw.create")) == 1
    final = world.events.of("fw.list")[-1]
    assert _owned(final["rules"], world.build_root) == []
    assert not [r for r in world.ps_after if r["DisplayName"] == s1("firewall").RULE_NAME]


@then("they show every rule the runner doesn't own unchanged")
def _(world):
    assert _others(world.ps_before) == _others(world.ps_after)


@then("the run reports UxPlay exiting mid-test")
def _(world):
    assert world.killed.get("killed")
    assert world.record.reason == "uxplay exited"


# --- @pc: the live test with the owner's iPhone ----------------------------------------------

@given("the verified build and this PC's network")
def _(world):
    world.provenance = s1("build").load_verified(s1("build").DEFAULT_WORK_ROOT)


@given("the owner's iPhone on iOS 27 on the same network")
def _(world):
    pass  # the owner's own setup; the runner asks for the exact version and build afterwards


def _live(world, expect_reason: str) -> None:
    RUNS.mkdir(exist_ok=True)
    before = {p.name for p in RUNS.iterdir() if p.is_dir()}
    world.live_rc = s1("cli").main(["run"])
    new = [RUNS / n for n in {p.name for p in RUNS.iterdir() if p.is_dir()} - before]
    world.new_runs = new
    world.expect_reason = expect_reason
    if len(new) == 1:
        world.live_dir = LIVE["dir"] = new[0]


@when(parsers.re("the runner launches " + Q.format("name") + ", the owner starts Screen Mirroring to it and then ends the test at the console"))
def _(world, name):
    _live(world, "owner ended")


@when(parsers.re("the runner launches " + Q.format("name") + ", the owner starts Screen Mirroring to it and leaves it until the timeout"))
def _(world, name):
    _live(world, "timeout")


@then(parsers.re('the command completed and created exactly one new run folder under "runs/"'))
def _(world):
    assert world.live_rc == 0
    assert len(world.new_runs) == 1, world.new_runs


def _latest(world) -> Path:
    if getattr(world, "live_dir", None) is None:
        if "dir" in LIVE:
            world.live_dir = LIVE["dir"]
        else:
            pc_runs = [p for p in RUNS.glob("*/run.json")
                       if (r := read_json(p)).get("tier") == "pc" and r.get("evidence_complete") is True]
            assert pc_runs, "no complete pc run under runs/"
            world.live_dir = max(pc_runs, key=lambda p: p.stat().st_mtime).parent
    return world.live_dir


def _contains(cmd, words) -> bool:
    return any(list(cmd[i:i + len(words)]) == list(words) for i in range(len(cmd)))


@then("the recorded launch command is the one the offline launch scenarios specify")
def _(world):
    saved = read_json(_latest(world) / "run.json")["command"]
    assert saved[0] == world.provenance.executable
    for flags in EXPECTED_FLAGS:
        assert _contains(saved, flags), flags
    assert not set(saved) & FORBIDDEN_FLAGS


@then(parsers.re("the run records the launch time, the deadline 600 seconds later, the end time and the reason " + Q.format("reason")))
def _(world, reason):
    rec = read_json(_latest(world) / "run.json")
    assert reason == world.expect_reason
    assert rec["deadline"] == rec["launched_at"] + 600
    assert rec["launched_at"] <= rec["ended_at"] <= rec["deadline"]
    assert rec["reason"] == reason
    if reason == "timeout":
        assert rec["ended_at"] == rec["deadline"]


@then("no UxPlay process launched by the run is still running")
def _(world):
    rec = read_json(_latest(world) / "run.json")
    assert rec["residual_pid"] is None
    out = subprocess.run(["tasklist", "/FI", f"PID eq {rec['pid']}", "/NH"], capture_output=True, text=True).stdout
    assert str(rec["pid"]) not in out, f"process {rec['pid']} is still running"


@then("no rule the runner owns remains in the firewall")
def _(world):
    assert not [r for r in _powershell_rules() if r["DisplayName"] == s1("firewall").RULE_NAME]


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
    assert rec["ios"]["provenance"] == mark
    assert IOS_FULL.match(rec["ios"]["value"]), rec["ios"]["value"]
    assert rec["evidence_complete"] is True


@then(parsers.re("the live run's folder under \"runs/\" holds the log, the analyser report, every session verdict and the run verdict"))
def _(world):
    d = _latest(world)
    rec = read_json(d / "run.json")
    report = s1("analyser").analyse(d / "uxplay.log", command=rec["command"])
    judgement = s1("evaluator").judge(report, notes=rec["notes"], ended_at=rec["ended_at"], end_reason=rec["reason"])
    assert read_json(d / "report.json") == report.to_dict()
    assert read_json(d / "verdict.json") == judgement.to_dict()


@then("the live run's folder, the build tree and the working directory hold no video, image or stream dump file")
def _(world):
    from steps.evidence_steps import _forbidden
    places = [_latest(world), Path(world.provenance.build_dir), Path.cwd()]
    assert _forbidden(places) == []
    assert s1("evidence").screen_content(places) == []


@given(parsers.re('the most recent live run under "runs/"(?P<agree> whose saved results agree with its own log)?'))
def _(world, agree=None):
    world.provenance = s1("build").load_verified(s1("build").DEFAULT_WORK_ROOT)
    d = _latest(world)
    assert read_json(d / "run.json").get("tier") == "pc"
    if agree:
        _rederive(world)
        assert world.rederived_report == read_json(d / "report.json")
        assert world.rederived_verdict == read_json(d / "verdict.json")


def _rederive(world):
    d = _latest(world)
    rec = read_json(d / "run.json")
    report = s1("analyser").analyse(d / "uxplay.log", command=rec["command"])
    judgement = s1("evaluator").judge(report, notes=rec["notes"], ended_at=rec["ended_at"], end_reason=rec["reason"])
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
    checked = 0
    for session in world.rederived_report["sessions"]:
        for key, obs in session.items():
            if isinstance(obs, dict) and "line" in obs:
                assert isinstance(obs["line"], int) and 1 <= obs["line"] <= len(lines), (key, obs)
                assert obs.get("message") and any(m in obs["message"] for m in KNOWN_MESSAGES), (key, obs)
                assert obs["message"] in lines[obs["line"] - 1], (key, obs)
                checked += 1
    assert checked, "the report has no observations to check"


@then("every saved session verdict and reason matches the re-derived one")
def _(world):
    saved = read_json(_latest(world) / "verdict.json")["sessions"]
    assert saved == world.rederived_verdict["sessions"]


@then("the saved run verdict matches the re-derived one")
def _(world):
    saved = read_json(_latest(world) / "verdict.json")
    assert (saved["verdict"], saved["reason"]) == (world.rederived_verdict["verdict"], world.rederived_verdict["reason"])


@then("the saved run verdict is GO")
def _(world):
    assert read_json(_latest(world) / "verdict.json")["verdict"] == "GO"


@then(parsers.re("a GO session cites a " + Q.format("text") + " line in the retained log"))
def _(world, text):
    d = _latest(world)
    lines = (d / "uxplay.log").read_text(encoding="utf-8").splitlines()
    report = read_json(d / "report.json")
    verdict = read_json(d / "verdict.json")
    starts = [s["start_line"] for s in report["sessions"]] + [len(lines) + 1]
    found = False
    for i, s in enumerate(verdict["sessions"]):
        if s["verdict"] != "GO":
            continue
        span = range(starts[i], starts[i + 1])
        assert set(s["cites"]) <= set(span), "a GO session cites a line outside itself"
        # Independently of the evaluator: the session's own lines hold a connection and an h264 codec line.
        own = [lines[n - 1] for n in span]
        if any("connection request from" in l for l in own) and any(text in lines[n - 1] for n in s["cites"]):
            found = True
    assert found, f"no GO session cites a {text!r} line inside itself"


@then(parsers.re('the summary names the tier "pc", the build\'s commit, the iOS version with its build, and says the head unit is untested'))
def _(world):
    d = _latest(world)
    text = s1("evidence").summary(d, tier="pc")
    rec = read_json(d / "run.json")
    assert re.search(r"\btier\W+pc\b", text, re.I), text
    assert world.provenance.commit in text and rec["ios"]["value"] in text
    assert re.search(r"head unit\W+(?:is\W+)?untested", text, re.I), text
