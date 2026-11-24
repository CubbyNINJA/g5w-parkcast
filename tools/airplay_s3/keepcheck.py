"""The keep check for a receiver APK (AIR-084(d); OD-199, OD-204).

A build the owner keeps must have raw diagnostics off. The check reads the APK's version name with
`aapt dump badging` and scans `lib/armeabi-v7a/libparkcast.so` for the native build's marker string, then
reports every condition that applies: a version name ending in `-rawdiag` (`version name`), a library holding
the marker (`native library`), and the two disagreeing (`disagreement`). It passes only if none applies.
"""

from __future__ import annotations

import re
import subprocess
import zipfile
from pathlib import Path

MARKER = b"parkcast raw diagnostics on"
SUFFIX = "-rawdiag"
LIB = "lib/armeabi-v7a/libparkcast.so"
VERSION_NAME = "version name"
NATIVE_LIBRARY = "native library"
DISAGREEMENT = "disagreement"


class KeepCheckError(Exception):
    """The APK couldn't be checked: no library, no readable version name, or aapt failed."""


def default_aapt() -> Path:
    from airplay_s3 import hostchecks
    return hostchecks._aapt()


def version_name(apk, aapt) -> str:
    """The `versionName` that `aapt dump badging` prints for the APK."""
    try:
        proc = subprocess.run([str(aapt), "dump", "badging", str(apk)], capture_output=True, text=True,
                              errors="replace", timeout=120)
    except (OSError, subprocess.SubprocessError) as exc:
        raise KeepCheckError(f"aapt could not run: {exc}") from exc
    if proc.returncode != 0:
        raise KeepCheckError(f"aapt dump badging failed with exit {proc.returncode}: {proc.stderr.strip()}")
    for line in proc.stdout.splitlines():
        if line.startswith("package:"):
            match = re.search(r"\bversionName='([^']*)'", line)
            if match:
                return match.group(1)
    raise KeepCheckError("aapt dump badging printed no versionName")


def library_marked(apk) -> bool:
    """Whether the APK's `libparkcast.so` holds the raw-diagnostics marker string."""
    try:
        with zipfile.ZipFile(apk) as zf:
            data = zf.read(LIB)
    except KeyError as exc:
        raise KeepCheckError(f"the APK has no {LIB}") from exc
    except (OSError, zipfile.BadZipFile) as exc:
        raise KeepCheckError(f"the APK could not be read: {exc}") from exc
    return MARKER in data


def reasons(version: str, marked: bool) -> list:
    """Every reason that applies, in a fixed order: version name, native library, disagreement."""
    suffixed = version.endswith(SUFFIX)
    found = []
    if suffixed:
        found.append(VERSION_NAME)
    if marked:
        found.append(NATIVE_LIBRARY)
    if suffixed != marked:
        found.append(DISAGREEMENT)
    return found
