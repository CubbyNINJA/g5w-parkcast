"""Steps for 07_test_boundary.feature (AIR-015)."""

from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path

from pytest_bdd import given, parsers, then, when

from steps.fakes import s1
from steps.world import PROJECT, REPO

Q = r'"(?P<{}>[^"]*)"'
BLOCK_BDD = ("import sys; sys.modules['pytest_bdd'] = None; import pytest; "
             "sys.exit(pytest.main(sys.argv[1:]))")


def _collect_without_bdd(python: str, target: Path) -> subprocess.CompletedProcess:
    env = dict(os.environ, PYTEST_DISABLE_PLUGIN_AUTOLOAD="1")
    return subprocess.run([python, "-c", BLOCK_BDD, "--collect-only", "-q", "-p", "no:cacheprovider", str(target)],
                          cwd=target, env=env, capture_output=True, text=True, timeout=300)


def _write(root: Path, rel: str, text: str) -> None:
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8")


@given(parsers.re("a synthetic repository with feature files and pytest_bdd imports only under " + Q.format("project")))
def _(world, project):
    root = world.repo = world.tmp / "repo"
    _write(root, f"{project}specs/features/a.feature", "Feature: a\n")
    _write(root, f"{project}tests/test_features.py", "from pytest_bdd import scenarios\n")
    _write(root, "Sources/tests/unit/test_builder.py", "import pytest\n\ndef test_x():\n    pass\n")


@given(parsers.re("it also has (?P<what>a \".feature\" file|a Python file importing pytest_bdd) at " + Q.format("path")))
def _(world, what, path):
    _write(world.repo, path, "Feature: x\n" if path.endswith(".feature") else "import pytest_bdd\n")


@given("hmg_nemesis's real source tree")
def _(world):
    world.repo = REPO


@when("the boundary check runs on it")
def _(world):
    world.problems = s1("boundary").check(world.repo)


@then("the boundary check passes")
def _(world):
    assert world.problems == []


@then(parsers.re("the boundary check fails naming " + Q.format("path")))
def _(world, path):
    assert any(path in p for p in world.problems), world.problems


@given("an interpreter in which importing pytest_bdd fails")
def _(world):
    world.python = sys.executable


@when("it collects this project's tests")
def _(world):
    world.collected = _collect_without_bdd(world.python, PROJECT)


@then("it collects nothing and reports no error")
def _(world):
    res = world.collected
    assert res.returncode in (0, 5), res.stdout + res.stderr  # 5: no tests collected
    assert "error" not in (res.stdout + res.stderr).lower()


@given("this project's .venv")
def _(world):
    world.python = str(PROJECT / ".venv" / "Scripts" / "python.exe")
    assert Path(world.python).is_file()


@when("its interpreter collects this project's tests")
def _(world):
    probe = ("import json, sys, pytest, pytest_bdd; print(json.dumps({'executable': sys.executable, "
             "'prefix': sys.prefix, 'pytest': pytest.__version__, 'pytest_bdd': pytest_bdd.__version__}))")
    world.env_info = json.loads(subprocess.run([world.python, "-c", probe], capture_output=True, text=True,
                                               check=True).stdout)
    world.collected = subprocess.run([world.python, "-m", "pytest", "--collect-only", "-q"], cwd=PROJECT,
                                     capture_output=True, text=True, timeout=300)


@then(parsers.re("the interpreter's path is inside this project's " + Q.format("venv")))
def _(world, venv):
    assert Path(world.env_info["prefix"]).resolve() == (PROJECT / venv).resolve()


@then("pytest-bdd is importable there")
def _(world):
    assert world.env_info["pytest_bdd"]


@then(parsers.re("the feature files are bound by " + Q.format("binder")))
def _(world, binder):
    assert world.collected.returncode == 0, world.collected.stdout + world.collected.stderr
    assert f"{binder}::" in world.collected.stdout.replace("\\", "/")


@then("the interpreter path and the pytest and pytest-bdd versions are saved")
def _(world):
    out = PROJECT / "runs" / "host-environment.json"
    out.parent.mkdir(exist_ok=True)
    out.write_text(json.dumps(world.env_info, indent=2), encoding="utf-8")
    assert json.loads(out.read_text(encoding="utf-8"))["executable"] == world.env_info["executable"]


@then(parsers.re("the main suite " + Q.format("suite") + " collects without error where pytest_bdd can't be imported"))
def _(world, suite):
    res = _collect_without_bdd(sys.executable, REPO / suite)
    assert res.returncode == 0, res.stdout + res.stderr
