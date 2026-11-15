"""@pc steps for 06_evidence.feature: the live test with the owner's iPhone.

They use the real UxPlay build, this PC's clock and the owner at the console. The firewall is the
owner's, through Windows' own prompt (OD-31). They're skipped unless --run-pc is given.

The evidence doesn't rest on what the runner wrote about itself:
- The launcher and the console owner are wrapped in test-owned recorders, and Windows itself is
  asked which uxplay.exe is running and with what command line.
- A live scenario uses only the one run folder its own command created. Later scenarios use
  that same folder, or else the newest run folder recorded as a complete pc run.
"""

from __future__ import annotations

import ctypes
import json
import re
import subprocess
import threading
import time
from pathlib import Path

from pytest_bdd import given, parsers, then, when

from steps.fakes import Events, s1
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


class RecordedProc:
    """The real process, with the test's own monotonic times for its start and every stop request."""

    def __init__(self, inner, launcher):
        self.inner = inner
        self.launcher = launcher

    def __getattr__(self, name):
        return getattr(self.inner, name)

    def stop(self, *args, **kwargs):
        self.launcher.stop_times.append(time.monotonic())
        self.launcher.events.add("proc.stop", pid=self.inner.pid)
        stopped = self.inner.stop(*args, **kwargs)
        if self.inner.poll() is not None:
            self.launcher.events.add("proc.exit", pid=self.inner.pid)
        return stopped


class RecordingLauncher:
    """The real launcher. The test keeps the argv it was given, the process ID, and when it started."""

    def __init__(self, events: Events):
        self.inner = s1("runner").SubprocessLauncher()
        self.events = events
        self.procs = []
        self.argv = None
        self.start_time = None
        self.stop_times = []
        self.os_procs = None

    def start(self, argv, log):
        self.argv = tuple(argv)
        self.start_time = time.monotonic()
        proc = self.inner.start(argv, log)
        self.procs.append(proc)
        self.events.add("proc.start", pid=proc.pid, argv=tuple(argv))
        self.os_procs = _os_uxplay()
        return RecordedProc(proc, self)


def _os_uxplay() -> list[dict]:
    """Every running uxplay.exe as Windows lists it: process ID, executable path and command line."""
    script = ("Get-CimInstance Win32_Process -Filter \"Name='uxplay.exe'\" | "
              "Select-Object ProcessId,ExecutablePath,CommandLine | ConvertTo-Json -Compress")
    res = subprocess.run(["powershell", "-NoProfile", "-Command", script], capture_output=True, encoding="utf-8-sig",
                         timeout=120)
    assert res.returncode == 0, res.stderr
    rows = json.loads(res.stdout) if res.stdout.strip() else []
    return rows if isinstance(rows, list) else [rows]


def _launched_os_row(world) -> dict:
    """The one Windows process that is this build's uxplay.exe, and that has the launcher's PID."""
    launcher = world.real_launcher
    assert launcher.procs, "the test saw no UxPlay launch"
    exe = world.provenance.executable.lower()
    rows = [r for r in launcher.os_procs or [] if (r.get("ExecutablePath") or "").lower() == exe]
    assert len(rows) == 1, f"expected one running {exe} just after launch, Windows listed {launcher.os_procs}"
    assert rows[0]["ProcessId"] == launcher.procs[0].pid, "the launcher's PID isn't uxplay.exe's own"
    return rows[0]


# --- @pc: the live test with the owner's iPhone ----------------------------------------------

@given("the verified build and this PC's network")
def _(world):
    world.provenance = s1("build").load_verified(s1("build").DEFAULT_WORK_ROOT)


@given("the owner's iPhone on iOS 27 on the same network")
def _(world):
    pass  # the owner's own setup; the runner asks for the exact version and build afterwards


class RecordingOwner:
    """The real console owner, with every message recorded by the test, in order with the launch."""

    def __init__(self, events: Events):
        self.inner = s1("runner").ConsoleOwner()
        self.events = events

    def tell(self, message):
        running = _os_uxplay() if "Allowed apps" in message else None
        self.events.add("owner.tell", text=message, running=running)
        return self.inner.tell(message)

    def ended(self, now):
        return self.inner.ended(now)

    def ask(self, question):
        return self.inner.ask(question)


class EnterExitObserver:
    """Test-owned timing for OD-52, independent of the runner: one thread polls the Enter key's
    state every 5 ms (GetAsyncKeyState, read-only) and, once UxPlay is launched, waits on its
    process handle for the exit. Times are time.monotonic()."""

    VK_RETURN, SYNCHRONIZE, WAIT_OBJECT_0 = 0x0D, 0x00100000, 0

    def __init__(self):
        self.presses: list[float] = []
        self.exit_time: float | None = None
        self.launcher = None
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._run, daemon=True)

    def start(self, launcher) -> None:
        self.launcher = launcher
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        self._thread.join(5)

    def _run(self) -> None:
        user32, kernel32 = ctypes.windll.user32, ctypes.windll.kernel32
        down, handle = False, None
        while not self._stop.is_set():
            now = time.monotonic()
            pressed = bool(user32.GetAsyncKeyState(self.VK_RETURN) & 0x8000)
            if pressed and not down:
                self.presses.append(now)
            down = pressed
            if handle is None and self.launcher.procs:
                handle = kernel32.OpenProcess(self.SYNCHRONIZE, False, self.launcher.procs[0].pid)
            if handle and self.exit_time is None and kernel32.WaitForSingleObject(handle, 0) == self.WAIT_OBJECT_0:
                self.exit_time = time.monotonic()
            time.sleep(0.005)
        if handle:
            kernel32.CloseHandle(handle)

    def enter_after_launch(self) -> float | None:
        """The first Enter press after UxPlay started: the one that ends the test."""
        start = self.launcher.start_time
        later = [t for t in self.presses if start is not None and t >= start]
        return later[0] if later else None


def _live(world, expect_reason: str) -> None:
    RUNS.mkdir(exist_ok=True)
    world.events = Events()
    world.real_launcher = RecordingLauncher(world.events)
    world.real_owner = RecordingOwner(world.events)
    observer = getattr(world, "observer", None)
    if observer is not None:
        observer.start(world.real_launcher)
    before = {p.name for p in RUNS.iterdir() if p.is_dir()}
    try:
        world.live_rc = s1("cli").main(["run"], deps=s1("cli").Deps.real(launcher=world.real_launcher,
                                                                          owner=world.real_owner))
    finally:
        if observer is not None:
            observer.stop()
    new = [RUNS / n for n in {p.name for p in RUNS.iterdir() if p.is_dir()} - before]
    world.new_runs = new
    world.expect_reason = expect_reason
    if len(new) == 1:
        world.live_dir = LIVE["dir"] = new[0]
        if observer is not None:
            enter = observer.enter_after_launch()
            (new[0] / "timing-observer.json").write_text(json.dumps({
                "source": "test-owned observer (GetAsyncKeyState, process handle), time.monotonic()",
                "launched_at": world.real_launcher.start_time, "enter_at": enter, "exit_at": observer.exit_time,
                "enter_to_exit": (observer.exit_time - enter) if enter is not None and observer.exit_time else None,
            }, indent=2), encoding="utf-8")


@given("an independent observer timestamps Enter input and the launched process's exit")
def _(world):
    world.observer = EnterExitObserver()


@then("the observed process exit is between 0 and 1 second after the observed Enter event")
def _(world):
    from steps.run_steps import within_enter_bound
    enter, exit_ = world.observer.enter_after_launch(), world.observer.exit_time
    assert enter is not None, "the observer saw no Enter press after UxPlay started"
    assert exit_ is not None, "the observer never saw UxPlay exit"
    assert within_enter_bound(enter, exit_), f"UxPlay exited {exit_ - enter:.3f} s after Enter"


@then(parsers.re('the independent Enter and process-exit timestamps are retained under "runs/"'))
def _(world):
    saved = read_json(_latest(world) / "timing-observer.json")
    assert saved["enter_at"] == world.observer.enter_after_launch()
    assert saved["exit_at"] == world.observer.exit_time


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
    launched = list(world.real_launcher.argv)
    assert launched[0] == world.provenance.executable
    for flags in EXPECTED_FLAGS:
        assert _contains(launched, flags), flags
    assert not set(launched) & FORBIDDEN_FLAGS
    assert read_json(_latest(world) / "run.json")["command"] == launched, "the saved command isn't the one launched"
    ran = _launched_os_row(world)["CommandLine"]
    assert ran == subprocess.list2cmdline(launched), f"Windows ran {ran!r}, not the requested command"


@then(parsers.re("the run records the launch time, the deadline 600 seconds later, the end time and the reason " + Q.format("reason")))
def _(world, reason):
    rec = read_json(_latest(world) / "run.json")
    assert reason == world.expect_reason
    assert rec["deadline"] == rec["launched_at"] + 600
    assert rec["launched_at"] <= rec["ended_at"]
    assert rec["reason"] == reason
    # The test's own monotonic measurements, not the runner's timestamps.
    launcher = world.real_launcher
    assert launcher.start_time is not None and launcher.stop_times, "the test saw no launch or no stop request"
    measured = launcher.stop_times[0] - launcher.start_time
    assert abs((rec["ended_at"] - rec["launched_at"]) - measured) <= 2.0, (rec, measured)
    if reason == "timeout":
        assert 598.0 <= measured <= 605.0, f"stopped {measured:.1f} s after launch"
        assert rec["deadline"] <= rec["ended_at"] <= rec["deadline"] + 5
    else:
        assert measured < 598.0
        assert rec["ended_at"] <= rec["deadline"]


@then("no UxPlay process launched by the run is still running")
def _(world):
    rec = read_json(_latest(world) / "run.json")
    procs = world.real_launcher.procs
    assert len(procs) == 1, "the test saw no single UxPlay launch"
    pid = _launched_os_row(world)["ProcessId"]
    assert rec["pid"] == pid and rec["residual_pid"] is None
    exe = world.provenance.executable.lower()
    assert not [r for r in _os_uxplay() if (r.get("ExecutablePath") or "").lower() == exe], \
        f"{exe} is still running"
    res = subprocess.run(["tasklist", "/FI", f"PID eq {pid}", "/FO", "CSV", "/NH"], capture_output=True, text=True)
    assert res.returncode == 0, res.stderr
    assert not [l for l in res.stdout.splitlines() if l.split(",")[1:2] == [f'"{pid}"']], f"process {pid} is still running"


@then("the owner was told about Windows' prompt before UxPlay started")
def _(world):
    start = world.events.first("proc.start")
    assert start >= 0, "the test saw no launch"
    notices = [e for e in world.events[:start] if e["kind"] == "owner.tell" and "Public networks" in e["text"]]
    assert len(notices) == 1 and "uxplay.exe" in notices[0]["text"]


@then("the owner was reminded to remove the launched uxplay.exe's allowance after UxPlay stopped")
def _(world):
    stopped = world.real_launcher.stop_times
    assert stopped, "the test saw no stop request"
    told = [e for e in world.events if e["kind"] == "owner.tell" and "Allowed apps" in e["text"]]
    assert len(told) == 1
    text = told[0]["text"]
    assert world.provenance.executable in text and "Windows Defender Firewall" in text and "block" in text.lower()
    index = world.events.index(told[0])
    assert index > world.events.last("proc.stop"), "the reminder came before UxPlay was stopped"
    exe = world.provenance.executable.lower()
    still = [r for r in told[0]["running"] or [] if (r.get("ExecutablePath") or "").lower() == exe]
    assert not still, f"{exe} was still running when the owner was reminded"


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


def _sessions_from_text(lines: list[str]) -> list[range]:
    """Each session's lines, found from the log text: a session runs from one connection request
    to the line before the next."""
    starts = [i for i, l in enumerate(lines, 1) if "connection request from" in l]
    return [range(s, e) for s, e in zip(starts, starts[1:] + [len(lines) + 1])]


@then("every cited line number holds the message the observation names")
def _(world):
    lines = (_latest(world) / "uxplay.log").read_text(encoding="utf-8").splitlines()
    spans = _sessions_from_text(lines)
    sessions = world.rederived_report["sessions"]
    assert [s["start_line"] for s in sessions] == [sp.start for sp in spans], "session boundaries differ from the log"
    checked = 0
    for session, span in zip(sessions, spans):
        for key, obs in session.items():
            if isinstance(obs, dict) and "line" in obs:
                assert isinstance(obs["line"], int) and obs["line"] in span, (key, obs, span)
                assert obs.get("message") and any(m in obs["message"] for m in KNOWN_MESSAGES), (key, obs)
                assert obs["message"] in lines[obs["line"] - 1], (key, obs)
                checked += 1
    assert len(world.rederived_verdict["sessions"]) == len(spans), "a re-derived session is missing"
    for sv, span in zip(world.rederived_verdict["sessions"], spans):
        assert set(sv["cites"]) <= set(span), (sv["cites"], span)
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
    verdict = read_json(d / "verdict.json")
    spans = _sessions_from_text(lines)
    assert len(spans) == len(verdict["sessions"]), "the verdict's sessions don't match the log's connections"
    found = False
    for i, s in enumerate(verdict["sessions"]):
        if s["verdict"] != "GO":
            continue
        span = spans[i]
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
