"""Build the S3 `airplay.receiver` install zip (AIR-078(c)): `NEMESIS.data` and the APK, nothing else.

The manifest is checked by `zipcheck` before anything is written; on any finding no zip is produced and every
finding is returned (AIR-078(h)). A stale zip at the output path is removed first, so a failed build never leaves
an older zip that could be staged by mistake. The zip is written to a temporary file, checked again, then renamed
into place. Member layout follows hmg_nemesis `make_nemesis_package.py`: deflated, relative posix paths.

Usage: python -m airplay_s3.zipbuild --apk <receiver.apk> -o <airplay.receiver.zip> [--manifest NEMESIS.data]
"""

from __future__ import annotations

import argparse
import os
import sys
import zipfile
from pathlib import Path

from airplay_s3 import zipcheck

APK_MEMBER = "parkcast-receiver.apk"


def manifest_text():
    """The default `NEMESIS.data` for the S3 test build."""
    return (
        "[nemesis]\n"
        f"id={zipcheck.PACKAGE_ID}\n"
        "name=ParkCast Receiver (S3 test build)\n"
        "version=0.1.0\n"
        "package-version=0.1.0\n"
        "types=application\n"
        'short-desc="ParkCast AirPlay receiver, S3 test build"\n'
        'detailed-desc="S3 test build of the ParkCast AirPlay screen-mirroring receiver for use while parked. '
        'Installs the receiver APK only: no services, hooks or config."\n'
        f"os={zipcheck.OS_VALUE}\n"
        f"models={zipcheck.MODELS_VALUE}\n"
        "\n"
        "[app.0]\n"
        "kind=apk\n"
        f"path={APK_MEMBER}\n"
        f"package={zipcheck.RECEIVER_PACKAGE}\n"
    )


def _apk_member(text):
    paths = [v.strip() for v in zipcheck._values(zipcheck.parse(text), "app.0", "path")]
    return paths[0] if len(paths) == 1 and paths[0] else APK_MEMBER


def build_zip(apk_path, out_path, manifest=None):
    """Build the zip at `out_path` from `apk_path`. Returns the findings; [] means the zip was written."""
    out = Path(out_path)
    if out.exists() or out.is_symlink():
        out.unlink()
    text = manifest_text() if manifest is None else manifest
    member = _apk_member(text)
    apk = Path(apk_path)
    findings = zipcheck.check_manifest(text, [zipcheck.MANIFEST, member])
    if not apk.is_file():
        findings.append(f"apk: {apk.name} not found")
    if findings:
        return findings

    tmp = out.with_name(out.name + ".partial")
    try:
        with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as z:
            z.writestr(zipcheck.MANIFEST, text.encode("utf-8"))
            z.write(apk, member)
        findings = zipcheck.check_zip(tmp)
        if findings:
            return findings
        os.replace(tmp, out)
    finally:
        if tmp.exists():
            tmp.unlink()
    return []


def main(argv=None):
    parser = argparse.ArgumentParser(prog="zipbuild", description=__doc__.splitlines()[0])
    parser.add_argument("--apk", required=True, help="the signed receiver APK")
    parser.add_argument("-o", "--output", required=True, help="the zip to write")
    parser.add_argument("--manifest", help="a NEMESIS.data to use instead of the default")
    args = parser.parse_args(argv)
    manifest = Path(args.manifest).read_bytes().decode("utf-8") if args.manifest else None
    findings = build_zip(args.apk, args.output, manifest)
    for line in findings:
        print(line)
    if findings:
        print("no install zip written", file=sys.stderr)
        return 1
    print(args.output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
