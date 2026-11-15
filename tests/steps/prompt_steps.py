"""Steps for 02_firewall.feature: Windows' own prompt, the launch notice, the end-of-run reminder
and the owner-handled record (AIR-032 to AIR-034; OD-31, OD-33 to OD-38)."""

from __future__ import annotations

import json
import re
import subprocess
from pathlib import Path

from pytest_bdd import given, parsers, then, when

from steps.fakes import s1
from steps.run_steps import reminders
from steps.world import PROJECT, host_path, read_json, rec, run_dir

Q = r'"(?P<{}>[^"]*)"'
# Firewall-changing commands, listed here independently of the code under test (AT-32).
FIREWALL_COMMANDS = re.compile(r"New-NetFirewallRule|Set-NetFirewallRule|Remove-NetFirewallRule|Enable-NetFirewallRule|"
                               r"Disable-NetFirewallRule|Set-NetFirewallProfile|netsh(?:\.exe)?\s+(?:advfirewall|firewall)",
                               re.I)
# Ways of sending input to, focusing or closing another program's window.
WINDOW_CONTROL = re.compile(r"SendKeys|keybd_event|SendInput|PostMessage|SendMessage|SetForegroundWindow|WM_CLOSE|"
                            r"AppActivate|pyautogui|pywinauto", re.I)
SOURCE_SUFFIXES = {".py", ".ps1", ".psm1", ".sh", ".cmd", ".bat"}
CLAIMS = re.compile(r"\b(granted|declined|present|removed)\b", re.I)


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
    stops = [i for i, e in enumerate(world.events) if e["kind"] == "proc.stop"]
    assert stops and stops[-1] < index


@given("the run record can't be written once UxPlay is running")
def _(world):
    def block():
        record = run_dir(world) / "run.json"
        if record.is_file():
            record.unlink()
        record.mkdir(parents=True, exist_ok=True)
    world.clock.at(10, block)


@then("no run record exists")
def _(world):
    assert not (run_dir(world) / "run.json").is_file()


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
    text = _record_path(world).read_text(encoding="utf-8")
    assert not CLAIMS.search(text), CLAIMS.search(text)


@then("the summary says the firewall allowance was handled by the owner through Windows' prompt")
def _(world):
    line = [l for l in world.summary.splitlines() if "firewall" in l.lower()]
    assert len(line) == 1, line
    text = line[0].lower()
    assert "owner" in text and "windows" in text and "prompt" in text


@given("every process the S1 tools start is recorded")
def _(world, monkeypatch):
    real = subprocess.Popen
    world.processes = []

    class Recording(real):
        def __init__(self, args, *a, **kw):
            world.processes.append(args if isinstance(args, str) else " ".join(map(str, args)))
            super().__init__(args, *a, **kw)

    monkeypatch.setattr(subprocess, "Popen", Recording)


@then("none of the recorded processes is a firewall command")
def _(world):
    if not world.interrupted:
        rec(world)
    started = list(world.processes) + [" ".join(e["argv"]) for e in world.events.of("proc.start", "proc.create.failed")]
    assert started, "no process was recorded at all"
    assert not [p for p in started if FIREWALL_COMMANDS.search(p) or re.search(r"\bnetsh\b", p, re.I)], started


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
