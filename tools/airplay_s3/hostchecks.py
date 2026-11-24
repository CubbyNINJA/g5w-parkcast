"""Checks of the receiver's build outputs on this PC (AIR-042..047, AIR-055, AIR-063, AIR-068; S3_RECEIVER.md).

Readers used by the @host scenarios of specs/features/14_receiver_tools.feature (and the @offline signing-file
check): git tracking and ignore state, the APK's entries, native libraries (NEEDED entries and ARM CPU
architecture read straight from the ELF, the way `readelf -d` / `readelf -A` show them), printable strings,
the CMake source list, `bind()` calls in the patched UxPlay source, the NDK API 19 platform libraries, the
SDK install log, APK signatures, JUnit totals, JNI class references, the host callback table, the APK's
permissions (`aapt dump permissions`), and the static check that raw diagnostics have no runtime switch
(AIR-084(c): exported entry points against the design's JNI contract, and the app's run-time reads). Nothing
here downloads or builds anything.

Usage: python -m airplay_s3.hostchecks apk APK     (entries, signature, permissions, NEEDED, arch)
       python -m airplay_s3.hostchecks binds DIR   (bind calls and any wildcard address they pass)
"""

from __future__ import annotations

import json
import os
import re
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath

ANDROID_ROOT = Path(os.environ.get("PARKCAST_ANDROID_ROOT", "C:\\Android"))
NDK_VERSION = "25.2.9519653"  # r25c
WILDCARDS = ("INADDR_ANY", "in6addr_any")
FORBIDDEN_PREFIXES = ("android.", "com.hkmc.", "com.mobis.")
FORBIDDEN_WORDS = ("audio", "display", "surface", "sound")
ARM_ARCH = {0: "pre-v4", 1: "v4", 2: "v4T", 3: "v5T", 4: "v5TE", 5: "v5TEJ", 6: "v6", 7: "v6KZ", 8: "v6T2",
            9: "v6K", 10: "v7", 11: "v6-M", 12: "v6S-M", 13: "v7E-M", 14: "v8"}


# ---------------------------------------------------------------------------------------------------------
# git
# ---------------------------------------------------------------------------------------------------------

def _git(repo, *args):
    return subprocess.run(["git", "-C", str(repo), *args], capture_output=True, text=True, errors="replace")


def tracked(repo, paths) -> list:
    """The paths (repo-relative, posix) that git tracks, in the order given."""
    paths = list(paths)
    if not paths:
        return []
    proc = _git(repo, "ls-files", "-z", "--", *paths)
    if proc.returncode != 0:
        raise RuntimeError(f"git ls-files failed: {proc.stderr.strip()}")
    listed = set(proc.stdout.split("\0"))
    return [p for p in paths if p in listed]


def ignored(repo, paths) -> list:
    """The paths that .gitignore rules ignore, in the order given (`git check-ignore --no-index`)."""
    paths = list(paths)
    if not paths:
        return []
    proc = subprocess.run(["git", "-C", str(repo), "check-ignore", "--no-index", "-z", "--stdin"],
                          input="\0".join(paths) + "\0", capture_output=True, text=True, errors="replace")
    if proc.returncode not in (0, 1):
        raise RuntimeError(f"git check-ignore failed: {proc.stderr.strip()}")
    listed = set(proc.stdout.split("\0"))
    return [p for p in paths if p in listed]


# ---------------------------------------------------------------------------------------------------------
# APK
# ---------------------------------------------------------------------------------------------------------

def apk_entries(apk) -> list:
    with zipfile.ZipFile(apk) as zf:
        return zf.namelist()


def extract_so(apk, out) -> list:
    """Write every `.so` entry of the APK under `out`, keeping its path; return the written files."""
    out = Path(out)
    files = []
    with zipfile.ZipFile(apk) as zf:
        for name in zf.namelist():
            if not name.endswith(".so"):
                continue
            p = PurePosixPath(name)
            if p.is_absolute() or ".." in p.parts:
                raise ValueError(f"APK entry {name!r} has an unsafe path")
            dest = out.joinpath(*p.parts)
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(zf.read(name))
            files.append(dest)
    return files


def apk_signed(apk) -> bool:
    """True when the APK has a v1 signature (META-INF manifest plus signature block) or a v2+ signing block."""
    with zipfile.ZipFile(apk) as zf:
        names = zf.namelist()
    v1 = "META-INF/MANIFEST.MF" in names and any(
        re.fullmatch(r"META-INF/[^/]+\.(RSA|DSA|EC)", n) for n in names)
    data = Path(apk).read_bytes()
    eocd = data.rfind(b"PK\x05\x06")
    v2 = False
    if eocd >= 0:
        cd_offset = struct.unpack_from("<I", data, eocd + 16)[0]
        v2 = cd_offset >= 24 and data[cd_offset - 16:cd_offset] == b"APK Sig Block 42"
    return v1 or v2


def _aapt():
    found = sorted((ANDROID_ROOT / "build-tools").glob("*/aapt.exe")) + sorted((ANDROID_ROOT / "build-tools").glob("*/aapt"))
    if not found:
        raise FileNotFoundError(f"no aapt under {ANDROID_ROOT / 'build-tools'}")
    return found[-1]


def aapt_permissions(apk) -> list:
    """The uses-permission names that `aapt dump permissions` prints, in its order."""
    proc = subprocess.run([str(_aapt()), "dump", "permissions", str(apk)], capture_output=True, text=True,
                          errors="replace")
    if proc.returncode != 0:
        raise RuntimeError(f"aapt dump permissions failed: {proc.stderr.strip()}")
    out = []
    for line in proc.stdout.splitlines():
        if not line.startswith("uses-permission"):
            continue
        m = re.search(r"name='([^']+)'", line) or re.match(r"uses-permission(?:-sdk-23)?:\s*(\S+)", line)
        if m:
            out.append(m.group(1))
    return out


# ---------------------------------------------------------------------------------------------------------
# ELF
# ---------------------------------------------------------------------------------------------------------

def _elf_sections(data: bytes):
    if data[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    is64, end = data[4] == 2, "<" if data[5] == 1 else ">"
    if is64:
        shoff, = struct.unpack_from(end + "Q", data, 0x28)
        shentsize, shnum, shstrndx = struct.unpack_from(end + "HHH", data, 0x3A)
        fmt = end + "IIQQQQIIQQ"
    else:
        shoff, = struct.unpack_from(end + "I", data, 0x20)
        shentsize, shnum, shstrndx = struct.unpack_from(end + "HHH", data, 0x2E)
        fmt = end + "IIIIIIIIII"
    raw = [struct.unpack_from(fmt, data, shoff + i * shentsize) for i in range(shnum)]
    strtab = raw[shstrndx]
    names_at = strtab[4]

    def cstr(off):
        return data[off:data.index(b"\0", off)].decode("ascii", "replace")

    secs = []
    for s in raw:
        name_off, stype, _flags, _addr, offset, size, link = s[0], s[1], s[2], s[3], s[4], s[5], s[6]
        secs.append({"name": cstr(names_at + name_off), "type": stype, "offset": offset, "size": size, "link": link})
    return secs, is64, end, cstr


def needed(so) -> list:
    """The DT_NEEDED entries of an ELF shared object, in order (what `readelf -d` lists as NEEDED)."""
    data = Path(so).read_bytes()
    secs, is64, end, cstr = _elf_sections(data)
    dyn = [s for s in secs if s["type"] == 6]  # SHT_DYNAMIC
    if not dyn:
        return []
    d = dyn[0]
    strtab = secs[d["link"]]["offset"]
    fmt, step = (end + "qQ", 16) if is64 else (end + "iI", 8)
    out = []
    for off in range(d["offset"], d["offset"] + d["size"], step):
        tag, val = struct.unpack_from(fmt, data, off)
        if tag == 0:
            break
        if tag == 1:  # DT_NEEDED
            out.append(cstr(strtab + val))
    return out


def _uleb(data, i):
    result = shift = 0
    while True:
        b = data[i]
        i += 1
        result |= (b & 0x7F) << shift
        shift += 7
        if not b & 0x80:
            return result, i


def cpu_arch(so) -> str | None:
    """Tag_CPU_arch from the `.ARM.attributes` section (what `readelf -A` prints as CPU_arch), e.g. "v7"."""
    data = Path(so).read_bytes()
    secs, _is64, end, _cstr = _elf_sections(data)
    attr = [s for s in secs if s["name"] == ".ARM.attributes"]
    if not attr:
        return None
    blob = data[attr[0]["offset"]:attr[0]["offset"] + attr[0]["size"]]
    if not blob or blob[0:1] != b"A":
        return None
    i = 1
    while i < len(blob):
        sub_len, = struct.unpack_from(end + "I", blob, i)
        vend_end = blob.index(b"\0", i + 4)
        vendor = blob[i + 4:vend_end]
        j, sub_end = vend_end + 1, i + sub_len
        while vendor == b"aeabi" and j < sub_end:
            tag, size = blob[j], struct.unpack_from(end + "I", blob, j + 1)[0]
            k, tag_end = j + 5, j + size
            if tag != 1:  # only file-scope attributes
                j = tag_end
                continue
            while k < tag_end:
                t, k = _uleb(blob, k)
                if t in (4, 5, 67) or (t > 32 and t % 2 == 1 and t != 65):
                    k = blob.index(b"\0", k) + 1
                elif t == 32:
                    _v, k = _uleb(blob, k)
                    k = blob.index(b"\0", k) + 1
                else:
                    v, k = _uleb(blob, k)
                    if t == 6:
                        return ARM_ARCH.get(v, str(v))
            j = tag_end
        i = sub_end
    return None


def strings(path, minimum: int = 4) -> list:
    """Printable ASCII runs of at least `minimum` characters (what `strings` prints)."""
    data = Path(path).read_bytes()
    return [m.group(0).decode("ascii") for m in re.finditer(rb"[\x20-\x7e]{%d,}" % minimum, data)]


def api19_libs() -> set:
    """Names of the platform libraries NDK r25c's sysroot exposes for arm-linux-androideabi API 19."""
    ndk = Path(os.environ.get("ANDROID_NDK_HOME") or ANDROID_ROOT / "ndk" / NDK_VERSION)
    found = ndk.glob("toolchains/llvm/prebuilt/*/sysroot/usr/lib/arm-linux-androideabi/19/*.so")
    return {p.name for p in found}


# ---------------------------------------------------------------------------------------------------------
# CMake and the patched UxPlay source
# ---------------------------------------------------------------------------------------------------------

def cmake_sources(repo):
    """Source files of every compile_commands.json under receiver/app/.cxx, or None when there is none."""
    cxx = Path(repo) / "receiver" / "app" / ".cxx"
    dbs = sorted(cxx.rglob("compile_commands.json")) if cxx.exists() else []
    if not dbs:
        return None
    out = set()
    for db in dbs:
        for entry in json.loads(db.read_text(encoding="utf-8")):
            f = Path(entry["file"])
            if not f.is_absolute():
                f = Path(entry.get("directory", "")) / f
            out.add(str(f).replace("\\", "/"))
    return sorted(out)


_COMMENT = re.compile(r"/\*.*?\*/|//[^\n]*", re.S)


def _blank_comments(text):
    return _COMMENT.sub(lambda m: re.sub(r"[^\n]", " ", m.group(0)), text)


def _call_args(text, open_paren):
    depth, i = 0, open_paren
    while i < len(text):
        c = text[i]
        depth += c == "("
        depth -= c == ")"
        if depth == 0:
            break
        i += 1
    inner = text[open_paren + 1:i]
    args, depth, cur = [], 0, ""
    for c in inner:
        if c == "," and depth == 0:
            args.append(cur.strip())
            cur = ""
            continue
        depth += c in "([{"
        depth -= c in ")]}"
        cur += c
    args.append(cur.strip())
    return args, i


@dataclass
class BindCall:
    file: str
    line: int
    text: str
    wildcards: set


def bind_calls(tree) -> list:
    """Every `bind(` call in the C/C++ sources under `tree`. `wildcards` names the wildcard addresses the call
    can pass: one written in its address argument, or assigned earlier in the same function (back to the
    previous line that starts with `}`) in a statement that names the address variable or a variable it
    points to."""
    tree = Path(tree)
    out = []
    for f in sorted(p for p in tree.rglob("*") if p.suffix in (".c", ".cc", ".cpp") and p.is_file()):
        text = _blank_comments(f.read_text(encoding="utf-8", errors="replace"))
        for m in re.finditer(r"(?<![\w.>])bind\s*\(", text):
            args, close = _call_args(text, m.end() - 1)
            if len(args) < 2:
                continue
            addr = args[1]
            names = set(re.findall(r"[A-Za-z_]\w*", addr)) - {"struct", "sockaddr", "sockaddr_in", "sockaddr_in6"}
            start = text.rfind("\n}", 0, m.start())
            scope = text[start + 1 if start >= 0 else 0:m.start()]
            statements = re.split(r"[;{}]", scope)
            for st in statements:  # one level of aliasing: `T *p = (T *)&saddr;`
                for n in list(names):
                    if re.search(rf"\b{n}\s*=", st):
                        names |= set(re.findall(r"&\s*([A-Za-z_]\w*)", st))
            found = {w for w in WILDCARDS if re.search(rf"\b{w}\b", addr)}
            for st in statements:
                if any(re.search(rf"\b{n}\b", st) for n in names):
                    found |= {w for w in WILDCARDS if re.search(rf"\b{w}\b", st)}
            line = text.count("\n", 0, m.start()) + 1
            out.append(BindCall(f.relative_to(tree).as_posix(), line, " ".join(text[m.start():close + 1].split()),
                                found))
    return out


# ---------------------------------------------------------------------------------------------------------
# SDK install log (AIR-045)
# ---------------------------------------------------------------------------------------------------------

def sdk_log_path():
    """The SDK install log on this PC: sdk_install's own log, else the older download log, else None."""
    for p in (ANDROID_ROOT / "sdk-install.log", ANDROID_ROOT / "dl" / "install.log"):
        if p.is_file():
            return p
    return None


def sdk_log_checked(text) -> set:
    """Package paths that the log shows a passed SHA-256 check for (`checksum ok <relpath> sha256=<hex>`)."""
    return set(re.findall(r"^checksum ok (\S+) sha256=[0-9a-f]{64}\b", text, re.M))


def sdk_packages(root) -> list:
    """Installed SDK packages: each directory under `root` holding a source.properties, as a posix relpath.
    The download cache `dl` and hidden directories are skipped."""
    root = Path(root)
    out = []

    def walk(d: Path, depth: int):
        if (d / "source.properties").is_file() and d != root:
            out.append(d.relative_to(root).as_posix())
            return
        if depth == 0:
            return
        for child in sorted(d.iterdir()):
            if child.is_dir() and not child.name.startswith(".") and not (d == root and child.name == "dl"):
                walk(child, depth - 1)

    if root.is_dir():
        walk(root, 3)
    return out


# ---------------------------------------------------------------------------------------------------------
# Signing and Gradle results
# ---------------------------------------------------------------------------------------------------------

def _keytool():
    home = os.environ.get("JAVA_HOME")
    if home:
        for name in ("keytool.exe", "keytool"):
            p = Path(home) / "bin" / name
            if p.is_file():
                return str(p)
    return "keytool"


def make_test_keystore(path, alias, storepass, keypass):
    """Create a throwaway RSA keystore. The passwords go to keytool through the environment, never argv."""
    env = dict(os.environ, PARKCAST_TEST_STOREPASS=storepass, PARKCAST_TEST_KEYPASS=keypass)
    proc = subprocess.run([_keytool(), "-genkeypair", "-keystore", str(path), "-storetype", "PKCS12",
                           "-alias", alias, "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                           "-dname", "CN=ParkCast Throwaway Test Key", "-storepass:env", "PARKCAST_TEST_STOREPASS",
                           "-keypass:env", "PARKCAST_TEST_KEYPASS", "-noprompt"],
                          env=env, capture_output=True, text=True, errors="replace")
    if proc.returncode != 0:
        raise RuntimeError(f"keytool could not create the throwaway keystore (exit {proc.returncode})")


def junit_totals(directory) -> dict:
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    d = Path(directory)
    for xml in sorted(d.glob("TEST-*.xml")) if d.exists() else []:
        suite = ET.parse(xml).getroot()
        for k in totals:
            totals[k] += int(suite.get(k, 0) or 0)
    return totals


# ---------------------------------------------------------------------------------------------------------
# Static checks (AIR-055)
# ---------------------------------------------------------------------------------------------------------

@dataclass
class ClassRef:
    file: str
    line: int
    call: str
    cls: str | None


_JNI_CALL = re.compile(r"\b(FindClass|GetMethodID|GetStaticMethodID)\s*\(")
_LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')


def _literal(arg):
    m = _LITERAL.fullmatch(arg.strip())
    return m.group(1) if m else None


def jni_class_refs(directory) -> list:
    """Class names in FindClass, GetMethodID and GetStaticMethodID arguments under `directory`.

    FindClass gives its class-name argument; the method-ID calls give each class their signature names
    (`Lpkg/Cls;`). Names are dotted. A non-literal argument gives one ref with `cls` None."""
    out = []
    for f in sorted(p for p in Path(directory).rglob("*") if p.suffix in (".c", ".cc", ".cpp", ".h") and p.is_file()):
        text = _blank_comments(f.read_text(encoding="utf-8", errors="replace"))
        for m in _JNI_CALL.finditer(text):
            args, _close = _call_args(text, m.end() - 1)
            if args and args[0] in ("env", "jenv", "e") and len(args) > 1:
                pass
            call, line, rel = m.group(1), text.count("\n", 0, m.start()) + 1, f.relative_to(directory).as_posix()
            if call == "FindClass":
                lit = _literal(args[-1]) if args else None
                out.append(ClassRef(rel, line, call, lit.replace("/", ".") if lit is not None else None))
                continue
            sig = _literal(args[-1]) if args else None
            if sig is None:
                out.append(ClassRef(rel, line, call, None))
                continue
            for cls in re.findall(r"L([\w/$]+);", sig):
                out.append(ClassRef(rel, line, call, cls.replace("/", ".")))
    return out


def forbidden_class(cls) -> bool:
    """A class under android., com.hkmc. or com.mobis., or one whose name speaks of audio or display."""
    if cls is None:
        return True
    if cls.startswith(FORBIDDEN_PREFIXES):
        return True
    lowered = cls.lower()
    return any(w in lowered for w in FORBIDDEN_WORDS)


def host_table(path) -> dict:
    """Members the host sets on a raop_callbacks_t: designated initialisers (`.member = fn`) and assignments
    (`x.member = fn;` / `x->member = fn;`). A NULL or 0 value maps to None."""
    text = _blank_comments(Path(path).read_text(encoding="utf-8", errors="replace"))
    table = {}
    for m in re.finditer(r"(?:^|[,{\s])\.(\w+)\s*=\s*([^,;}]+)", text):
        table[m.group(1)] = m.group(2).strip()
    for m in re.finditer(r"\w+\s*(?:\.|->)\s*(\w+)\s*=\s*([^;]+);", text):
        table.setdefault(m.group(1), m.group(2).strip())
    return {k: (None if v.strip("() ") in ("NULL", "0", "nullptr") else v) for k, v in table.items()}


# ---------------------------------------------------------------------------------------------------------
# No runtime switch for raw diagnostics (AIR-084(c); OD-199, OD-204)
# ---------------------------------------------------------------------------------------------------------

_NATIVE_SUFFIXES = (".c", ".cc", ".cpp", ".h")
_TEXT_SUFFIXES = (".java", ".kt", ".xml", ".properties", ".json", ".txt", ".conf", ".cfg")
_NATIVE_DECL = re.compile(r"\bstatic\s+native\s+[\w.\[\]<>]+\s+(\w+)\s*\(")
_ANY_NATIVE = re.compile(r"\bnative\b[^;{}()=]*?\b(\w+)\s*\(")
_EXPORT = re.compile(r'visibility\s*\(\s*"default"\s*\)|\bJNIEXPORT\b')
_FUNC_NAME = re.compile(r"(\w+)\s*\(")
_VISIBILITY_PUSH = re.compile(r"#\s*pragma\s+GCC\s+visibility\s+push\s*\(\s*default\s*\)")
RAW_DIAG_TERMS = re.compile(r"raw[\s_-]?diag|uxplay-raw|parkcast_raw", re.I)
RUNTIME_SETTING_APIS = re.compile(
    r"\bgetSharedPreferences\b|\bPreferenceManager\b|\bSystem\s*\.\s*getProperty\b|\bSystem\s*\.\s*getenv\b"
    r"|\bSettings\s*\.\s*(?:System|Global|Secure)\b|\bBuild\s*\.\s*TYPE\b")
PROPERTY_LITERAL = re.compile(r'"((?:ro|persist|sys|debug|vendor|service|ctl|log)\.[\w.\-]+)"')
ALLOWED_PROPERTIES = {"ro.build.product"}
NATIVE_RUNTIME_READS = re.compile(r"\bgetenv\s*\(|\bsecure_getenv\s*\(|\bproperty_get\b|__system_property_\w+")


def jni_contract_methods(design_text) -> set:
    """The native method names in the design's JNI contract: the first ```java block after "## JNI contract"."""
    head = design_text.find("## JNI contract")
    if head < 0:
        return set()
    start = design_text.find("```java", head)
    end = design_text.find("```", start + 7) if start >= 0 else -1
    if start < 0 or end < 0:
        return set()
    return set(_NATIVE_DECL.findall(_blank_comments(design_text[start + 7:end])))


def java_native_methods(java_text) -> set:
    """The names of every method declared `native` in a Java source, whatever its other modifiers."""
    return set(_ANY_NATIVE.findall(_blank_comments(java_text)))


def _native_files(src_dir):
    return sorted(p for p in Path(src_dir).rglob("*") if p.suffix in _NATIVE_SUFFIXES and p.is_file())


def native_exports(src_dir) -> list:
    """Functions the native sources mark for export (`visibility("default")` or `JNIEXPORT`), each named by the
    function it declares. A `#pragma GCC visibility push(default)` exports everything after it, so it is
    reported as itself."""
    out = set()
    for f in _native_files(src_dir):
        text = _blank_comments(f.read_text(encoding="utf-8", errors="replace"))
        for m in _EXPORT.finditer(text):
            name = _FUNC_NAME.search(text, m.end())
            if name:
                out.add(name.group(1))
        if _VISIBILITY_PUSH.search(text):
            out.add(f"#pragma GCC visibility push(default) in {f.name}")
    return sorted(out)


def registers_natives(src_dir) -> list:
    """`file:line` of each RegisterNatives call, which would add JNI entry points the contract doesn't list."""
    out = []
    for f in _native_files(src_dir):
        text = _blank_comments(f.read_text(encoding="utf-8", errors="replace"))
        for m in re.finditer(r"\bRegisterNatives\b", text):
            out.append(f"{f.name}:{text.count(chr(10), 0, m.start()) + 1}")
    return out


def cmake_hides_symbols(cmake_text) -> bool:
    """Whether every library the CMake file compiles is compiled with -fvisibility=hidden (a target option, or a
    hidden C visibility preset for the whole file). Imported, interface and alias libraries compile nothing."""
    text = re.sub(r"#[^\n]*", "", cmake_text)
    if re.search(r"\bCMAKE_C_VISIBILITY_PRESET\s+hidden\b", text):
        return True
    targets = [m.group(1) for m in re.finditer(r"\badd_library\s*\(\s*(\w+)([^)]*)\)", text)
               if not re.search(r"\b(IMPORTED|INTERFACE|ALIAS)\b", m.group(2))]
    if not targets:
        return False
    for target in targets:
        options = " ".join(m.group(1) for m in re.finditer(
            rf"\btarget_compile_options\s*\(\s*{re.escape(target)}\b([^)]*)\)", text))
        if "-fvisibility=hidden" not in options or "-fvisibility=default" in options:
            return False
    return True


def runtime_switch_problems(native_src, app_dirs, design_text, bridge_text) -> list:
    """Every way the receiver could turn raw diagnostics on at run time (AIR-084(c)), as `file:line: what`.

    Joins the no-runtime-switch checks so each finding names its file: a native export beyond the JNI contract's
    entry points (and `JNI_OnLoad`), a `RegisterNatives` call, a Java `native` method outside the contract, and
    every read `runtime_switch_reads` finds. An empty list means there is no runtime switch."""
    found = []
    contract = jni_contract_methods(design_text)
    allowed = {"Java_parkcast_receiver_NativeBridge_" + name for name in contract} | {"JNI_OnLoad"}
    # native_exports reports names only, so look each unexpected one up in the files to name where it is.
    for name in sorted(set(native_exports(native_src)) - allowed):
        hits = [f for f in _native_files(native_src)
                if re.search(rf"\b{re.escape(name)}\b", f.read_text(encoding="utf-8", errors="replace"))]
        where = ", ".join(f.as_posix() for f in hits) or Path(native_src).as_posix()
        found.append(f"{where}: native export outside the JNI contract {name!r}")
    found.extend(f"{hit}: RegisterNatives adds JNI entry points" for hit in registers_natives(native_src))
    extra = java_native_methods(bridge_text) - contract
    found.extend(f"NativeBridge.java: native method outside the JNI contract {m!r}" for m in sorted(extra))
    found.extend(runtime_switch_reads(app_dirs, native_src))
    return found


def runtime_switch_reads(app_dirs, native_src) -> list:
    """Every place the app could read a run-time switch for raw diagnostics, as `file:line: what`.

    In the app's sources (comments blanked): any mention of raw diagnostics or the raw file; a shared preference,
    Java system property, environment variable or Settings read; and any system-property key other than
    ro.build.product. In the native sources: any environment or system-property read. The native code's own
    build-time `PARKCAST_RAW_DIAG` and its raw file's name are not reads, so terms aren't checked there."""
    found = []

    def report(f, text, m, what):
        found.append(f"{f.as_posix()}:{text.count(chr(10), 0, m.start()) + 1}: {what} {m.group(0)!r}")

    for d in app_dirs:
        for f in sorted(p for p in Path(d).rglob("*") if p.is_file() and p.suffix in _TEXT_SUFFIXES):
            text = f.read_text(encoding="utf-8", errors="replace")
            code = f.suffix in (".java", ".kt")
            if code:
                text = _blank_comments(text)
            elif f.suffix == ".xml":
                text = re.sub(r"<!--.*?-->", lambda c: re.sub(r"[^\n]", " ", c.group(0)), text, flags=re.S)
            for m in RAW_DIAG_TERMS.finditer(text):
                report(f, text, m, "raw diagnostics named")
            if code:
                for m in RUNTIME_SETTING_APIS.finditer(text):
                    report(f, text, m, "runtime setting read")
                for m in PROPERTY_LITERAL.finditer(text):
                    if m.group(1) not in ALLOWED_PROPERTIES:
                        report(f, text, m, "system property key")
    for f in _native_files(native_src):
        text = _blank_comments(f.read_text(encoding="utf-8", errors="replace"))
        for m in NATIVE_RUNTIME_READS.finditer(text):
            report(f, text, m, "native runtime read")
    return found


# ---------------------------------------------------------------------------------------------------------
# Command line
# ---------------------------------------------------------------------------------------------------------

def main(argv=None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    if len(argv) != 2 or argv[0] not in ("apk", "binds"):
        print(__doc__.rsplit("Usage: ", 1)[-1].strip(), file=sys.stderr)
        return 2
    cmd, target = argv
    if cmd == "binds":
        bad = 0
        for b in bind_calls(target):
            print(f"{b.file}:{b.line}: {b.text}  wildcards={sorted(b.wildcards) or '-'}")
            bad += bool(b.wildcards)
        return 1 if bad else 0
    print("signed:", apk_signed(target))
    for e in apk_entries(target):
        print("entry:", e)
    try:
        for p in aapt_permissions(target):
            print("uses-permission:", p)
    except (OSError, RuntimeError) as exc:
        print(f"aapt: {exc}", file=sys.stderr)
    with zipfile.ZipFile(target) as zf:
        for name in zf.namelist():
            if name.endswith(".so"):
                tmp = Path(os.environ.get("TEMP", ".")) / "parkcast-hostchecks"
                for so in extract_so(target, tmp):
                    print(f"{so.name}: NEEDED {needed(so)} arch {cpu_arch(so)}")
                break
    return 0


if __name__ == "__main__":
    sys.exit(main())
