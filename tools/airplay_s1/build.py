"""Build UxPlay 1.74 from a recorded upstream commit, and keep its provenance (AIR-001, AIR-002;
OD-05, OD-14, OD-20). A build never leaves a stale or partial executable selectable: the saved
provenance is removed before compiling and written only after a successful build."""

from __future__ import annotations

import dataclasses
import hashlib
import json
import os
import subprocess
from pathlib import Path

from airplay_s1 import prereqs

EXPECTED_VERSION = "1.74"
UPSTREAM = "https://github.com/FDH2/UxPlay.git"
DEFAULT_WORK_ROOT = Path(os.environ.get("AIRPLAY_S1_ROOT", r"C:\uxplay-s1"))
PROVENANCE = "provenance.json"


class BuildError(Exception):
    pass


class ProvenanceError(Exception):
    pass


class NotVerified(Exception):
    pass


@dataclasses.dataclass
class Provenance:
    commit: str
    version: str
    build_root: str
    source_dir: str
    build_dir: str
    executable: str
    sha256: str
    dependencies: dict
    commands: list

    def to_dict(self) -> dict:
        return dataclasses.asdict(self)


@dataclasses.dataclass
class BuildResult:
    status: str  # built, failed, interrupted, stopped, blocked
    check: prereqs.Check
    provenance: Provenance | None = None
    error: str | None = None


def sha256(path: Path) -> str:
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def save_provenance(prov: Provenance, work_root) -> Path:
    path = Path(work_root) / PROVENANCE
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(prov.to_dict(), indent=2), encoding="utf-8")
    os.replace(tmp, path)
    return path


def provenance_path(work_root) -> Path:
    return Path(work_root) / PROVENANCE


def load_provenance(work_root) -> Provenance:
    path = provenance_path(work_root)
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        return Provenance(**data)
    except FileNotFoundError:
        raise ProvenanceError(f"no build provenance at {path}") from None
    except (OSError, UnicodeDecodeError) as e:
        raise ProvenanceError(f"can't read build provenance {path}: {e}") from None
    except (ValueError, TypeError) as e:
        raise ProvenanceError(f"{path} isn't valid build provenance: {e}") from None


def load_verified(work_root) -> Provenance:
    """The recorded build, if its executable still has the recorded SHA-256."""
    try:
        prov = load_provenance(work_root)
    except ProvenanceError as e:
        raise NotVerified(str(e)) from None
    exe = Path(prov.executable)
    if not exe.is_file():
        raise NotVerified(f"the recorded executable {exe} doesn't exist")
    if sha256(exe) != prov.sha256:
        raise NotVerified(f"{exe}'s SHA-256 differs from its provenance")
    return prov


def build(*, inventory, upstream, compiler, work_root, owner, pinned: Provenance | None = None) -> BuildResult:
    work_root = Path(work_root)
    check = prereqs.check(inventory)
    if not check.ok:
        return BuildResult("blocked", check, error="missing prerequisites: " + ", ".join(check.missing))
    commit = pinned.commit if pinned else upstream.master_commit()
    version = upstream.version_at(commit)
    if version != EXPECTED_VERSION:
        owner.ask(f"Upstream master is at commit {commit}, which is UxPlay version {version}, not "
                  f"{EXPECTED_VERSION}. Which version should be built?")
        return BuildResult("stopped", check, error=f"commit {commit} is UxPlay {version}, not {EXPECTED_VERSION}")
    provenance_path(work_root).unlink(missing_ok=True)  # nothing stays selectable while this build runs
    source_dir, build_dir = work_root / "src", work_root / "build"
    try:
        upstream.checkout(commit, source_dir)
        exe = Path(compiler.compile(str(source_dir), str(build_dir)))
    except BuildError as e:
        return BuildResult("failed", check, error=f"build failed: {e}")
    except KeyboardInterrupt:
        return BuildResult("interrupted", check, error="build interrupted (keyboard interrupt); nothing recorded")
    if not exe.is_file():
        return BuildResult("failed", check, error=f"the build produced no executable at {exe}")
    prov = Provenance(commit=commit, version=version, build_root=str(work_root), source_dir=str(source_dir),
                      build_dir=str(build_dir), executable=str(exe), sha256=sha256(exe),
                      dependencies=dict(inventory.packages), commands=list(compiler.commands))
    save_provenance(prov, work_root)
    return BuildResult("built", check, provenance=prov)


# --- the real backends (used by HostBuild and `s1.py build`) --------------------------------

class GitUpstream:
    """FDH2/UxPlay on GitHub, read with git."""

    def __init__(self, url: str = UPSTREAM):
        self.url = url

    def master_commit(self) -> str:
        out = subprocess.run(["git", "ls-remote", self.url, "refs/heads/master"], capture_output=True, text=True,
                             check=True, timeout=120).stdout
        return out.split()[0]

    def _fetch(self, commit: str, dest: Path) -> None:
        dest.mkdir(parents=True, exist_ok=True)
        if not (dest / ".git").exists():
            subprocess.run(["git", "init", "-q", str(dest)], check=True)
        subprocess.run(["git", "-C", str(dest), "fetch", "-q", "--depth", "1", self.url, commit], check=True,
                       timeout=600)
        subprocess.run(["git", "-C", str(dest), "checkout", "-q", "--detach", commit], check=True)

    def version_at(self, commit: str) -> str:
        probe = DEFAULT_WORK_ROOT / "version-probe"
        self._fetch(commit, probe)
        for line in (probe / "uxplay.cpp").read_text(encoding="utf-8", errors="replace").splitlines():
            if line.startswith("#define VERSION"):
                return line.split('"')[1]
        raise BuildError(f"no VERSION in uxplay.cpp at {commit}")

    def checkout(self, commit: str, dest) -> None:
        self._fetch(commit, Path(dest))


class MsysCompiler:
    """cmake and ninja in MSYS2's UCRT64 environment, as UxPlay's Windows instructions say."""

    commands = ["cmake -S <src> -B <build> -G Ninja", "ninja -C <build>"]

    def compile(self, source_dir, build_dir) -> Path:
        src, out = Path(source_dir).as_posix(), Path(build_dir).as_posix()
        res = prereqs.ucrt64(f"cmake -S '{src}' -B '{out}' -G Ninja && ninja -C '{out}'", timeout=3600)
        exe = Path(build_dir) / "uxplay.exe"
        if res.returncode != 0 or not exe.is_file():
            raise BuildError((res.stdout + res.stderr)[-2000:] or f"exit {res.returncode}")
        return exe


class ConsoleAsker:
    def ask(self, question: str) -> str | None:
        print(question)
        try:
            return input("> ").strip() or None
        except EOFError:
            return None


class HostBuild:
    """The real build on this PC (@host scenarios)."""

    def __init__(self, upstream=None, compiler=None, owner=None):
        self.upstream = upstream or GitUpstream()
        self.compiler = compiler or MsysCompiler()
        self.owner = owner or ConsoleAsker()
        self.inventory = None

    def check_and_save(self, dest) -> prereqs.Check:
        dest = Path(dest)
        dest.mkdir(parents=True)
        self.inventory = prereqs.inventory_from_host()
        (dest / "inventory.json").write_text(json.dumps(self.inventory.to_dict(), indent=2), encoding="utf-8")
        return prereqs.check(self.inventory)

    def build_clean(self, dest, pinned: Provenance | None = None) -> BuildResult:
        dest = Path(dest)
        dest.mkdir(parents=True)
        return build(inventory=self.inventory or prereqs.inventory_from_host(), upstream=self.upstream,
                     compiler=self.compiler, work_root=dest, owner=self.owner, pinned=pinned)
