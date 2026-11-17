"""Native prep for the receiver's protocol library (AIR-042, AIR-044, AIR-055; docs/design/S3_RECEIVER.md).

CMake depends on this step: it builds only after native prep wrote `<cache>/prep.stamp`. Native prep

1. reads `receiver/native/deps.lock` and refuses a pin outside the allowed release lines (OpenSSL 3.5.x,
   libplist 2.x, OD-168), naming the dependency and the version;
2. downloads each dependency archive into `<cache>/downloads/`, checks its SHA-256 against the pin before
   anything is extracted, and deletes a mismatching download; then extracts each archive into
   `<cache>/src/<key>/` (no absolute paths, no `..`, no links);
3. fetches the pinned UxPlay commit with a shallow git fetch and checks that HEAD is that commit;
4. applies, in name order, each `receiver/native/patches/*.patch` that is tracked in the repository
   (`git apply --check` first, then `git apply`); a patch from anywhere else is never applied (OD-174), and a
   patch that does not apply fails the build naming it, leaving no UxPlay tree for CMake;
5. lists every `raop_callbacks_t` member the patched source calls without a preceding NULL check, logs
   `unguarded callbacks: a, b, ...` and fails unless the list equals `receiver/native/unguarded-callbacks.txt`.

Lock format (`deps.lock`, INI read by configparser, no interpolation, LF):

    [uxplay]
    url = https://github.com/FDH2/UxPlay.git
    commit = <40 lowercase hex>

    [dep openssl]
    name = OpenSSL
    version = 3.5.4
    url = https://.../openssl-3.5.4.tar.gz
    sha256 = <64 lowercase hex>
    archive = openssl-3.5.4.tar.gz        ; optional, defaults to the last part of url

    [dep libplist]
    name = libplist
    version = 2.6.0
    url = ...
    sha256 = ...

`[dep openssl]` and `[dep libplist]` are required; every `[dep <key>]` needs name, version, url and sha256.
`unguarded-callbacks.txt` holds one member name per line; blank lines and lines starting with `#` are skipped.

Cache layout: `downloads/<archive>` (written as `<archive>.part`, renamed once the SHA-256 matches),
`src/<key>/` (extracted into `src/<key>.tmp` first), `src/uxplay/` (fetched and patched in `src/uxplay.tmp`),
`native-prep.log`, and `prep.stamp` (written last, only on success; deleted first on every run).

Usage: python -m airplay_s3.native_prep [--lock L] [--cache C] [--repo R] [--source DIR]
       python -m airplay_s3.native_prep --list-unguarded DIR
`--source` uses a local copy of the UxPlay tree instead of fetching it; HEAD is still checked when it is a
git checkout.
"""

from __future__ import annotations

import argparse
import configparser
import hashlib
import os
import re
import shutil
import stat
import subprocess
import sys
import tarfile
import urllib.request
import zipfile
from dataclasses import dataclass, field
from pathlib import Path, PurePosixPath

PATCH_DIR = "receiver/native/patches"
CALLBACKS_FILE = "receiver/native/unguarded-callbacks.txt"
REQUIRED_DEPS = ("openssl", "libplist")
RELEASE_LINES = {
    "openssl": (re.compile(r"3\.5\.\d+"), "3.5.x"),
    "libplist": (re.compile(r"2\.\d+(?:\.\d+)?"), "2.x"),
}


class PrepError(Exception):
    """Native prep failed; the message names what failed."""


@dataclass
class Dep:
    key: str
    name: str
    version: str
    url: str
    sha256: str
    archive: str


@dataclass
class Lock:
    uxplay_url: str
    uxplay_commit: str
    deps: list = field(default_factory=list)


@dataclass
class Result:
    tree: Path
    applied: list
    log: list
    unguarded: list


# ---------------------------------------------------------------------------------------------------------
# Lock
# ---------------------------------------------------------------------------------------------------------

def parse_lock(path) -> Lock:
    cp = configparser.ConfigParser(interpolation=None)
    try:
        text = Path(path).read_text(encoding="utf-8")
    except OSError as exc:
        raise PrepError(f"cannot read the dependency pins {path}: {exc}") from exc
    try:
        cp.read_string(text, source=str(path))
    except configparser.Error as exc:
        raise PrepError(f"cannot parse the dependency pins {path}: {exc}") from exc
    if not cp.has_section("uxplay"):
        raise PrepError(f"{path}: no [uxplay] section")
    ux = cp["uxplay"]
    commit = ux.get("commit", "").strip()
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise PrepError(f"{path}: [uxplay] commit is not a 40-character lowercase hex commit")
    if not ux.get("url", "").strip():
        raise PrepError(f"{path}: [uxplay] has no url")
    deps = []
    for section in cp.sections():
        if not section.startswith("dep "):
            continue
        key = section[4:].strip()
        sec = cp[section]
        missing = [k for k in ("name", "version", "url", "sha256") if not sec.get(k, "").strip()]
        if missing:
            raise PrepError(f"{path}: [{section}] lacks {', '.join(missing)}")
        url = sec["url"].strip()
        archive = sec.get("archive", "").strip() or PurePosixPath(url.split("?", 1)[0]).name
        if not archive or "/" in archive or "\\" in archive or archive in (".", ".."):
            raise PrepError(f"{path}: [{section}] archive name {archive!r} is not a plain file name")
        deps.append(Dep(key, sec["name"].strip(), sec["version"].strip(), url, sec["sha256"].strip().lower(), archive))
    keys = [d.key for d in deps]
    absent = [k for k in REQUIRED_DEPS if k not in keys]
    if absent:
        raise PrepError(f"{path}: no pin for {', '.join(absent)}")
    return Lock(ux["url"].strip(), commit, deps)


def check_pins(lock: Lock):
    for dep in lock.deps:
        if not re.fullmatch(r"[0-9a-f]{64}", dep.sha256):
            raise PrepError(f"{dep.name} {dep.version}: the pinned SHA-256 is not 64 hex characters")
        line = RELEASE_LINES.get(dep.key)
        if line and not line[0].fullmatch(dep.version):
            raise PrepError(f"{dep.name} {dep.version} is outside the allowed {line[1]} release line")


# ---------------------------------------------------------------------------------------------------------
# Downloads and extraction
# ---------------------------------------------------------------------------------------------------------

def default_fetch(url, dest):
    with urllib.request.urlopen(url, timeout=120) as resp, open(dest, "wb") as out:
        shutil.copyfileobj(resp, out)


def _sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch_dep(dep: Dep, cache_dir, fetch=None, log=None) -> Path:
    """Download `dep` into `<cache>/downloads/` and return the archive path once its SHA-256 matches."""
    fetch = fetch or default_fetch
    downloads = Path(cache_dir) / "downloads"
    downloads.mkdir(parents=True, exist_ok=True)
    final = downloads / dep.archive
    if final.exists() and _sha256(final) == dep.sha256:
        _log(log, f"cached {dep.name} {dep.version} sha256={dep.sha256}")
        return final
    final.unlink(missing_ok=True)
    part = downloads / (dep.archive + ".part")
    part.unlink(missing_ok=True)
    try:
        fetch(dep.url, part)
    except Exception as exc:  # noqa: BLE001 - any download failure is reported the same way
        part.unlink(missing_ok=True)
        raise PrepError(f"download of {dep.name} {dep.version} from {dep.url} failed: {exc}") from exc
    got = _sha256(part)
    if got != dep.sha256:
        part.unlink(missing_ok=True)
        raise PrepError(f"SHA-256 mismatch for {dep.name} {dep.version}: pinned {dep.sha256}, got {got}")
    part.replace(final)
    _log(log, f"checksum ok {dep.name} {dep.version} sha256={got}")
    return final


def _safe_member(name: str) -> PurePosixPath:
    p = PurePosixPath(name.replace("\\", "/"))
    if p.is_absolute() or ".." in p.parts or (p.parts and ":" in p.parts[0]):
        raise PrepError(f"archive member {name!r} would land outside the extraction directory")
    return p


def extract(archive: Path, dest: Path):
    """Extract `archive` (tar.* or zip) into `dest`, stripping its single top-level directory if it has one."""
    dest.mkdir(parents=True)
    members = []
    if zipfile.is_zipfile(archive):
        with zipfile.ZipFile(archive) as zf:
            for info in zf.infolist():
                p = _safe_member(info.filename)
                if (info.external_attr >> 16) & 0o170000 == 0o120000:
                    raise PrepError(f"archive member {info.filename!r} is a link")
                members.append((p, info.is_dir(), lambda i=info, z=zf: z.read(i)))
            _write_members(members, dest)
        return
    with tarfile.open(archive) as tf:
        for m in tf.getmembers():
            p = _safe_member(m.name)
            if m.issym() or m.islnk() or m.isdev():
                raise PrepError(f"archive member {m.name!r} is a link or device")
            if m.isdir():
                members.append((p, True, None))
            elif m.isfile():
                members.append((p, False, lambda mm=m, t=tf: t.extractfile(mm).read()))
        _write_members(members, dest)


def _write_members(members, dest: Path):
    tops = {p.parts[0] for p, _d, _r in members if p.parts}
    strip = len(tops) == 1 and any(len(p.parts) > 1 for p, _d, _r in members)
    for p, is_dir, read in members:
        parts = p.parts[1:] if strip else p.parts
        if not parts:
            continue
        target = dest.joinpath(*parts)
        if is_dir:
            target.mkdir(parents=True, exist_ok=True)
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(read())


def _clear_readonly(func, path, _exc):
    # Git marks its object files read-only, and Windows refuses to delete a read-only file.
    os.chmod(path, stat.S_IWRITE)
    func(path)


def _rmtree(path: Path):
    if path.exists():
        shutil.rmtree(path, onerror=_clear_readonly)


# ---------------------------------------------------------------------------------------------------------
# UxPlay
# ---------------------------------------------------------------------------------------------------------

def _git_env(tree: Path):
    env = dict(os.environ)
    env["GIT_CEILING_DIRECTORIES"] = str(Path(tree).resolve().parent)
    return env


def _git(args, cwd, env=None):
    return subprocess.run(["git", *args], cwd=cwd, env=env, capture_output=True, text=True, errors="replace")


def default_fetch_uxplay(url, commit, dest) -> str:
    """Shallow-fetch `commit` from `url` into `dest`; return the checked-out HEAD."""
    dest = Path(dest)
    dest.mkdir(parents=True)
    env = _git_env(dest)
    for args in (["init", "-q"], ["fetch", "-q", "--depth", "1", url, commit], ["checkout", "-q", "FETCH_HEAD"]):
        proc = _git(args, dest, env)
        if proc.returncode != 0:
            raise PrepError(f"git {args[0]} of UxPlay {commit} failed: {proc.stderr.strip()}")
    return _git(["rev-parse", "HEAD"], dest, env).stdout.strip()


def default_tracked(repo_root):
    def tracked(rel):
        proc = _git(["ls-files", "--error-unmatch", "--", rel], repo_root)
        return proc.returncode == 0
    return tracked


def repo_patches(repo_root, tracked):
    """The repository's patch files, in name order, as repo-relative posix paths; all must be tracked."""
    root = Path(repo_root).resolve()
    pdir = root / PATCH_DIR
    if not pdir.is_dir():
        raise PrepError(f"no patch directory {PATCH_DIR} in the repository")
    out = []
    for p in sorted(pdir.glob("*.patch"), key=lambda x: x.name):
        real = p.resolve()
        if not real.is_relative_to(root) or not p.is_file():
            raise PrepError(f"patch {p.name} resolves outside the repository")
        rel = f"{PATCH_DIR}/{p.name}"
        if not tracked(rel):
            raise PrepError(f"patch {rel} is not tracked in the repository")
        out.append(rel)
    return out


def apply_patch(tree: Path, patch: Path, rel: str):
    env = _git_env(tree)
    for args in (["apply", "--check", str(patch)], ["apply", str(patch)]):
        proc = _git(args, tree, env)
        if proc.returncode != 0:
            raise PrepError(f"patch {rel} does not apply cleanly to UxPlay: {proc.stderr.strip()}")


# ---------------------------------------------------------------------------------------------------------
# Unguarded callbacks (AIR-055)
# ---------------------------------------------------------------------------------------------------------

_STRUCT = re.compile(r"struct\s+raop_callbacks_s\s*\{")
_MEMBER = re.compile(r"\(\s*\*\s*(\w+)\s*\)\s*\(")
_REF = re.compile(r"callbacks\s*(?:\.|->)\s*(\w+)(\s*\()?")
_COMMENT = re.compile(r"/\*.*?\*/|//[^\n]*", re.S)


def _strip_comments(text):
    return _COMMENT.sub(lambda m: re.sub(r"[^\n]", " ", m.group(0)), text)


def _blank_asserts(text):
    """Blank the argument of every `assert(...)`, keeping line numbers."""
    out, pos = [], 0
    for m in re.finditer(r"\bassert\s*\(", text):
        if m.start() < pos:
            continue
        depth, i = 0, m.end() - 1
        while i < len(text):
            depth += {"(": 1, ")": -1}.get(text[i], 0)
            if depth == 0:
                break
            i += 1
        out.append(text[pos:m.end()])
        out.append(re.sub(r"[^\n]", " ", text[m.end():i]))
        pos = i
    out.append(text[pos:])
    return "".join(out)


def callback_members(tree) -> list:
    """The function-pointer members of `struct raop_callbacks_s`, in declaration order."""
    for h in sorted(Path(tree).rglob("*.h")):
        text = _strip_comments(h.read_text(encoding="utf-8", errors="replace"))
        m = _STRUCT.search(text)
        if not m:
            continue
        depth, i = 1, m.end()
        while depth and i < len(text):
            depth += {"{": 1, "}": -1}.get(text[i], 0)
            i += 1
        return _MEMBER.findall(text[m.end():i])
    raise PrepError("no struct raop_callbacks_s in the UxPlay source")


def unguarded_callbacks(tree) -> list:
    """Members of raop_callbacks_t that some call site invokes without an earlier, non-call reference to the
    same member in the same function (an `if (x->callbacks.m)` or `!= NULL` test). The function runs back to
    the previous line that starts with `}`. Both `.c` and `.h` files are read, because UxPlay includes
    handler code from headers (raop_handlers.h into raop.c). A test inside `assert(...)` is no guard: release
    builds define NDEBUG and drop it. Files that declare `httpd_callbacks_t callbacks;` are skipped: their
    `callbacks` is another struct with some of the same member names."""
    members = callback_members(tree)
    unguarded = set()
    for c in sorted(p for p in Path(tree).rglob("*") if p.suffix in (".c", ".h") and p.is_file()):
        text = _blank_asserts(_strip_comments(c.read_text(encoding="utf-8", errors="replace")))
        if re.search(r"\bhttpd_callbacks_t\s+callbacks\s*;", text):
            continue
        for m in _REF.finditer(text):
            name, call = m.group(1), m.group(2)
            if not call or name not in members:
                continue
            start = text.rfind("\n}", 0, m.start())
            scope = text[start + 1 if start >= 0 else 0:m.start()]
            guarded = any(r.group(1) == name and not r.group(2) for r in _REF.finditer(scope))
            if not guarded:
                unguarded.add(name)
    return [m for m in members if m in unguarded]


def read_callback_list(path) -> list:
    lines = Path(path).read_text(encoding="utf-8").splitlines()
    return [ln.strip() for ln in lines if ln.strip() and not ln.strip().startswith("#")]


# ---------------------------------------------------------------------------------------------------------
# Prepare
# ---------------------------------------------------------------------------------------------------------

def _log(log, line):
    if log is not None:
        log.append(line)


def prepare(lock_path, cache_dir, repo_root, *, fetch=None, fetch_uxplay=None, tracked=None, log=None,
            source=None) -> Result:
    # Absolute paths: patches are applied with the UxPlay tree as git's working directory.
    cache = Path(cache_dir).resolve()
    repo_root = Path(repo_root).resolve()
    lines = log if log is not None else []
    cache.mkdir(parents=True, exist_ok=True)
    stamp = cache / "prep.stamp"
    stamp.unlink(missing_ok=True)
    src = cache / "src"
    uxplay = src / "uxplay"
    work = src / "uxplay.tmp"
    try:
        lock = parse_lock(lock_path)
        check_pins(lock)
        archives = [(dep, fetch_dep(dep, cache, fetch, lines)) for dep in lock.deps]
        for dep, archive in archives:
            out, tmp = src / dep.key, src / (dep.key + ".tmp")
            _rmtree(tmp)
            extract(archive, tmp)
            _rmtree(out)
            tmp.rename(out)
            _log(lines, f"extracted {dep.name} {dep.version} into src/{dep.key}")

        _rmtree(uxplay)
        _rmtree(work)
        if source is not None:
            shutil.copytree(source, work, ignore=shutil.ignore_patterns(".git"))
            head = _git(["rev-parse", "HEAD"], source, _git_env(source)).stdout.strip() or lock.uxplay_commit
        else:
            head = (fetch_uxplay or default_fetch_uxplay)(lock.uxplay_url, lock.uxplay_commit, work)
        if head != lock.uxplay_commit:
            raise PrepError(f"UxPlay HEAD is {head}, not the pinned commit {lock.uxplay_commit}")
        _log(lines, f"uxplay: checked out {head}")

        applied = []
        for rel in repo_patches(repo_root, tracked or default_tracked(repo_root)):
            apply_patch(work, repo_root / rel, rel)
            applied.append(rel)
            _log(lines, f"patch applied: {rel}")

        found = unguarded_callbacks(work)
        _log(lines, "unguarded callbacks: " + ", ".join(found))
        listed_path = repo_root / CALLBACKS_FILE
        if not listed_path.is_file():
            raise PrepError(f"{CALLBACKS_FILE} is missing")
        listed = read_callback_list(listed_path)
        if sorted(listed) != sorted(found):
            extra = sorted(set(found) - set(listed))
            gone = sorted(set(listed) - set(found))
            raise PrepError(f"unguarded callbacks differ from {CALLBACKS_FILE}: not listed {extra}, listed but "
                            f"guarded or absent {gone}")
        work.rename(uxplay)
        stamp.write_text(f"uxplay {head}\n", encoding="utf-8", newline="\n")
        _log(lines, "native prep ok")
        return Result(uxplay, applied, lines, found)
    except PrepError as exc:
        _log(lines, f"native prep failed: {exc}")
        _rmtree(work)
        _rmtree(uxplay)
        raise
    finally:
        (cache / "native-prep.log").write_text("".join(f"{ln}\n" for ln in lines), encoding="utf-8", newline="\n")


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(prog="native_prep", description=__doc__.split("\n", 1)[0])
    repo_default = Path(__file__).resolve().parents[2]
    ap.add_argument("--repo", type=Path, default=repo_default)
    ap.add_argument("--lock", type=Path)
    ap.add_argument("--cache", type=Path)
    ap.add_argument("--source", type=Path, help="local UxPlay tree instead of a git fetch")
    ap.add_argument("--list-unguarded", type=Path, metavar="DIR", help="print the unguarded callbacks of DIR")
    args = ap.parse_args(argv)
    try:
        if args.list_unguarded:
            print("unguarded callbacks: " + ", ".join(unguarded_callbacks(args.list_unguarded)))
            return 0
        lock = args.lock or args.repo / "receiver" / "native" / "deps.lock"
        cache = args.cache or args.repo / "receiver" / "native" / ".cache"
        result = prepare(lock, cache, args.repo, source=args.source)
    except PrepError as exc:
        print(f"native prep failed: {exc}", file=sys.stderr)
        return 1
    for line in result.log:
        print(line)
    return 0


if __name__ == "__main__":
    sys.exit(main())
