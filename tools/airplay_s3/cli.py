"""The S3 command, `python tools/s3.py` (AIR-084(d); OD-199, OD-204).

`keep-check APK` reports every reason the APK isn't a build to keep (raw diagnostics on, or the version name
and native library disagreeing). Exit codes: 0 when no reason applies; 1 when any does; 2 on a usage error or
when the APK can't be checked (no library, no version name, or aapt failed).
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from airplay_s3 import keepcheck


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="s3.py", allow_abbrev=False,
                                     description="S3 tools for the on-unit receiver.")
    sub = parser.add_subparsers(dest="command", required=True)
    keep = sub.add_parser("keep-check", allow_abbrev=False,
                          help="check that an APK is a build to keep (raw diagnostics off, AIR-084(d))")
    keep.add_argument("apk", type=Path, help="the APK to check")
    keep.add_argument("--aapt", type=Path, default=None,
                      help="the aapt to read the version name with (default: the SDK's newest build-tools aapt)")
    return parser


def _keep_check(args) -> int:
    try:
        aapt = args.aapt if args.aapt is not None else keepcheck.default_aapt()
        version = keepcheck.version_name(args.apk, aapt)
        marked = keepcheck.library_marked(args.apk)
    except (keepcheck.KeepCheckError, FileNotFoundError) as exc:
        print(f"keep-check: error: {exc}", file=sys.stderr)
        return 2
    found = keepcheck.reasons(version, marked)
    details = {
        keepcheck.VERSION_NAME: f"versionName '{version}' ends in '{keepcheck.SUFFIX}'",
        keepcheck.NATIVE_LIBRARY: f"{keepcheck.LIB} holds '{keepcheck.MARKER.decode()}'",
        keepcheck.DISAGREEMENT: "the version name and the native library disagree",
    }
    for reason in found:
        print(f"reason: {reason} ({details[reason]})")
    if found:
        print(f"keep-check: FAIL: {args.apk} is not a build to keep")
        return 1
    print(f"keep-check: PASS: {args.apk} (versionName '{version}', no raw-diagnostics marker)")
    return 0


def main(argv) -> int:
    args = _parser().parse_args(argv)
    if args.command == "keep-check":
        return _keep_check(args)
    return 2
