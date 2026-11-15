"""Tier selection (specs/README.md): @offline runs by default; @host needs --run-host; @pc needs --run-pc;
@unit never runs here.

Run from this project's own .venv (AGENTS.md). If collected from hmg_nemesis's root by a bare `pytest`,
where pytest-bdd isn't installed, these tests skip instead of breaking the main suite.
"""

import sys
from pathlib import Path

import pytest

_HERE = Path(__file__).resolve().parent
for _p in (_HERE, _HERE.parent / "tools"):  # also works without this project's pytest.ini
    if str(_p) not in sys.path:
        sys.path.insert(0, str(_p))

try:
    import pytest_bdd  # noqa: F401
except ImportError:  # outside this project's .venv
    collect_ignore_glob = ["*.py"]
else:
    # Each module's steps and the world fixture register as plugins, so pytest-bdd sees all of them.
    pytest_plugins = ["steps.world", "steps.build_steps", "steps.run_steps", "steps.prompt_steps", "steps.log_steps",
                      "steps.evidence_steps", "steps.boundary_steps", "steps.cli_steps", "steps.live_steps"]


def pytest_addoption(parser):
    parser.addoption("--run-host", action="store_true", default=False,
                     help="also run @host scenarios (this PC's MSYS2 and repository; no iPhone)")
    parser.addoption("--run-pc", action="store_true", default=False,
                     help="also run @pc scenarios (this PC's UxPlay build and the owner's iPhone)")


def pytest_collection_modifyitems(config, items):
    for item in items:
        tags = {m.name for m in item.iter_markers()}
        if "unit" in tags:
            item.add_marker(pytest.mark.skip(reason="@unit runs on the head unit; its evidence is the stick logs"))
        elif "host" in tags and not config.getoption("--run-host"):
            item.add_marker(pytest.mark.skip(reason="@host needs --run-host (this PC, no iPhone)"))
        elif "pc" in tags and not config.getoption("--run-pc"):
            item.add_marker(pytest.mark.skip(reason="@pc needs --run-pc (this PC and the owner's iPhone)"))
