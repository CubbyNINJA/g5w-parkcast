"""Steps for 16_receiver_diagnostics.feature (S3 revision 10, OD-198 to OD-205).

This module holds the parkcast-diag stick tool's scenarios (AIR-089, AT-89, OD-202 to OD-204). The script
`tools/airplay_s3/stick/parkcast-diag.sh` runs under the host's POSIX `sh` against a fake unit root and a fake
stick (`DiagFakes`, the stick scenarios' journalling toolbox plus the commands this tool needs). The logcat check
is `tools/airplay_s3/stickcheck.py`. Every fixture is synthetic. State lives on `rworld` under `diag_` fields;
the run's result is `rworld.stick_result`, so the stick scenarios' exit-status steps apply.
"""

from __future__ import annotations

import re

import pytest
from pytest_bdd import given, parsers, then, when

from steps.receiver_stick_steps import RECEIVER_PACKAGE, _make_symlink, _output
from steps.receiver_toolbox import ALLOWED, LOG_DIR, REPO, STICK_DIR, TIMESTAMP, Fakes, _HEAD, _write_lf, sha

SCRIPT = "parkcast-diag.sh"
BUILD = "eng.vmadmin.20260206.060005"
DIAG_PARENT = "NEMESIS-diag/parkcast-diag"
APP_DIR = f"data/data/{RECEIVER_PACKAGE}/files"
RAW = "uxplay-raw.log"
SOURCES = (RAW, "receiver.log", "receiver.log.1")
KEY_NAME = "fake.key"
OTHER_NAME = "other.bin"
KEY_BYTES = b"FAKE-KEY-BYTES-not-a-real-key\n"
SENTINEL = "UNFILTERED-SENTINEL"
LINK_SENTINEL = b"SYMLINK-SENTINEL-CONTENT\n"
PID = "1234"
AM_START = f"am start -n {RECEIVER_PACKAGE}/.ReceiverActivity"
TOOL_NEMESIS_DATA = (b"[nemesis]\nversion=1.0\ntype=execute\npath=tools/parkcast-diag/parkcast-diag.sh\n")
# The mutant the logcat check must catch (review finding 4): unfiltered logcat redirected to a stick file, that
# file overwritten with filtered lines, then deleted. A fake shell can't see the transient file; the check can.
MUTANT_REDIRECTED_CALL = 'logcat -d > "$USB/lc.tmp"'
MUTANT_SCRIPT = ("#!/bin/sh\n"
                 "USB=$(pwd)\n"
                 f"{MUTANT_REDIRECTED_CALL}\n"
                 'grep -E "parkcast" "$USB/lc.tmp" > "$USB/lc.txt"\n'
                 'mv "$USB/lc.txt" "$USB/lc.tmp"\n'
                 'rm "$USB/lc.tmp"\n')
FAILURE_WORDS = re.compile(r"fail|missing|symlink|not copied|\bno ", re.I)

# --- The diag toolbox: the stick scenarios' fakes plus the commands parkcast-diag uses ------------------------

# Touched by cp, cmp and ls when a log already holds the end line, so "written after every copy" is observable.
_AFTER_END = r"""
for f in $(cat "$S/diag.logdir" 2>/dev/null)/*.txt; do
  [ -f "$f" ] && grep -q -s "parkcast diag end" "$f" && : > "$S/cp.after_end"
done
"""

_DIAG_ALLOWED = {
    "getprop": "",
    "ps": "",
    "logcat": "-d -v -b -f -c -s",
    "grep": "-E -v -i -e -c -q -s -F -n -l -o -w",
    "cmp": "-s",
    "dumpsys": "",
    "dmesg": "",
    "getenforce": "",
    "sleep": "",
    "sync": "",
    "touch": "",
    "ln": "-s -f",
}

_DIAG_BODY = {
    "getprop": r"""
case "$1" in
  ro.build.version.incremental) if [ -f "$S/getprop.build" ]; then cat "$S/getprop.build"; else echo "@BUILD@"; fi ;;
  sys.boot_completed) if [ -f "$S/getprop.boot" ]; then cat "$S/getprop.boot"; else echo 1; fi ;;
  ro.build.product) echo fixture-product ;;
  *) echo ;;
esac
exit 0
""",
    "ps": r"""
echo "USER     PID   PPID  VSIZE  RSS     WCHAN    PC         NAME"
echo "root      1     0     1000   100   ffffffff 00000000 S /init"
echo "system    300   1     1000   100   ffffffff 00000000 S system_server"
[ -f "$S/ps.running" ] && echo "u0_a12    @PID@  1     1000   100   ffffffff 00000000 S @PKG@"
exit 0
""",
    "am": r"""
if [ "$1" = start ]; then
  : > "$S/ps.running"
  [ -f "$S/am.start.out" ] && cat "$S/am.start.out"
  marks; exit $(rc_of am.start.rc)
fi
marks; exit 0
""",
    "logcat": r"""
for a in "$@"; do case "$a" in -f*) : > "$S/logcat.wrote_file" ;; esac; done
[ -f /dev/stdout ] && : > "$S/logcat.wrote_file"
echo "11-17 12:00:00.000  @PID@  @PID@ I parkcast: receiver started"
echo "11-17 12:00:00.100  @PID@  @PID@ I parkcast: key at /data/data/@PKG@/files/@KEY@"
echo "11-17 12:00:00.200  @PID@  @PID@ W ACodec  : OMX.TCC.avcdec configured"
echo "11-17 12:00:00.300   300   300 I ActivityManager: unrelated line"
[ -f "$S/logcat.sentinel" ] && echo "11-17 12:00:00.400     1     1 I Fixture : @SENTINEL@"
exit 0
""",
    "grep": "\ngrep \"$@\"\n",
    "cmp": "\n" + _AFTER_END + "cmp \"$@\"\n",
    "dumpsys": r"""
echo "  mCurrentFocus=Window{1 u0 @PKG@/@PKG@.ReceiverActivity}"
echo "  mFocusedApp=AppWindowToken{2 token=Token{3 ActivityRecord{4 u0 @PKG@/.ReceiverActivity t5}}}"
exit 0
""",
    "dmesg": r"""
echo "<5>[  10.000] type=1400 audit(0.0:1): avc: denied { read } for path=\"/data/data/@PKG@/files/@OTHER@\" scontext=u:r:untrusted_app:s0"
echo "<5>[  11.000] type=1400 audit(0.0:2): avc: denied { search } for name=\"vendor\" scontext=u:r:untrusted_app:s0"
exit 0
""",
    "getenforce": "\necho Enforcing\nexit 0\n",
    "sleep": "\nexit 0\n",
    "sync": "\nexit 0\n",
    "touch": "\ntouch \"$@\"\n",
    "ln": "\nln \"$@\"\n",
    "ls": "\n" + _AFTER_END + r"""
for a in "$@"; do
  case "$a" in */proc/*/fd|*/proc/*/fd/) [ -f "$S/fd.listing" ] && cat "$S/fd.listing"; exit 0 ;; esac
done
ls "$@"
""",
    "rm": r"""
if [ -f "$S/rm.keep" ]; then exit 0; fi
rm "$@"
""",
    "cp": "\n" + _AFTER_END + r"""
src=; dst=
for a in "$@"; do case "$a" in -*) ;; *) if [ -z "$src" ]; then src=$a; else dst=$a; fi ;; esac; done
target=$dst
[ -d "$dst" ] && target="${dst%/}/${src##*/}"
if [ -f "$S/cp.fail_src" ] && [ "${src##*/}" = "$(cat "$S/cp.fail_src")" ]; then
  head -c 3 "$src" > "$target"; exit 0
fi
if [ -f "$S/cp.half_src" ] && [ "${src##*/}" = "$(cat "$S/cp.half_src")" ]; then
  size=$(wc -c < "$src"); head -c $((size / 2)) "$src" > "$target"; exit 0
fi
cp "$@"
""",
}


class DiagFakes(Fakes):
    """The stick scenarios' fake unit and stick, with getprop, ps, logcat and the rest parkcast-diag calls."""

    def __init__(self, tmp):
        super().__init__(tmp)
        for name, body in _DIAG_BODY.items():
            allowed = _DIAG_ALLOWED.get(name, ALLOWED.get(name, ""))
            text = (_HEAD.replace("@NAME@", name).replace("@ALLOWED@", allowed)
                    + body.replace("@BUILD@", BUILD).replace("@PID@", PID).replace("@PKG@", RECEIVER_PACKAGE)
                    .replace("@KEY@", KEY_NAME).replace("@OTHER@", OTHER_NAME).replace("@SENTINEL@", SENTINEL))
            path = self.root / "system/bin" / name
            _write_lf(path, text)
            path.chmod(0o755)
        self.set("diag.logdir", f"{self.stick_posix}/{DIAG_PARENT}")

    @property
    def app_dir(self):
        return self.root / APP_DIR

    @property
    def diag_run_dir(self):
        return self.stick / DIAG_PARENT / TIMESTAMP

    def raw_calls(self):
        """[(index, name, [args as passed])] for every call that was not rejected; args keep the fake root."""
        log = self.state / "calls.log"
        out = []
        if not log.is_file():
            return out
        for line in log.read_text(encoding="utf-8").split("\n"):
            if not line or line.startswith("REJECTED"):
                continue
            parts = line.split("\t")
            out.append((int(parts[0]), parts[1], parts[2:]))
        return out

    def is_unit(self, arg):
        return arg == self.root_posix or arg.startswith(self.root_posix + "/")


def _diag(rworld):
    fakes = getattr(rworld, "stick_fakes", None)
    assert isinstance(fakes, DiagFakes), "no fake unit tree for parkcast-diag in this scenario"
    return fakes


# --- Fixtures -------------------------------------------------------------------------------------------------


def _source_bytes(name):
    if name == RAW:
        return (b"1000 L7 fixture raw record one\n"
                b"1001 L5 counters gen=2 au_in=0 csd_sent=0 control_drops=0 raw_written=1 raw_dropped=0\n")
    return (f"1000 parkcast start app=0.0.1 commit=fixture uxplay=fixture product=fixture\n"
            f"2000 fixture line of {name}\n").encode("utf-8")


def _fd_listing(rworld):
    fakes = _diag(rworld)
    lines = ["lrwx------ u0_a12 u0_a12 2026-11-17 12:00 0 -> /dev/null",
             "lrwx------ u0_a12 u0_a12 2026-11-17 12:00 1 -> socket:[1234]",
             f"l-wx------ u0_a12 u0_a12 2026-11-17 12:00 2 -> /{LOG_DIR}/receiver.log"]
    for i, name in enumerate(rworld.diag_fd_targets, 3):
        lines.append(f"lr-x------ u0_a12 u0_a12 2026-11-17 12:00 {i} -> /{APP_DIR}/{name}")
    fakes.set("fd.listing", "\n".join(lines) + "\n")


def _write_source(rworld, name, data=None):
    fakes = _diag(rworld)
    where = fakes.app_dir if name == RAW else fakes.log_dir
    where.mkdir(parents=True, exist_ok=True)
    (where / name).write_bytes(_source_bytes(name) if data is None else data)


def _source_path(rworld, name):
    fakes = _diag(rworld)
    return (fakes.app_dir if name == RAW else fakes.log_dir) / name


def _tree(rworld, running, *, build=None):
    """A whole fake unit: ParkCast's process (or not), its app data with the raw file, a key file and another
    file, both receiver logs, and the stick with the tool's folder."""
    rworld.stick_fakes = DiagFakes(rworld.tmp)
    fakes = rworld.stick_fakes
    if running:
        fakes.set("ps.running", "")
    if build is not None:
        fakes.set("getprop.build", build + "\n")
    _write_lf(fakes.root / "proc" / PID / "status", "Name:\tparkcast.receiver\nState:\tS (sleeping)\n"
              "Uid:\t10012\t10012\t10012\t10012\nGid:\t10012\t10012\t10012\t10012\nThreads:\t9\n")
    _write_lf(fakes.root / "proc/net/tcp", "  sl  local_address rem_address   st\n"
              "   0: 00000000:88B8 00000000:0000 0A 00000000:00000000\n")
    _write_lf(fakes.root / "proc/net/udp", "  sl  local_address rem_address   st\n")
    _write_lf(fakes.root / "data/tombstones/tombstone_00", "fixture tombstone\n")
    for name in SOURCES:
        _write_source(rworld, name)
    (fakes.app_dir / KEY_NAME).write_bytes(KEY_BYTES)
    (fakes.app_dir / OTHER_NAME).write_bytes(b"OTHER-APP-FILE\n")
    _write_lf(fakes.stick / "tools/parkcast-diag/NEMESIS.data", TOOL_NEMESIS_DATA)
    rworld.diag_fd_targets = [RAW, KEY_NAME, OTHER_NAME]
    _fd_listing(rworld)
    return fakes


@given(parsers.re(r"a fake unit tree with ParkCast (?P<state>running|not running)"))
def diag_tree(rworld, state):
    _tree(rworld, state == "running")


@given(parsers.parse('its app data holds "{raw}", a key file "{key}" and another file "{other}"'))
def diag_app_data(rworld, raw, key, other):
    assert (raw, key, other) == (RAW, KEY_NAME, OTHER_NAME)
    fakes = _diag(rworld)
    _write_source(rworld, RAW)
    (fakes.app_dir / KEY_NAME).write_bytes(KEY_BYTES)
    (fakes.app_dir / OTHER_NAME).write_bytes(b"OTHER-APP-FILE\n")


@given(parsers.parse('its log folder holds "{first}" and "{second}"'))
def diag_log_folder(rworld, first, second):
    assert (first, second) == ("receiver.log", "receiver.log.1")
    _write_source(rworld, first)
    _write_source(rworld, second)


@given("ParkCast's descriptor list points at each of those app-data files")
def diag_descriptors(rworld):
    rworld.diag_fd_targets = [RAW, KEY_NAME, OTHER_NAME]
    _fd_listing(rworld)


@given(parsers.parse('the fake "logcat" puts the sentinel line "{sentinel}" in every unfiltered output'))
def diag_logcat_sentinel(rworld, sentinel):
    assert sentinel == SENTINEL
    _diag(rworld).set("logcat.sentinel", "")


@given('the fake "rm" records deletions and keeps the file')
def diag_rm_keeps(rworld):
    _diag(rworld).set("rm.keep", "")


@given(parsers.re(r"a fake unit tree with ParkCast (?P<state>running|not running) and all three sources"))
def diag_tree_all_sources(rworld, state):
    fakes = _tree(rworld, state == "running")
    for name in SOURCES:
        assert _source_path(rworld, name).is_file()
    return fakes


@given(parsers.re(r'"(?P<source>uxplay-raw\.log|receiver\.log|receiver\.log\.1)" '
                  r'(?P<problem>is missing|fails to copy|is a symlink to a sentinel file)'))
def diag_source_problem(rworld, source, problem):
    fakes = _diag(rworld)
    path = _source_path(rworld, source)
    rworld.diag_bad_source = source
    if problem == "is missing":
        path.unlink()
        if source == RAW:
            rworld.diag_fd_targets = [KEY_NAME, OTHER_NAME]
            _fd_listing(rworld)
    elif problem == "fails to copy":
        fakes.set("cp.fail_src", source + "\n")
    else:
        target = fakes.root / "elsewhere" / "link-target.bin"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(LINK_SENTINEL)
        path.unlink()
        _make_symlink(target, path)
        rworld.diag_link = (path, target)


@given(parsers.parse('a fake unit tree with ParkCast running, both receiver logs, and no "{raw}"'))
def diag_tree_no_raw(rworld, raw):
    assert raw == RAW
    _tree(rworld, True)
    _source_path(rworld, RAW).unlink()
    rworld.diag_fd_targets = [KEY_NAME, OTHER_NAME]
    _fd_listing(rworld)


@given("the stick already holds the folder the script would make, with a file in it")
def diag_existing_folder(rworld):
    fakes = _diag(rworld)
    _write_lf(fakes.diag_run_dir / "earlier.txt", "an earlier run's file\n")


@given(parsers.parse('a fake unit tree whose build is not "{build}"'))
def diag_tree_other_build(rworld, build):
    assert build == BUILD
    # ParkCast not running, so a script that starts it before the guard is seen calling `am`.
    _tree(rworld, False, build="eng.vmadmin.20250101.000000")


@given('the stick\'s root "NEMESIS.data" is the tool\'s own')
def diag_root_nemesis_data(rworld):
    _write_lf(_diag(rworld).stick / "NEMESIS.data", TOOL_NEMESIS_DATA)


@given("the parkcast-diag script in the repository")
def diag_script_in_repo(rworld):
    rworld.diag_check_name = f"tools/airplay_s3/stick/{SCRIPT}"
    path = STICK_DIR / SCRIPT
    assert path.is_file(), f"{path} does not exist"
    rworld.diag_check_text = path.read_bytes().decode("utf-8")


@given(parsers.parse('a fixture script that redirects "logcat -d" to a file on the stick, overwrites that file '
                     'with filtered lines, and deletes it'))
def diag_mutant_fixture(rworld):
    rworld.diag_check_name = "mutant-fixture.sh"
    rworld.diag_check_text = MUTANT_SCRIPT


# --- Running ------------------------------------------------------------------------------------------------


def _snapshot(base, skip=()):
    """Every file and directory under base: directories, file hashes and symlink targets."""
    out = {}
    if not base.exists():
        return out
    for p in sorted(base.rglob("*")):
        rel = p.relative_to(base).as_posix()
        if rel.split("/")[0] in skip:
            continue
        if p.is_symlink():
            out[rel] = ("link", str(p.readlink()))
        elif p.is_dir():
            out[rel] = ("dir",)
        else:
            out[rel] = ("file", sha(p))
    return out


@when("the parkcast-diag script runs from the fake stick")
def diag_run(rworld):
    fakes = _diag(rworld)
    rworld.diag_unit_before = _snapshot(fakes.root, skip=("system",))
    rworld.diag_run_before = _snapshot(fakes.diag_run_dir)
    rworld.diag_run_existed = fakes.diag_run_dir.exists()
    rworld.stick_result = fakes.run(SCRIPT)
    assert fakes.rejected() == [], f"a fake refused an option: {fakes.rejected()}"


@when("the logcat check reads it")
def diag_logcat_check(rworld):
    from airplay_s3 import stickcheck
    rworld.diag_problems = stickcheck.logcat_problems(rworld.diag_check_text)


# --- Reading the run ----------------------------------------------------------------------------------------


def _log_text(rworld):
    fakes = _diag(rworld)
    logs = sorted((fakes.stick / DIAG_PARENT).rglob("parkcast-diag*.txt"))
    assert len(logs) == 1, f"expected one parkcast-diag log on the stick, found {logs}\n{_output(rworld)}"
    return logs[0].read_bytes().decode("utf-8", "replace")


def _log_lines(rworld):
    return [ln.rstrip("\r") for ln in _log_text(rworld).split("\n") if ln.strip()]


def _names(line, name):
    return re.search(r"(?<![\w.-])" + re.escape(name) + r"(?![\w]|\.\w)", line) is not None


def _changes(rworld):
    """Every journalled call that changes the unit, as 'name args' with unit paths."""
    fakes = _diag(rworld)
    out = []
    for _, name, args in fakes.raw_calls():
        unit_args = [a for a in args if fakes.is_unit(a)]
        plain = [a for a in args if not a.startswith("-")]
        text = " ".join([name] + [fakes.unit_path(a) for a in args])
        if name in ("am", "pm", "nemesis"):
            out.append(text)
        elif name == "logcat" and any(a.startswith("-c") for a in args):
            out.append(text)
        elif name in ("rm", "mv", "chmod", "chown", "mkdir", "touch", "ln") and unit_args:
            out.append(text)
        elif name == "cp" and plain and fakes.is_unit(plain[-1]):
            out.append(text)
    return out


def _assert_copied(rworld, names):
    fakes = _diag(rworld)
    for name in names:
        copy = fakes.diag_run_dir / name
        assert copy.is_file(), f"{name} was not copied into {fakes.diag_run_dir}\n{_output(rworld)}"
        assert sha(copy) == sha(_source_path(rworld, name)), f"the copy of {name} differs from its source"


@then(parsers.parse('a new folder "NEMESIS-diag/parkcast-diag/<timestamp>/" holds copies of "{a}", "{b}" and '
                    '"{c}" and a listing of the log folder'))
def diag_new_folder(rworld, a, b, c):
    fakes = _diag(rworld)
    assert not rworld.diag_run_existed, "the run folder existed before the run"
    _assert_copied(rworld, (a, b, c))
    others = [p for p in fakes.diag_run_dir.iterdir() if p.name not in (a, b, c)]
    listings = [p for p in others if p.is_file() and "receiver.log.1" in p.read_text(encoding="utf-8", errors="replace")]
    assert listings, f"no listing of the log folder in {fakes.diag_run_dir}: {[p.name for p in others]}"
    ls_calls = [args for _, name, args in fakes.calls() if name == "ls" and "/" + LOG_DIR in args]
    assert any("-l" in args for args in ls_calls), f"no `ls -l /{LOG_DIR}` in the journal: {ls_calls}"


@then(parsers.parse('the journal holds no read of "{key}" or "{other}", no listing of app data, and no change on '
                    'the unit'))
def diag_no_app_read(rworld, key, other):
    fakes = _diag(rworld)
    raw_path = f"{fakes.root_posix}/{APP_DIR}/{RAW}"
    for idx, name, args in fakes.raw_calls():
        for a in args:
            assert key not in a and other not in a, f"call {idx} {name} names {a}"
            if fakes.is_unit(a):
                unit = fakes.unit_path(a)
                if unit.startswith("/data/data/") or unit.startswith("/data/user/"):
                    assert a == raw_path, f"call {idx} {name} touches app data: {unit}"
    assert _changes(rworld) == [], f"changes on the unit: {_changes(rworld)}"
    after = _snapshot(fakes.root, skip=("system",))
    assert after == rworld.diag_unit_before, "the unit tree changed"


@then(parsers.parse('no file on the fake stick holds the text "{key}", the key file\'s path or bytes, or "{other}"'))
def diag_no_key_on_stick(rworld, key, other):
    fakes = _diag(rworld)
    key_path = f"/{APP_DIR}/{key}"
    for p in fakes.stick.rglob("*"):
        assert key not in p.name and other not in p.name, f"stick file named {p}"
        if p.is_file():
            data = p.read_bytes()
            for bad in (key.encode(), key_path.encode(), KEY_BYTES.strip(), other.encode()):
                assert bad not in data, f"{p} holds {bad!r}"


@then(parsers.parse('the script\'s log ends with "{end}", written after every copy'))
def diag_log_ends(rworld, end):
    lines = _log_lines(rworld)
    assert lines and lines[-1] == end, f"the log's last line is {lines[-1:]}"
    assert not (_diag(rworld).state / "cp.after_end").exists(), "a copy, compare or listing ran after the end line"


@then(parsers.parse('no file on the fake stick holds "{sentinel}"'))
def diag_no_sentinel(rworld, sentinel):
    fakes = _diag(rworld)
    for p in fakes.stick_files():
        assert sentinel.encode() not in p.read_bytes(), f"{p} holds {sentinel}"


@then('the fake "logcat" was never asked to write to a file')
def diag_logcat_no_file(rworld):
    fakes = _diag(rworld)
    logcats = [args for _, name, args in fakes.calls() if name == "logcat"]
    assert logcats, "the script never called logcat"
    assert not (fakes.state / "logcat.wrote_file").exists(), f"logcat wrote to a file: {logcats}"


@then(parsers.parse('the check passes: every "logcat" call has no "-f" option and its output goes through a pipe '
                    'straight into "grep", with no redirect before the pipe'))
def diag_check_passes(rworld):
    from airplay_s3 import stickcheck
    assert stickcheck.logcat_calls(rworld.diag_check_text), "the check found no logcat call in the script"
    assert rworld.diag_problems == [], rworld.diag_problems


@then('the check fails and names the redirected "logcat" call')
def diag_check_fails(rworld):
    assert rworld.diag_problems, "the check passed the mutant"
    assert any(MUTANT_REDIRECTED_CALL in p for p in rworld.diag_problems), rworld.diag_problems


@then(parsers.re(r'the journal\'s changes on the unit are (?P<changes>exactly one "am start -n '
                 r'parkcast\.receiver/\.ReceiverActivity"|none)'))
def diag_changes(rworld, changes):
    expected = [] if changes == "none" else [AM_START]
    assert _changes(rworld) == expected, _changes(rworld)
    after = _snapshot(_diag(rworld).root, skip=("system",))
    assert after == rworld.diag_unit_before, "the unit tree changed"


@then(parsers.re(r'its log has a failure line naming "(?P<source>uxplay-raw\.log|receiver\.log|receiver\.log\.1)"'))
def diag_failure_line(rworld, source):
    lines = [ln for ln in _log_lines(rworld) if _names(ln, source) and FAILURE_WORDS.search(ln)]
    assert lines, f"no failure line naming {source}: {_log_lines(rworld)}"


@then("the other two sources are copied")
def diag_others_copied(rworld):
    _assert_copied(rworld, [n for n in SOURCES if n != rworld.diag_bad_source])


@then("the journal holds no read through a symlink")
def diag_no_symlink_read(rworld):
    fakes = _diag(rworld)
    link = getattr(rworld, "diag_link", None)
    for p in fakes.stick_files():
        assert LINK_SENTINEL.strip() not in p.read_bytes(), f"{p} holds the symlink target's bytes"
    if link is None:
        return
    link_posix = fakes.root_posix + "/" + link[0].relative_to(fakes.root).as_posix()
    for idx, name, args in fakes.raw_calls():
        for a in args:
            assert a != link_posix and "link-target.bin" not in a, f"call {idx} {name} reads through {a}"


@then(parsers.parse('its log has the line "{line}"'))
def diag_log_line(rworld, line):
    assert line in _log_lines(rworld), _log_lines(rworld)


@then("both receiver logs are copied")
def diag_both_logs(rworld):
    _assert_copied(rworld, ("receiver.log", "receiver.log.1"))


@then("that folder's contents are unchanged")
def diag_folder_unchanged(rworld):
    assert rworld.diag_run_existed
    assert _snapshot(_diag(rworld).diag_run_dir) == rworld.diag_run_before


@then("its log reports that the folder already exists")
def diag_folder_reported(rworld):
    lines = [ln for ln in _log_lines(rworld) if re.search(r"exist", ln, re.I)]
    assert lines, _log_lines(rworld)


@then("no source was copied")
def diag_nothing_copied(rworld):
    fakes = _diag(rworld)
    cps = [(idx, args) for idx, name, args in fakes.calls() if name == "cp"
           and any(a.rsplit("/", 1)[-1] in SOURCES for a in args)]
    assert cps == [], cps
    on_stick = [p for p in fakes.stick_files() if p.name in SOURCES]
    assert on_stick == [], on_stick


@then('the root "NEMESIS.data" is gone, and the journal shows it removed before the build was queried')
def diag_root_removed_first(rworld):
    fakes = _diag(rworld)
    assert not (fakes.stick / "NEMESIS.data").exists(), "the root NEMESIS.data is still on the stick"
    root_data = f"{fakes.stick_posix}/NEMESIS.data"
    removed = [idx for idx, name, args in fakes.raw_calls() if name == "rm" and root_data in args]
    queried = [idx for idx, name, args in fakes.raw_calls()
               if name == "getprop" and "ro.build.version.incremental" in args]
    assert removed, "the journal has no rm of the root NEMESIS.data"
    assert queried, "the journal has no build query"
    assert removed[0] < queried[0], f"removed at call {removed[0]}, build queried at call {queried[0]}"


@then("its log says the guard failed")
def diag_guard_failed(rworld):
    lines = [ln for ln in _log_lines(rworld) if re.search(r"guard.*fail", ln, re.I)]
    assert lines, _log_lines(rworld)


@then('the journal holds no source read, no copy and no "am" call')
def diag_nothing_read(rworld):
    fakes = _diag(rworld)
    for idx, name, args in fakes.raw_calls():
        assert name not in ("cp", "am"), f"call {idx}: {name} {args}"
        for a in args:
            unit = fakes.unit_path(a) if fakes.is_unit(a) else ""
            assert not any(s in a for s in SOURCES), f"call {idx} {name} names a source: {a}"
            assert not unit.startswith("/" + LOG_DIR) and not unit.startswith("/data/data/"), \
                f"call {idx} {name} reads {unit}"
    assert [p for p in fakes.stick_files() if p.name in SOURCES] == []


# --- On the unit: evidence from the stick, never run from the PC suite ---------------------------------------

_UNIT = "@unit: evidence from the stick"


@given("the stick copy from a parkcast-diag run with a raw-diagnostics build, during which the iPhone cast")
def unit_raw_copy_cast():
    pytest.skip(_UNIT)


@given("the stick after a parkcast-diag run")
def unit_stick_after_run():
    pytest.skip(_UNIT)


@given("the stick copy from a parkcast-diag run with a raw-diagnostics build")
def unit_raw_copy():
    pytest.skip(_UNIT)


@when('"Mirroring initialized successfully" is in "uxplay-raw.log" but not in "receiver.log"')
def unit_setup_line_only_raw():
    pytest.skip(_UNIT)


@then('"uxplay-raw.log" holds records from UxPlay during the session and counter lines')
def unit_raw_holds_records():
    pytest.skip(_UNIT)


@then("the owner finds in it no unredacted run of 32 or more hex digits, of 44 or more base64 characters, "
      "`<data>` value, byte dump, PIN value, or the key file's path or name")
def unit_owner_finds_nothing():
    pytest.skip(_UNIT)


@then('its copy is in "NEMESIS-diag/parkcast-diag/<timestamp>/"')
def unit_copy_in_folder():
    pytest.skip(_UNIT)


@then('the root "NEMESIS.data" is gone')
def unit_root_data_gone(rworld):
    # Offline scenarios check the fake stick; on the unit the evidence is the real stick.
    fakes = getattr(rworld, "stick_fakes", None)
    if not isinstance(fakes, DiagFakes):
        pytest.skip(_UNIT)
    assert not (fakes.stick / "NEMESIS.data").exists(), "the root NEMESIS.data is still on the fake stick"


@then('the copied listing of the receiver\'s log folder shows no "uxplay-raw.log"')
def unit_listing_no_raw():
    pytest.skip(_UNIT)


@then("AT-72's unit check is not met")
def unit_at72_not_met():
    pytest.skip(_UNIT)


# --- Scenarios added after the mutation check (SOL_REVIEW_s3_rawdiag_scenarios_6, OD-206) --------------------

# One fixture per rule of the logcat check; each breaks that rule only, so turning the rule off passes it.
ONE_RULE_FIXTURES = {
    'has a "-f" option, but is piped straight into grep':
        'logcat -d -f "$USB/lc.txt" | grep -E parkcast > "$OUT/logcat.txt"',
    "is redirected to a file before its pipe into grep":
        'logcat -d > "$USB/lc.txt" | grep -E parkcast > "$OUT/logcat.txt"',
    "is piped into tee before grep":
        'logcat -d | tee "$USB/lc.txt" | grep -E parkcast > "$OUT/logcat.txt"',
    'sits inside an if, after "then", and is redirected to a file before its pipe into grep':
        'if true; then logcat -d > "$USB/lc.txt" | grep -E parkcast > "$OUT/logcat.txt"; fi',
}


@given(parsers.re(r"a fixture script whose one logcat call (?P<breaks>.+)"))
def diag_one_rule_fixture(rworld, breaks):
    assert breaks in ONE_RULE_FIXTURES, breaks
    call = ONE_RULE_FIXTURES[breaks]
    rworld.diag_check_name = "one-rule-fixture.sh"
    rworld.diag_check_text = "#!/bin/sh\nUSB=/storage/usb0\nOUT=$USB/out\n" + call + "\n"
    rworld.diag_check_call = call.split(" | grep")[0].replace("if true; then ", "")


@then("the check fails and names that call")
def diag_check_fails_names_call(rworld):
    assert rworld.diag_problems, "the check passed a script that breaks one of its rules"
    head = rworld.diag_check_call.split(" >")[0].split(" |")[0]
    assert any(head in p for p in rworld.diag_problems), (head, rworld.diag_problems)


@given(parsers.re(r'the fake "cp" copies only the first half of "(?P<source>receiver\.log|receiver\.log\.1|'
                  r'uxplay-raw\.log)" and exits 0'))
def diag_cp_half(rworld, source):
    _diag(rworld).set("cp.half_src", source + "\n")
    rworld.diag_bad_source = source


@then(parsers.re(r'the run folder holds no "(?P<name>[^"]+)"'))
def diag_run_folder_lacks(rworld, name):
    copy = _diag(rworld).diag_run_dir / name
    assert not copy.exists(), f"a partial copy of {name} was left on the stick"


@then(parsers.re(r'"(?P<a>[^"]+)" and "(?P<b>[^"]+)" are copied whole'))
def diag_copied_whole(rworld, a, b):
    _assert_copied(rworld, [a, b])


@given(parsers.re(r'the fake "getprop" gives "(?P<value>[^"]*)" for "sys\.boot_completed"'))
def diag_boot_not_done(rworld, value):
    _diag(rworld).set("getprop.boot", value + "\n")
