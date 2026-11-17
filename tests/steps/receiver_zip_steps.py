"""S3 tool step definitions: zip area of 14_receiver_tools.feature (docs/design/S3_RECEIVER.md).

The install zip of AIR-078(c), (h): `tools/airplay_s3/zipbuild.py` builds it and `zipcheck.py` checks it.
Every fixture uses a synthetic placeholder APK, never a real one (`*.apk` is git-ignored), built in the
scenario's temporary directory. State lives on `rworld` under `zip_` fields.
"""

from __future__ import annotations

import re
import zipfile
from pathlib import Path

from pytest_bdd import given, parsers, then, when  # noqa: F401

ROOT = Path(__file__).resolve().parents[2]
ANDROID_MANIFEST = ROOT / "receiver" / "app" / "src" / "main" / "AndroidManifest.xml"
PLACEHOLDER_APK = b"placeholder APK for the S3 zip scenarios; not an Android package\n"
OS_VALUE = "CVEV.CAN.0000.V015.011.260206"
MODELS_VALUE = "cvev*"


def _receiver_package():
    """The receiver's Android package, read from its AndroidManifest.xml (not from the tool under test)."""
    match = re.search(r'\bpackage="([^"]+)"', ANDROID_MANIFEST.read_text(encoding="utf-8"))
    assert match, f"no package attribute in {ANDROID_MANIFEST}"
    return match.group(1)


def _placeholder_apk(rworld):
    apk = rworld.tmp / "input" / "receiver-placeholder.apk"
    apk.parent.mkdir(parents=True, exist_ok=True)
    apk.write_bytes(PLACEHOLDER_APK)
    return apk


def _sections(text):
    """[(section, [(key, raw value, raw line)])] in file order; the lines before any header are section ''.

    Lines are split on LF only, so a trailing space or CR stays part of the raw line."""
    out = [("", [])]
    for line in text.split("\n"):
        stripped = line.strip()
        if not stripped or stripped.startswith(("#", ";")):
            continue
        if stripped.startswith("[") and stripped.endswith("]"):
            out.append((stripped[1:-1].strip(), []))
        elif "=" in line:
            key, value = line.split("=", 1)
            out[-1][1].append((key.strip(), value, line))
    return out


def _lines_with_key(text, key):
    return [raw for _, entries in _sections(text) for k, _, raw in entries if k == key]


def _value(text, key, section="nemesis"):
    values = [v.strip() for name, entries in _sections(text) if name == section for k, v, _ in entries if k == key]
    assert len(values) == 1, f"expected one {key}= in [{section}], found {len(values)}"
    return values[0]


def _unquoted(value):
    return value[1:-1] if len(value) >= 2 and value[0] == value[-1] == '"' else value


# --- The install zip built for the stick ---------------------------------------------------------------


@given("the install zip built for the stick")
def zip_built_for_stick(rworld):
    from airplay_s3 import zipbuild

    apk = _placeholder_apk(rworld)
    out = rworld.tmp / "stick" / "airplay.receiver.zip"
    out.parent.mkdir(parents=True, exist_ok=True)
    findings = zipbuild.build_zip(apk, out)
    assert findings == [], findings
    assert out.is_file(), "the build reported no findings but wrote no zip"
    rworld.zip_path = out


def _read_members(rworld):
    with zipfile.ZipFile(rworld.zip_path) as z:
        rworld.zip_members = z.namelist()
        rworld.zip_contents = {name: z.read(name) for name in rworld.zip_members}


def _read_manifest(rworld):
    with zipfile.ZipFile(rworld.zip_path) as z:
        rworld.zip_manifest = z.read("NEMESIS.data").decode("utf-8")


@when("its members are listed")
def zip_members_listed(rworld):
    _read_members(rworld)


@when('its "NEMESIS.data" is read')
def zip_manifest_read(rworld):
    _read_manifest(rworld)


@when('its "NEMESIS.data" and members are read')
def zip_manifest_and_members_read(rworld):
    _read_members(rworld)
    _read_manifest(rworld)


@then('they are exactly "NEMESIS.data" and the receiver APK')
def zip_members_exact(rworld):
    members = rworld.zip_members
    assert len(members) == 2 and len(set(members)) == 2, members
    assert "NEMESIS.data" in members, members
    (apk,) = [m for m in members if m != "NEMESIS.data"]
    assert apk.endswith(".apk") and "/" not in apk, apk
    assert rworld.zip_contents[apk] == PLACEHOLDER_APK, "the APK member is not the APK that was given"


@then(parsers.parse('it has "{line}"'))
def zip_manifest_has_line(rworld, line):
    key = line.split("=", 1)[0]
    raws = [raw for name, entries in _sections(rworld.zip_manifest) if name == "nemesis"
            for k, _, raw in entries if k == key]
    assert raws == [line], f"[nemesis] {key} lines: {raws!r}"


@then('it has exactly one launchable section, "[app.0]"')
def zip_one_launchable(rworld):
    apps = [name for name, _ in _sections(rworld.zip_manifest) if re.fullmatch(r"app\.\S*", name)]
    assert apps == ["app.0"], apps


@then('"[app.0]" has "kind=apk", a "path" naming the APK in the zip, '
      'and a "package" naming the receiver\'s Android package')
def zip_app0_declares_apk(rworld):
    text = rworld.zip_manifest
    assert _value(text, "kind", "app.0") == "apk"
    path = _value(text, "path", "app.0")
    with zipfile.ZipFile(rworld.zip_path) as z:
        assert path in z.namelist(), f"path={path} is not a member of the zip"
        assert z.read(path) == PLACEHOLDER_APK, f"path={path} is not the APK"
    assert _value(text, "package", "app.0") == _receiver_package()


@then(parsers.re(r'it has no "(?P<item>[^"]+)" (?P<kind>section|key|member)'))
def zip_has_no_item(rworld, item, kind):
    text = rworld.zip_manifest
    if kind == "section":
        pattern = re.escape(item.strip("[]")).replace("N", r"\d+")
        found = [name for name, _ in _sections(text) if re.fullmatch(pattern, name)]
        assert found == [], f"sections {found!r}"
    elif kind == "key":
        assert _lines_with_key(text, item) == [], f"{item} lines: {_lines_with_key(text, item)!r}"
    else:
        assert item not in rworld.zip_members, rworld.zip_members


@then(parsers.parse('"{line}" appears exactly once'))
def zip_line_once(rworld, line):
    key = line.split("=", 1)[0]
    assert _lines_with_key(rworld.zip_manifest, key) == [line], _lines_with_key(rworld.zip_manifest, key)


@when("its metadata is checked against NEMESIS 1.16's limits")
def zip_metadata_checked(rworld):
    from airplay_s3 import zipcheck

    _read_manifest(rworld)
    rworld.zip_findings = zipcheck.check_zip(rworld.zip_path)


def _no_finding_for(rworld, field):
    named = [f for f in rworld.zip_findings if f.startswith(f"{field}:")]
    assert named == [], named


@then('"short-desc" is at most 64 characters and "detailed-desc" at most 256')
def zip_desc_limits(rworld):
    text = rworld.zip_manifest
    assert 0 < len(_unquoted(_value(text, "short-desc"))) <= 64
    assert 0 < len(_unquoted(_value(text, "detailed-desc"))) <= 256
    _no_finding_for(rworld, "short-desc")
    _no_finding_for(rworld, "detailed-desc")


@then('"package-version" is dotted numbers under 32 bytes')
def zip_package_version(rworld):
    value = _value(rworld.zip_manifest, "package-version")
    assert re.fullmatch(r"[0-9]+(\.[0-9]+)*", value), value
    assert len(value.encode("utf-8")) < 32
    _no_finding_for(rworld, "package-version")


@then('"id", "name" and "type" are under 96, 128 and 80 bytes')
def zip_id_name_type(rworld):
    text = rworld.zip_manifest
    type_keys = [k for k in ("type", "types") if _lines_with_key(text, k)]
    assert len(type_keys) == 1, f"expected one of type= or types=, found {type_keys}"
    for key, limit in (("id", 96), ("name", 128), (type_keys[0], 80)):
        assert 0 < len(_value(text, key).encode("utf-8")) < limit, key
        _no_finding_for(rworld, key)


@then('"os" and "models" are under 256 bytes each')
def zip_os_models_length(rworld):
    for key in ("os", "models"):
        assert 0 < len(_value(rworld.zip_manifest, key).encode("utf-8")) < 256, key
        _no_finding_for(rworld, key)


# --- Manifest fixtures run through the check -------------------------------------------------------------

# A valid manifest written here, not taken from the tool under test; each scenario changes one or two lines.
BASE_MANIFEST = {
    "nemesis": [
        ("id", "airplay.receiver"),
        ("name", "ParkCast Receiver fixture"),
        ("version", "0.1.0"),
        ("package-version", "0.1.0"),
        ("types", "application"),
        ("short-desc", "Fixture short description"),
        ("detailed-desc", "Fixture detailed description for the install zip check."),
        ("os", OS_VALUE),
        ("models", MODELS_VALUE),
    ],
    "app.0": [("kind", "apk"), ("path", "parkcast-receiver.apk"), ("package", "parkcast.receiver")],
}


def _fixture_lines():
    return {section: list(entries) for section, entries in BASE_MANIFEST.items()}


def _render(lines):
    out = []
    for section, entries in lines.items():
        out.append(f"[{section}]")
        out.extend(f"{k}={v}" for k, v in entries)
        out.append("")
    return "\n".join(out)


def _set(lines, key, value):
    lines["nemesis"] = [(k, value if k == key else v) for k, v in lines["nemesis"]]


def _store_fixture(rworld, lines):
    rworld.zip_fixture = _render(lines)


@given(parsers.re(r"an install manifest fixture whose (?P<field>short-desc|detailed-desc) is "
                  r"(?P<length>\d+) characters long"))
def zip_fixture_desc_length(rworld, field, length):
    lines = _fixture_lines()
    _set(lines, field, "D" * int(length))
    _store_fixture(rworld, lines)


@given("an install manifest fixture whose short-desc is 64 characters long, written in surrounding quotes "
       "(66 characters with them)")
def zip_fixture_quoted_short_desc(rworld):
    lines = _fixture_lines()
    value = '"' + "Q" * 64 + '"'
    assert len(value) == 66
    _set(lines, "short-desc", value)
    _store_fixture(rworld, lines)


@given(parsers.re(r'an install manifest fixture whose (?P<key>os|models) is '
                  r'(?P<fault>missing|present twice|"(?P<line>[^"]*)"(?P<space> with a trailing space)?)'))
def zip_fixture_exact_fault(rworld, key, fault, line, space):
    lines = _fixture_lines()
    if fault == "missing":
        lines["nemesis"] = [(k, v) for k, v in lines["nemesis"] if k != key]
    elif fault == "present twice":
        lines["nemesis"].append((key, dict(lines["nemesis"])[key]))
    else:
        if space:
            assert line.endswith(" "), f"the fault text says a trailing space but {line!r} has none"
        written_key, value = line.split("=", 1)
        assert written_key == key
        _set(lines, key, value)
    _store_fixture(rworld, lines)


@given(parsers.re(r"an install manifest fixture whose (?P<key>os|models) value is (?P<length>\d+) bytes long"))
def zip_fixture_value_length(rworld, key, length):
    lines = _fixture_lines()
    _set(lines, key, "C" * int(length))
    _store_fixture(rworld, lines)


@given("an install manifest fixture whose short-desc is 65 characters long and whose models value is "
       "256 bytes long")
def zip_fixture_two_faults(rworld):
    lines = _fixture_lines()
    _set(lines, "short-desc", "D" * 65)
    _set(lines, "models", "C" * 256)
    _store_fixture(rworld, lines)


@when("the install zip check runs on it")
def zip_check_runs(rworld):
    from airplay_s3 import zipbuild

    apk = _placeholder_apk(rworld)
    out = rworld.tmp / "stick" / "airplay.receiver.zip"
    out.parent.mkdir(parents=True, exist_ok=True)
    rworld.zip_out = out
    rworld.zip_findings = zipbuild.build_zip(apk, out, manifest=rworld.zip_fixture)


@then("the check accepts it")
def zip_check_accepts(rworld):
    assert rworld.zip_findings == [], rworld.zip_findings
    assert rworld.zip_out.is_file(), "the check accepted the manifest but no zip was produced"


@then(parsers.re(r"the check refuses it naming (?P<field>[a-z-]+)"))
def zip_check_refuses_naming(rworld, field):
    assert any(f.startswith(f"{field}: ") for f in rworld.zip_findings), rworld.zip_findings


@then("the check refuses it")
def zip_check_refuses(rworld):
    assert rworld.zip_findings, "the check accepted it"


@then("no install zip is produced")
def zip_none_produced(rworld):
    out = rworld.zip_out
    assert not out.exists(), f"{out.name} was written"
    assert [p.name for p in out.parent.iterdir()] == [], "something was left next to the zip"


@then(parsers.parse('the check\'s findings have no line starting "{prefix}"'))
def zip_findings_lack_prefix(rworld, prefix):
    assert not any(f.startswith(prefix) for f in rworld.zip_findings), rworld.zip_findings


@then(parsers.parse('the check\'s findings include the line "{line}"'))
def zip_check_findings_include(rworld, line):
    assert line in rworld.zip_findings, rworld.zip_findings


@then("its findings include a line naming short-desc")
def zip_findings_name_short_desc(rworld):
    assert any(f.startswith("short-desc: ") for f in rworld.zip_findings), rworld.zip_findings


@then(parsers.parse('its findings include the line "{line}"'))
def zip_findings_include(rworld, line):
    assert line in rworld.zip_findings, rworld.zip_findings


@then("each failed rule has its own line")
def zip_one_line_per_rule(rworld):
    findings = rworld.zip_findings
    assert len(findings) == len(set(findings)), findings
    fields = {"short-desc", "models"}
    for line in findings:
        assert "\n" not in line and "\r" not in line, repr(line)
        assert re.fullmatch(r"[A-Za-z0-9._/-]+: \S.*", line), f"not '<field>: <problem>': {line!r}"
        assert len([f for f in fields if f in line]) <= 1, f"one line names two rules: {line!r}"
    for field in fields:
        assert any(f.startswith(f"{field}: ") for f in findings), f"no line for {field}: {findings}"


@given(parsers.re(r"an install manifest fixture whose (?P<field>name|types) is (?P<length>\d+) bytes long"))
def zip_fixture_byte_length(rworld, field, length):
    lines = _fixture_lines()
    _set(lines, field, "B" * int(length))
    _store_fixture(rworld, lines)


@given(parsers.parse('an install manifest fixture with the line "{line}" in its [nemesis] section'))
def zip_fixture_extra_line(rworld, line):
    lines = _fixture_lines()
    key, value = line.split("=", 1)
    lines["nemesis"].append((key, value))
    _store_fixture(rworld, lines)


@given('an install zip fixture holding a valid "NEMESIS.data", the APK and a "config/receiver.conf" member')
def zip_fixture_with_config(rworld):
    out = rworld.tmp / "input" / "airplay.receiver.zip"
    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("NEMESIS.data", _render(_fixture_lines()).encode("utf-8"))
        z.writestr("parkcast-receiver.apk", PLACEHOLDER_APK)
        z.writestr("config/receiver.conf", b"video.require_park=true\n")
    rworld.zip_path = out


@when("the package check runs on that zip")
def zip_check_runs_on_zip(rworld):
    from airplay_s3 import zipcheck

    rworld.zip_findings = zipcheck.check_zip(rworld.zip_path)
