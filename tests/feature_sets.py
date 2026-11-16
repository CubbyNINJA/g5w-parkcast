"""Which feature files pytest-bdd runs. Slices in JAVA_SLICES run under Cucumber-JVM instead (OD-133, OD-134):
`./gradlew test` runs them; the tag-rule and traceability checks still cover every feature file."""

import re
from pathlib import Path

FEATURES = Path(__file__).resolve().parents[1] / "specs" / "features"
JAVA_SLICES = {"S2"}
_TAG_LINE = re.compile(r"^\s*(@\S+(?:\s+@\S+)*)\s*$")


def feature_slice(path):
    """The slice tag (S1, S2, ...) on the line(s) of tags directly above `Feature:`, or None."""
    tags = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip().startswith("Feature:"):
            break
        m = _TAG_LINE.match(line)
        if m:
            tags += m.group(1).split()
    found = [t[1:] for t in tags if re.fullmatch(r"@S\d+", t)]
    return found[0] if len(found) == 1 else None


def python_features():
    """Feature files whose slice runs under pytest-bdd."""
    return [p for p in sorted(FEATURES.glob("*.feature")) if feature_slice(p) not in JAVA_SLICES]
