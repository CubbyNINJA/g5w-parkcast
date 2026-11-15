"""Fakes for the S1 step definitions.

Every fake writes what it was actually asked to do into one shared, ordered event list, with the
controlled clock's time and immutable copies of the arguments and results. Steps check those
events, not what the code under test says it did. Production types (Rule, FirewallError,
BuildError, CaptureError) are imported lazily from airplay_s1, so a missing implementation fails
a scenario rather than collection.
"""

from __future__ import annotations

import dataclasses
import importlib
import itertools
from pathlib import Path


def s1(module: str):
    """Import airplay_s1.<module>, the S1 implementation under test (tools/airplay_s1/)."""
    return importlib.import_module(f"airplay_s1.{module}")


class Events(list):
    def __init__(self):
        super().__init__()
        self.clock = None

    def add(self, kind: str, **detail):
        self.append(dict(kind=kind, t=self.clock.now() if self.clock else None, **detail))

    def of(self, *kinds: str) -> list[dict]:
        return [e for e in self if e["kind"] in kinds]

    def first(self, kind: str, **match) -> int:
        """Index of the first event of this kind matching every given field, or -1."""
        for i, e in enumerate(self):
            if e["kind"] == kind and all(e.get(k) == v for k, v in match.items()):
                return i
        return -1

    def last(self, kind: str, **match) -> int:
        found = -1
        for i, e in enumerate(self):
            if e["kind"] == kind and all(e.get(k) == v for k, v in match.items()):
                found = i
        return found


class FakeClock:
    """Seconds from 0. sleep() advances time and fires scheduled callbacks in time order."""

    def __init__(self, events: Events):
        self.t = 0.0
        self._queue: list[tuple[float, int, object]] = []
        self._seq = itertools.count()
        events.clock = self

    def now(self) -> float:
        return self.t

    def at(self, t: float, fn) -> None:
        self._queue.append((float(t), next(self._seq), fn))
        self._queue.sort(key=lambda q: (q[0], q[1]))

    def sleep(self, seconds: float) -> None:
        target = self.t + max(seconds, 0)
        while self._queue and self._queue[0][0] <= target:
            t, _, fn = self._queue.pop(0)
            self.t = max(self.t, t)
            fn()
        self.t = target


def rule_fields(rule) -> dict:
    """An immutable copy of a rule's fields, as the fake firewall received or returned it."""
    d = dataclasses.asdict(rule)
    d["profiles"] = tuple(d["profiles"])
    return d


class FakeFirewall:
    """In-memory Windows Firewall. Rules are airplay_s1.firewall.Rule (a dataclass with an `id`).

    Failure modes: fail_create, fail_remove (remove raises), remove_no_effect (remove returns
    normally but the rule stays, as a backend that silently fails would)."""

    def __init__(self, events: Events, profile: str = "Public"):
        self.events = events
        self.profile = profile
        self.rules: dict[str, object] = {}
        self.fail_create = False
        self.fail_remove = False
        self.remove_no_effect = False
        self._ids = itertools.count(1)

    def seed(self, rule) -> object:
        rule = dataclasses.replace(rule, id=f"seed-{next(self._ids)}")
        self.rules[rule.id] = rule
        return rule

    def active_profile(self) -> str:
        return self.profile

    def list_rules(self) -> list:
        rules = list(self.rules.values())
        self.events.add("fw.list", ids=tuple(r.id for r in rules), rules=tuple(rule_fields(r) for r in rules))
        return rules

    def create(self, rule):
        asked = rule_fields(rule)
        if self.fail_create:
            self.events.add("fw.create.failed", asked=asked)
            raise s1("firewall").FirewallError("creating firewall rules fails (fake)")
        rule = dataclasses.replace(rule, id=f"run-{next(self._ids)}")
        self.rules[rule.id] = rule
        self.events.add("fw.create", id=rule.id, asked=asked)
        return rule

    def remove(self, rule_id: str) -> None:
        self.events.add("fw.remove", id=rule_id)
        if self.fail_remove:
            raise s1("firewall").FirewallError("removing firewall rules fails (fake)")
        if not self.remove_no_effect:
            self.rules.pop(rule_id, None)


class FakeProc:
    _pids = itertools.count(4100)

    def __init__(self, events: Events, clock: FakeClock, log_path: Path, ignores_stop: bool, stop_delay: float):
        self.events = events
        self.clock = clock
        self.log_path = log_path
        self.pid = next(self._pids)
        self.exit_code: int | None = None
        self.ignores_stop = ignores_stop
        self.stop_delay = stop_delay

    @property
    def alive(self) -> bool:
        return self.exit_code is None

    def exit(self, code: int) -> None:
        if self.exit_code is None:
            self.exit_code = code
            log = self.log_path.read_text(encoding="utf-8", errors="replace") if self.log_path.is_file() else ""
            self.events.add("proc.exit", pid=self.pid, code=code, log_at_exit=log)

    def poll(self) -> int | None:
        return self.exit_code

    def stop(self, timeout: float = 5.0) -> bool:
        """Ask the process to stop and wait up to `timeout` seconds. It exits after stop_delay."""
        self.events.add("proc.stop", pid=self.pid)
        if self.ignores_stop:
            self.clock.sleep(timeout)
            return False
        if self.stop_delay > timeout:
            self.clock.sleep(timeout)
            return False
        self.clock.sleep(self.stop_delay)
        self.exit(0)
        return True


class FakeLauncher:
    """Starts a fake UxPlay that writes `lines` ((second, text) pairs) to the run's log.

    By default it prints UxPlay's banner and one diagnostic line just after launch. Failure
    modes: exit_at_once, exit_at, ignores_stop, stop_delay, capture_fails (the process starts
    but its output can't be attached), create_fails (process creation raises OSError)."""

    BANNER = "UxPlay 1.74: An Open-Source AirPlay mirroring and audio-streaming server."
    DIAGNOSTIC = "fake diagnostic line from UxPlay"

    def __init__(self, events: Events, clock: FakeClock):
        self.events = events
        self.clock = clock
        self.lines: list[tuple[float, str]] = [(0.5, self.BANNER), (0.6, self.DIAGNOSTIC)]
        self.exit_at_once: int | None = None
        self.exit_at: float | None = None
        self.ignores_stop = False
        self.stop_delay = 2.0
        self.capture_fails = False
        self.create_fails = False
        self.procs: list[FakeProc] = []
        self.emitted: list[str] = []
        self.log_at_start: str | None = None
        self.argv: tuple[str, ...] | None = None

    def start(self, argv: list[str], log):
        self.argv = tuple(argv)
        if self.create_fails:
            self.events.add("proc.create.failed", argv=tuple(argv))
            raise OSError(2, "fake: the system cannot create the process")
        log.flush()
        log_path = Path(log.name)
        self.log_at_start = log_path.read_text(encoding="utf-8", errors="replace")
        proc = FakeProc(self.events, self.clock, log_path, self.ignores_stop, self.stop_delay)
        self.procs.append(proc)
        self.events.add("proc.start", pid=proc.pid, argv=tuple(argv))
        if self.capture_fails:
            raise s1("runner").CaptureError("fake: UxPlay's output can't be attached to the log", proc=proc)
        if self.exit_at_once is not None:
            proc.exit(self.exit_at_once)
            return proc

        def write(text):
            if not proc.alive:
                return
            if log.closed:
                self.events.add("log.write.after.close", text=text)
                return
            log.write((text + "\n").encode("utf-8"))
            log.flush()
            self.emitted.append(text)

        for t, text in self.lines:
            self.clock.at(t, lambda text=text: write(text))
        if self.exit_at is not None:
            self.clock.at(self.exit_at, lambda: proc.exit(1))
        return proc


class FakeOwner:
    """The owner at the console: messages shown, when the owner ends the test, answers to questions."""

    def __init__(self):
        self.messages: list[str] = []
        self.asked: list[str] = []
        self.answers: list[str | None] = ["27.0 (24A5260a)"]
        self.end_at: float | None = 1.0
        self.on_poll = None

    def tell(self, message: str) -> None:
        self.messages.append(message)

    def ended(self, now: float) -> bool:
        if self.on_poll:
            self.on_poll(now)
        return self.end_at is not None and now >= self.end_at

    def ask(self, question: str) -> str | None:
        self.asked.append(question)
        return self.answers.pop(0) if self.answers else None


class FakeUpstream:
    def __init__(self, events: Events, commit: str = "05b0275cb1b726592700e47a6e0307544b3d3a52", version: str = "1.74"):
        self.events = events
        self.master = commit
        self.versions = {commit: version}

    def master_commit(self) -> str:
        return self.master

    def version_at(self, commit: str) -> str:
        return self.versions.get(commit, "1.74")

    def checkout(self, commit: str, dest: Path) -> None:
        self.events.add("build.checkout", commit=commit, dest=str(dest))
        Path(dest).mkdir(parents=True, exist_ok=True)


class FakeCompiler:
    """mode: "ok" writes uxplay.exe; "fail" writes part of it, then raises BuildError;
    "fail-early" raises BuildError before touching any output; "interrupt" writes part of it,
    then raises KeyboardInterrupt."""

    def __init__(self, events: Events):
        self.events = events
        self.mode = "ok"
        self.commands = ["cmake -G Ninja ..", "ninja"]

    def compile(self, source_dir: Path, build_dir: Path) -> Path:
        self.events.add("build.compile", source=str(source_dir), build=str(build_dir), mode=self.mode)
        if self.mode == "fail-early":
            raise s1("build").BuildError("compiler failed before writing anything (fake)")
        build_dir = Path(build_dir)
        build_dir.mkdir(parents=True, exist_ok=True)
        exe = build_dir / "uxplay.exe"
        if self.mode == "ok":
            exe.write_bytes(b"MZ fake uxplay 1.74 " + str(source_dir).encode())
            return exe
        exe.write_bytes(b"MZ partial")
        if self.mode == "interrupt":
            raise KeyboardInterrupt
        raise s1("build").BuildError("compiler failed (fake)")
