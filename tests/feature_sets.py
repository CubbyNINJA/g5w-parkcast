"""Which feature files pytest-bdd runs. Files listed in specs/java-features.txt run under Cucumber-JVM instead
(OD-133, OD-177): `./gradlew test` runs them; the tag-rule and traceability checks still cover every feature file."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FEATURES = ROOT / "specs" / "features"
JAVA_LIST = ROOT / "specs" / "java-features.txt"


MODULES = ("gate", "core")


def java_feature_entries():
    """(module, file name) pairs from specs/java-features.txt (comments and blank lines ignored).

    Each line is "<module> <file name>" (OD-189). An unknown module or a malformed line raises ValueError,
    so a typo can't silently move a feature file to the wrong runner."""
    entries = []
    for number, line in enumerate(JAVA_LIST.read_text(encoding="utf-8").splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split()
        if len(parts) != 2 or parts[0] not in MODULES:
            raise ValueError(f"{JAVA_LIST.name}:{number}: expected '<gate|core> <file>', got {line!r}")
        entries.append((parts[0], parts[1]))
    return entries


def java_feature_names():
    """The feature file names listed in specs/java-features.txt, whichever module runs them."""
    return {name for _, name in java_feature_entries()}


def python_features():
    """Feature files that run under pytest-bdd."""
    java = java_feature_names()
    return [p for p in sorted(FEATURES.glob("*.feature")) if p.name not in java]
