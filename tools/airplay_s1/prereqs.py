"""The build's prerequisites (AIR-001): MSYS2 UCRT64, its build packages, and a GStreamer H.264
decoder. Package names follow UxPlay's Windows build instructions (docs/evidence/S1_PC_FACTS.md),
with ninja, which that build runs."""

from __future__ import annotations

import dataclasses
import os
import subprocess
from pathlib import Path

REQUIRED_PACKAGES = [
    "mingw-w64-ucrt-x86_64-cmake",
    "mingw-w64-ucrt-x86_64-ninja",
    "mingw-w64-ucrt-x86_64-gcc",
    "mingw-w64-ucrt-x86_64-libplist",
    "mingw-w64-ucrt-x86_64-gstreamer",
    "mingw-w64-ucrt-x86_64-gst-plugins-base",
    "git",
]
UCRT64 = "the UCRT64 environment"
DECODER = "a GStreamer H.264 decoder"
H264_DECODERS = {"avdec_h264", "openh264dec", "d3d11h264dec", "d3d12h264dec", "nvh264dec", "qsvh264dec",
                 "vah264dec", "mfh264dec"}
MSYS2 = Path(os.environ.get("AIRPLAY_S1_MSYS2", r"C:\msys64"))


@dataclasses.dataclass
class Inventory:
    ucrt64: bool
    packages: dict[str, str]
    gst_elements: set[str]

    def to_dict(self) -> dict:
        return {"ucrt64": self.ucrt64, "packages": dict(self.packages), "gst_elements": sorted(self.gst_elements)}


@dataclasses.dataclass
class Check:
    ok: bool
    missing: list[str]


def check(inventory: Inventory) -> Check:
    missing = []
    if not inventory.ucrt64:
        missing.append(UCRT64)
    missing += [p for p in REQUIRED_PACKAGES if p not in inventory.packages]
    if not inventory.gst_elements & H264_DECODERS:
        missing.append(DECODER)
    return Check(ok=not missing, missing=missing)


def ucrt64(command: str, timeout: int = 120) -> subprocess.CompletedProcess:
    """Run a shell command in MSYS2's UCRT64 environment."""
    env = dict(os.environ, MSYSTEM="UCRT64", CHERE_INVOKING="1")
    return subprocess.run([str(MSYS2 / "usr" / "bin" / "bash.exe"), "-lc", command], env=env, capture_output=True,
                          text=True, timeout=timeout)


def inventory_from_host() -> Inventory:
    """This PC's real inventory: installed MSYS2 packages and the GStreamer elements UCRT64 can load."""
    bash = MSYS2 / "usr" / "bin" / "bash.exe"
    if not bash.is_file() or not (MSYS2 / "ucrt64" / "bin").is_dir():
        return Inventory(ucrt64=False, packages={}, gst_elements=set())
    q = ucrt64("pacman -Q")
    packages = dict(line.split(maxsplit=1) for line in q.stdout.splitlines() if " " in line)
    elements = set()
    inspect = ucrt64("gst-inspect-1.0 2>/dev/null")
    for line in inspect.stdout.splitlines():
        parts = [p.strip() for p in line.split(":")]
        if len(parts) >= 3 and parts[1]:
            elements.add(parts[1])
    return Inventory(ucrt64=True, packages=packages, gst_elements=elements)
