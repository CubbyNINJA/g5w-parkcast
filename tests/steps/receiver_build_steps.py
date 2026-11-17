"""S3 tool step definitions: build area of 14_receiver_tools.feature (docs/design/S3_RECEIVER.md).

@offline scenarios run `native_prep`, `sdk_install` and `hostchecks` on tmp fixtures only: tiny tar.gz and
zip archives served by a fake download function, a fixture copy of the UxPlay source tree with a known
`raop_callbacks_s`, a fixture repository holding the patches, and a fake SDK root. Nothing is downloaded.
The one exception is the signing scenario, which runs read-only `git ls-files` and `git check-ignore` on
this repository; its failure twin runs them on a tmp scratch repository.

@host scenarios read the real artifacts of a build on this PC (the APK, the native-prep log under
`receiver/native/.cache/`, the patched UxPlay tree, `compile_commands.json`) and skip, naming the missing
artifact, until the app has been built. The Gradle scenarios run `gradlew.bat` in a tmp `git clone` of this
repository, so they test committed state. Test passwords are obviously fake and are never printed.

State lives on `rworld` in fields named `build_*`. The tool modules are imported inside the steps, so a
scenario whose module is missing fails at that step with ModuleNotFoundError.
"""

from __future__ import annotations

import difflib
import hashlib
import io
import os
import re
import shutil
import subprocess
import tarfile
import zipfile
from pathlib import Path

import pytest
from pytest_bdd import given, parsers, then, when

from steps.world import COMMIT

REPO = Path(__file__).resolve().parents[2]
NATIVE = REPO / "receiver" / "native"
CACHE = NATIVE / ".cache"
PREP_LOG = CACHE / "native-prep.log"
UXPLAY_TREE = CACHE / "src" / "uxplay"
APK_DIR = REPO / "receiver" / "app" / "build" / "outputs" / "apk" / "release"
PATCH_DIR = "receiver/native/patches"
FAKE_URL = "https://example.invalid/"
# Obviously fake test passwords (AIR-047). Never print them, never put them in an assertion message.
FAKE_STORE_PASS = "test-store-pass-not-real"
FAKE_KEY_PASS = "test-key-pass-not-real"


# ---------------------------------------------------------------------------------------------------------
# Fixture helpers
# ---------------------------------------------------------------------------------------------------------

def _write(path: Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(text.encode("utf-8"))  # LF only
    return path


def _targz(top: str, files: dict) -> bytes:
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w:gz") as tar:
        for name, text in files.items():
            data = text.encode("utf-8")
            info = tarfile.TarInfo(f"{top}/{name}")
            info.size = len(data)
            info.mtime = 0
            tar.addfile(info, io.BytesIO(data))
    return buf.getvalue()


def _zip(top: str, files: dict) -> bytes:
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        for name, text in files.items():
            zf.writestr(f"{top}/{name}", text)
    return buf.getvalue()


def _sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _patch(path: str, old: str, new: str) -> str:
    diff = difflib.unified_diff(old.splitlines(keepends=True), new.splitlines(keepends=True),
                                fromfile=f"a/{path}", tofile=f"b/{path}")
    return "".join(diff)


UXPLAY_FILES = {
    "lib/raop.h": (
        "typedef struct raop_callbacks_s {\n"
        "    void *cls;\n"
        "    void  (*audio_process)(void *cls, void *data);\n"
        "    void  (*video_process)(void *cls, void *data);\n"
        "    void  (*conn_init)(void *cls);\n"
        "    void  (*report_client_request)(void *cls, char *name);\n"
        "} raop_callbacks_t;\n"),
    "lib/raop.c": (
        "#include \"raop.h\"\n"
        "\n"
        "static void\n"
        "on_audio(raop_t *raop, void *data)\n"
        "{\n"
        "    raop->callbacks.audio_process(raop->callbacks.cls, data);\n"
        "}\n"
        "\n"
        "static void\n"
        "on_video(raop_t *raop, void *data)\n"
        "{\n"
        "    raop->callbacks.video_process(raop->callbacks.cls, data);\n"
        "}\n"
        "\n"
        "static void\n"
        "on_init(raop_t *raop)\n"
        "{\n"
        "    if (raop->callbacks.conn_init) {\n"
        "        raop->callbacks.conn_init(raop->callbacks.cls);\n"
        "    }\n"
        "}\n"
        "\n"
        "static void\n"
        "on_request(raop_t *raop, char *name)\n"
        "{\n"
        "    if (raop->callbacks.report_client_request != NULL)\n"
        "        raop->callbacks.report_client_request(raop->callbacks.cls, name);\n"
        "}\n"),
    "lib/dnssd.c": (
        "#include <dlfcn.h>\n"
        "\n"
        "#define DNSSD_LIB \"libdns_sd.so\"\n"
        "\n"
        "void *dnssd_open(void) { return dlopen(DNSSD_LIB, RTLD_NOW); }\n"),
    "lib/netutils.c": (
        "#include <netinet/in.h>\n"
        "\n"
        "int netutils_init(struct sockaddr_in *sinptr, unsigned int addr)\n"
        "{\n"
        "    sinptr->sin_addr.s_addr = INADDR_ANY;\n"
        "    return 0;\n"
        "}\n"),
}
UNGUARDED = ["audio_process", "video_process"]
GOOD_PATCHES = {
    "0001-dnssd-load-libmdnssd.patch": ("lib/dnssd.c", "\"libdns_sd.so\"", "\"libmdnssd.so\""),
    "0002-bind-passed-address.patch": ("lib/netutils.c", "s_addr = INADDR_ANY;", "s_addr = addr;"),
}
DECOY = "PARKCAST-DECOY-PATCH-APPLIED"


def _good_patch(name):
    path, old, new = GOOD_PATCHES[name]
    return _patch(path, UXPLAY_FILES[path], UXPLAY_FILES[path].replace(old, new))


def _native_fixture(rworld, *, deps=None, lock_override=None):
    """Build the fixture repository, download mirror, UxPlay source and lock for one native-prep run."""
    base = rworld.tmp / "native"
    repo = base / "repo"
    src = base / "uxplay-fixture"
    for name, text in UXPLAY_FILES.items():
        _write(src / name, text)
    # A patch inside the UxPlay tree itself: native prep must never apply it.
    _write(src / "patches" / "0009-upstream.patch",
           _patch("lib/raop.c", UXPLAY_FILES["lib/raop.c"], UXPLAY_FILES["lib/raop.c"] + f"/* {DECOY} */\n"))
    # A patch outside the repository: it must never be applied either.
    _write(base / "outside" / "0003-outside.patch",
           _patch("lib/raop.c", UXPLAY_FILES["lib/raop.c"], UXPLAY_FILES["lib/raop.c"] + f"/* {DECOY} */\n"))
    for name in GOOD_PATCHES:
        _write(repo / PATCH_DIR / name, _good_patch(name))
    _write(repo / "receiver" / "native" / "unguarded-callbacks.txt",
           "# raop_callbacks_t members UxPlay calls without a NULL check\n" + "".join(f"{n}\n" for n in UNGUARDED))

    deps = deps or [("openssl", "OpenSSL", "3.5.4"), ("libplist", "libplist", "2.6.0")]
    mirror = {}
    lines = ["[uxplay]", f"url = {FAKE_URL}UxPlay.git", f"commit = {COMMIT}", ""]
    for key, name, version in deps:
        archive = f"{key}-{version}.tar.gz"
        data = _targz(f"{key}-{version}", {f"MARKER-{key}": f"{name} {version} fixture\n"})
        mirror[FAKE_URL + archive] = data
        sha = (lock_override or {}).get(key, _sha(data))
        lines += [f"[dep {key}]", f"name = {name}", f"version = {version}", f"url = {FAKE_URL}{archive}",
                  f"archive = {archive}", f"sha256 = {sha}", ""]
    lock = _write(repo / "receiver" / "native" / "deps.lock", "\n".join(lines))

    rworld.build_base = base
    rworld.build_repo = repo
    rworld.build_lock = lock
    rworld.build_cache = repo / "receiver" / "native" / ".cache"
    rworld.build_fetched = []
    rworld.build_uxplay_fetched = []
    rworld.build_tracked = {f"{PATCH_DIR}/{n}" for n in GOOD_PATCHES} | {
        "receiver/native/deps.lock", "receiver/native/unguarded-callbacks.txt"}

    def fetch(url, dest):
        rworld.build_fetched.append(url)
        Path(dest).write_bytes(mirror[url])

    def fetch_uxplay(url, commit, dest):
        rworld.build_uxplay_fetched.append((url, commit))
        shutil.copytree(src, dest)
        return COMMIT

    rworld.build_fetch = fetch
    rworld.build_fetch_uxplay = fetch_uxplay


def _prep(rworld):
    from airplay_s3 import native_prep
    rworld.build_error = None
    rworld.build_result = None
    try:
        rworld.build_result = native_prep.prepare(
            rworld.build_lock, rworld.build_cache, rworld.build_repo,
            fetch=rworld.build_fetch, fetch_uxplay=rworld.build_fetch_uxplay,
            tracked=lambda rel: rel in rworld.build_tracked)
    except native_prep.PrepError as exc:
        rworld.build_error = exc


def _files(root: Path):
    return [p for p in root.rglob("*") if p.is_file()] if root.exists() else []


def _tree_hashes(root: Path):
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in _files(root)}


def _error(rworld):
    err = rworld.build_error
    assert err is not None, "the build did not fail"
    return str(err)


# ---------------------------------------------------------------------------------------------------------
# @offline: patches (AIR-042)
# ---------------------------------------------------------------------------------------------------------

@given("a clean checkout with the receiver's patch files in the repository")
def _clean_checkout_patches(rworld):
    _native_fixture(rworld)


@when("the native build runs against a fixture copy of the UxPlay source tree")
def _native_build_fixture_tree(rworld):
    _prep(rworld)
    assert rworld.build_error is None, f"native prep failed: {rworld.build_error}"


@then("every patch it applied is a file tracked in the repository")
def _applied_tracked(rworld):
    applied = list(rworld.build_result.applied)
    assert sorted(applied) == sorted(f"{PATCH_DIR}/{n}" for n in GOOD_PATCHES), applied
    for rel in applied:
        assert rel in rworld.build_tracked, f"{rel} is not tracked"
        assert (rworld.build_repo / rel).is_file(), f"{rel} is not a file in the repository"
    logged = [ln.split(": ", 1)[1] for ln in rworld.build_result.log if ln.startswith("patch applied: ")]
    assert logged == applied, logged
    tree = rworld.build_result.tree
    for name, (path, _old, new) in GOOD_PATCHES.items():
        assert new in (tree / path).read_text(encoding="utf-8"), f"{name} did not change {path}"


@then("no patch is applied from outside the repository")
def _no_outside_patch(rworld):
    repo = rworld.build_repo.resolve()
    for rel in rworld.build_result.applied:
        assert (repo / rel).resolve().is_relative_to(repo), rel
    tree = rworld.build_result.tree
    for path in _files(tree):
        if path.suffix in (".c", ".h"):
            assert DECOY not in path.read_text(encoding="utf-8"), f"a decoy patch changed {path.name}"


@given("a patch fixture that does not apply cleanly to the UxPlay source tree")
def _bad_patch_fixture(rworld):
    _native_fixture(rworld)
    name = "0002-bind-passed-address.patch"
    path = "lib/netutils.c"
    stale = UXPLAY_FILES[path].replace("unsigned int addr", "unsigned long address")
    _write(rworld.build_repo / PATCH_DIR / name, _patch(path, stale, stale.replace("INADDR_ANY", "address")))
    rworld.build_bad_patch = name


@when("the native build runs")
def _native_build_runs(rworld):
    _prep(rworld)


@then("the build fails naming the patch")
def _fails_naming_patch(rworld):
    assert rworld.build_bad_patch in _error(rworld), _error(rworld)


@then("no protocol library is produced")
def _no_protocol_library(rworld):
    cache = rworld.build_cache
    assert not (cache / "prep.stamp").exists(), "native prep wrote its success stamp"
    assert not (cache / "src" / "uxplay").exists(), "the half-patched UxPlay tree was left for CMake"
    so = [p for p in _files(rworld.build_base) if p.suffix == ".so"]
    assert so == [], so


@given("a clean checkout with an untracked patch file in the receiver's patch directory")
def _untracked_patch_fixture(rworld):
    _native_fixture(rworld)
    rel = f"{PATCH_DIR}/0003-untracked.patch"
    _write(rworld.build_repo / rel,
           _patch("lib/raop.c", UXPLAY_FILES["lib/raop.c"], UXPLAY_FILES["lib/raop.c"] + f"/* {DECOY} */\n"))
    rworld.build_untracked_patch = rel  # deliberately not added to build_tracked


@then("the untracked patch is never applied")
def _untracked_never_applied(rworld):
    log = (rworld.build_cache / "native-prep.log").read_text(encoding="utf-8")
    assert f"patch applied: {rworld.build_untracked_patch}" not in log.splitlines(), log
    changed = [p for p in _files(rworld.build_cache / "src") if DECOY in p.read_text(encoding="utf-8", errors="replace")]
    assert changed == [], changed


@given("a UxPlay fetch fixture whose HEAD is not the pinned commit")
def _uxplay_wrong_head(rworld):
    _native_fixture(rworld)
    inner = rworld.build_fetch_uxplay

    def fetch_uxplay(url, commit, dest):
        inner(url, commit, dest)
        return "1" * 40

    rworld.build_fetch_uxplay = fetch_uxplay


@given("a committed unguarded-callbacks list that leaves out a callback the source calls without a NULL check")
def _callback_list_short(rworld):
    _native_fixture(rworld)
    _write(rworld.build_repo / "receiver" / "native" / "unguarded-callbacks.txt",
           "# raop_callbacks_t members UxPlay calls without a NULL check\n" + "".join(f"{n}\n" for n in UNGUARDED[:1]))


@then("the build fails naming the unguarded-callbacks list")
def _fails_naming_callback_list(rworld):
    assert "unguarded-callbacks.txt" in _error(rworld), _error(rworld)


# ---------------------------------------------------------------------------------------------------------
# @offline: dependency archives and pins (AIR-044)
# ---------------------------------------------------------------------------------------------------------

@given("a fixture dependency archive whose SHA-256 differs from its pin")
def _wrong_sha_fixture(rworld):
    _native_fixture(rworld, lock_override={"openssl": "0" * 64})


@when("the native build fetches that dependency")
def _native_build_fetches(rworld):
    _prep(rworld)
    assert FAKE_URL + "openssl-3.5.4.tar.gz" in rworld.build_fetched, "the dependency was never fetched"


@then("the build fails naming the SHA-256 mismatch")
def _fails_sha(rworld):
    msg = _error(rworld)
    assert "SHA-256 mismatch" in msg and "OpenSSL" in msg, msg


@then("the archive is never extracted")
def _never_extracted(rworld):
    markers = [p for p in _files(rworld.build_base) if p.name == "MARKER-openssl"]
    assert markers == [], markers
    assert not (rworld.build_cache / "src" / "openssl").exists()


@then("no output of that dependency is left")
def _no_output_left(rworld):
    left = [p for p in _files(rworld.build_cache) if "openssl" in p.name.lower()]
    left += [p for p in (rworld.build_cache / "src").glob("openssl*")] if (rworld.build_cache / "src").exists() else []
    assert left == [], left
    assert not (rworld.build_cache / "prep.stamp").exists()


@given("a fixture archive of the second dependency whose SHA-256 differs from its pin")
def _wrong_sha_second(rworld):
    _native_fixture(rworld, lock_override={"libplist": "0" * 64})


@then("the build fails naming the libplist SHA-256 mismatch")
def _fails_sha_libplist(rworld):
    msg = _error(rworld)
    assert "SHA-256 mismatch" in msg and "libplist" in msg, msg


@then("no dependency archive is extracted")
def _no_dep_extracted(rworld):
    markers = [p for p in _files(rworld.build_base) if p.name.startswith("MARKER-")]
    assert markers == [], markers
    src = rworld.build_cache / "src"
    left = sorted(p.name for p in src.iterdir()) if src.exists() else []
    assert left == [], left


@given(parsers.re(r"a pin fixture naming (?P<dependency>OpenSSL|libplist) (?P<version>\d+(?:\.\d+)*)"))
def _pin_fixture(rworld, dependency, version):
    deps = [("openssl", "OpenSSL", "3.5.4"), ("libplist", "libplist", "2.6.0")]
    deps = [(k, n, version if n == dependency else v) for k, n, v in deps]
    _native_fixture(rworld, deps=deps)


@when("the native build reads the pins")
def _native_build_reads_pins(rworld):
    _prep(rworld)


@then(parsers.re(r"the build fails naming (?P<dependency>OpenSSL|libplist) and its version (?P<version>\d+(?:\.\d+)*)"))
def _fails_naming_pin(rworld, dependency, version):
    msg = _error(rworld)
    assert re.search(rf"\b{re.escape(dependency)}\b", msg) and re.search(rf"(?<![\d.]){re.escape(version)}(?![\d.])", msg), msg


# ---------------------------------------------------------------------------------------------------------
# @offline: SDK install (AIR-045)
# ---------------------------------------------------------------------------------------------------------

@given("a fake SDK root with packages already installed")
def _fake_sdk_root(rworld):
    root = rworld.tmp / "Android"
    _write(root / "platform-tools" / "source.properties", "Pkg.Revision=35.0.2\nPkg.Path=platform-tools\n")
    _write(root / "platform-tools" / "adb.txt", "fake adb\n")
    _write(root / "build-tools" / "34.0.0" / "source.properties", "Pkg.Revision=34.0.0\nPkg.Path=build-tools;34.0.0\n")
    _write(root / "build-tools" / "34.0.0" / "aapt.txt", "fake aapt\n")
    rworld.build_sdk_root = root
    rworld.build_sdk_packages = ["platform-tools", "build-tools/34.0.0"]
    rworld.build_sdk_before = {p: _tree_hashes(root / p) for p in rworld.build_sdk_packages}
    rworld.build_sdk_top_before = sorted(p.name for p in root.iterdir())


@given("a fixture SDK archive whose checksum does not match")
def _corrupt_sdk_archive(rworld):
    good = _zip("android-14", {"source.properties": "Pkg.Revision=34.0.0\n", "MARKER-SDK-build-tools": "new\n"})
    bad = _zip("android-34", {"source.properties": "Pkg.Revision=3\n", "MARKER-SDK-platform": "corrupt\n"})
    mirror = {FAKE_URL + "build-tools_r34-windows.zip": good, FAKE_URL + "platform-34-ext7_r03.zip": bad}
    lock = _write(rworld.tmp / "sdk.lock", "\n".join([
        # A good update of an installed package comes first: it must not be applied either.
        "[package build-tools/34.0.0]", f"url = {FAKE_URL}build-tools_r34-windows.zip",
        "archive = build-tools_r34-windows.zip", f"sha256 = {_sha(good)}", "",
        "[package platforms/android-34]", f"url = {FAKE_URL}platform-34-ext7_r03.zip",
        "archive = platform-34-ext7_r03.zip", f"sha256 = {_sha(b'the archive this pin was made from')}", ""]))
    rworld.build_sdk_lock = lock
    rworld.build_sdk_cache = rworld.tmp / "sdk-cache"
    rworld.build_sdk_log = rworld.tmp / "sdk-install.log"

    def fetch(url, dest):
        Path(dest).write_bytes(mirror[url])

    rworld.build_sdk_fetch = fetch


@given("fixture SDK archives whose checksums all match")
def _good_sdk_archives(rworld):
    tools = _zip("android-14", {"source.properties": "Pkg.Revision=34.0.0\n", "MARKER-SDK-build-tools": "new\n"})
    platform = _zip("android-34", {"source.properties": "Pkg.Revision=3\n", "MARKER-SDK-platform": "new\n"})
    mirror = {FAKE_URL + "build-tools_r34-windows.zip": tools, FAKE_URL + "platform-34-ext7_r03.zip": platform}
    lock = _write(rworld.tmp / "sdk.lock", "\n".join([
        "[package build-tools/34.0.0]", f"url = {FAKE_URL}build-tools_r34-windows.zip",
        "archive = build-tools_r34-windows.zip", f"sha256 = {_sha(tools)}", "",
        "[package platforms/android-34]", f"url = {FAKE_URL}platform-34-ext7_r03.zip",
        "archive = platform-34-ext7_r03.zip", f"sha256 = {_sha(platform)}", ""]))
    rworld.build_sdk_lock = lock
    rworld.build_sdk_lock_packages = ["build-tools/34.0.0", "platforms/android-34"]
    rworld.build_sdk_cache = rworld.tmp / "sdk-cache"
    rworld.build_sdk_log = rworld.tmp / "sdk-install.log"

    def fetch(url, dest):
        Path(dest).write_bytes(mirror[url])

    rworld.build_sdk_fetch = fetch


@when("the SDK install script runs")
def _sdk_install_runs(rworld):
    from airplay_s3 import sdk_install
    rworld.build_error = None
    try:
        sdk_install.install(rworld.build_sdk_lock, rworld.build_sdk_cache, rworld.build_sdk_root,
                            fetch=rworld.build_sdk_fetch, log_path=rworld.build_sdk_log)
    except sdk_install.InstallError as exc:
        rworld.build_error = exc


@then("the install stops naming the checksum mismatch")
def _install_stops_checksum(rworld):
    msg = _error(rworld)
    assert "checksum mismatch" in msg and "platforms/android-34" in msg, msg


@then("the install log shows a checksum check for each package in the lock")
def _sdk_log_each_checked(rworld):
    from airplay_s3 import hostchecks
    checked = hostchecks.sdk_log_checked(rworld.build_sdk_log.read_text(encoding="utf-8"))
    missing = [p for p in rworld.build_sdk_lock_packages if p not in checked]
    assert missing == [], f"no checksum line for: {missing}"


@then("the corrupted archive is not extracted")
def _corrupt_not_extracted(rworld):
    found = [p for p in _files(rworld.tmp) if p.name.startswith("MARKER-SDK-")]
    assert found == [], found
    assert not (rworld.build_sdk_root / "platforms").exists()


@then("the hashes of the already-installed package directories are unchanged")
def _installed_unchanged(rworld):
    root = rworld.build_sdk_root
    for pkg in rworld.build_sdk_packages:
        assert _tree_hashes(root / pkg) == rworld.build_sdk_before[pkg], f"{pkg} changed"
    assert sorted(p.name for p in root.iterdir()) == rworld.build_sdk_top_before, "the SDK root gained an entry"


# ---------------------------------------------------------------------------------------------------------
# @offline: signing inputs are untracked and ignored (AIR-047, OD-188)
# ---------------------------------------------------------------------------------------------------------

@given("the repository and the paths of the signing key and the local signing file")
def _signing_paths(rworld):
    # The key itself lives outside the repository (%USERPROFILE%\.parkcast\parkcast-release.jks, OD-188); what
    # can be tracked by mistake is a copy of it in the checkout, so the check uses its repo-relative name.
    rworld.build_signing_paths = ["parkcast-release.jks", "local.properties"]


@given("a scratch repository in which a copy of the signing key and the local signing file are added "
       "and no ignore rule covers them")
def _scratch_signing_repo(rworld):
    # A tmp repository, never this one. A repo-local empty excludes file shuts out any global ignore rule, so
    # nothing but this fixture decides what is ignored. `git add` is enough to make a path tracked.
    repo = rworld.tmp / "scratch-repo"
    repo.mkdir()
    paths = ["parkcast-release.jks", "local.properties"]
    _write(repo / paths[0], "synthetic keystore bytes, not a real key\n")
    _write(repo / paths[1], "storeFile=parkcast-release.jks\n")
    _write(repo / ".git-empty-excludes", "")
    for args in (["init", "-q"], ["config", "core.excludesFile", str(repo / ".git-empty-excludes")],
                 ["add", "--", *paths]):
        proc = subprocess.run(["git", "-C", str(repo), *args], capture_output=True, text=True)
        assert proc.returncode == 0, f"git {args[0]} failed: {proc.stderr.strip()}"
    rworld.build_signing_repo = repo
    rworld.build_signing_paths = paths


@when('"git ls-files" and "git check-ignore" are run on both paths')
def _git_on_signing_paths(rworld):
    from airplay_s3 import hostchecks
    repo = getattr(rworld, "build_signing_repo", REPO)
    rworld.build_tracked_paths = hostchecks.tracked(repo, rworld.build_signing_paths)
    rworld.build_ignored_paths = hostchecks.ignored(repo, rworld.build_signing_paths)


@then("neither path is tracked")
def _neither_tracked(rworld):
    assert rworld.build_tracked_paths == [], rworld.build_tracked_paths


@then("both paths are ignored")
def _both_ignored(rworld):
    assert sorted(rworld.build_ignored_paths) == sorted(rworld.build_signing_paths), rworld.build_ignored_paths


@then("the check reports both paths as tracked")
def _both_reported_tracked(rworld):
    assert rworld.build_tracked_paths == rworld.build_signing_paths, rworld.build_tracked_paths


@then("the check reports neither path as ignored")
def _neither_reported_ignored(rworld):
    assert rworld.build_ignored_paths == [], rworld.build_ignored_paths


# ---------------------------------------------------------------------------------------------------------
# @host helpers
# ---------------------------------------------------------------------------------------------------------

def _need(path: Path, what: str):
    if not path.exists():
        pytest.skip(f"{what} not found at {path}; build the receiver on this PC first")
    return path


def _apk():
    apks = sorted(APK_DIR.glob("*.apk")) if APK_DIR.exists() else []
    apks = [a for a in apks if "unsigned" not in a.name]
    if not apks:
        pytest.skip(f"no signed APK under {APK_DIR}; run ./gradlew assembleRelease first")
    assert len(apks) == 1, f"more than one release APK: {[a.name for a in apks]}"
    return apks[0]


def _so_files(rworld):
    from airplay_s3 import hostchecks
    out = rworld.tmp / "apk-so"
    files = hostchecks.extract_so(rworld.build_apk, out)
    assert files, "the APK holds no .so"
    return files


def _log_lines():
    return _need(PREP_LOG, "the native-prep log").read_text(encoding="utf-8", errors="replace").splitlines()


# ---------------------------------------------------------------------------------------------------------
# @host: build log, CMake source list, patched sources (AIR-042)
# ---------------------------------------------------------------------------------------------------------

@given("a clean build of the receiver on this PC")
def _clean_build(rworld):
    rworld.build_log_path = _need(PREP_LOG, "the native-prep log")


@when("the build log is read")
def _build_log_read(rworld):
    rworld.build_log = _log_lines()


@then(parsers.re(r"it shows UxPlay commit (?P<commit>[0-9a-f]{40}) checked out"))
def _log_commit(rworld, commit):
    assert f"uxplay: checked out {commit}" in rworld.build_log, [ln for ln in rworld.build_log if "uxplay" in ln]


@then("it shows patches applied only from files in the repository")
def _log_patches_in_repo(rworld):
    from airplay_s3 import hostchecks
    applied = [ln.split(": ", 1)[1] for ln in rworld.build_log if ln.startswith("patch applied: ")]
    assert applied, "the log names no applied patch"
    outside = [p for p in applied if not p.startswith(PATCH_DIR + "/")]
    assert outside == [], outside
    untracked = sorted(set(applied) - set(hostchecks.tracked(REPO, applied)))
    assert untracked == [], untracked
    rworld.build_applied = applied


@then("the applied patches include the libmdnssd.so patch and the bind patch")
def _log_named_patches(rworld):
    names = [p.rsplit("/", 1)[-1] for p in rworld.build_applied]
    assert any("libmdnssd" in n for n in names), names
    assert any("bind" in n for n in names), names


@when("the CMake source list is read")
def _cmake_sources(rworld):
    from airplay_s3 import hostchecks
    rworld.build_sources = hostchecks.cmake_sources(REPO)
    if rworld.build_sources is None:
        pytest.skip("no compile_commands.json under receiver/app/.cxx; build the receiver on this PC first")
    assert rworld.build_sources, "the CMake source list is empty"


@then('no source file is under "renderers/"')
def _no_renderers(rworld):
    bad = [s for s in rworld.build_sources if "/renderers/" in "/" + s.replace("\\", "/")]
    assert bad == [], bad


@then('"uxplay.cpp" is not in the list')
def _no_uxplay_cpp(rworld):
    bad = [s for s in rworld.build_sources if s.replace("\\", "/").rsplit("/", 1)[-1] == "uxplay.cpp"]
    assert bad == [], bad


@given("the UxPlay sources as patched by a clean build on this PC")
def _patched_sources(rworld):
    rworld.build_tree = _need(UXPLAY_TREE, "the patched UxPlay tree")


@when('every "bind" call in them is read')
def _bind_calls(rworld):
    from airplay_s3 import hostchecks
    rworld.build_binds = hostchecks.bind_calls(rworld.build_tree)
    assert rworld.build_binds, "no bind call found in the patched sources"


@then(parsers.re(r'no call passes "(?P<wildcard>INADDR_ANY|in6addr_any)"'))
def _no_wildcard(rworld, wildcard):
    bad = [b for b in rworld.build_binds if wildcard in b.wildcards]
    assert bad == [], [f"{b.file}:{b.line}: {b.text}" for b in bad]


# ---------------------------------------------------------------------------------------------------------
# @host: the APK's native libraries (AIR-042, AIR-043, AIR-044)
# ---------------------------------------------------------------------------------------------------------

@given("the signed APK from a clean build on this PC")
def _signed_apk(rworld):
    rworld.build_apk = _apk()


@given('the signed APK from a clean "./gradlew" build on this PC')
def _signed_apk_gradlew(rworld):
    rworld.build_apk = _apk()


@when(parsers.re(r'"readelf -(?P<flag>[dA])" is run on each "\.so" in the APK'))
def _readelf(rworld, flag):
    from airplay_s3 import hostchecks
    files = _so_files(rworld)
    if flag == "d":
        rworld.build_needed = {f.name: hostchecks.needed(f) for f in files}
    else:
        rworld.build_arch = {f.name: hostchecks.cpu_arch(f) for f in files}


@when('the NEEDED entries of each ".so" in the APK are read')
def _needed_read(rworld):
    _readelf(rworld, "d")


@then('no NEEDED entry names a "gst" library')
def _no_gst(rworld):
    bad = {so: [n for n in needed if "gst" in n.lower()] for so, needed in rworld.build_needed.items()}
    bad = {k: v for k, v in bad.items() if v}
    assert bad == {}, bad


@then("each reports the ARMv7 architecture")
def _armv7(rworld):
    bad = {so: arch for so, arch in rworld.build_arch.items() if arch != "v7"}
    assert bad == {}, bad


@then("every entry is a platform library that NDK API 19 exposes")
def _api19_only(rworld):
    from airplay_s3 import hostchecks
    allowed = hostchecks.api19_libs()
    assert allowed, "no NDK API 19 platform libraries found"
    bad = {so: sorted(set(needed) - allowed) for so, needed in rworld.build_needed.items()}
    bad = {k: v for k, v in bad.items() if v}
    assert bad == {}, bad


@then('no entry is "libmdnssd.so"')
def _no_mdnssd_needed(rworld):
    bad = [so for so, needed in rworld.build_needed.items() if "libmdnssd.so" in needed]
    assert bad == [], bad


@when("the strings of the built protocol library are read")
def _strings_read(rworld):
    from airplay_s3 import hostchecks
    lib = [f for f in _so_files(rworld) if f.name == "libparkcast.so"]
    assert lib, "the APK holds no libparkcast.so"
    rworld.build_strings = hostchecks.strings(lib[0])


@then(parsers.re(r'they include "(?P<name>libmdnssd\.so)"'))
def _strings_include(rworld, name):
    assert any(name in s for s in rworld.build_strings), f"{name} is not in the library's strings"


@then(parsers.re(r'they do not include "(?P<name>libdns_sd\.so)"'))
def _strings_exclude(rworld, name):
    hits = [s for s in rworld.build_strings if name in s]
    assert hits == [], hits


@when('"unzip -l" lists the APK')
def _unzip_list(rworld):
    from airplay_s3 import hostchecks
    rworld.build_entries = hostchecks.apk_entries(rworld.build_apk)


@then('every native library entry is under "lib/armeabi-v7a/"')
def _so_under_v7a(rworld):
    so = [e for e in rworld.build_entries if e.endswith(".so")]
    assert so, "the APK holds no native library"
    bad = [e for e in so if not e.startswith("lib/armeabi-v7a/")]
    assert bad == [], bad


@then('there is no other directory under "lib/"')
def _no_other_abi(rworld):
    dirs = {e.split("/")[1] for e in rworld.build_entries if e.startswith("lib/") and e.count("/") >= 2}
    assert dirs == {"armeabi-v7a"}, sorted(dirs)


# ---------------------------------------------------------------------------------------------------------
# @host: pins in the repository (AIR-044)
# ---------------------------------------------------------------------------------------------------------

@given("the repository's native dependency pins")
def _repo_pins(rworld):
    rworld.build_lock = _need(NATIVE / "deps.lock", "the native dependency pins")


@when("each third-party dependency of the protocol library is checked")
def _deps_checked(rworld):
    from airplay_s3 import native_prep
    rworld.build_pins = native_prep.parse_lock(rworld.build_lock)


@then("each has a pinned version in the repository")
def _each_version(rworld):
    deps = {d.key: d for d in rworld.build_pins.deps}
    assert {"openssl", "libplist"} <= set(deps), sorted(deps)
    bad = [k for k, d in deps.items() if not re.fullmatch(r"\d+(\.\d+)+", d.version or "")]
    assert bad == [], bad
    assert re.fullmatch(r"[0-9a-f]{40}", rworld.build_pins.uxplay_commit or ""), "UxPlay has no pinned commit"


@then("each has the SHA-256 of its source archive in the repository")
def _each_sha(rworld):
    bad = [d.key for d in rworld.build_pins.deps if not re.fullmatch(r"[0-9a-f]{64}", d.sha256 or "")]
    assert bad == [], bad


@when("the OpenSSL and libplist pins are read")
def _pins_read(rworld):
    from airplay_s3 import native_prep
    rworld.build_pins = {d.key: d for d in native_prep.parse_lock(rworld.build_lock).deps}


@then("the OpenSSL pin is a 3.5.x release")
def _openssl_35(rworld):
    assert re.fullmatch(r"3\.5\.\d+", rworld.build_pins["openssl"].version), rworld.build_pins["openssl"].version


@then("the libplist pin is a 2.x release")
def _libplist_2(rworld):
    assert re.fullmatch(r"2\.\d+(\.\d+)?", rworld.build_pins["libplist"].version), rworld.build_pins["libplist"].version


# ---------------------------------------------------------------------------------------------------------
# @host: SDK install log (AIR-045)
# ---------------------------------------------------------------------------------------------------------

@given("the Android SDK install log from this PC")
def _sdk_log(rworld):
    from airplay_s3 import hostchecks
    path = hostchecks.sdk_log_path()
    if path is None:
        pytest.skip("no SDK install log under C:\\Android")
    rworld.build_sdk_log = path


@when("the log is read")
def _sdk_log_read(rworld):
    from airplay_s3 import hostchecks
    rworld.build_sdk_checked = hostchecks.sdk_log_checked(rworld.build_sdk_log.read_text(encoding="utf-8", errors="replace"))


@then(parsers.re(r'it shows a checksum check for each package installed under "(?P<root>C:\\Android)"'))
def _sdk_each_checked(rworld, root):
    from airplay_s3 import hostchecks
    packages = hostchecks.sdk_packages(Path(root))
    assert packages, f"no SDK package found under {root}"
    missing = [p for p in packages if p not in rworld.build_sdk_checked]
    assert missing == [], f"no checksum line for: {missing}"


# ---------------------------------------------------------------------------------------------------------
# @host: Gradle builds in a clean clone (AIR-043, AIR-046, AIR-047)
# ---------------------------------------------------------------------------------------------------------

def _clone(rworld):
    if not (REPO / "receiver" / "app" / "build.gradle").exists():
        pytest.skip("receiver/app/build.gradle does not exist yet")
    dest = rworld.tmp / "clone"
    subprocess.run(["git", "clone", "--quiet", "--no-hardlinks", str(REPO), str(dest)], check=True,
                   capture_output=True)
    rworld.build_clone = dest
    env = dict(os.environ)
    env["ANDROID_HOME"] = "C:\\Android"
    rworld.build_env = env
    return dest


def _local_props(clone: Path, props: dict):
    text = "".join(f"{k}={str(v).replace(chr(92), '/')}\n" for k, v in props.items())
    (clone / "local.properties").write_bytes(text.encode("utf-8"))


def _gradle(rworld, *args):
    clone = rworld.build_clone
    proc = subprocess.run(["cmd", "/c", str(clone / "gradlew.bat"), "--no-daemon", "--console=plain", *args],
                          cwd=clone, env=rworld.build_env, capture_output=True, text=True, errors="replace",
                          timeout=3600)
    rworld.build_rc = proc.returncode
    rworld.build_out = proc.stdout + proc.stderr


def _clone_apks(rworld):
    out = rworld.build_clone / "receiver" / "app" / "build" / "outputs" / "apk"
    return sorted(out.rglob("*.apk")) if out.exists() else []


@given("an NDK other than r25c configured for the receiver build on this PC")
def _other_ndk(rworld):
    clone = _clone(rworld)
    fake = rworld.tmp / "ndk" / "26.1.10909125"
    _write(fake / "source.properties", "Pkg.Desc = Android NDK\nPkg.Revision = 26.1.10909125\n")
    _local_props(clone, {"sdk.dir": "C:\\Android", "ndk.dir": fake})
    rworld.build_env["ANDROID_NDK_HOME"] = str(fake)
    rworld.build_fake_ndk = "26.1.10909125"


@given(parsers.re(r"a build setup on this PC in which the (?P<input>local signing file|signing key) is absent"))
def _signing_input_absent(rworld, input):
    clone = _clone(rworld)
    if input == "signing key":
        _local_props(clone, {"sdk.dir": "C:\\Android", "parkcast.keystore": rworld.tmp / "absent" / "parkcast-release.jks",
                             "parkcast.keystore.password": FAKE_STORE_PASS, "parkcast.key.alias": "parkcast-test",
                             "parkcast.key.password": FAKE_KEY_PASS})


@given("a throwaway test key and test passwords, never the owner's, in the local signing file")
def _throwaway_key(rworld):
    from airplay_s3 import hostchecks
    clone = _clone(rworld)
    keystore = rworld.tmp / "throwaway" / "test.jks"
    keystore.parent.mkdir(parents=True)
    hostchecks.make_test_keystore(keystore, "parkcast-test", FAKE_STORE_PASS, FAKE_KEY_PASS)
    _local_props(clone, {"sdk.dir": "C:\\Android", "parkcast.keystore": keystore,
                         "parkcast.keystore.password": FAKE_STORE_PASS, "parkcast.key.alias": "parkcast-test",
                         "parkcast.key.password": FAKE_KEY_PASS})


@given("a clean clone of the repository on this PC, with the SDK, the NDK and the local signing file present")
def _clean_clone_ready(rworld):
    owner_props = _need(REPO / "local.properties", "the local signing file")
    clone = _clone(rworld)
    shutil.copyfile(owner_props, clone / "local.properties")


@when('"./gradlew" builds the receiver')
def _gradlew_builds(rworld):
    _gradle(rworld, "assembleRelease")


@when('"./gradlew" is run once')
def _gradlew_once(rworld):
    _gradle(rworld, "assembleRelease")


@then("the build fails naming the NDK version it found")
def _fails_naming_ndk(rworld):
    assert rworld.build_rc != 0, "the build succeeded"
    assert rworld.build_fake_ndk in rworld.build_out, "the build output does not name the NDK version found"


@then("no APK is produced")
def _no_apk(rworld):
    assert _clone_apks(rworld) == [], _clone_apks(rworld)


@then("no other NDK on the PC is used instead")
def _no_other_ndk(rworld):
    ndk_root = Path("C:\\Android\\ndk")
    others = [p.name for p in ndk_root.iterdir()] if ndk_root.exists() else []
    used = [v for v in others if v in rworld.build_out]
    assert used == [], f"the build output names other NDKs: {used}"
    assert not (rworld.build_clone / "receiver" / "app" / ".cxx").exists(), "CMake ran"


@then(parsers.re(r"the build fails naming the (?P<input>local signing file|signing key) as missing"))
def _fails_naming_input(rworld, input):
    assert rworld.build_rc != 0, "the build succeeded"
    names = {"local signing file": ["local.properties"], "signing key": ["parkcast.keystore", "parkcast-release.jks"]}
    lines = [ln for ln in rworld.build_out.splitlines() if "missing" in ln.lower()]
    assert any(n in ln for ln in lines for n in names[input]), f"no line names the {input} as missing"


@then("the build output does not contain either test password")
def _no_password_in_output(rworld):
    leaked = FAKE_STORE_PASS in rworld.build_out or FAKE_KEY_PASS in rworld.build_out
    if leaked:
        pytest.fail("the build output contains a test password", pytrace=False)


@then("the signed APK is produced")
def _signed_apk_produced(rworld):
    from airplay_s3 import hostchecks
    apks = [a for a in _clone_apks(rworld) if "unsigned" not in a.name]
    assert rworld.build_rc == 0 and apks, "assembleRelease produced no signed APK"
    assert hostchecks.apk_signed(apks[0]), f"{apks[0].name} is not signed"


@given('"ANDROID_HOME" is unset')
def _android_home_unset(rworld):
    dest = rworld.tmp / "clone"
    subprocess.run(["git", "clone", "--quiet", "--no-hardlinks", str(REPO), str(dest)], check=True,
                   capture_output=True)
    rworld.build_clone = dest
    env = {k: v for k, v in os.environ.items() if k.upper() not in ("ANDROID_HOME", "ANDROID_SDK_ROOT")}
    rworld.build_env = env


@when('"receiver/gate" is built and its tests are run')
def _gate_build(rworld):
    _gradle(rworld, "-Pparkcast.noApp", ":receiver:gate:build")


@then("the build succeeds")
def _build_succeeds(rworld):
    tail = "\n".join(rworld.build_out.splitlines()[-30:])
    assert rworld.build_rc == 0, tail


@then("every test passes")
def _every_test_passes(rworld):
    from airplay_s3 import hostchecks
    totals = hostchecks.junit_totals(rworld.build_clone / "receiver" / "gate" / "build" / "test-results" / "test")
    assert totals["tests"] > 0, "no test ran"
    assert totals["failures"] == 0 and totals["errors"] == 0, totals


# ---------------------------------------------------------------------------------------------------------
# @host: static checks (AIR-055)
# ---------------------------------------------------------------------------------------------------------

@given("the receiver's JNI sources in the repository")
def _jni_sources(rworld):
    rworld.build_jni = _need(NATIVE / "src", "the receiver's JNI sources")


@when("the static check scans them for FindClass, GetMethodID and GetStaticMethodID arguments")
def _jni_scan(rworld):
    from airplay_s3 import hostchecks
    rworld.build_jni_refs = hostchecks.jni_class_refs(rworld.build_jni)


@then('no argument names a class under "android.", "com.hkmc." or "com.mobis.", or any audio or display class')
def _no_forbidden_class(rworld):
    from airplay_s3 import hostchecks
    unreadable = [r for r in rworld.build_jni_refs if r.cls is None]
    assert unreadable == [], [f"{r.file}:{r.line}: {r.call} argument is not a string literal" for r in unreadable]
    bad = [r for r in rworld.build_jni_refs if hostchecks.forbidden_class(r.cls)]
    assert bad == [], [f"{r.file}:{r.line}: {r.call} {r.cls}" for r in bad]


@given("the pinned, patched UxPlay source")
def _pinned_patched(rworld):
    rworld.build_tree = _need(UXPLAY_TREE, "the patched UxPlay tree")


@when("the static check lists every raop_callbacks_t member the source calls without a preceding NULL check")
def _unguarded_list(rworld):
    from airplay_s3 import native_prep
    rworld.build_unguarded = native_prep.unguarded_callbacks(rworld.build_tree)


@then('the build log has the line "unguarded callbacks: " followed by that list, comma-separated')
def _log_unguarded(rworld):
    line = "unguarded callbacks: " + ", ".join(rworld.build_unguarded)
    lines = _log_lines()
    assert line in lines, [ln for ln in lines if ln.startswith("unguarded callbacks:")]


@then("the host callback table sets every member on that list to a non-NULL function")
def _host_table(rworld):
    from airplay_s3 import hostchecks
    table = hostchecks.host_table(_need(NATIVE / "src" / "host.c", "the host callback table"))
    unset = [m for m in rworld.build_unguarded if not table.get(m)]
    assert unset == [], unset


# ---------------------------------------------------------------------------------------------------------
# @host: APK contents (AIR-063, AIR-068)
# ---------------------------------------------------------------------------------------------------------

@when("the build check lists the APK's entries")
def _list_entries(rworld):
    _unzip_list(rworld)


@then('no entry is named "receiver.conf", in any folder')
def _no_receiver_conf(rworld):
    bad = [e for e in rworld.build_entries if e.rsplit("/", 1)[-1] == "receiver.conf"]
    assert bad == [], bad


@when('the build check runs "aapt dump permissions" on it')
def _aapt_permissions(rworld):
    from airplay_s3 import hostchecks
    rworld.build_permissions = hostchecks.aapt_permissions(rworld.build_apk)


@then('the uses-permission lines name exactly "android.permission.INTERNET"')
def _only_internet(rworld):
    assert rworld.build_permissions == ["android.permission.INTERNET"], rworld.build_permissions

