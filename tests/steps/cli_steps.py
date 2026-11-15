"""Steps for 08_cli.feature and the runner's refused capture options (AIR-031, OD-28)."""

from __future__ import annotations

import contextlib
import io
import subprocess
import sys
from pathlib import Path

from pytest_bdd import given, parsers, then, when

from steps.fakes import s1
from steps.world import PROJECT, SESSION, make_verified_build, read_json, snapshot, wrap

Q = r'"(?P<{}>[^"]*)"'
MUTATING = ("build.checkout", "build.compile", "fw.create", "fw.remove", "proc.start")


def _deps(w):
    return s1("cli").Deps(work_root=w.work_root, runs_root=w.runs_root, firewall=w.firewall, launcher=w.launcher,
                          clock=w.clock, owner=w.owner, upstream=w.upstream, compiler=w.compiler,
                          inventory=lambda: None)


def _cli(w, argv: list[str]) -> None:
    w.before = snapshot(w.tmp)
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        try:
            w.rc = s1("cli").main(argv, deps=_deps(w))
        except SystemExit as e:  # argparse exits for --help and usage errors
            w.rc = e.code if isinstance(e.code, int) else 1
    w.out, w.err = out.getvalue(), err.getvalue()
    w.after = snapshot(w.tmp)


def _no_changes(w) -> None:
    assert not w.events.of(*MUTATING), w.events
    assert w.after == w.before


@when(parsers.re(r'"s1\.py(?P<args>[^"]*)" runs'))
def _(world, args):
    _cli(world, args.split())


@when(parsers.re("the runner is started with the extra argument " + Q.format("option")))
def _(world, option):
    _cli(world, ["run", option])


@then(parsers.re(r"it exits with code (?P<code>\d+)"))
def _(world, code):
    assert world.rc == int(code), (world.rc, world.out, world.err)


@then("it exits with code 2 and prints usage")
def _(world):
    assert world.rc == 2
    assert "usage" in world.err.lower()


@then(parsers.re("it prints usage naming " + Q.format("shown")))
def _(world, shown):
    assert "usage" in world.out.lower() and shown in world.out


@then("it prints usage on stderr")
def _(world):
    assert "usage" in world.err.lower()


@then("no build starts, no firewall rule changes, UxPlay is never launched and no file is created or changed")
def _(world):
    _no_changes(world)


@then("no firewall rule changes and UxPlay is never launched")
def _(world):
    assert not world.events.of("fw.create", "fw.remove", "proc.start"), world.events


@given(parsers.re("the build provenance file is (?P<state>missing|unreadable|not valid provenance)"))
def _(world, state):
    world.work_root.mkdir(parents=True, exist_ok=True)
    world.provenance_file = world.work_root / "provenance.json"
    if state == "unreadable":
        world.provenance_file.mkdir()  # exists, but reading it as a file fails
    elif state == "not valid provenance":
        world.provenance_file.write_text("{ not json", encoding="utf-8")


@then("it exits with code 1 naming the provenance file")
def _(world):
    assert world.rc == 1
    assert str(world.provenance_file) in world.err


@given(parsers.re("(?P<location>the runs folder|the report path|the verdict path) can't be written"))
def _(world, location):
    make_verified_build(world)
    (world.cwd / "fixture.log").write_text(wrap(SESSION["GO"]), encoding="utf-8", newline="\n")
    (world.cwd / "fixture-run").mkdir()
    (world.cwd / "fixture-run" / "uxplay.log").write_text(wrap(SESSION["GO"]), encoding="utf-8", newline="\n")
    if location == "the runs folder":
        world.runs_root.write_text("a file where the runs folder should be", encoding="utf-8")
        world.location = world.runs_root
    elif location == "the report path":
        world.location = world.cwd / "report.json"
        world.location.mkdir()
    else:
        world.location = world.cwd / "fixture-run" / "verdict.json"
        world.location.mkdir()


@then("it exits with code 1 naming that location")
def _(world):
    assert world.rc == 1
    assert world.location.name in world.err


@then("no partial output file is left behind")
def _(world):
    added = set(world.after) - set(world.before)
    changed = {k for k in world.before if world.after.get(k) != world.before[k]}
    assert not added and not changed, (added, changed)


def _project_files() -> dict[str, str]:
    skip = {".venv", "__pycache__", ".pytest_cache"}  # the interpreter and its caches, as the step says
    return {k: v for k, v in snapshot(PROJECT).items()
            if not set(Path(k).relative_to(PROJECT).parts) & skip}


@when(parsers.re("this project's interpreter runs " + Q.format("command") + " as a subprocess"))
def _(world, command):
    world.before = _project_files()
    venv = PROJECT / ".venv" / "Scripts" / "python.exe"
    world.proc = subprocess.run([str(venv if venv.is_file() else sys.executable), *command.split()], cwd=PROJECT, capture_output=True, text=True,
                                timeout=120)
    world.after = _project_files()


@then(parsers.re(r"the subprocess exits with code (?P<code>\d+)"))
def _(world, code):
    assert world.proc.returncode == int(code), world.proc.stdout + world.proc.stderr


@then("its output names the commands check, build, run, analyse, evaluate and summary")
def _(world):
    for command in ("check", "build", "run", "analyse", "evaluate", "summary"):
        assert command in world.proc.stdout, command


@then("no file in the project, apart from its .venv and Python caches, is created or changed")
def _(world):
    assert world.after == world.before


@given("a fixture run whose evidence makes its verdict NO-GO")
def _(world):
    (world.cwd / "fixture-run").mkdir()
    (world.cwd / "fixture-run" / "uxplay.log").write_text(wrap(SESSION["NO-GO"]), encoding="utf-8", newline="\n")


@then("the saved run verdict is NO-GO")
def _(world):
    assert read_json(world.cwd / "fixture-run" / "verdict.json")["verdict"] == "NO-GO"
