"""Steps for 02_firewall.feature: Windows' own prompt, the launch notice, the end-of-run reminder
and the owner-handled record (AIR-032 to AIR-034; OD-31, OD-33 to OD-38)."""

from __future__ import annotations

import os
import re
import subprocess
from pathlib import Path

from pytest_bdd import given, parsers, then, when

from steps.fakes import s1
from steps.run_steps import reminders
from steps.world import PROJECT, host_path, read_json, rec, run_dir

Q = r'"(?P<{}>[^"]*)"'
# Firewall-changing commands, listed here independently of the code under test (AT-32).
# Any NetSecurity firewall cmdlet, any netsh, and the firewall COM API, in any form (string or list).
FIREWALL_COMMANDS = re.compile(r"NetFirewall|\bnetsh\b|advfirewall|HNetCfg\.Fw|INetFw|FwPolicy2", re.I)
# Ways of sending input to, focusing or closing another program's window.
WINDOW_CONTROL = re.compile(r"SendKeys|keybd_event|SendInput|PostMessage|SendMessage|SetForegroundWindow|WM_CLOSE|"
                            r"AppActivate|pyautogui|pywinauto", re.I)
SOURCE_SUFFIXES = {".py", ".ps1", ".psm1", ".sh", ".cmd", ".bat"}
CLAIMS = re.compile(r"\b(granted|declined|present|removed|allowed|added|created)\b", re.I)
FIREWALL_KEYS = re.compile(r"firewall|allow|rule|block", re.I)


def _notices(w) -> list[dict]:
    """Owner messages, before UxPlay started, about Windows' firewall prompt."""
    start = w.events.first("proc.start")
    before = w.events[:start] if start >= 0 else []
    return [e for e in before if e["kind"] == "owner.tell" and "Public networks" in e["text"]]


def _reminder(w) -> dict:
    found = reminders(w)
    assert len(found) == 1, f"expected one reminder, got {len(found)}"
    return w.events[found[0]]


@then(parsers.re("before UxPlay started, the owner was told that Windows may ask to allow " + Q.format("program")))
def _(world, program):
    rec(world)
    notices = _notices(world)
    assert len(notices) == 1, "no single firewall notice before launch"
    text = notices[0]["text"]
    assert "Windows" in text and program in text and "allow" in text.lower()
    world.notice = text


@then(parsers.re("that notice said to tick " + Q.format("box") + " and click Allow"))
def _(world, box):
    assert box in world.notice and "Allow" in world.notice


@then("that notice said no prompt may appear if an allowance or block already exists")
def _(world):
    text = world.notice.lower()
    assert "no prompt" in text and "already" in text and "block" in text and "allow" in text


@given("nothing else is set up")
def _(world):
    pass


@then("the console owner is given no firewall notice or reminder")
def _(world):
    told = [m for m in world.owner.messages if "Public networks" in m or "Allowed apps" in m or "firewall" in m.lower()]
    assert not told, told


@then("the owner is given no firewall notice or reminder")
def _(world):
    rec(world)
    told = [m for m in world.owner.messages if "Public networks" in m or "Allowed apps" in m or "firewall" in m.lower()]
    assert not told, told


@then("the owner is given no firewall reminder")
def _(world):
    rec(world)
    assert not reminders(world)


@then(parsers.re("the owner is reminded to remove " + Q.format("program") + " at " + Q.format("path") + " from "
                 + Q.format("place")))
def _(world, program, path, place):
    text = _reminder(world)["text"]
    for words in (program, str(host_path(world, path)), place):
        assert words in text, words
    assert "remove" in text.lower()


@then("the reminder says a declined or closed prompt may have left a block entry to remove too")
def _(world):
    text = _reminder(world)["text"].lower()
    assert "block" in text and ("declined" in text or "closed" in text)


@then("the reminder came after UxPlay had stopped and after the log's end marker")
def _(world):
    reminder = _reminder(world)
    index = world.events.index(reminder)
    exited = world.events.first("proc.exit")
    assert 0 <= exited < index, "the reminder came before UxPlay had exited"
    logfile = s1("logfile")
    assert any(logfile.is_end_marker(l) for l in reminder["log"].splitlines()), \
        "the reminder came before the end marker was written"


@then("the log's last line is still the runner's end marker")
def _(world):
    text = (run_dir(world) / "uxplay.log").read_text(encoding="utf-8")
    assert text.endswith("\n") and s1("logfile").is_end_marker(text.splitlines()[-1])
    assert "Allowed apps" not in text, "the reminder was written into the log"


@then("the reminder came after the run reported the process it couldn't stop")
def _(world):
    index = world.events.index(_reminder(world))
    pid = str(world.launcher.procs[0].pid)
    reports = [i for i, e in enumerate(world.events) if e["kind"] == "owner.tell" and pid in e["text"]
               and "Allowed apps" not in e["text"]]
    assert reports, "the run never told the owner which process it couldn't stop"
    assert reports[0] < index, "the reminder came before the leftover process was reported"


@then("the end marker names the process ID still running")
def _(world):
    last = (run_dir(world) / "uxplay.log").read_text(encoding="utf-8").splitlines()[-1]
    assert s1("logfile").is_end_marker(last) and str(world.launcher.procs[0].pid) in last, last


@given("the run record can't be written once UxPlay is running")
def _(world):
    def block():
        # Every run folder that exists by now (the runner's or the command's) gets a directory
        # where its run.json would go, so writing the record fails.
        for folder in [p for p in world.runs_root.iterdir() if p.is_dir()] if world.runs_root.is_dir() else []:
            record = folder / "run.json"
            if record.is_file():
                record.unlink()
            record.mkdir(exist_ok=True)
    world.clock.at(10, block)


@then("no run record exists")
def _(world):
    assert not [p for p in world.runs_root.rglob("run.json") if p.is_file()]


@then("the console output doesn't claim a record was made")
def _(world):
    said = world.out + world.err + "\n".join(world.owner.messages)
    assert not re.search(r"\b(saved|written|recorded) (to|in)\b|run\.json (saved|written)", said, re.I), said
    assert re.search(r"run record", world.err + "\n".join(world.owner.messages), re.I), "the failure wasn't reported"


@then("the run doesn't claim a record was made")
def _(world):
    assert rec(world).record_written is False
    assert any("run record" in e.lower() for e in world.record.errors)
    assert not any(re.search(r"\b(saved|written|recorded) to\b", m, re.I) for m in world.owner.messages)


@when(parsers.re("the summary is written for the tier " + Q.format("tier")))
def _(world, tier):
    world.summary = s1("evidence").write_summary(world.tmp / "docs" / "evidence", run_dir(world),
                                                 tier=tier).read_text(encoding="utf-8")


def _record_path(world) -> Path:
    live = getattr(world, "live_dir", None)
    return (live if live is not None else run_dir(world)) / "run.json"


@then(parsers.re("the run record says the firewall was " + Q.format("value")))
def _(world, value):
    assert read_json(_record_path(world))["firewall"] == value


@then("the run record doesn't say an allowance was granted, declined, present or removed")
def _(world):
    record = read_json(_record_path(world))
    fields = {k: v for k, v in record.items() if FIREWALL_KEYS.search(k)}
    assert fields == {"firewall": "owner-handled"}, fields
    assert not CLAIMS.search(str(fields)), fields


@then("the summary says the firewall allowance was handled by the owner through Windows' prompt")
def _(world):
    line = [l for l in world.summary.splitlines() if "firewall" in l.lower()]
    assert len(line) == 1, line
    text = line[0].lower()
    assert "owner" in text and "windows" in text and "prompt" in text


@given("every process the S1 tools start is recorded")
def _(world, monkeypatch):
    """Record every process the tools try to start, through every Windows route, and start none:
    an offline test must never run a real command, least of all a firewall one."""
    import _winapi
    world.processes = []

    def refuse(command):
        world.processes.append(command if isinstance(command, str) else " ".join(map(str, command)))
        raise OSError(1, "process creation refused by the offline test", world.processes[-1])

    monkeypatch.setattr(_winapi, "CreateProcess", lambda executable, args, *a, **kw: refuse(args or executable))
    monkeypatch.setattr(os, "system", refuse)
    if hasattr(os, "startfile"):
        monkeypatch.setattr(os, "startfile", lambda path, *a, **kw: refuse(path))
    for name in ("spawnv", "spawnve", "spawnl", "spawnle"):
        if hasattr(os, name):
            monkeypatch.setattr(os, name, lambda mode, path, *a, **kw: refuse([path, *map(str, a[:1])]))
    # Canary: the hook must see and refuse a process before its silence means anything.
    try:
        subprocess.run(["s1-canary", "--probe"])
    except OSError:
        pass
    assert world.processes == ["s1-canary --probe"], world.processes
    world.processes.clear()


@then("none of the recorded processes is a firewall command")
def _(world):
    started = list(world.processes) + [" ".join(e["argv"]) for e in world.events.of("proc.start", "proc.create.failed")]
    assert not [p for p in started if FIREWALL_COMMANDS.search(p)], started


@given(parsers.re("the S1 tools' source under " + Q.format("folder")))
def _(world, folder):
    root = PROJECT / folder
    world.sources = [p for p in root.rglob("*") if p.is_file() and p.suffix.lower() in SOURCE_SUFFIXES
                     and "__pycache__" not in p.parts]
    assert any(p.name == "runner.py" for p in world.sources), "the runner's source isn't there to check"


@then("no file there names a firewall-changing command")
def _(world):
    hits = [str(p) for p in world.sources if FIREWALL_COMMANDS.search(p.read_text(encoding="utf-8", errors="replace"))]
    assert not hits, hits


@then("no file there sends input to, focuses or closes another window")
def _(world):
    hits = [str(p) for p in world.sources if WINDOW_CONTROL.search(p.read_text(encoding="utf-8", errors="replace"))]
    assert not hits, hits
