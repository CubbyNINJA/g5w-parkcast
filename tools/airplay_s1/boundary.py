"""The Gherkin exception stays inside this project (AIR-015; OD-03, OD-07): no `.feature` file and
no `pytest_bdd` import anywhere else in the repository."""

from __future__ import annotations

import os
import re
from pathlib import Path

PROJECT = "Sources/airplay-receiver/"
SKIP_DIRS = {".git", ".venv", "venv", "__pycache__", "node_modules", ".astra"}
IMPORT = re.compile(r"^\s*(?:from|import)\s+pytest_bdd\b", re.M)


def check(repo_root) -> list[str]:
    """Repository-relative paths outside this project that hold Gherkin or import pytest_bdd."""
    root = Path(repo_root)
    problems = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        rel_dir = Path(dirpath).relative_to(root).as_posix()
        rel_dir = "" if rel_dir == "." else rel_dir + "/"
        if (rel_dir + "/").startswith(PROJECT) or rel_dir.startswith(PROJECT):
            dirnames[:] = []
            continue
        for name in filenames:
            rel = rel_dir + name
            if name.endswith(".feature"):
                problems.append(rel)
            elif name.endswith(".py"):
                try:
                    text = (Path(dirpath) / name).read_text(encoding="utf-8", errors="replace")
                except OSError:
                    continue
                if IMPORT.search(text):
                    problems.append(rel)
    return sorted(problems)
