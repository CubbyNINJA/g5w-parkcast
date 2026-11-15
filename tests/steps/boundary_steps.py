"""Steps for 07_test_boundary.feature (AIR-015)."""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
from pathlib import Path

from pytest_bdd import given, parsers, then, when

from steps.fakes import s1
from steps.world import PROJECT, REPO

Q = r'"(?P<{}>[^"]*)"'
BLOCK_BDD = ("import sys; sys.modules['pytest_bdd'] = None; import pytest; "
             "sys.exit(pytest.main(sys.argv[1:]))")
COLLECTED = re.compile(r"^(?:(\d+) tests? collected|no tests collected)", re.M)


def _collect(python: str, target: Path, block_bdd: bool) -> dict:
    """Collect the tests under target, saving the command, interpreter, exit code, output and count."""
    env = dict(os.environ)
    if block_bdd:
        env["PYTEST_DISABLE_PLUGIN_AUTOLOAD"] = "1"
        argv = [python, "-c", BLOCK_BDD, "--collect-only", "-q", "-p", "no:cacheprovider", str(target)]
    else:
        argv = [python, "-m", "pytest", "--collect-only", "-q", "-p", "no:cacheprovider", str(target)]
    res = subprocess.run(argv, cwd=target, env=env, capture_output=True, text=True, timeout=300)
    items = [l for l in res.stdout.splitlines() if "::" in l]
    return dict(command=argv, interpreter=python, returncode=res.returncode, stdout=res.stdout, stderr=res.stderr,
                items=len(items), item_ids=items)


def _save(name: str, data: dict) -> Path:
    out = PROJECT / "runs" / name
    out.parent.mkdir(exist_ok=True)
    out.write_text(json.dumps(data, indent=2), encoding="utf-8")
    return out


def _write(root: Path, rel: str, text: str) -> None:
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8")


@given(parsers.re("a synthetic repository with feature files and pytest_bdd imports only under " + Q.format("project")))
def _(world, project):
    root = world.repo = world.tmp / "repo"
    _write(root, f"{project}specs/features/a.feature", "Feature: a\n")
    _write(root, f"{project}tests/test_features.py", "from pytest_bdd import scenarios\n")
    _write(root, f"{project}tests/steps/x.py", "import pytest_bdd.parsers\n")
    _write(root, "Sources/tests/unit/test_builder.py", "import pytest\n\ndef test_x():\n    pass\n")


@given(parsers.re("it also has (?P<what>a \".feature\" file|a Python file importing pytest_bdd) at " + Q.format("path")))
def _(world, what, path):
    _write(world.repo, path, "Feature: x\n" if path.endswith(".feature") else "from pytest_bdd import given\n")


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
    assert world.problems == [path], world.problems


@given("an interpreter in which importing pytest_bdd fails")
def _(world):
    world.python = sys.executable


@when("it collects this project's tests")
def _(world):
    world.collected = _collect(world.python, PROJECT, block_bdd=True)


@then("it collects nothing and reports no error")
def _(world):
    res = world.collected
    out = res["stdout"] + res["stderr"]
    assert res["returncode"] == 5, out  # pytest's "no tests collected"
    assert res["items"] == 0 and "no tests collected" in out
    assert "error" not in out.lower()


@given("this project's .venv")
def _(world):
    world.python = str(PROJECT / ".venv" / "Scripts" / "python.exe")
    assert Path(world.python).is_file()


@when("its interpreter collects this project's tests")
def _(world):
    probe = ("import json, sys, importlib.metadata as m; print(json.dumps({'executable': sys.executable, "
             "'prefix': sys.prefix, 'pytest': m.version('pytest'), 'pytest_bdd': m.version('pytest-bdd')}))")
    world.env_info = json.loads(subprocess.run([world.python, "-c", probe], capture_output=True, text=True,
                                               check=True).stdout)
    world.collected = _collect(world.python, PROJECT, block_bdd=False)


@then(parsers.re("the interpreter's path is inside this project's " + Q.format("venv")))
def _(world, venv):
    assert Path(world.env_info["prefix"]).resolve() == (PROJECT / venv).resolve()
    assert Path(world.env_info["executable"]).resolve().is_relative_to((PROJECT / venv).resolve())


@then("pytest-bdd is importable there")
def _(world):
    assert world.env_info["pytest_bdd"]


@then(parsers.re("the feature files are bound by " + Q.format("binder")))
def _(world, binder):
    res = world.collected
    assert res["returncode"] == 0, res["stdout"] + res["stderr"]
    bound = [i for i in res["item_ids"] if i.replace("\\", "/").startswith(f"{binder}::")]
    assert bound, "no scenario was collected from the binder"


@then("the interpreter path and the pytest and pytest-bdd versions are saved")
def _(world):
    out = _save("host-environment.json", dict(world.env_info, collection=world.collected))
    saved = json.loads(out.read_text(encoding="utf-8"))
    assert saved["executable"] == world.env_info["executable"] and saved["collection"]["items"] > 0


@then(parsers.re("the main suite " + Q.format("suite") + " collects without error where pytest_bdd can't be imported"))
def _(world, suite):
    res = _collect(sys.executable, REPO / suite, block_bdd=True)
    _save("host-main-suite-collection.json", res)
    assert res["returncode"] == 0, res["stdout"] + res["stderr"]
    assert res["items"] > 0
