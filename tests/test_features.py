"""Bind every Gherkin scenario. Tier selection happens in conftest.py."""

from pathlib import Path

from pytest_bdd import scenarios

FEATURES = Path(__file__).resolve().parents[1] / "specs" / "features"

if any(FEATURES.glob("*.feature")):
    scenarios(str(FEATURES))
