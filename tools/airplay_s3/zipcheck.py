"""Check the S3 `airplay.receiver` install zip against AIR-078(c) and NEMESIS 1.16's metadata limits.

The zip may hold only `NEMESIS.data` and the APK. Its `NEMESIS.data` declares `id=airplay.receiver` and one
launchable, `[app.0]`, `kind=apk`, with no services, hooks, `depends` or `config/receiver.conf`. `os=` and
`models=` must each appear once and be byte-for-byte their values (OD-171, OD-172), never `any`.

AIR-078(h), OD-185: the check reports every failing rule, one line each, as `<field>: <problem>`, and never
stops at the first failure. An empty list means the zip passes.

Usage: python -m airplay_s3.zipcheck <zip>   (exit 0 when it passes, 1 with one finding per line otherwise)
"""

from __future__ import annotations

import re
import sys
import zipfile

PACKAGE_ID = "airplay.receiver"
RECEIVER_PACKAGE = "parkcast.receiver"
OS_VALUE = "CVEV.CAN.0000.V015.011.260206"
MODELS_VALUE = "cvev*"
MANIFEST = "NEMESIS.data"

SHORT_DESC_MAX = 64          # characters, one surrounding pair of quotes not counted
DETAILED_DESC_MAX = 256      # characters, same rule
BYTES_UNDER = {"id": 96, "name": 128, "type": 80, "types": 80, "package-version": 32}
OS_MODELS_UNDER = 256        # bytes; a value of 256 bytes or more is "longer than 255 bytes"
HOOK_KEYS = ("preinstall", "postinstall", "preuninstall", "postuninstall")
FORBIDDEN_MEMBERS = ("config/receiver.conf",)


def parse(text):
    """[(section, [(key, raw value)])] in file order; lines before any header are section ''.

    Lines split on LF only, so a CR or trailing space stays in the raw value; that is what lets the
    byte-for-byte `os` and `models` check refuse them."""
    out = [("", [])]
    for line in text.split("\n"):
        stripped = line.strip()
        if not stripped or stripped.startswith(("#", ";")):
            continue
        if stripped.startswith("[") and stripped.endswith("]"):
            out.append((stripped[1:-1].strip(), []))
        elif "=" in line:
            key, value = line.split("=", 1)
            out[-1][1].append((key.strip(), value))
    return out


def _unquoted(value):
    value = value.strip()
    return value[1:-1] if len(value) >= 2 and value[0] == value[-1] == '"' else value


def _values(sections, section, key):
    return [v for name, entries in sections if name == section for k, v in entries if k == key]


def _check_exact(findings, nemesis_values, key, expected):
    """`os` / `models`: once, never `any`, byte-for-byte `expected`, under 256 bytes. Every failing rule gets a line."""
    if not nemesis_values:
        findings.append(f"{key}: missing")
        return
    if len(nemesis_values) > 1:
        findings.append(f"{key}: present {len(nemesis_values)} times, must appear exactly once")
    for raw in nemesis_values:
        if raw.strip().lower() == "any":
            findings.append(f"{key}: any is not allowed")
        elif raw != expected:
            findings.append(f"{key}: not exactly {expected}")
        if len(raw.encode("utf-8")) >= OS_MODELS_UNDER:
            findings.append(f"{key}: longer than {OS_MODELS_UNDER - 1} bytes")


def check_manifest(text, members=None):
    """Findings for one `NEMESIS.data` text. With `members` (the zip's member names) the member rules and
    `[app.0]`'s `path` are checked too."""
    findings = []
    sections = parse(text)
    names = [name for name, _ in sections if name]
    nem = {}
    for name, entries in sections:
        if name == "nemesis":
            for key, value in entries:
                nem.setdefault(key, []).append(value)

    if "nemesis" not in names:
        findings.append("nemesis: no [nemesis] section")
    if names.count("nemesis") > 1:
        findings.append("nemesis: more than one [nemesis] section")

    # Every [nemesis] key except os and models must appear at most once; os/models say so themselves.
    for key, values in nem.items():
        if len(values) > 1 and key not in ("os", "models"):
            findings.append(f"{key}: present {len(values)} times, must appear at most once")

    ids = nem.get("id", [])
    if not ids:
        findings.append("id: missing")
    elif any(v.strip() != PACKAGE_ID for v in ids):
        findings.append(f"id: not {PACKAGE_ID}")

    for key, under in BYTES_UNDER.items():
        for value in nem.get(key, []):
            if len(value.strip().encode("utf-8")) >= under:
                findings.append(f"{key}: {under} bytes or longer, must be under {under}")
    if "type" in nem and "types" in nem:
        findings.append("types: declared as both type and types")
    for value in nem.get("package-version", []):
        if not re.fullmatch(r"[0-9]+(\.[0-9]+)*", value.strip()):
            findings.append("package-version: not dotted numbers")

    for key, most in (("short-desc", SHORT_DESC_MAX), ("detailed-desc", DETAILED_DESC_MAX)):
        for value in nem.get(key, []):
            length = len(_unquoted(value))
            if length > most:
                findings.append(f"{key}: {length} characters, at most {most} allowed")

    _check_exact(findings, nem.get("os", []), "os", OS_VALUE)
    _check_exact(findings, nem.get("models", []), "models", MODELS_VALUE)

    if nem.get("depends"):
        findings.append("depends: not allowed")
    for hook in HOOK_KEYS:
        if any(k == hook for _, entries in sections for k, _ in entries):
            findings.append(f"{hook}: hooks are not allowed")
    for name in names:
        if re.fullmatch(r"service\..*", name):
            findings.append(f"{name}: services are not allowed")

    apps = [name for name in names if re.fullmatch(r"app\..*", name)]
    for name in apps:
        if name != "app.0":
            findings.append(f"{name}: only [app.0] is allowed")
    if "app.0" not in apps:
        findings.append("app.0: missing, the APK must be the one launchable")
    else:
        if apps.count("app.0") > 1:
            findings.append("app.0: more than one [app.0] section")
        if [v.strip() for v in _values(sections, "app.0", "kind")] != ["apk"]:
            findings.append("app.0: kind is not apk")
        if [v.strip() for v in _values(sections, "app.0", "package")] != [RECEIVER_PACKAGE]:
            findings.append(f"app.0: package is not {RECEIVER_PACKAGE}")
        paths = [v.strip() for v in _values(sections, "app.0", "path")]
        if len(paths) != 1:
            findings.append("app.0: needs exactly one path")
        elif not paths[0].endswith(".apk") or "/" in paths[0] or "\\" in paths[0] or ".." in paths[0]:
            findings.append("app.0: path must name an .apk at the zip root")
        elif members is not None and paths[0] not in members:
            findings.append(f"app.0: path {paths[0]} is not in the zip")

    if members is not None:
        findings.extend(_check_members(members, paths=_values(sections, "app.0", "path")))
    return findings


def _check_members(members, paths):
    findings = []
    if len(set(members)) != len(members):
        findings.append("members: a member name appears more than once")
    for forbidden in FORBIDDEN_MEMBERS:
        if forbidden in members:
            findings.append(f"{forbidden}: must not be shipped")
    apk = paths[0].strip() if len(paths) == 1 else None
    extra = sorted(m for m in set(members) if m not in (MANIFEST, apk) and m not in FORBIDDEN_MEMBERS)
    for member in extra:
        findings.append(f"members: {member} is not allowed, only {MANIFEST} and the APK")
    if MANIFEST not in members:
        findings.append(f"members: no {MANIFEST}")
    return findings


def check_zip(path):
    """Findings for a built zip; [] when it passes."""
    try:
        with zipfile.ZipFile(path) as z:
            members = z.namelist()
            if MANIFEST not in members:
                return [f"members: no {MANIFEST}"]
            raw = z.read(MANIFEST)
    except (OSError, zipfile.BadZipFile) as exc:
        return [f"zip: cannot be read ({type(exc).__name__})"]
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        return [f"{MANIFEST}: not UTF-8"]
    return check_manifest(text, members)


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    if len(argv) != 1:
        print("usage: zipcheck <zip>", file=sys.stderr)
        return 2
    findings = check_zip(argv[0])
    for line in findings:
        print(line)
    if not findings:
        print("ok")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
