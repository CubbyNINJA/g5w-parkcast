"""Bind the Gherkin scenarios of the slices pytest-bdd runs (S1). S2 runs under Cucumber-JVM (OD-133).
Tier selection happens in conftest.py."""

from pytest_bdd import scenarios

from feature_sets import python_features

for _feature in python_features():
    scenarios(str(_feature))
