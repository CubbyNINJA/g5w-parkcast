"""Every scenario follows specs/README.md's tag rules, and every tag names a real requirement or test."""

import trace_specs


def test_every_scenario_follows_the_tag_rules():
    """Given the feature files and docs/spec/REQUIREMENTS.md
    When the tag rules are checked
    Then no scenario breaks them (missing or duplicate slice, tier or kind, or an unknown AIR/AT ID)
    """
    assert trace_specs.check() == []
