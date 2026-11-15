"""Launch the verified UxPlay build as HU-Test, wait for the owner, and always stop it (AIR-006,
AIR-007, AIR-013, AIR-028, AIR-033, AIR-034; OD-08, OD-14, OD-31, OD-33 to OD-35, OD-38, OD-41, OD-42).

The firewall is the owner's: Windows asks when UxPlay first listens. The runner only tells the
owner about that prompt before launch and reminds them afterwards how to remove the allowance.
Every backend (launcher, clock, owner) is passed in; the real ones are at the end of this file.
"""

from __future__ import annotations

import dataclasses
import json
import os
import subprocess
import sys
import time
from pathlib import Path

from airplay_s1 import build, logfile, prereqs

NAME = "HU-Test"
DISPLAY = "1920x720@60"
TIMEOUT = 600
POLL = 0.5
STOP_TIMEOUT = 10.0
FIREWALL = "owner-handled"
LOG = "uxplay.log"
RECORD = "run.json"

OWNER_ENDED, TIMED_OUT, FAILED_TO_START = "owner ended", "timeout", "failed to start"
UXPLAY_EXITED, INTERRUPTED, CAPTURE_FAILED, REFUSED = "uxplay exited", "interrupted", "capture failed", "refused"


class CaptureError(Exception):
    """UxPlay started, but its output couldn't be attached to the run's log. Carries the started process."""

    def __init__(self, message: str, proc=None):
        super().__init__(message)
        self.proc = proc


@dataclasses.dataclass
class RunRecord:
    started: bool = False
    command: list | None = None
    launched_at: float | None = None
    deadline: float | None = None
    ended_at: float | None = None
    reason: str | None = None
    exit_code: int | None = None
    errors: list = dataclasses.field(default_factory=list)
    residual_pid: int | None = None
    pid: int | None = None
    record_written: bool = False
    commit: str | None = None

    def to_dict(self) -> dict:
        return {"started": self.started, "command": self.command, "launched_at": self.launched_at,
                "deadline": self.deadline, "ended_at": self.ended_at, "reason": self.reason,
                "exit_code": self.exit_code, "errors": list(self.errors), "residual_pid": self.residual_pid,
                "pid": self.pid, "commit": self.commit, "firewall": FIREWALL}


def build_command(executable) -> list[str]:
    """H.264 only (no -h265), as HU-Test, at the head unit's size, with debug logging. Never a
    capture or forwarding option (AIR-006, AIR-013)."""
    return [str(executable), "-n", NAME, "-nh", "-s", DISPLAY, "-d"]


def firewall_notice(executable) -> str:
    return (f"Windows may ask whether to allow uxplay.exe ({executable}) to communicate on networks. "
            "If it asks, tick Public networks and click Allow. If an earlier allowance or block for "
            "uxplay.exe already exists, no prompt may appear.")


def firewall_reminder(executable) -> str:
    return (f"The test is over. Open Windows Defender Firewall > Allowed apps and remove the entry for "
            f"uxplay.exe at {executable}. If you declined or closed Windows' prompt, it may have left a "
            "block entry for uxplay.exe in the same list: remove that too.")


def instructions(timeout: float) -> str:
    return (f'On the iPhone, open Control Center, tap Screen Mirroring and choose "{NAME}". '
            f"Press Enter here to end the test. It ends by itself after {int(timeout // 60)} minutes.")


def _write_record(record: RunRecord, run_dir: Path) -> None:
    path = run_dir / RECORD
    tmp = run_dir / (RECORD + ".tmp")
    try:
        tmp.write_text(json.dumps(record.to_dict(), indent=2), encoding="utf-8")
        os.replace(tmp, path)
        record.record_written = True
    except OSError as e:
        record.errors.append(f"the run record {path} couldn't be saved: {e}")
        try:
            tmp.unlink(missing_ok=True)
        except OSError:
            pass


def _ends_with_newline(path: Path) -> bool:
    with open(path, "rb") as f:
        f.seek(0, os.SEEK_END)
        if f.tell() == 0:
            return True
        f.seek(-1, os.SEEK_END)
        return f.read(1) == b"\n"


def _preflight(provenance, run_dir: Path) -> str | None:
    """Why the run can't launch, or None (OD-29, OD-39)."""
    exe = Path(provenance.executable)
    if not exe.is_file():
        return f"the recorded executable {exe} doesn't exist"
    if build.sha256(exe) != provenance.sha256:
        return f"{exe}'s SHA-256 differs from its provenance; it isn't the verified build"
    try:
        run_dir.mkdir(parents=True, exist_ok=True)
        (run_dir / LOG).open("wb").close()
    except OSError as e:
        return f"the run's output location {run_dir} can't be written: {e}"
    return None


def run(provenance, run_dir, launcher, clock, owner, timeout: float = TIMEOUT) -> RunRecord:
    run_dir = Path(run_dir)
    record = RunRecord(commit=getattr(provenance, "commit", None))

    # 1. Preflight: nothing is launched, and the owner gets no notice or reminder.
    refusal = _preflight(provenance, run_dir)
    if refusal:
        record.reason = REFUSED
        record.errors.append(refusal)
        return record

    executable = provenance.executable
    argv = build_command(executable)
    record.command = list(argv)
    proc = None

    # 2. The log and its start marker, before anything else can write to it.
    with open(run_dir / LOG, "ab") as log:
        log.write((logfile.start_marker(clock.now()) + "\n").encode("utf-8"))
        log.flush()

        # 3. Windows' prompt is the owner's to answer.
        owner.tell(firewall_notice(executable))

        # 4. Launch.
        try:
            proc = launcher.start(argv, log)
        except CaptureError as e:
            proc = e.proc
            record.reason = CAPTURE_FAILED
            record.errors.append(f"UxPlay's output couldn't be captured to the run's log: {e}")
        except OSError as e:
            record.reason = FAILED_TO_START
            record.errors.append(f"the UxPlay process couldn't be created: {e}")
        if proc is not None:
            record.pid = proc.pid
            record.launched_at = clock.now()
            record.deadline = record.launched_at + timeout
        if record.reason is None and proc.poll() is not None:
            record.reason = FAILED_TO_START
            record.errors.append(f"UxPlay (process {proc.pid}) exited at once with code {proc.poll()}")

        # 5. Wait for the owner, UxPlay or the deadline.
        if record.reason is None:
            record.started = True
            owner.tell(instructions(timeout))
            try:
                while True:
                    now = clock.now()
                    if proc.poll() is not None:
                        record.reason = UXPLAY_EXITED
                        break
                    if now >= record.deadline:
                        record.reason = TIMED_OUT
                        break
                    if owner.ended(now):
                        record.reason = OWNER_ENDED
                        break
                    clock.sleep(min(POLL, record.deadline - now))
            except KeyboardInterrupt:
                record.reason = INTERRUPTED
        record.ended_at = clock.now()

        # 6. Stop UxPlay and wait for it to exit.
        if proc is not None:
            if proc.poll() is None and not proc.stop(STOP_TIMEOUT) and proc.poll() is None:
                record.residual_pid = proc.pid
                record.errors.append(f"UxPlay (process {proc.pid}) is still running after the stop request")
                owner.tell(f"UxPlay didn't stop: process {proc.pid} is still running. End it in Task Manager.")
            record.exit_code = proc.poll()

        # 7. Only then the end marker, then the record. A stopped UxPlay can leave its buffered
        #    output cut off mid-line, so the marker starts a new line if the log doesn't (OD-50).
        log.flush()
        if not _ends_with_newline(run_dir / LOG):
            log.write(b"\n")
        log.write((logfile.end_marker(record.reason, record.ended_at, pid=record.residual_pid) + "\n").encode("utf-8"))
        log.flush()
    _write_record(record, run_dir)

    # 8. Last, the reminder, for every run that launched UxPlay.
    if proc is not None:
        owner.tell(firewall_reminder(executable))
    return record


# --- the real backends --------------------------------------------------------------------------

class SubprocessProc:
    def __init__(self, popen: subprocess.Popen):
        self.popen = popen
        self.pid = popen.pid

    def poll(self) -> int | None:
        return self.popen.poll()

    @property
    def alive(self) -> bool:
        return self.popen.poll() is None

    def stop(self, timeout: float = STOP_TIMEOUT) -> bool:
        if self.popen.poll() is None:
            self.popen.terminate()
        try:
            self.popen.wait(timeout)
        except subprocess.TimeoutExpired:
            return False
        return True


class SubprocessLauncher:
    """Starts uxplay.exe itself, with no shell or wrapper, its output going to the run's log. It
    gets its own process group, so the console's Ctrl+C reaches the runner, which then stops it.

    The MSYS2 build links against UCRT64's DLLs (GStreamer, libplist, ...), so that folder goes
    first on UxPlay's own PATH. Without it Windows refuses to start it (0xC0000135, DLL not found)."""

    def __init__(self, dll_dir=None):
        self.dll_dir = Path(dll_dir) if dll_dir else prereqs.MSYS2 / "ucrt64" / "bin"

    def start(self, argv, log) -> SubprocessProc:
        flags = getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0)
        env = dict(os.environ)
        env["PATH"] = str(self.dll_dir) + os.pathsep + env.get("PATH", "")
        popen = subprocess.Popen(list(argv), stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT,
                                 creationflags=flags, env=env)
        return SubprocessProc(popen)


class SystemClock:
    """Seconds since this clock was made, just before the run starts, so a run's times and the
    verdict's "ended at second T" read as time into the run. The run folder's name carries the date."""

    def __init__(self):
        self._zero = time.monotonic()

    def now(self) -> float:
        return round(time.monotonic() - self._zero, 3)

    def sleep(self, seconds: float) -> None:
        time.sleep(max(seconds, 0))


class ConsoleOwner:
    """The owner at this console. Enter ends the test; the check never blocks."""

    def tell(self, message: str) -> None:
        print(message, flush=True)

    def ended(self, now: float) -> bool:
        if sys.platform == "win32":
            import msvcrt
            pressed = False
            while msvcrt.kbhit():
                if msvcrt.getwch() in ("\r", "\n"):
                    pressed = True
            return pressed
        import select
        ready, _, _ = select.select([sys.stdin], [], [], 0)
        return bool(ready) and sys.stdin.readline() != ""

    def ask(self, question: str) -> str | None:
        print(question, flush=True)
        try:
            return input("> ").strip() or None
        except EOFError:
            return None


def process_running(pid: int) -> bool:
    """Whether a process with this ID is still running, asked of the operating system directly."""
    if sys.platform == "win32":
        import ctypes
        kernel32 = ctypes.windll.kernel32
        handle = kernel32.OpenProcess(0x1000, False, int(pid))  # PROCESS_QUERY_LIMITED_INFORMATION
        if not handle:
            return False
        try:
            code = ctypes.c_ulong()
            return bool(kernel32.GetExitCodeProcess(handle, ctypes.byref(code))) and code.value == 259  # STILL_ACTIVE
        finally:
            kernel32.CloseHandle(handle)
    try:
        os.kill(pid, 0)
    except OSError:
        return False
    return True
