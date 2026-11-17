"""S3 tool step definitions: stick-script area of 14_receiver_tools.feature (docs/design/S3_RECEIVER.md).

The log-copy script (`tools/airplay_s3/stick/copy-logs.sh`, AIR-074, AIR-079(e), AIR-083(e)) and the install
script (`tools/airplay_s3/stick/install-receiver.sh`, AIR-078, AIR-062) run under the host's POSIX `sh`
against a fake unit root and a fake stick (`receiver_toolbox.Fakes`). Every fixture is synthetic. State lives on
`rworld` under `stick_` fields.
"""

from __future__ import annotations

import re

from pytest_bdd import given, parsers, then, when

from steps.receiver_toolbox import BLANK_CHECKLIST, LOG_DIR, MARKED, RUN_PARENT, TIMESTAMP, Fakes, sha

RECEIVER_PACKAGE = "parkcast.receiver"
ZIP_ID = "airplay.receiver"
ZIP_NAME = "airplay.receiver.zip"
UNIT_LOG_DIR = "/" + LOG_DIR
PLACEHOLDER_APK = b"placeholder APK for the S3 stick scenarios; not an Android package\n"
KEY_NAME = "fixture-identity.key"
KEY_BYTES = b"FIXTURE-IDENTITY-KEY-3f9a\x00\x01\x02 not a real key\n"
CHECKLIST_KEYS = ("parkcast listed", "letterboxed", "sound on unit", "stayed behind other app",
                  "nothing on cluster or hud", "carplay after exit")


def _fakes(rworld):
    if not hasattr(rworld, "stick_fakes"):
        rworld.stick_fakes = Fakes(rworld.tmp)
    return rworld.stick_fakes


def _output(rworld):
    res = rworld.stick_result
    return res.stdout + res.stderr


# --- Log-copy fixtures -----------------------------------------------------------------------------------


def _write_logs(rworld, names):
    fakes = _fakes(rworld)
    fakes.log_dir.mkdir(parents=True, exist_ok=True)
    rworld.stick_logs = {}
    for i, name in enumerate(names, 1):
        data = (f"1000 parkcast start app=0.0.{i} commit=fixture uxplay=fixture product=fixture require_park=true\n"
                f"2000 session 1 connection accepted model=Fixture iPhone\r\n"
                f"3000 log {i} é\n").encode("utf-8")
        (fakes.log_dir / name).write_bytes(data)
        rworld.stick_logs[name] = sha(fakes.log_dir / name)


@given('a fake unit tree with two receiver log files in "/data/nemesis/logs/airplay-receiver/"')
def two_receiver_logs(rworld):
    _write_logs(rworld, ["receiver-1.log", "receiver-2.log"])


@given("a fake unit tree with receiver log files and an identity key file in the receiver's app-data directory")
def logs_and_identity_key(rworld):
    _write_logs(rworld, ["receiver-1.log", "receiver-2.log"])
    key = _fakes(rworld).root / "data" / "data" / RECEIVER_PACKAGE / "files" / KEY_NAME
    key.parent.mkdir(parents=True)
    key.write_bytes(KEY_BYTES)


@given('the fake "cp" fails after copying the first file')
def cp_fails_after_first(rworld):
    _fakes(rworld).set("cp.fail_after_first_log", "1\n")


@given("the run's timestamped folder on the stick does not exist before the run")
def run_folder_absent(rworld):
    fakes = _fakes(rworld)
    assert not fakes.run_dir.exists()
    assert not (fakes.stick / RUN_PARENT).exists()


_PLANTED = {
    'an "owner-note.txt" holding "picture: yes"': ("owner-note.txt", b"picture: yes"),
    'a "run-checklist.txt" with all six keys "yes"': ("run-checklist.txt",
                                                      "".join(f"{k}: yes\n" for k in CHECKLIST_KEYS).encode()),
}


@given(parsers.re(r'the fake "cp" creates (?P<existing>' + "|".join(re.escape(k) for k in _PLANTED)
                  + r") in the run's folder before it copies the first log"))
def cp_creates_file_first(rworld, existing):
    name, data = _PLANTED[existing]
    fakes = _fakes(rworld)
    fakes.set("cp.create.name", name)
    fakes.set("cp.create.bytes", data)
    rworld.stick_planted = (name, data)


@given(parsers.re(r'placing "(?P<name>owner-note\.txt|run-checklist\.txt)" on the fake stick fails'))
def placing_fails(rworld, name):
    fakes = _fakes(rworld)
    fakes.set("cp.refuse_dest", f"{fakes.run_dir_posix()}/{name}")
    rworld.stick_refused = name


@given("the fake stick already has the run's \"NEMESIS-diag/airplay-receiver/<timestamp>/\" folder with files in it")
def run_folder_exists(rworld):
    fakes = _fakes(rworld)
    fakes.run_dir.mkdir(parents=True)
    (fakes.run_dir / "receiver-1.log").write_bytes(b"an earlier run's log\n")
    (fakes.run_dir / "owner-note.txt").write_bytes(b"picture: no\n")
    rworld.stick_existing = {p.name: sha(p) for p in fakes.run_dir.iterdir()}


@when("the log-copy stick script runs")
def run_copy_logs(rworld):
    fakes = _fakes(rworld)
    rworld.stick_result = fakes.run("copy-logs.sh")
    assert fakes.rejected() == [], f"a fake refused an option: {fakes.rejected()}"


# --- Log-copy outcomes -----------------------------------------------------------------------------------


def _new_run_dir(rworld):
    parent = _fakes(rworld).stick / RUN_PARENT
    dirs = [p for p in parent.iterdir() if p.is_dir()] if parent.is_dir() else []
    assert len(dirs) == 1, f"expected one run folder under {RUN_PARENT}, found {[d.name for d in dirs]}"
    assert re.fullmatch(r"\d{8}-\d{6}", dirs[0].name), f"run folder {dirs[0].name!r} is not a timestamp"
    return dirs[0]


def _assert_logs_copied(rworld):
    run = _new_run_dir(rworld)
    log_dir = _fakes(rworld).log_dir
    for name in rworld.stick_logs:
        assert (run / name).is_file(), f"{name} was not copied"
        assert (run / name).read_bytes() == (log_dir / name).read_bytes(), f"{name} differs from its original"


@then('the stick holds both files under "NEMESIS-diag/airplay-receiver/<timestamp>/"')
def stick_holds_both(rworld):
    run = _new_run_dir(rworld)
    for name in rworld.stick_logs:
        assert (run / name).is_file(), f"{name} is not in {run}"


@then("each copied file has the same bytes as its original")
def copied_same_bytes(rworld):
    _assert_logs_copied(rworld)


@then("the originals' hashes are unchanged")
def originals_unchanged(rworld):
    log_dir = _fakes(rworld).log_dir
    assert {n: sha(log_dir / n) for n in rworld.stick_logs} == rworld.stick_logs
    assert sorted(p.name for p in log_dir.iterdir()) == sorted(rworld.stick_logs), "the log directory changed"


@then('the new "<timestamp>/" folder holds an "owner-note.txt" of zero bytes')
def new_folder_zero_note(rworld):
    note = _new_run_dir(rworld) / "owner-note.txt"
    assert note.is_file() and note.read_bytes() == b""


@then('it holds a "run-checklist.txt" whose bytes are exactly the blank checklist')
def holds_blank_checklist(rworld):
    assert (_new_run_dir(rworld) / "run-checklist.txt").read_bytes() == BLANK_CHECKLIST


@then('the run\'s folder holds an "owner-note.txt" of zero bytes and the blank checklist as "run-checklist.txt"')
def run_folder_holds_blanks(rworld):
    run = _new_run_dir(rworld)
    assert (run / "owner-note.txt").read_bytes() == b""
    assert (run / "run-checklist.txt").read_bytes() == BLANK_CHECKLIST


def _said(rworld):
    """What the script reported: its output, and any log it kept on the stick."""
    return _output(rworld) + "\n" + _fakes(rworld).stick_text()


@then("the script reports the copy as incomplete")
def reports_incomplete(rworld):
    assert "incomplete" in _output(rworld).lower(), _output(rworld)


@then("the copy is never reported as complete")
def never_complete(rworld):
    assert not re.search(r"(?<!in)complete", _said(rworld), re.IGNORECASE), _said(rworld)


@then("the copy is not reported as incomplete")
def not_incomplete(rworld):
    assert "incomplete" not in _said(rworld).lower(), _said(rworld)


@then("that file's hash is unchanged")
def planted_unchanged(rworld):
    name, data = rworld.stick_planted
    assert (_new_run_dir(rworld) / name).read_bytes() == data


@then(parsers.re(r'the script reports that it left "(?P<name>owner-note\.txt|run-checklist\.txt)"'))
def reports_left(rworld, name):
    lines = [ln for ln in _output(rworld).splitlines() if name in ln and "left" in ln.lower()]
    assert lines, _output(rworld)


@then("every log was copied")
def every_log_copied(rworld):
    _assert_logs_copied(rworld)


@then(parsers.re(r'the script names "(?P<name>owner-note\.txt|run-checklist\.txt)"'))
def script_names(rworld, name):
    assert name in _output(rworld), _output(rworld)
    assert not (_new_run_dir(rworld) / name).exists(), f"{name} was placed although the stick refused it"


@then("the copied logs' hashes are unchanged")
def copied_logs_unchanged(rworld):
    _assert_logs_copied(rworld)


@then("nothing is copied into that folder")
def nothing_copied_into(rworld):
    fakes = _fakes(rworld)
    target = fakes.run_dir_posix()
    into = [c for c in fakes.calls() if c[1] == "cp" and any(a.startswith(target) for a in c[2])]
    assert into == [], into


@then("every file in that folder keeps its hash, and no file is added to it")
def folder_unchanged(rworld):
    run = _fakes(rworld).run_dir
    assert {p.name: sha(p) for p in run.iterdir()} == rworld.stick_existing


@then("the script reports that the folder exists")
def reports_folder_exists(rworld):
    out = _output(rworld)
    assert "exist" in out.lower() and TIMESTAMP in out, out


@then("no file is copied from the receiver's app-data directory")
def nothing_from_app_data(rworld):
    calls = _fakes(rworld).calls()
    assert any(c[1] == "cp" for c in calls), "no file was copied at all"
    touching = [c for c in calls if any(a.startswith("/data/data") for a in c[2])]
    assert touching == [], touching


@then("no file on the stick has the key file's name")
def no_key_name(rworld):
    names = [p.name for p in _fakes(rworld).stick_files()]
    assert KEY_NAME not in names, names


@then("no file on the stick contains the key file's bytes")
def no_key_bytes(rworld):
    hits = [p for p in _fakes(rworld).stick_files() if KEY_BYTES in p.read_bytes()]
    assert hits == [], hits


# --- The evaluator on the placed blank files (the eval area owns airplay_s3/evaluator.py) -----------------


@when("the evaluator reads that folder")
def evaluator_reads_folder(rworld):
    from airplay_s3 import evaluator

    rworld.stick_eval = evaluator.read_run_folder(_new_run_dir(rworld))


@then('it reports every checklist key as "not recorded"')
def every_key_not_recorded(rworld):
    expected = "Checklist: " + "; ".join(f"{k}=not recorded" for k in CHECKLIST_KEYS)
    assert expected in rworld.stick_eval.summary.splitlines(), rworld.stick_eval.summary


@then("it reads the owner's note as missing")
def note_missing(rworld):
    assert rworld.stick_eval.note_missing is True


# --- Install fixtures ----------------------------------------------------------------------------------

_PACKAGES_LIST = (f"com.android.settings 1000 0 /data/data/com.android.settings platform 3003\n"
                  f"{RECEIVER_PACKAGE} 10123 0 /data/data/{RECEIVER_PACKAGE} default 3003\n")


def _install_tree(rworld, *, packages_list=_PACKAGES_LIST, fail_at=None, **outputs):
    """A unit on which every fake command succeeds, with overrides in `outputs` (state file name -> bytes)."""
    fakes = _fakes(rworld)
    plist = fakes.root / "data" / "system" / "packages.list"
    plist.parent.mkdir(parents=True, exist_ok=True)
    plist.write_bytes(packages_list.encode())
    base = {
        "nemesis.install.out": "installed airplay.receiver\n",
        "nemesis.pkglist.out": f"usb-handler|USB Handler|1|service\n{ZIP_ID}|AirPlay Receiver|0|application\n",
        "pm.list.out": f"package:com.android.settings\npackage:{RECEIVER_PACKAGE}\n",
        "am.start.out": f"Starting: Intent {{ cmp={RECEIVER_PACKAGE}/.Fixture }}\n",
    }
    base.update(outputs)
    for name, data in base.items():
        fakes.set(name, data)
    rworld.stick_fail_at = fail_at
    rworld.stick_dir_before = fakes.log_dir.exists()


_INSTALL_FAILURES = {
    '"nemesis install" exits 1': dict(fail_at=1, **{"nemesis.install.out": "error: install failed\n",
                                                    "nemesis.install.rc": "1\n"}),
    '"nemesis install" exits 0 but prints "invalid package metadata: short-desc"':
        dict(fail_at=1, **{"nemesis.install.out": "invalid package metadata: short-desc\n"}),
    '"nemesis install" exits 0 with "Failure [INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES]"':
        dict(fail_at=1, **{"nemesis.install.out": "Failure [INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES]\n"}),
    '"nemesis pkg-list" has no line whose first field is "airplay.receiver"':
        dict(fail_at=2, **{"nemesis.pkglist.out": f"usb-handler|USB Handler|1|service\n"
                                                  f"{ZIP_ID}.old|Old|0|application\nx|{ZIP_ID}|0|application\n"}),
    '"pm list packages" has no line "package:" followed by the receiver\'s package':
        dict(fail_at=3, **{"pm.list.out": f"package:com.android.settings\npackage:{RECEIVER_PACKAGE}.helper\n"
                                          f"{RECEIVER_PACKAGE}\n"}),
}


@given("a fake unit tree on which every fake command succeeds and the receiver's package has uid 10123")
def install_tree_all_succeed(rworld):
    _install_tree(rworld)


@given(parsers.re(r"a fake unit tree on which (?P<failure>" + "|".join(re.escape(k) for k in _INSTALL_FAILURES)
                  + r")"))
def install_tree_failure(rworld, failure):
    _install_tree(rworld, **_INSTALL_FAILURES[failure])


@given("a fake unit tree on which the install succeeds and the receiver's uid cannot be found")
def install_tree_no_uid(rworld):
    _install_tree(rworld, packages_list=(f"com.android.settings 1000 0 /data/data/com.android.settings platform 3003\n"
                                         f"{RECEIVER_PACKAGE}.helper 10200 0 /data/data/{RECEIVER_PACKAGE}.helper "
                                         f"default 3003\n"))


@given('a fake unit tree on which the install succeeds and the fake "chown" exits non-zero')
def install_tree_chown_fails(rworld):
    _install_tree(rworld, **{"chown.rc": "1\n"})


@given("a fake unit tree on which the install and the directory step succeed")
def install_tree_install_and_dir_succeed(rworld):
    _install_tree(rworld)
    _stage_zip(rworld)  # this scenario has no staging step; the install needs the zip on the stick


@given('the fake "am start" prints "Error: Activity class does not exist." and exits 0')
def am_start_error(rworld):
    _fakes(rworld).set("am.start.out", f"Starting: Intent {{ cmp={RECEIVER_PACKAGE}/.Fixture }}\n"
                                       "Error: Activity class does not exist.\n")


def _stage_zip(rworld):
    from airplay_s3 import zipbuild

    fakes = _fakes(rworld)
    apk = rworld.tmp / "input" / "receiver-placeholder.apk"
    apk.parent.mkdir(parents=True, exist_ok=True)
    apk.write_bytes(PLACEHOLDER_APK)
    out = fakes.stick / ZIP_NAME
    assert zipbuild.build_zip(apk, out) == []
    rworld.stick_zip = out


@given("the install zip staged on the fake stick")
def zip_staged(rworld):
    _stage_zip(rworld)


@when("the install stick script runs")
def run_install(rworld):
    fakes = _fakes(rworld)
    rworld.stick_result = fakes.run("install-receiver.sh")
    assert fakes.rejected() == [], f"a fake refused an option: {fakes.rejected()}"


# --- Install outcomes ----------------------------------------------------------------------------------


def _unit_calls(rworld):
    return [c for c in _fakes(rworld).calls() if c[1] in MARKED]


def _is_mkdir_log_dir(call):
    return call[1] == "mkdir" and call[2] and call[2][-1] == UNIT_LOG_DIR


def _expected_call(rworld, text):
    """A predicate for one row of the call table."""
    fakes = _fakes(rworld)
    zip_on_stick = f"{fakes.stick_posix}/{ZIP_NAME}"
    rows = {
        "nemesis install --update <zip on the stick>":
            lambda c: (c[1], c[2]) == ("nemesis", ["install", "--update", zip_on_stick]),
        "nemesis pkg-list": lambda c: (c[1], c[2]) == ("nemesis", ["pkg-list"]),
        "pm list packages": lambda c: (c[1], c[2]) == ("pm", ["list", "packages"]),
        f"creates {UNIT_LOG_DIR}/ if missing": _is_mkdir_log_dir,
        "chown to uid 10123": lambda c: c[1] == "chown" and c[2] in (["10123.10123", UNIT_LOG_DIR],
                                                                     ["10123:10123", UNIT_LOG_DIR]),
        "chmod 0700": lambda c: (c[1], c[2]) == ("chmod", ["0700", UNIT_LOG_DIR]),
        "am start": lambda c: c[1] == "am" and c[2][:1] == ["start"],
    }
    assert text in rows, f"no call matcher for table row {text!r}"
    return rows[text]


@then("the calls are, in this order:")
def calls_in_order(rworld, datatable):
    header, *rows = datatable
    assert [h.strip() for h in header] == ["step", "call"], header
    expected = []
    for _step, text in rows:
        text = text.strip()
        if text.endswith("if missing") and rworld.stick_dir_before:
            continue  # the directory exists already, so nothing creates it
        expected.append((text, _expected_call(rworld, text)))
    calls = _unit_calls(rworld)
    assert len(calls) == len(expected), f"calls {calls} against rows {[t for t, _ in expected]}"
    for call, (text, matches) in zip(calls, expected):
        assert matches(call), f"call {call} is not {text!r}"
    assert _fakes(rworld).log_dir.is_dir()


@then(parsers.re(r'"(?P<cmd>pm install|am start)" is never called'))
def never_called(rworld, cmd):
    name, sub = cmd.split()
    hits = [c for c in _fakes(rworld).calls() if c[1] == name and c[2][:1] == [sub]]
    assert hits == [], hits


@then("the script exits 0")
def exits_zero(rworld):
    assert rworld.stick_result.returncode == 0, _output(rworld)


@then("the script exits non-zero")
def exits_non_zero(rworld):
    assert rworld.stick_result.returncode != 0, _output(rworld)


@then("the directory step is never run")
def directory_step_never_run(rworld):
    calls = [c for c in _fakes(rworld).calls() if c[1] in ("mkdir", "chown", "chmod")]
    assert calls == [], calls
    assert not _fakes(rworld).log_dir.exists(), "the log directory was created"


@then("no further install or uninstall step is run")
def no_further_step(rworld):
    order = ["nemesis install --update <zip on the stick>", "nemesis pkg-list", "pm list packages"]
    calls = _unit_calls(rworld)
    expected = order[:rworld.stick_fail_at]
    assert len(calls) == len(expected), f"calls {calls} after a failure at {expected[-1]!r}"
    for call, text in zip(calls, expected):
        assert _expected_call(rworld, text)(call), f"call {call} is not {text!r}"


@then("the output of every command it ran is kept on the stick")
def output_kept(rworld):
    fakes = _fakes(rworld)
    kept = fakes.stick_text()
    calls = _unit_calls(rworld)
    assert calls, "no command ran"
    missing = [f"fake-{name} {stream} {n}" for n, name, _ in calls for stream in ("out", "err")
               if f"fake-{name} {stream} {n}" not in kept]
    assert missing == [], f"not on the stick: {missing}"


@then("the script reports the directory step's failure")
def reports_directory_failure(rworld):
    lines = [ln for ln in _output(rworld).lower().splitlines() if "directory" in ln and "fail" in ln]
    assert lines, _output(rworld)
