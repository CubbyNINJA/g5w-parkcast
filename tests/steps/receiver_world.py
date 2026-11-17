"""Shared state for the S3 tool scenarios (14_receiver_tools.feature).

`rworld` holds one scenario's fixtures and results. Each area's step module keeps its own fields on it,
named with its area prefix (`build_`, `stick_`, `zip_`, `eval_`), so the modules never overwrite each
other's state. `tmp` is the scenario's temporary directory.
"""

from __future__ import annotations

from types import SimpleNamespace

import pytest


@pytest.fixture
def rworld(tmp_path):
    return SimpleNamespace(tmp=tmp_path)
