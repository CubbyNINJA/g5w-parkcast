"""A fake unit toolbox and a fake stick for the stick-script scenarios of 14_receiver_tools.feature.

The stick scripts (`tools/airplay_s3/stick/*.sh`) run under the host's POSIX `sh`. Each script reads one
test-only variable, `PARKCAST_TEST_ROOT`, and puts it in front of every unit path and every PATH directory.
On the unit it is unset, so the paths and PATH are the stock ones. Here it names a fake unit root whose
`system/bin` and `system/xbin` hold only the fakes below, so a script that calls a tool toolbox lacks
(awk, sed, busybox, ...) fails with "not found".

Each fake records every call, with its arguments, in `$FAKE_STATE/calls.log` (one line per call:
index, name and arguments, tab-separated) and rejects an option it does not know, as toolbox does: it prints
"unknown option" and exits 1, and the call is logged as REJECTED. The fakes of the unit's own commands
(`nemesis`, `pm`, `am`, `chown`, `chmod`, `mkdir`) print `fake-<name> out <index>` on stdout and
`fake-<name> err <index>` on stderr, so a scenario can prove that a script kept the output of every command
it ran. Their behaviour is set by files in `$FAKE_STATE` (see `Fakes`).
"""

from __future__ import annotations

import hashlib
import os
import shutil
import subprocess
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
STICK_DIR = REPO / "tools" / "airplay_s3" / "stick"
FIXED_DATE = "2026-11-17 12:00:00"
TIMESTAMP = "20261117-120000"  # what the fake `date` gives for +%Y%m%d-%H%M%S
LOG_DIR = "data/nemesis/logs/airplay-receiver"
RUN_PARENT = "NEMESIS-diag/airplay-receiver"
BLANK_CHECKLIST = (b"parkcast listed: \nletterboxed: \nsound on unit: \nstayed behind other app: \n"
                   b"nothing on cluster or hud: \ncarplay after exit: \n")
MARKED = ("nemesis", "pm", "am", "chown", "chmod", "mkdir")

# Options each fake accepts; anything else starting with "-" is refused.
ALLOWED = {
    "nemesis": "--update",
    "pm": "-f -d -e -s -3 -i -u",
    "am": "-n -a -W -S -D -c -d -t -f",
    "mkdir": "-p -m",
    "cp": "-f -p -r -R",
    "rm": "-f -r -rf",
    "chown": "",
    "chmod": "",
    "cat": "",
    "mv": "",
    "ls": "-l -a -d",
    "date": "-u",
}

_HEAD = r"""#!/bin/sh
PATH=/usr/bin:/bin; export PATH
S="$FAKE_STATE"
NAME=@NAME@
n=$(cat "$S/count" 2>/dev/null || echo 0); n=$((n + 1)); echo "$n" > "$S/count"
line="$n	$NAME"; for a in "$@"; do line="$line	$a"; done
for a in "$@"; do
  case "$a" in
    -*) case " @ALLOWED@ " in
          *" $a "*) ;;
          *) echo "$NAME: unknown option $a" >&2; echo "REJECTED	$line" >> "$S/calls.log"; exit 1 ;;
        esac ;;
  esac
done
echo "$line" >> "$S/calls.log"
marks() { echo "fake-$NAME out $n"; echo "fake-$NAME err $n" >&2; }
rc_of() { if [ -f "$S/$1" ]; then cat "$S/$1"; else echo 0; fi; }
"""

_BODY = {
    "nemesis": r"""
case "$1" in
  install) [ -f "$S/nemesis.install.out" ] && cat "$S/nemesis.install.out"; marks; exit $(rc_of nemesis.install.rc) ;;
  pkg-list) [ -f "$S/nemesis.pkglist.out" ] && cat "$S/nemesis.pkglist.out"; marks; exit 0 ;;
  *) marks; exit 0 ;;
esac
""",
    "pm": r"""
if [ "$1" = list ] && [ "$2" = packages ]; then
  [ -f "$S/pm.list.out" ] && cat "$S/pm.list.out"
fi
marks; exit 0
""",
    "am": r"""
if [ "$1" = start ]; then
  [ -f "$S/am.start.out" ] && cat "$S/am.start.out"
  marks; exit $(rc_of am.start.rc)
fi
marks; exit 0
""",
    "chown": r"""
marks; exit $(rc_of chown.rc)
""",
    "chmod": r"""
marks; exit $(rc_of chmod.rc)
""",
    "mkdir": r"""
marks; mkdir "$@"
""",
    "cp": r"""
src=; dst=
for a in "$@"; do case "$a" in -*) ;; *) if [ -z "$src" ]; then src=$a; else dst=$a; fi ;; esac; done
target=$dst
[ -d "$dst" ] && target="${dst%/}/${src##*/}"
if [ -f "$S/cp.refuse_dest" ] && [ "$target" = "$(cat "$S/cp.refuse_dest")" ]; then
  echo "cp: $target: Permission denied" >&2; exit 1
fi
logdir=$(cat "$S/cp.logdir" 2>/dev/null)
case "$src" in
  "$logdir"/*)
    k=$(cat "$S/cp.logcount" 2>/dev/null || echo 0); k=$((k + 1)); echo "$k" > "$S/cp.logcount"
    if [ "$k" = 1 ] && [ -f "$S/cp.create.name" ]; then
      cat "$S/cp.create.bytes" > "${target%/*}/$(cat "$S/cp.create.name")"
    fi
    if [ "$k" -ge 2 ] && [ -f "$S/cp.fail_after_first_log" ]; then
      head -c 3 "$src" > "$target"; echo "cp: write error: No space left on device" >&2; exit 1
    fi ;;
esac
cp "$@"
""",
    "date": r"""
date -d "@FIXED_DATE@" "$@"
""",
    "cat": "\ncat \"$@\"\n",
    "rm": "\nrm \"$@\"\n",
    "mv": "\nmv \"$@\"\n",
    "ls": "\nls \"$@\"\n",
}


def _write_lf(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data if isinstance(data, bytes) else data.encode("utf-8"))


def sh_executable():
    """The host's POSIX sh: $PARKCAST_TEST_SH, Git for Windows' sh, or the first `sh` on PATH."""
    env = os.environ.get("PARKCAST_TEST_SH")
    if env:
        return env
    git_sh = Path(r"C:\Program Files\Git\bin\sh.exe")
    if os.name == "nt" and git_sh.is_file():
        return str(git_sh)
    found = shutil.which("sh")
    assert found, "no POSIX sh on this host"
    return found


def posix_path(path: Path):
    """The path as the host's sh sees it (on Windows, /c/...)."""
    out = subprocess.run([sh_executable(), "-c", "pwd"], cwd=str(path), capture_output=True, text=True, check=True)
    return out.stdout.strip()


def sha(path: Path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


class Fakes:
    """One scenario's fake unit root, fake stick and fake toolbox."""

    def __init__(self, tmp: Path):
        self.root = tmp / "unit"
        self.stick = tmp / "stick"
        self.state = tmp / "fake-state"
        for d in (self.root, self.stick, self.state):
            d.mkdir(parents=True, exist_ok=True)
        for name, body in _BODY.items():
            where = "system/xbin" if name == "nemesis" else "system/bin"
            text = (_HEAD.replace("@NAME@", name).replace("@ALLOWED@", ALLOWED[name])
                    + body.replace("@FIXED_DATE@", FIXED_DATE))
            path = self.root / where / name
            _write_lf(path, text)
            path.chmod(0o755)
        self.root_posix = posix_path(self.root)
        self.stick_posix = posix_path(self.stick)
        self.state_posix = posix_path(self.state)
        self.set("cp.logdir", f"{self.root_posix}/{LOG_DIR}")

    # --- configuration -----------------------------------------------------------------------------
    def set(self, name, data):
        _write_lf(self.state / name, data)

    @property
    def log_dir(self):
        return self.root / LOG_DIR

    @property
    def run_dir(self):
        return self.stick / RUN_PARENT / TIMESTAMP

    def run_dir_posix(self):
        return f"{self.stick_posix}/{RUN_PARENT}/{TIMESTAMP}"

    # --- running -----------------------------------------------------------------------------------
    def run(self, script_name):
        script = STICK_DIR / script_name
        assert script.is_file(), f"the stick script {script} does not exist"
        env = dict(os.environ)
        env["PARKCAST_TEST_ROOT"] = self.root_posix
        env["FAKE_STATE"] = self.state_posix
        return subprocess.run([sh_executable(), script.as_posix()], cwd=str(self.stick), env=env,
                              capture_output=True, text=True, timeout=120)

    # --- reading the record ------------------------------------------------------------------------
    def calls(self):
        """[(index, name, [args])] for every call that was not rejected."""
        log = self.state / "calls.log"
        out = []
        if not log.is_file():
            return out
        for line in log.read_text(encoding="utf-8").split("\n"):
            if not line or line.startswith("REJECTED"):
                continue
            parts = line.split("\t")
            out.append((int(parts[0]), parts[1], [self.unit_path(a) for a in parts[2:]]))
        return out

    def rejected(self):
        log = self.state / "calls.log"
        if not log.is_file():
            return []
        return [ln for ln in log.read_text(encoding="utf-8").split("\n") if ln.startswith("REJECTED")]

    def unit_path(self, arg):
        """An argument with the fake root's prefix taken off, so it reads as the unit path."""
        if arg.startswith(self.root_posix + "/"):
            return arg[len(self.root_posix):].rstrip("/") or "/"
        return arg

    def stick_files(self):
        return sorted(p for p in self.stick.rglob("*") if p.is_file())

    def stick_text(self):
        return "\n".join(p.read_bytes().decode("utf-8", "replace") for p in self.stick_files())
