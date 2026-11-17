"""Checksum-verified Android SDK package install (AIR-045; docs/design/S3_RECEIVER.md).

Every archive named in the SDK lock is downloaded and its SHA-256 checked before any archive is extracted, so
one corrupt archive leaves the SDK root exactly as it was: no package is replaced, none is added, and nothing
from the corrupt archive is written anywhere. Only when every archive matches is each one extracted into a
temporary directory inside the SDK root and swapped into place.

Lock format (INI read by configparser, no interpolation, LF):

    [package build-tools/34.0.0]          ; the package's directory relative to the SDK root
    url = https://dl.google.com/android/repository/build-tools_r34-windows.zip
    sha256 = <64 lowercase hex>
    archive = build-tools_r34-windows.zip ; optional, defaults to the last part of url

Archives with a single top-level directory (as Google's are) have it stripped. The log gets, per package,
`checksum ok <relpath> sha256=<hex>` and then `installed <relpath>`; a mismatch is logged and raised as
`checksum mismatch for <relpath>: ...`.

Usage: python -m airplay_s3.sdk_install LOCK [--sdk-root C:\\Android] [--cache DIR] [--log FILE]
"""

from __future__ import annotations

import argparse
import configparser
import re
import shutil
import sys
from dataclasses import dataclass
from pathlib import Path, PurePosixPath

from airplay_s3 import native_prep

DEFAULT_ROOT = Path("C:\\Android")
LOG_NAME = "sdk-install.log"


class InstallError(Exception):
    """The SDK install stopped; the message names the package."""


@dataclass
class Package:
    relpath: str
    url: str
    sha256: str
    archive: str


def parse_lock(path) -> list:
    cp = configparser.ConfigParser(interpolation=None)
    try:
        cp.read_string(Path(path).read_text(encoding="utf-8"), source=str(path))
    except (OSError, configparser.Error) as exc:
        raise InstallError(f"cannot read the SDK lock {path}: {exc}") from exc
    out = []
    for section in cp.sections():
        if not section.startswith("package "):
            continue
        rel = section[8:].strip()
        p = PurePosixPath(rel)
        if not rel or p.is_absolute() or ":" in rel or "\\" in rel or any(s in ("", ".", "..") for s in rel.split("/")):
            raise InstallError(f"{path}: package path {rel!r} is not a plain relative path")
        sec = cp[section]
        url, sha = sec.get("url", "").strip(), sec.get("sha256", "").strip().lower()
        if not url or not re.fullmatch(r"[0-9a-f]{64}", sha):
            raise InstallError(f"{path}: [{section}] needs a url and a 64-hex sha256")
        archive = sec.get("archive", "").strip() or PurePosixPath(url.split("?", 1)[0]).name
        if not archive or "/" in archive or "\\" in archive or archive in (".", ".."):
            raise InstallError(f"{path}: [{section}] archive name {archive!r} is not a plain file name")
        out.append(Package(rel, url, sha, archive))
    if not out:
        raise InstallError(f"{path}: no [package ...] section")
    return out


def _download(pkg: Package, cache: Path, fetch, log) -> Path:
    downloads = cache / "downloads"
    downloads.mkdir(parents=True, exist_ok=True)
    final = downloads / pkg.archive
    if final.exists() and native_prep._sha256(final) == pkg.sha256:
        log.append(f"checksum ok {pkg.relpath} sha256={pkg.sha256} (cached)")
        return final
    final.unlink(missing_ok=True)
    part = downloads / (pkg.archive + ".part")
    part.unlink(missing_ok=True)
    try:
        fetch(pkg.url, part)
    except Exception as exc:  # noqa: BLE001 - any download failure is reported the same way
        part.unlink(missing_ok=True)
        raise InstallError(f"download of {pkg.relpath} from {pkg.url} failed: {exc}") from exc
    got = native_prep._sha256(part)
    if got != pkg.sha256:
        part.unlink(missing_ok=True)
        raise InstallError(f"checksum mismatch for {pkg.relpath}: pinned {pkg.sha256}, got {got}")
    part.replace(final)
    log.append(f"checksum ok {pkg.relpath} sha256={got}")
    return final


def _swap_in(pkg: Package, archive: Path, root: Path, log):
    stage = root / f".parkcast-sdk-stage-{pkg.relpath.replace('/', '_')}"
    shutil.rmtree(stage, ignore_errors=True)
    dest = root.joinpath(*pkg.relpath.split("/"))
    old = dest.with_name(dest.name + ".parkcast-old")
    try:
        try:
            native_prep.extract(archive, stage)
        except native_prep.PrepError as exc:
            raise InstallError(f"cannot extract {pkg.relpath}: {exc}") from exc
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.rmtree(old, ignore_errors=True)
        if dest.exists():
            dest.rename(old)
        stage.rename(dest)
        shutil.rmtree(old, ignore_errors=True)
        log.append(f"installed {pkg.relpath}")
    finally:
        shutil.rmtree(stage, ignore_errors=True)
        if old.exists() and not dest.exists():
            old.rename(dest)


def install(lock_path, cache_dir, sdk_root, *, fetch=None, log_path=None) -> list:
    """Verify every archive in the lock, then install them all. Returns the log lines."""
    fetch = fetch or native_prep.default_fetch
    root, cache = Path(sdk_root), Path(cache_dir)
    log: list = []
    try:
        packages = parse_lock(lock_path)
        verified = [(pkg, _download(pkg, cache, fetch, log)) for pkg in packages]
        for pkg, archive in verified:
            _swap_in(pkg, archive, root, log)
        return log
    except InstallError as exc:
        log.append(f"sdk install stopped: {exc}")
        raise
    finally:
        if log_path is not None:
            Path(log_path).parent.mkdir(parents=True, exist_ok=True)
            with open(log_path, "a", encoding="utf-8", newline="\n") as f:
                f.writelines(f"{ln}\n" for ln in log)


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(prog="sdk_install", description=__doc__.split("\n", 1)[0])
    ap.add_argument("lock", type=Path)
    ap.add_argument("--sdk-root", type=Path, default=DEFAULT_ROOT)
    ap.add_argument("--cache", type=Path)
    ap.add_argument("--log", type=Path)
    args = ap.parse_args(argv)
    cache = args.cache or args.sdk_root / "dl"
    log_path = args.log or args.sdk_root / LOG_NAME
    try:
        lines = install(args.lock, cache, args.sdk_root, log_path=log_path)
    except InstallError as exc:
        print(f"sdk install stopped: {exc}", file=sys.stderr)
        return 1
    for line in lines:
        print(line)
    return 0


if __name__ == "__main__":
    sys.exit(main())
