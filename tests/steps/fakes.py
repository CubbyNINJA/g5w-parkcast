"""Fakes for the S1 step definitions.

Every fake writes what it was asked to do into one shared, ordered event list, so scenarios can
check order ("stale rules removed before the new rule is created", "UxPlay stopped before
cleanup"). Each event carries the controlled clock's time. Production types (Rule,
FirewallError, BuildError) are imported lazily from airplay_s1, so a missing implementation
fails a scenario rather than collection.
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


class FakeFirewall:
    """In-memory Windows Firewall. Rules are airplay_s1.firewall.Rule (a dataclass with an `id`)."""

    def __init__(self, events: Events, profile: str = "Public"):
        self.events = events
        self.profile = profile
        self.rules: dict[str, object] = {}
        self.fail_create = False
        self.fail_remove = False
        self._ids = itertools.count(1)

    def seed(self, rule) -> object:
        rule = dataclasses.replace(rule, id=f"seed-{next(self._ids)}")
        self.rules[rule.id] = rule
        return rule

    def active_profile(self) -> str:
        return self.profile

    def list_rules(self) -> list:
        self.events.add("fw.list")
        return list(self.rules.values())

    def create(self, rule):
        if self.fail_create:
            self.events.add("fw.create.failed")
            raise s1("firewall").FirewallError("creating firewall rules fails (fake)")
        rule = dataclasses.replace(rule, id=f"run-{next(self._ids)}")
        self.rules[rule.id] = rule
        self.events.add("fw.create", id=rule.id)
        return rule

    def remove(self, rule_id: str) -> None:
        self.events.add("fw.remove", id=rule_id)
        if self.fail_remove:
            raise s1("firewall").FirewallError("removing firewall rules fails (fake)")
        self.rules.pop(rule_id, None)


class FakeProc:
    _pids = itertools.count(4100)

    def __init__(self, events: Events, ignores_stop: bool):
        self.events = events
        self.pid = next(self._pids)
        self.exit_code: int | None = None
        self.ignores_stop = ignores_stop

    @property
    def alive(self) -> bool:
        return self.exit_code is None

    def poll(self) -> int | None:
        return self.exit_code

    def stop(self, timeout: float = 5.0) -> bool:
        self.events.add("proc.stop", pid=self.pid)
        if self.ignores_stop:
            return False
        if self.exit_code is None:
            self.exit_code = 0
        return True


class FakeLauncher:
    """Starts a fake UxPlay. `lines` are (second, text) pairs written to the run's log."""

    def __init__(self, events: Events, clock: FakeClock):
        self.events = events
        self.clock = clock
        self.lines: list[tuple[float, str]] = []
        self.exit_at_once: int | None = None
        self.exit_at: float | None = None
        self.ignores_stop = False
        self.procs: list[FakeProc] = []
        self.log_at_start: str | None = None
        self.argv: list[str] | None = None

    def start(self, argv: list[str], log):
        self.argv = list(argv)
        log.flush()
        self.log_at_start = Path(log.name).read_text(encoding="utf-8", errors="replace")
        self.events.add("proc.start", argv=list(argv))
        proc = FakeProc(self.events, self.ignores_stop)
        self.procs.append(proc)
        if self.exit_at_once is not None:
            proc.exit_code = self.exit_at_once
            return proc
        for t, text in self.lines:
            self.clock.at(t, lambda text=text: proc.alive and not log.closed and (log.write((text + "\n").encode("utf-8")), log.flush()))
        if self.exit_at is not None:
            self.clock.at(self.exit_at, lambda: setattr(proc, "exit_code", 1) if proc.alive else None)
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
        self.events.add("build.checkout", commit=commit)
        Path(dest).mkdir(parents=True, exist_ok=True)


class FakeCompiler:
    """mode: "ok" writes uxplay.exe; "fail" writes part of it and raises BuildError;
    "interrupt" writes part of it and raises KeyboardInterrupt."""

    def __init__(self, events: Events):
        self.events = events
        self.mode = "ok"
        self.commands = ["cmake -G Ninja ..", "ninja"]

    def compile(self, source_dir: Path, build_dir: Path) -> Path:
        self.events.add("build.compile", source=str(source_dir), build=str(build_dir))
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
