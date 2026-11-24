"""Steps for 16_receiver_diagnostics.feature (S3 revision 10, OD-198 to OD-205).

This module holds the build switch, the keep check and the PC side (AIR-084, AIR-089(g); OD-199, OD-204):

- the Gradle scenarios run `gradlew.bat` in this working tree, not a clone, because the release build needs the
  git-ignored `local.properties` (SDK path and signing) and the native-prep cache; they need JAVA_HOME set to a
  JDK 17. The three-build scenario leaves the working tree's APK as the property-"false" build;
- the keep-check scenarios build a fixture APK (a zip holding `lib/armeabi-v7a/libparkcast.so`) and a fake
  `aapt` that prints one `dump badging` line, and run `tools/s3.py keep-check` with `--aapt`;
- the "no runtime switch" scenario reads the native and app sources with `airplay_s3.hostchecks`;
- the evaluator scenarios reuse the run-folder model of `receiver_eval_steps` and call `airplay_s3.evaluator`.

State lives on `rworld` in fields named `diag_*` (and the `eval_*` fields of the shared model). The tool
modules are imported inside the steps, so a missing module fails at that step.
"""

from __future__ import annotations

import importlib
import os
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

import pytest
from pytest_bdd import given, parsers, then, when

from steps.receiver_eval_steps import (MODEL, START, STOP, _ev, _go_run, _rec, _run_dir, _st)

REPO = Path(__file__).resolve().parents[2]
APK_DIR = REPO / "receiver" / "app" / "build" / "outputs" / "apk" / "release"
LIB = "lib/armeabi-v7a/libparkcast.so"
MARKER = b"parkcast raw diagnostics on"
SUFFIX = "-rawdiag"
RAW = "uxplay-raw.log"
NATIVE_SRC = REPO / "receiver" / "native" / "src"
CMAKE = REPO / "receiver" / "native" / "CMakeLists.txt"
DESIGN = REPO / "docs" / "design" / "S3_RECEIVER.md"
BRIDGE = REPO / "receiver" / "app" / "src" / "main" / "java" / "parkcast" / "receiver" / "NativeBridge.java"
APP_SOURCES = [REPO / "receiver" / d / "src" / "main" for d in ("app", "core", "gate")]
JNI_PREFIX = "Java_parkcast_receiver_NativeBridge_"


def _hc():
    return importlib.import_module("airplay_s3.hostchecks")


# --- Gradle in this working tree ---------------------------------------------------------------------------

def _gradle(rworld, *args):
    if not (REPO / "local.properties").is_file():
        pytest.skip("no local.properties in this working tree; the receiver app build needs sdk.dir and signing")
    proc = subprocess.run(["cmd", "/c", str(REPO / "gradlew.bat"), "--no-daemon", "--console=plain", *args],
                          cwd=REPO, env=dict(os.environ), capture_output=True, text=True, errors="replace",
                          timeout=3600)
    rworld.diag_rc, rworld.diag_out, rworld.diag_err = proc.returncode, proc.stdout, proc.stderr
    return proc


@when(parsers.re(r'"\./gradlew (?P<args>[^"]*)" runs'))
def diag_gradle_runs(rworld, args):
    _gradle(rworld, *args.split())


@then(parsers.re(r"it (?P<result>passes|fails)"))
def diag_it_result(rworld, result):
    rc = rworld.diag_rc
    if result == "fails":
        assert rc != 0, f"expected a failure, got exit 0\n{getattr(rworld, 'diag_out', '')[-2000:]}"
    else:
        assert rc == 0, f"expected a pass, got exit {rc}\n{rworld.diag_out}\n{rworld.diag_err}"


@then(parsers.re(r'its error output names the value "(?P<value>[^"]*)"'))
def diag_error_names_value(rworld, value):
    assert f"'{value}'" in rworld.diag_err or f'"{value}"' in rworld.diag_err, rworld.diag_err[-3000:]


def _release_apk():
    apks = [a for a in sorted(APK_DIR.glob("*.apk")) if "unsigned" not in a.name] if APK_DIR.exists() else []
    assert len(apks) == 1, f"expected one signed release APK under {APK_DIR}, found {[a.name for a in apks]}"
    return apks[0]


@given('the release APK is built with the property absent, then with "true", then with "false", with no clean '
       'in between')
def diag_three_builds(rworld):
    rworld.diag_apks = {}
    for label, extra in (("absent", []), ("true", ["-Pparkcast.rawDiagnostics=true"]),
                         ("false", ["-Pparkcast.rawDiagnostics=false"])):
        proc = _gradle(rworld, ":receiver:app:assembleRelease", *extra)
        assert proc.returncode == 0, f"the {label} build failed\n{proc.stdout[-3000:]}\n{proc.stderr[-3000:]}"
        dest = rworld.tmp / f"apk-{label}.apk"
        shutil.copyfile(_release_apk(), dest)
        rworld.diag_apks[label] = dest


def _badging_version(apk) -> str:
    """The version name `aapt dump badging` prints (the SDK's own aapt, not the keep check's reader)."""
    proc = subprocess.run([str(_hc()._aapt()), "dump", "badging", str(apk)], capture_output=True, text=True,
                          errors="replace")
    assert proc.returncode == 0, proc.stderr
    m = re.search(r"versionName='([^']*)'", proc.stdout)
    assert m, proc.stdout[:500]
    return m.group(1)


def _lib_bytes(apk) -> bytes:
    with zipfile.ZipFile(apk) as zf:
        return zf.read(LIB)


@then(parsers.re(r'the APK built with "true" has a version name ending in "(?P<suffix>[^"]*)"'))
def diag_true_suffix(rworld, suffix):
    version = _badging_version(rworld.diag_apks["true"])
    assert version.endswith(suffix), version


@then(parsers.re(r'its "libparkcast\.so" holds the string "(?P<marker>[^"]*)"'))
def diag_true_marker(rworld, marker):
    assert marker.encode() in _lib_bytes(rworld.diag_apks["true"])


@then('the APKs built with the property absent and with "false" have neither the suffix nor the string')
def diag_others_unmarked(rworld):
    for label in ("absent", "false"):
        apk = rworld.diag_apks[label]
        version = _badging_version(apk)
        assert not version.endswith(SUFFIX), (label, version)
        assert MARKER not in _lib_bytes(apk), f"the {label} build's library holds the marker"


# --- No runtime switch (AIR-084(c)) ------------------------------------------------------------------------

@given("the receiver's native and app sources in the repository")
def diag_sources(rworld):
    for path in (NATIVE_SRC, CMAKE, DESIGN, BRIDGE, *APP_SOURCES):
        assert path.exists(), path
    rworld.diag_sources = True


@then("the native sources export only the JNI contract's entry points")
def diag_native_exports(rworld):
    hc = _hc()
    contract = hc.jni_contract_methods(DESIGN.read_text(encoding="utf-8"))
    assert contract, "the design's JNI contract lists no native method"
    assert hc.java_native_methods(BRIDGE.read_text(encoding="utf-8")) == contract
    expected = {JNI_PREFIX + name for name in contract} | {"JNI_OnLoad"}
    exports = hc.native_exports(NATIVE_SRC)
    assert set(exports) == expected, sorted(set(exports) ^ expected)
    assert hc.registers_natives(NATIVE_SRC) == [], "a native source registers methods with RegisterNatives"
    assert hc.cmake_hides_symbols(CMAKE.read_text(encoding="utf-8")), \
        "a native target is compiled without -fvisibility=hidden, so every non-static function would be exported"


@then("the app's sources read no setting, property or file that turns raw diagnostics on or off")
def diag_no_runtime_reads(rworld):
    found = _hc().runtime_switch_reads(APP_SOURCES, NATIVE_SRC)
    assert found == [], "\n".join(found)


# --- The keep check (AIR-084(d), OD-204) -------------------------------------------------------------------

@given(parsers.re(r'a fixture APK whose version name is "(?P<version>[^"]*)" and whose library '
                  r"(?P<library>holds|doesn't hold) the string \"(?P<marker>[^\"]*)\""))
def diag_fixture_apk(rworld, version, library, marker):
    root = rworld.tmp / "keep"
    root.mkdir()
    lib = b"\x7fELF fixture library\0" + (marker.encode() + b"\0" if library == "holds" else b"") + b"tail\0"
    apk = root / "fixture.apk"
    with zipfile.ZipFile(apk, "w") as zf:
        zf.writestr("AndroidManifest.xml", b"fixture")
        zf.writestr(LIB, lib)
    aapt = root / "aapt.cmd"
    aapt.write_text("@echo off\r\nif not \"%1 %2\"==\"dump badging\" exit /b 9\r\n"
                    f"echo package: name='parkcast.receiver' versionCode='1' versionName='{version}'\r\n"
                    "echo application-label:'ParkCast'\r\n", encoding="ascii")
    rworld.diag_apk, rworld.diag_aapt = apk, aapt


@when('"tools/s3.py keep-check" runs on it')
def diag_keep_check_runs(rworld):
    proc = subprocess.run([sys.executable, str(REPO / "tools" / "s3.py"), "keep-check", str(rworld.diag_apk),
                           "--aapt", str(rworld.diag_aapt)], capture_output=True, text=True, errors="replace",
                          timeout=120)
    rworld.diag_rc, rworld.diag_out, rworld.diag_err = proc.returncode, proc.stdout, proc.stderr


@then(parsers.re(r'its report names exactly the reasons "(?P<reasons>[^"]*)"'))
def diag_reasons(rworld, reasons):
    expected = [r.strip() for r in reasons.split(",") if r.strip()]
    found = [m.group(1) for m in re.finditer(r"^reason: ([^(\n]*?)(?: \(|$)", rworld.diag_out, re.M)]
    assert found == expected, (found, rworld.diag_out, rworld.diag_err)
    assert rworld.diag_rc in (0, 1), (rworld.diag_rc, rworld.diag_err)


# --- The PC side (AIR-089(g)) ------------------------------------------------------------------------------

@given("the repository")
def diag_repository(rworld):
    assert (REPO / ".git").exists()


@then(parsers.re(r'a file under "runs/" is ignored by git'))
def diag_runs_ignored(rworld):
    probe = "runs/NEMESIS-diag/parkcast-diag/20260101-000000/uxplay-raw.log"
    assert _hc().ignored(REPO, [probe]) == [probe]


@then('git tracks no file under "runs/" and no file named "uxplay-raw.log"')
def diag_runs_untracked(rworld):
    proc = subprocess.run(["git", "-C", str(REPO), "ls-files", "-z"], capture_output=True, text=True, check=True)
    files = [f for f in proc.stdout.split("\0") if f]
    assert files, "git ls-files listed nothing"
    bad = [f for f in files if f.startswith("runs/") or f.rsplit("/", 1)[-1] == RAW]
    assert bad == [], bad


@given(parsers.re(r'a synthetic run folder holding "receiver\.log" and a "uxplay-raw\.log" with the text '
                  r'"(?P<sentinel>[^"]*)"'))
def diag_run_with_raw(rworld, sentinel):
    _go_run(rworld)
    run_dir = _run_dir(rworld)
    (run_dir / RAW).write_bytes(f"1000 L7 {sentinel}\n".encode())
    rworld.diag_sentinel = sentinel


@when("the evaluator writes the result for it into a fake repository root")
def diag_write_result(rworld):
    root = rworld.tmp / "fake-root"
    root.mkdir()
    rworld.diag_root = root
    rworld.diag_summary = _ev().write_result(_run_dir(rworld), root, tier="offline")


@then(parsers.re(r'the fake root\'s "runs/" folder holds the copied "uxplay-raw\.log"'))
def diag_raw_copied(rworld):
    copies = list((rworld.diag_root / "runs").rglob(RAW))
    assert len(copies) == 1, copies
    assert copies[0].read_bytes() == (rworld.eval_dir / RAW).read_bytes()


@then(parsers.re(r'the only file written under the fake root\'s "docs/evidence/" is the summary'))
def diag_only_summary(rworld):
    evidence = rworld.diag_root / "docs" / "evidence"
    files = [p for p in evidence.rglob("*") if p.is_file()]
    assert files == [Path(rworld.diag_summary)], files
    outside = [p for p in rworld.diag_root.rglob("*") if p.is_file()
               and not p.is_relative_to(rworld.diag_root / "runs") and not p.is_relative_to(evidence)]
    assert outside == [], outside


@then(parsers.re(r'the summary does not hold "(?P<sentinel>[^"]*)"'))
def diag_summary_clean(rworld, sentinel):
    assert sentinel.encode() not in Path(rworld.diag_summary).read_bytes()


@given(parsers.re(r'a complete synthetic receiver run with the owner note "(?P<note>[^"]*)"'))
def diag_complete_run(rworld, note):
    _go_run(rworld)
    rworld.eval_note = f"{note}\n".encode()


@given(parsers.re(r'its one session has every GO record except "(?P<text>[^"]*)"'))
def diag_drop_record(rworld, text):
    recs = _st(rworld).eval_recs
    assert sum(r["text"] == text for r in recs) == 1, recs
    rworld.eval_recs = [r for r in recs if r["text"] != text]


def _judged(run_dir):
    ev = _ev()
    j = ev.judge_run_folder(run_dir)
    return j.verdict, ev.read_run_folder(run_dir).summary, list(j.cites)


@given("the evaluator's INCONCLUSIVE verdict, summary and cited lines for that folder are saved")
def diag_save_judgement(rworld):
    rworld.diag_saved = _judged(_run_dir(rworld))
    assert rworld.diag_saved[0] == "INCONCLUSIVE", rworld.diag_saved


@given(parsers.re(r'a "uxplay-raw\.log" is added holding, in receiver-log form, a complete start marker, '
                  r'"session 1 connection accepted model=iPhone15,2", "Mirroring initialized successfully", '
                  r'"session 1 codec h264 1920x1080", "decoder OMX\.TCC\.avcdec configured", '
                  r'"session 1 first frame rendered", a session end record and a complete end marker, all within '
                  r"the observation window"))
def diag_add_raw(rworld):
    recs = [_rec(0, START), _rec(1000, f"session 1 connection accepted model={MODEL}"),
            _rec(2000, "Mirroring initialized successfully"), _rec(3000, "session 1 codec h264 1920x1080"),
            _rec(4000, "decoder OMX.TCC.avcdec configured"), _rec(5000, "session 1 first frame rendered"),
            _rec(6000, "session 1 end reason=iphone disconnected withheld=0"), _rec(7000, STOP)]
    text = "".join(f"{r['ms']} {r['text']}\n" for r in recs)
    (_run_dir(rworld) / RAW).write_bytes(text.encode())


@when("the evaluator judges the folder again")
def diag_judge_again(rworld):
    rworld.diag_again = _judged(_run_dir(rworld))


@then("its verdict, summary and cited lines equal the saved ones")
def diag_same_judgement(rworld):
    assert rworld.diag_again == rworld.diag_saved


def _records_every_log(run_dir: Path):
    """The evaluator's reader as it was before revision 10: every *.log in the folder, in name order."""
    files = sorted(p for p in Path(run_dir).glob("*.log") if p.is_file())
    if not files:
        raise OSError("no log file")
    text = b"".join(p.read_bytes() for p in files).decode("utf-8", errors="replace")
    lines = text.split("\n")
    ends_lf = text.endswith("\n")
    if ends_lf:
        lines.pop()
    out = []
    for i, line in enumerate(lines, 1):
        line = line[:-1] if line.endswith("\r") else line
        m = re.match(r"^(\d+) (.*)$", line)
        out.append((i, int(m.group(1)), m.group(2)) if m else (i, None, line))
    return out, ends_lf


@then('judging the folder with the evaluator as it was before this revision, which reads every "*.log", gives GO, '
      "so the check can fail")
def diag_old_reader_go(rworld, monkeypatch):
    ev = _ev()
    monkeypatch.setattr(ev, "_records", _records_every_log)
    assert ev.judge_run_folder(_run_dir(rworld)).verdict == "GO"


# --- Scenarios added after the mutation check (SOL_REVIEW_s3_rawdiag_scenarios_6) ---------------------------

PLANTS = {
    'an extra JNIEXPORT function in "parkcast_jni.c"':
        ("native", "parkcast_jni.c",
         "\nJNIEXPORT jint JNICALL Java_parkcast_receiver_NativeBridge_nativeRawSwitch(JNIEnv *e, jclass c)"
         " { return 1; }\n"),
    'a RegisterNatives call in "parkcast_jni.c"':
        ("native", "parkcast_jni.c",
         "\nstatic void planted(JNIEnv *env, jclass cls, const JNINativeMethod *m)"
         " { (*env)->RegisterNatives(env, cls, m, 1); }\n"),
    'a getenv call in "rawdiag.c"':
        ("native", "rawdiag.c", '\nstatic int planted_switch(void) { return getenv("PARKCAST_RAW") != 0; }\n'),
    'a read of the system property "parkcast.rawDiagnostics" in "ReceiverActivity.java"':
        ("app", "ReceiverActivity.java", None),
}


@given(parsers.re(r"a copy of the receiver's native and app sources with (?P<plant>.+)"))
def diag_planted_copy(rworld, plant):
    assert plant in PLANTS, plant
    kind, name, text = PLANTS[plant]
    root = rworld.tmp / "planted"
    native = root / "native"
    shutil.copytree(NATIVE_SRC, native)
    apps = []
    for src in APP_SOURCES:
        dest = root / "app" / src.relative_to(REPO)
        shutil.copytree(src, dest)
        apps.append(dest)
    if kind == "native":
        target = native / name
        if not target.exists():
            target.write_text("#include <stdlib.h>\n", encoding="utf-8")
        with open(target, "a", encoding="utf-8", newline="\n") as f:
            f.write(text)
    else:
        target = next(p for d in apps for p in d.rglob(name))
        body = target.read_text(encoding="utf-8")
        cut = body.rindex("}")
        body = (body[:cut] + '    static boolean plantedSwitch() {\n'
                '        return Boolean.parseBoolean(System.getProperty("parkcast.rawDiagnostics"));\n'
                "    }\n" + body[cut:])
        target.write_text(body, encoding="utf-8", newline="\n")
    rworld.diag_planted = (native, apps)


@when("the no-runtime-switch check runs on the copy")
def diag_planted_check(rworld):
    native, apps = rworld.diag_planted
    rworld.diag_switch_problems = _hc().runtime_switch_problems(
        native, apps, DESIGN.read_text(encoding="utf-8"), BRIDGE.read_text(encoding="utf-8"))


@then(parsers.re(r'it fails and names "(?P<file>[^"]+)"'))
def diag_planted_named(rworld, file):
    problems = rworld.diag_switch_problems
    assert problems, "the check passed a planted runtime switch"
    assert any(file in p for p in problems), problems


@given(parsers.re(r'a complete synthetic GO run with the owner note "(?P<note>[^"]*)"'))
def diag_go_run(rworld, note):
    _go_run(rworld)
    rworld.eval_note = f"{note}\n".encode()


@given(parsers.re(r'its log split in the middle of its session into an older "receiver\.log\.1" and a newer '
                  r'"receiver\.log"'))
def diag_split_log(rworld):
    whole = _run_dir(rworld)
    data = (whole / "receiver.log").read_bytes()
    lines = data.split(b"\n")[:-1]
    assert len(lines) >= 4, lines
    cut = len(lines) // 2
    split = whole.parent.parent / "split" / whole.name  # same folder name: the summary carries it
    split.parent.mkdir(parents=True)
    shutil.copytree(whole, split)
    (split / "receiver.log.1").write_bytes(b"\n".join(lines[:cut]) + b"\n")
    (split / "receiver.log").write_bytes(b"\n".join(lines[cut:]) + b"\n")
    rworld.diag_split = (whole, split)


@when(parsers.re(r'the evaluator judges the split folder and the same log as one "receiver\.log"'))
def diag_judge_split(rworld):
    whole, split = rworld.diag_split
    rworld.diag_split_judged = (_judged(whole), _judged(split))


@then("both are GO with the same summary and cited lines")
def diag_split_same(rworld):
    whole, split = rworld.diag_split_judged
    assert whole[0] == "GO", whole
    assert split[0] == "GO", split
    assert split[1:] == whole[1:]
