"""Which feature files pytest-bdd runs. Files listed in specs/java-features.txt run under Cucumber-JVM instead
(OD-133, OD-177): `./gradlew test` runs them; the tag-rule and traceability checks still cover every feature file."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FEATURES = ROOT / "specs" / "features"
JAVA_LIST = ROOT / "specs" / "java-features.txt"


def java_feature_names():
    """The feature file names listed in specs/java-features.txt (comments and blank lines ignored)."""
    names = set()
    for line in JAVA_LIST.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            names.add(line)
    return names


def python_features():
    """Feature files that run under pytest-bdd."""
    java = java_feature_names()
    return [p for p in sorted(FEATURES.glob("*.feature")) if p.name not in java]
