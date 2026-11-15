"""Every step of every scenario, in every tier, has exactly one step definition.

pytest-bdd looks steps up one at a time as a scenario runs, so a scenario that fails at its first
step hides a missing or ambiguous definition later in it. This checks them all up front, including
the @host, @pc and @unit scenarios that the default run skips.
"""

import importlib
from pathlib import Path

from pytest_bdd.parser import FeatureParser

FEATURES = Path(__file__).resolve().parents[1] / "specs" / "features"
STEP_MODULES = ["steps.build_steps", "steps.run_steps", "steps.prompt_steps", "steps.log_steps", "steps.evidence_steps",
                "steps.boundary_steps", "steps.cli_steps", "steps.live_steps"]


def _definitions():
    out = []
    for name in STEP_MODULES:
        for value in vars(importlib.import_module(name)).values():
            ctx = getattr(value, "_pytest_bdd_step_context", None)
            if ctx is not None:
                out.append((name, ctx))
    return out


def _rendered_steps():
    for path in sorted(FEATURES.glob("*.feature")):
        feature = FeatureParser(str(FEATURES), path.name).parse()
        for template in feature.scenarios.values():
            contexts = [c for ex in ([template.examples] if not isinstance(template.examples, list) else template.examples)
                        if ex is not None for c in ex.as_contexts()] or [{}]
            for context in contexts:
                for step in template.render(context).steps:
                    yield f"{path.name}:{step.line_number}", step


def test_every_step_has_exactly_one_definition():
    """Given the feature files and the step modules in tests/steps
    When each scenario's steps are rendered with every Examples row
    Then each step matches exactly one definition of its type
    """
    defs = _definitions()
    problems = []
    for where, step in _rendered_steps():
        found = [mod for mod, ctx in defs if ctx.type in (None, step.type) and ctx.parser.is_matching(step.name)]
        if len(found) != 1:
            problems.append(f"{where} {step.type} {step.name!r}: {len(found)} definitions {found}")
    assert problems == [], "\n".join(problems)
