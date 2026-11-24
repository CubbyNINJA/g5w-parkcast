"""Steps for 16_receiver_diagnostics.feature (S3 revision 10, OD-198 to OD-205).

The @host scenarios of AIR-085 to AIR-088 (and the AIR-055, AIR-072, AIR-073 and AIR-083 ones on the native
path) run the APK's own receiver/native/src/logpath.c, rawdiag.c and ring.c in a DLL for this PC, built with
MSYS2's gcc together with the harness in tests/native/. The harness supplies a fake clock, a fake file layer,
a journal of ring pushes, callback entries and returns and JNI calls, and fixture events put straight into the
ring. Two DLLs are built: one with PARKCAST_RAW_DIAG=1 and one with it 0. They go to the git-ignored
build/native-harness/ and are rebuilt when any source is newer.

Fields on `rworld` use the `nat_` prefix.
"""

from __future__ import annotations

import ctypes
import os
import re
import subprocess
import threading
import time
from ctypes import POINTER, byref, c_char_p, c_int, c_long, c_longlong, c_uint, c_uint64, c_void_p
from pathlib import Path

import pytest
from pytest_bdd import given, parsers, then, when

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "receiver" / "native" / "src"
NAT = ROOT / "tests" / "native"
BUILD = ROOT / "build" / "native-harness"
MSYS_BIN = Path("C:/msys64/ucrt64/bin")
GCC = MSYS_BIN / "gcc.exe"
NDK_INCLUDE = Path("C:/Android/ndk/25.2.9519653/toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/include")

KEY = "/fake/files/fake.key"
THRESHOLD = 16_776_704
CAP = 16_777_216
FOP_OPEN, FOP_WRITE, FOP_CLOSE = 1, 2, 3
J_PUSH, J_CB_ENTER, J_CB_RETURN = 1, 2, 3
EV_LOG = 7
AU = b"\x00\x00\x00\x01\x65\x88\x84\x00"  # an H.264 IDR slice, no SPS or PPS
CSD = b"\x00\x00\x00\x01\x67\x42\x00\x1f\x00\x00\x00\x01\x68\xce\x3c\x80"

COUNTER = re.compile(rb"^(\d+) counters ")
RECORD = re.compile(rb"^(\d+) L(\d+) ")
FULL = re.compile(rb"^(\d+) raw log full gen=\d+ au_in=\d+ csd_sent=\d+ au_ring_drops=\d+ log_ring_drops=\d+ "
                  rb"control_drops=\d+ raw_written=\d+ raw_dropped=\d+$")

# ---- building and loading the harness DLLs ----

_LIBS: dict[bool, ctypes.CDLL] = {}
_SOURCES = [(NAT / "harness.c", False), (NAT / "harness_hooks.c", False),
            (SRC / "logpath.c", True), (SRC / "rawdiag.c", True)]
_DEPS = [NAT / "harness_wrap.h", SRC / "rawdiag.h", SRC / "logpath.h", SRC / "parkcast.h", SRC / "ring.c"]


def _build(on: bool) -> Path:
    tag = "on" if on else "off"
    out = BUILD / f"parkcast_harness_{tag}.dll"
    inputs = [p for p, _ in _SOURCES] + _DEPS
    if out.exists() and all(p.stat().st_mtime < out.stat().st_mtime for p in inputs):
        return out
    if not GCC.exists():
        pytest.fail(f"MSYS2 gcc not found at {GCC}")
    (BUILD / tag).mkdir(parents=True, exist_ok=True)
    flags = ["-std=gnu11", "-O1", "-g", "-Wall", "-DPARKCAST_TEST_SEAMS", f"-DPARKCAST_RAW_DIAG={1 if on else 0}",
             "-I", str(SRC), "-I", str(NAT), "-idirafter", str(NDK_INCLUDE)]
    env = dict(os.environ, PATH=str(MSYS_BIN) + os.pathsep + os.environ.get("PATH", ""))
    objs = []
    for src, wrap in _SOURCES:
        obj = BUILD / tag / (src.stem + ".o")
        cmd = [str(GCC), "-c", *flags, *(["-include", str(NAT / "harness_wrap.h")] if wrap else []),
               str(src), "-o", str(obj)]
        r = subprocess.run(cmd, capture_output=True, text=True, env=env)
        assert r.returncode == 0, f"compiling {src.name} failed:\n{r.stderr[-2000:]}"
        objs.append(str(obj))
    tmp = out.with_suffix(".tmp.dll")
    r = subprocess.run([str(GCC), "-shared", "-static", "-o", str(tmp), *objs, "-lpthread"],
                       capture_output=True, text=True, env=env)
    assert r.returncode == 0, f"linking the harness failed:\n{r.stderr[-2000:]}"
    os.replace(tmp, out)
    return out


def _sig(lib):
    I, L, P = c_int, c_long, c_void_p
    pi, pl = POINTER(c_int), POINTER(c_long)
    spec = {
        "harness_set_clock": (None, [c_longlong]), "harness_thread_id": (I, []),
        "harness_fail_open": (None, [I]), "harness_fail_write": (None, [I, I]),
        "harness_fail_all_writes": (None, [I]), "harness_fs_working": (None, []),
        "harness_set_write_delay": (None, [I]), "harness_fs_len": (L, []), "harness_fs_data": (P, []),
        "harness_fop_count": (I, []), "harness_fops_lost": (I, []),
        "harness_fop": (I, [I, pi, pi, pi, pi, pl, pl, pl, pl, pl, pi, pi, c_char_p, I]),
        "harness_flag_trunc": (I, []), "harness_flag_creat": (I, []), "harness_flag_wronly": (I, []),
        "harness_flag_accmode": (I, []),
        "harness_journal_count": (I, []),
        "harness_journal": (I, [I, pi, pi, pi, pi, pi, pi, pl, c_char_p, I]),
        "harness_log_on_thread": (I, [I, c_char_p]), "harness_log": (None, [I, c_char_p]),
        "harness_start_delivery": (I, []), "harness_set_filter_failing": (None, [I]),
        "harness_csvm_calls": (I, []), "harness_jni_calls": (I, []), "harness_jni_in_raw": (I, []),
        "harness_ring_open_stopped": (None, []), "harness_set_generation": (None, [I]),
        "harness_generation": (I, []), "harness_au_drops": (c_uint, []), "harness_log_drops": (c_uint, []),
        "harness_control_drops": (c_uint, []), "harness_fixture_fill": (I, [I]),
        "harness_fixture_empty": (I, []), "harness_ring_count": (I, []),
        "harness_ring_entry": (I, [I, pi, pi, pi, pi, POINTER(c_longlong), POINTER(P), POINTER(P), pi]),
        "harness_reset": (I, []),
        "lp_open": (None, [c_char_p]), "lp_log": (None, [I, c_char_p]),
        "lp_video_process": (None, [c_char_p, I, I, c_uint64]),
        "lp_push_codec_config": (I, [I, I, c_char_p, c_char_p, I]),
        "lp_set_h264": (None, [I, I]), "lp_reset_video": (None, []),
        "lp_session_start": (None, [I]), "lp_session_end": (None, [I]), "lp_stop_last": (None, []),
    }
    for name, (res, args) in spec.items():
        fn = getattr(lib, name)
        fn.restype = res
        fn.argtypes = args


def lib(on: bool = True) -> ctypes.CDLL:
    if on not in _LIBS:
        path = _build(on)
        if MSYS_BIN.exists():
            os.add_dll_directory(str(MSYS_BIN))
        dll = ctypes.CDLL(str(path))
        _sig(dll)
        _LIBS[on] = dll
    return _LIBS[on]


# ---- helpers ----

def _reset(w, on=True, stopped=True):
    w.nat = lib(on)
    assert w.nat.harness_reset() == 0
    if stopped:
        w.nat.harness_ring_open_stopped()
    w.nat_now = 1000
    w.nat.harness_set_clock(1000)
    w.nat_mark = 0


def _ensure(w):
    if getattr(w, "nat", None) is None:
        _reset(w)


def _clock(w, ms):
    w.nat_now = int(ms)
    w.nat.harness_set_clock(int(ms))


def _open(w, ms=None, key=KEY):
    _ensure(w)
    if ms is not None:
        _clock(w, ms)
    w.nat.lp_open(key.encode())


def _record(w, text: bytes, level=7, ms=None):
    if ms is not None:
        _clock(w, ms)
    w.nat_mark = len(_lines(w))
    w.nat.harness_log(level, text)


def _video(w, ms=None):
    if ms is not None:
        _clock(w, ms)
    w.nat.lp_video_process(AU, len(AU), 0, 0)


def _codec(w):
    return w.nat.lp_push_codec_config(1920, 1080, b"h264", CSD, len(CSD))


def _session_end(w, ms=None):
    if ms is not None:
        _clock(w, ms)
    w.nat.lp_session_end(w.nat.harness_generation())


def _data(w) -> bytes:
    n = w.nat.harness_fs_len()
    return ctypes.string_at(w.nat.harness_fs_data(), n) if n else b""


def _lines(w) -> list[bytes]:
    out = _data(w).split(b"\n")
    if out and out[-1] == b"":
        out.pop()
    return out


def _counter_times(w) -> list[int]:
    return [int(m.group(1)) for m in (COUNTER.match(x) for x in _lines(w)) if m]


def _records(w) -> list[bytes]:
    return [x for x in _lines(w) if RECORD.match(x)]


def _fops(w) -> list[dict]:
    out = []
    for i in range(w.nat.harness_fop_count()):
        v = [c_int() for _ in range(4)] + [c_long() for _ in range(5)] + [c_int(), c_int()]
        path = ctypes.create_string_buffer(256)
        assert w.nat.harness_fop(i, *(byref(x) for x in v), path, 256) == 0
        keys = ["type", "tid", "held_only_raw", "held_n", "seq0", "seq1", "len", "result", "off", "flags", "mode"]
        f = {k: x.value for k, x in zip(keys, v)}
        f["path"] = path.value.decode()
        out.append(f)
    return out


def _journal(w) -> list[dict]:
    out = []
    for i in range(w.nat.harness_journal_count()):
        v = [c_int() for _ in range(6)] + [c_long()]
        text = ctypes.create_string_buffer(256)
        assert w.nat.harness_journal(i, *(byref(x) for x in v), text, 256) == 0
        f = {k: x.value for k, x in zip(["type", "tid", "kind", "a", "ret", "in_raw", "seq"], v)}
        f["text"] = text.value
        out.append(f)
    return out


def _ring(w) -> list[tuple]:
    out = []
    for i in range(w.nat.harness_ring_count()):
        gen, kind, a, b, ln = c_int(), c_int(), c_int(), c_int(), c_int()
        pts, text, data = c_longlong(), c_void_p(), c_void_p()
        if w.nat.harness_ring_entry(i, byref(gen), byref(kind), byref(a), byref(b), byref(pts), byref(text),
                                    byref(data), byref(ln)) != 0:
            break
        t = ctypes.string_at(text.value) if text.value else None
        d = ctypes.string_at(data.value, ln.value) if data.value and ln.value else None
        out.append((gen.value, kind.value, a.value, b.value, pts.value, t, d))
    return out


def _logs(w) -> list[tuple]:
    return [e for e in _ring(w) if e[1] == EV_LOG]


def _expect(w, line: str) -> bytes:
    return line.replace("<uptime>", str(w.nat_now)).encode()


def _filler(w, target: int):
    """Writes '.' records at the current uptime until the file is exactly `target` bytes long."""
    prefix = len(f"{w.nat_now} L7 ") + 1
    full = prefix + 65_536
    while True:
        rest = target - w.nat.harness_fs_len()
        if rest == 0:
            return
        assert rest >= prefix, f"cannot reach {target}: {rest} bytes left"
        if rest <= full:
            n = rest - prefix
        elif rest - full < prefix:
            n = 60_000
        else:
            n = 65_536
        w.nat.harness_log(7, b"." * n)
        assert w.nat.harness_fs_len() > target - rest, "a filler record was not written"


# ---- Given ----

@given("the native log path and the ring are compiled with raw diagnostics on, and delivery is stopped")
def on_stopped(rworld):
    _reset(rworld, on=True, stopped=True)
    _open(rworld, 1000)


@given("the native log path and the ring are compiled with raw diagnostics on")
def on_unstarted(rworld):
    _reset(rworld, on=True, stopped=False)
    _open(rworld, 1000)


@given("the harness's JNI environment records every call made through it")
def jni_env(rworld):
    assert rworld.nat.harness_start_delivery() == 0, "the delivery thread did not attach"


@given("the Java-side filter is failing, so nothing is written to the receiver's log")
def filter_failing(rworld):
    rworld.nat.harness_set_filter_failing(1)


@given(parsers.re(r"the raw-file module is opened at uptime (?P<ms>\d+) ms"))
def opened_at(rworld, ms):
    _open(rworld, int(ms))


@given("the raw-file module is opened")
def opened(rworld):
    _open(rworld, 1000)


@given(parsers.re(r'the raw-file module is opened with the fake key file path "(?P<path>[^"]*)"'))
@when(parsers.re(r'the raw-file module is opened with the fake key file path "(?P<path>[^"]*)"'))
def opened_with_key(rworld, path):
    _open(rworld, 1000, key=path)
    rworld.nat_key = path


@given(parsers.re(r'the native source "(?P<path>receiver/native/src/host\.c)"'))
def native_source(rworld, path):
    rworld.nat_source = (ROOT / path).read_text(encoding="utf-8")


@given("the native log path is compiled with raw diagnostics off")
def off_lib(rworld):
    _reset(rworld, on=False, stopped=True)


@given("the ring's 64 general slots are full")
def general_full(rworld):
    assert rworld.nat.harness_fixture_fill(64) == 64
    rworld.nat_log_drops = rworld.nat.harness_log_drops()


@given(parsers.re(r"the ring is in generation (?P<gen>\d+)"))
def generation(rworld, gen):
    rworld.nat.harness_set_generation(int(gen))


@given("all 72 ring slots are full of fixture events")
def all_full(rworld):
    assert rworld.nat.harness_fixture_fill(72) == 72


@given('at 1100 ms, in this order: "video_process" with one access unit; one record; one record of 65,537 '
       'escaped bytes; the general slots filled up with fixture events; "video_process" with one access unit; '
       'one record; one codec configuration; the reserved slots filled up with fixture events; one codec '
       'configuration')
def carry_over_events(rworld):
    w = rworld
    _clock(w, 1100)
    _video(w)
    w.nat.harness_log(7, b"one record")
    w.nat.harness_log(7, b"." * 65_537)
    assert w.nat.harness_fixture_fill(64) == 64
    _video(w)
    w.nat.harness_log(7, b"one more record")
    assert _codec(w) == 1, "the first codec configuration should take a reserved slot"
    assert w.nat.harness_fixture_fill(72) == 72
    assert _codec(w) == 0, "the second codec configuration should be refused"


@given("the ring was emptied of fixture events, and the session-end callback ran at 1200 ms")
def emptied_and_ended(rworld):
    rworld.nat.harness_fixture_empty()
    _session_end(rworld, 1200)


@given(parsers.re(r'its first record is "(?P<text>[^"]*)"'))
def first_record(rworld, text):
    _record(rworld, text.encode())


@given("records are written until fewer bytes remain below 16,776,704 than a counter line needs")
def nearly_full(rworld):
    _filler(rworld, THRESHOLD - 20)


@given(parsers.re(r"the fake file layer will fail with (?P<failure>an open failure|a failed write|a short write) "
                  r"at (?P<at>the app start's open|the open's counter line|the first record's line|"
                  r"the third record's line)"))
def will_fail(rworld, failure, at):
    _ensure(rworld)
    rworld.nat_failure = failure
    if failure == "an open failure":
        assert at == "the app start's open"
        rworld.nat.harness_fail_open(1)
        return
    nth = {"the open's counter line": 1, "the first record's line": 2, "the third record's line": 4}[at]
    rworld.nat.harness_fail_write(nth, 1 if failure == "a failed write" else 2)


@given("the fake file layer fails every write")
def fails_every_write(rworld):
    rworld.nat.harness_fail_all_writes(1)


@given("a watchdog of 5 seconds guards the test")
def watchdog(rworld):
    rworld.nat_watchdog = 5.0


# ---- When ----

@when(parsers.re(r'UxPlay\'s log callback is called on a harness thread with level (?P<level>\d+) '
                 r'and the text "(?P<text>[^"]*)"'))
def log_on_thread(rworld, level, text):
    rworld.nat_tid = rworld.nat.harness_log_on_thread(int(level), text.encode())
    assert rworld.nat_tid > 0


@when(parsers.re(r'UxPlay\'s log callback is called with level (?P<level>\d+) and the text "(?P<text>[^"]*)"'))
def log_level(rworld, level, text):
    _record(rworld, text.encode(), int(level))


@when(parsers.re(r'UxPlay\'s log callback is called with the text "(?P<text>.*)"'))
def log_text(rworld, text):
    _record(rworld, text.encode())


@when("UxPlay's log callback is called with each of these texts")
def log_each(rworld, datatable):
    texts = [row[0].replace("<LF>", "\n").replace("<0x01>", "\x01").encode() for row in datatable[1:]]
    rworld.nat_texts = texts
    for t in texts:
        rworld.nat.harness_log(7, t)


@when("UxPlay's log callback is called with 3 records")
def log_three(rworld):
    w = rworld
    w.nat_texts = [b"record one", b"record two", b"record three"]
    done = []

    def run():
        for t in w.nat_texts:
            w.nat.harness_log(7, t)
            done.append(t)

    th = threading.Thread(target=run, daemon=True)
    th.start()
    th.join(getattr(w, "nat_watchdog", 5.0))
    w.nat_done = len(done)


@when('a record at level 7 arrives at 1234 ms with the text "SETUP 1" followed by CR, LF, a backslash '
      'and the byte 0x01')
def record_controls(rworld):
    rworld.nat_len_before = rworld.nat.harness_fs_len()
    _record(rworld, b"SETUP 1\r\n\\\x01", 7, 1234)


@when(parsers.re(r'a record at level (?P<level>\d+) arrives with the text "(?P<text>(?:(?!" LF).)*)"'))
def record_plain(rworld, level, text):
    _record(rworld, text.encode(), int(level))


@when(parsers.re(r'a record at level (?P<level>\d+) arrives with the text '
                 r'(?P<pieces>"[^"]*"(?: LF(?: "[^"]*")?)+)'))
def record_pieces(rworld, level, pieces):
    parts = re.findall(r'"([^"]*)"|LF', pieces)
    tokens = re.findall(r'"[^"]*"|LF', pieces)
    text = "".join("\n" if tok == "LF" else part for tok, part in zip(tokens, parts))
    _record(rworld, text.encode(), int(level))


@when("its KEYCHECK stage is read")
def keycheck_read(rworld):
    src = rworld.nat_source
    start = src.index("static void stage_keycheck(")
    rworld.nat_body = src[start:src.index("\n}\n", start)]


@when("records arrive at 1400, 1800, 2200 and 2600 ms")
def records_at(rworld):
    for ms in (1400, 1800, 2200, 2600):
        _record(rworld, f"record at {ms}".encode(), 7, ms)


@when(parsers.re(r"the session ends at (?P<ms>\d+) ms"))
def session_ends_at(rworld, ms):
    _session_end(rworld, int(ms))


@when(parsers.re(r"stop's last phase runs at (?P<ms>\d+) ms"))
def stop_at(rworld, ms):
    _clock(rworld, int(ms))
    rworld.nat.lp_stop_last()


@when(parsers.re(r'"video_process" is called at (?P<ms>\d+) ms'))
def video_at(rworld, ms):
    _video(rworld, int(ms))


@when(parsers.re(r"at (?P<ms>\d+) ms (?P<actions>.+)"))
def at_ms_actions(rworld, ms, actions):
    w = rworld
    _clock(w, int(ms))
    if actions == '"video_process" is called 3 times with a non-empty access unit before any codec data':
        w.nat.lp_set_h264(1920, 1080)
        for _ in range(3):
            _video(w)
    elif actions == "the 64 general slots are filled with fixture events and a codec configuration is pushed":
        assert w.nat.harness_fixture_fill(64) == 64
        assert _codec(w) == 1
    elif actions == ('the 64 general slots are filled with fixture events, "video_process" pushes one access '
                     'unit and one record arrives'):
        assert w.nat.harness_fixture_fill(64) == 64
        _video(w)
        w.nat.harness_log(7, b"one record")
    elif actions == "two records arrive and one record of 65,537 escaped bytes arrives":
        w.nat.harness_log(7, b"record one")
        w.nat.harness_log(7, b"record two")
        w.nat.harness_log(7, b"." * 65_537)
    else:
        pytest.fail(f"no action defined for {actions!r}")


@when("the ring is emptied of fixture events")
def ring_emptied(rworld):
    rworld.nat.harness_fixture_empty()


@when(parsers.re(r"the session-end callback runs at (?P<ms>\d+) ms"))
def session_end_at(rworld, ms):
    _session_end(rworld, int(ms))


@when("the session-end callback runs")
def session_end_now(rworld):
    _session_end(rworld)


@when("the session-end callback runs again at 1300 ms with no event between")
def session_end_again(rworld):
    _session_end(rworld, 1300)


@when(parsers.re(r"a codec configuration is pushed at (?P<ms>\d+) ms"))
def codec_at(rworld, ms):
    _clock(rworld, int(ms))
    assert _codec(rworld) == 0, "the ring should refuse it"


@when("stop's last phase runs, and a new app start opens the raw-file module at 5000 ms with the ring in "
      "generation 1")
def stop_and_restart(rworld):
    w = rworld
    w.nat.lp_stop_last()
    w.nat.harness_set_generation(1)
    w.nat.lp_reset_video()
    _open(w, 5000)


@when("records are written until the file is exactly 16,776,704 bytes")
def fill_to_threshold(rworld):
    _filler(rworld, THRESHOLD)
    assert rworld.nat.harness_fs_len() == THRESHOLD
    rworld.nat_snap = _data(rworld)


@when("one more record arrives")
def one_more(rworld):
    _record(rworld, b"one more record")


@when("a record arrives, a counter event comes due, the session ends and stop's last phase runs")
def after_full(rworld):
    w = rworld
    w.nat_snap = _data(w)
    w.nat_fops_before = w.nat.harness_fop_count()
    _record(w, b"after the final line")
    _video(w, w.nat_now + 1000)
    _session_end(w)
    w.nat.lp_stop_last()


@when("a counter line comes due")
def counter_due(rworld):
    _video(rworld, rworld.nat_now + 1000)


@when(parsers.re(r"a record arrives whose escaped text is (?P<n>[\d,]+) bytes long"))
def record_escaped_len(rworld, n):
    n = int(n.replace(",", ""))
    rworld.nat_escaped = getattr(rworld, "nat_escaped", []) + [n]
    rworld.nat.harness_log(7, b"\x01" + b"." * (n - 4))  # "\x01" escapes to four bytes


@when("five records arrive, the session ends and stop's last phase runs")
def five_records(rworld):
    for i in range(1, 6):
        rworld.nat.harness_log(7, f"record {i}".encode())
    _session_end(rworld)
    rworld.nat.lp_stop_last()


@when("a new app start opens the raw-file module with the fake file layer working")
def restart_working(rworld):
    w = rworld
    w.nat.harness_fs_working()
    w.nat.harness_set_generation(1)
    w.nat.lp_reset_video()
    _open(w, 5000)


@when("a record arrives")
def a_record(rworld):
    _record(rworld, b"after restart")


# ---- Then ----

def _write_of(w, line: bytes):
    data = _data(w)
    for f in _fops(w):
        if f["type"] == FOP_WRITE and f["result"] == f["len"] and data[f["off"]:f["off"] + f["len"]] == line:
            return f
    return None


@then(parsers.re(r'that thread wrote the raw line "(?P<line>[^"]*)" before it pushed to the ring'))
def wrote_before_push(rworld, line):
    w = rworld
    f = _write_of(w, _expect(w, line) + b"\n")
    assert f is not None, f"no single write of {line!r}"
    assert f["tid"] == w.nat_tid, "the raw line was written on another thread"
    pushes = [j for j in _journal(w) if j["type"] == J_PUSH and j["kind"] == EV_LOG and j["tid"] == w.nat_tid]
    assert pushes, "the callback thread pushed no log event"
    assert f["seq1"] < pushes[0]["seq"], "the raw write did not finish before the ring push"
    w.nat_write = f


@then("the write finished before the callback returned")
def write_before_return(rworld):
    w = rworld
    rets = [j for j in _journal(w) if j["type"] == J_CB_RETURN and j["tid"] == w.nat_tid]
    assert rets and w.nat_write["seq1"] < rets[0]["seq"]


@then(parsers.re(r'the ring received exactly one log event with level (?P<level>\d+) and the text "(?P<text>[^"]*)"'))
def ring_exactly_one(rworld, level, text):
    logs = _logs(rworld)
    assert len(logs) == 1, f"{len(logs)} log events in the ring"
    assert logs[0][2] == int(level) and logs[0][5] == text.encode()


@then(parsers.re(r'the raw file holds the line "(?P<line>.*)"'))
def holds_line(rworld, line):
    assert _expect(rworld, line) in _lines(rworld), f"no line {line!r}; last: {_lines(rworld)[-1:]!r}"


@then("no JNI call was made while a raw-file call was running")
def no_jni_in_raw(rworld):
    w = rworld
    deadline = time.monotonic() + 2.0
    while w.nat.harness_csvm_calls() < 1 and time.monotonic() < deadline:
        time.sleep(0.01)
    assert w.nat.harness_csvm_calls() >= 1, "the delivery thread never called Java"
    assert w.nat.harness_jni_calls() > 0
    assert w.nat.harness_jni_in_raw() == 0, "a JNI call ran during a raw-file call"


@then("that record adds exactly one line, ending in one LF")
def one_line_added(rworld):
    added = _data(rworld)[rworld.nat_len_before:]
    assert added.count(b"\n") == 1 and added.endswith(b"\n"), f"added {added!r}"


@then(parsers.re(r'the raw file\'s line for it is "(?P<line>.*)"'))
def line_for_it(rworld, line):
    new = [x for x in _lines(rworld)[rworld.nat_mark:] if not COUNTER.match(x)]
    assert new == [_expect(rworld, line)], f"got {new!r}"


@then(parsers.re(r'the fake file layer recorded exactly one open, of "(?P<path>[^"]*)", with mode 0600 and '
                 r'truncation'))
def one_open(rworld, path):
    w = rworld
    opens = [f for f in _fops(w) if f["type"] == FOP_OPEN]
    assert len(opens) == 1, f"{len(opens)} opens"
    f = opens[0]
    assert f["path"] == path, f"opened {f['path']!r}"
    assert f["mode"] == 0o600, f"mode {f['mode']:o}"
    assert f["flags"] & w.nat.harness_flag_trunc(), "no O_TRUNC"
    assert f["flags"] & w.nat.harness_flag_creat(), "no O_CREAT"
    assert f["flags"] & w.nat.harness_flag_accmode() == w.nat.harness_flag_wronly(), "not write-only"


@then(parsers.re(r'it recorded no open of "(?P<path>[^"]*)"'))
def no_open_of(rworld, path):
    assert not [f for f in _fops(rworld) if f["type"] == FOP_OPEN and f["path"] == path]


@then("the fake file layer recorded no open")
def no_open(rworld):
    assert not [f for f in _fops(rworld) if f["type"] == FOP_OPEN]


@then('the raw-file open comes after the stdio redirect\'s "dup2" calls and before "raop_init"')
def keycheck_order(rworld):
    body = rworld.nat_body
    assert "lp_open(" in body, "KEYCHECK does not open the raw file"
    assert body.rindex("dup2(") < body.index("lp_open(") < body.index("raop_init("), "wrong order"


@then(parsers.re(r'the ring received one log event with the text "(?P<text>[^"]*)"'))
def ring_one(rworld, text):
    logs = _logs(rworld)
    assert [e[5] for e in logs] == [text.encode()], f"ring log events {logs!r}"


@then(parsers.re(r'the raw file holds a line ending with "(?P<text>[^"]*)"'))
def holds_line_ending(rworld, text):
    assert any(x.endswith(text.encode()) for x in _lines(rworld))


@then("the ring's log drop count has risen by one")
def log_drop_one(rworld):
    assert rworld.nat.harness_log_drops() == rworld.nat_log_drops + 1


@then('the ring holds one log event per text, in order, each with exactly the text\'s bytes, where "<LF>" stands '
      'for the byte 0x0A and "<0x01>" for the byte 0x01')
def ring_per_text(rworld):
    w = rworld
    ring = _ring(w)
    assert len(ring) == len(w.nat_texts), f"{len(ring)} events for {len(w.nat_texts)} texts"
    for e, t in zip(ring, w.nat_texts):
        assert e[1] == EV_LOG and e[5] == t, f"event {e!r} for {t!r}"
    w.nat_ring_on = ring


@then("those events are the same as the native log path pushes when compiled with raw diagnostics off")
def same_as_off(rworld):
    w = rworld
    off = lib(False)
    assert off.harness_reset() == 0
    off.harness_ring_open_stopped()
    off.harness_set_clock(1000)
    off.lp_open(KEY.encode())
    for t in w.nat_texts:
        off.harness_log(7, t)
    probe = type(w)(nat=off)
    assert _ring(probe) == w.nat_ring_on


@then("the raw file holds one record line per text")
def record_per_text(rworld):
    assert len(_records(rworld)) == len(rworld.nat_texts)


@then(parsers.re(r"the raw file's first line is a counter line at (?P<ms>\d+) ms"))
def first_counter(rworld, ms):
    lines = _lines(rworld)
    assert lines, "the raw file is empty"
    first = lines[0]
    m = COUNTER.match(first)
    assert m and int(m.group(1)) == int(ms), f"first line {first!r}"


@then("between 1000 and 2600 ms the only other counter line is at 2200 ms")
def counters_1000_2600(rworld):
    assert _counter_times(rworld) == [1000, 2200]


@then("counter lines at 2700 ms and 2800 ms are written")
def counters_end_stop(rworld):
    assert _counter_times(rworld) == [1000, 2200, 2700, 2800]


@then(parsers.re(r"no counter line is written at (?P<ms>\d+) ms"))
def no_counter_at(rworld, ms):
    assert int(ms) not in _counter_times(rworld)


@then(parsers.re(r"exactly one counter line is written at (?P<ms>\d+) ms"))
def one_counter_at(rworld, ms):
    assert _counter_times(rworld).count(int(ms)) == 1, f"counter lines at {_counter_times(rworld)}"


@then(parsers.re(r'the raw file\'s last line is "(?P<line>.*)"'))
def last_line(rworld, line):
    lines = _lines(rworld)
    assert lines, "the raw file is empty"
    last = lines[-1]
    assert last == line.encode(), f"last line {last!r}"


@then(parsers.re(r'the raw file\'s first line is "(?P<line>.*)"'))
def first_line(rworld, line):
    lines = _lines(rworld)
    assert lines, "the raw file is empty"
    first = lines[0]
    assert first == line.encode(), f"first line {first!r}"


@then('the raw file\'s last line is the uptime, "raw log full" and the counter fields')
def last_is_full(rworld):
    lines = _lines(rworld)
    assert lines, "the raw file is empty"
    last = lines[-1]
    m = FULL.match(last)
    assert m, f"last line {last[:200]!r}"
    assert int(m.group(1)) == rworld.nat_now, "the final line's uptime is not the current uptime"
    assert sum(1 for x in _lines(rworld) if b"raw log full" in x) == 1


@then("the raw file is no larger than 16,777,216 bytes")
def within_cap(rworld):
    assert rworld.nat.harness_fs_len() <= CAP, f"{rworld.nat.harness_fs_len()} bytes"


@then("every line before the final line is byte for byte as it was before the final line was written")
def prefix_kept(rworld):
    data, snap = _data(rworld), rworld.nat_snap
    same = data[:len(snap)] == snap
    assert same, "the file's start changed"
    tail = data[len(snap):]
    assert tail.count(b"\n") == 1 and tail.endswith(b"\n") and FULL.match(tail[:-1]), f"tail {tail[:200]!r}"


@then("the raw file is byte for byte unchanged")
def unchanged(rworld):
    same = _data(rworld) == rworld.nat_snap
    assert same, f"the file changed: {rworld.nat.harness_fs_len()} bytes, was {len(rworld.nat_snap)}"
    writes = [f for f in _fops(rworld)[rworld.nat_fops_before:] if f["type"] in (FOP_WRITE, FOP_OPEN)]
    assert not writes, f"{len(writes)} writes or opens after the final line"


@then("the raw file holds the first record and no part of the second")
def first_not_second(rworld):
    w = rworld
    recs = _records(w)
    assert len(recs) == 1, f"{len(recs)} record lines"
    expected = f"{w.nat_now} L7 ".encode() + b"\\x01" + b"." * (65_536 - 4)
    same = recs[0] == expected
    assert same, f"record line is {len(recs[0])} bytes"
    others = [x for x in _lines(w) if not RECORD.match(x) and not COUNTER.match(x)]
    assert not others, f"other lines {[x[:80] for x in others]!r}"


@then(parsers.re(r'the session-end counter line has "(?P<a>[^"]+)" and "(?P<b>[^"]+)"'))
def session_end_counts(rworld, a, b):
    lines = _lines(rworld)
    assert lines, "the raw file is empty"
    last = lines[-1]
    assert COUNTER.match(last), f"last line {last[:120]!r}"
    fields = last.split(b" ")
    assert a.encode() in fields and b.encode() in fields, f"last line {last!r}"


@then("the fake file layer recorded no write, reopen or other open after the failure")
def nothing_after_failure(rworld):
    fops = _fops(rworld)
    bad = [i for i, f in enumerate(fops)
           if (f["type"] == FOP_OPEN and f["result"] < 0) or (f["type"] == FOP_WRITE and f["result"] != f["len"])]
    assert bad, "the injected failure never happened"
    after = fops[bad[0] + 1:]
    assert all(f["type"] == FOP_CLOSE for f in after), f"after the failure: {[f['type'] for f in after]}"
    assert len(after) <= 1, f"{len(after)} closes after the failure"


@then("that record is written")
def record_written(rworld):
    recs = _records(rworld)
    assert recs and recs[-1] == f"{rworld.nat_now} L7 after restart".encode(), f"records {recs[-2:]!r}"


@then("each call returned")
def each_returned(rworld):
    w = rworld
    assert w.nat_done == 3, f"{w.nat_done} of 3 calls returned within the watchdog"
    rets = [j for j in _journal(w) if j["type"] == J_CB_RETURN]
    assert len(rets) == 3


@then("the ring holds the same 3 log events it would hold with raw writes working, and no other event")
def same_three(rworld):
    w = rworld
    failing = _ring(w)
    w.nat_saved_fops = _fops(w)
    w.nat_saved_lost = w.nat.harness_fops_lost()
    _reset(w, on=True, stopped=True)
    _open(w, 1000)
    for t in w.nat_texts:
        w.nat.harness_log(7, t)
    working = _ring(w)
    assert len(working) == 3 and all(e[1] == EV_LOG for e in working)
    assert failing == working, f"failing {failing!r} working {working!r}"


@then("at no raw-file operation did the calling thread hold any mutex but the raw-file module's own")
def only_own_mutex(rworld):
    w = rworld
    fops = getattr(w, "nat_saved_fops", None) or _fops(w)
    assert getattr(w, "nat_saved_lost", 0) == 0
    assert fops, "no raw-file operation was recorded"
    bad = [f for f in fops if f["held_n"] != 0 and not f["held_only_raw"]]
    assert not bad, f"{len(bad)} raw-file operations held another mutex"
