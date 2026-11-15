"""The S1 command, `python tools/s1.py` (AIR-031; OD-28, OD-29, OD-39, OD-41).

Exit codes: 0 on success, including a NO-GO verdict and a run that ended normally; 2 on a usage
error, with nothing changed; 1 on any other failure; 130 when a run is interrupted. Provenance and
output-location failures are found before UxPlay is launched.
"""

from __future__ import annotations

import argparse
import dataclasses
import datetime
import json
import os
import sys
from pathlib import Path
from typing import Callable

from airplay_s1 import analyser, build, evaluator, evidence, prereqs, runner

PROJECT = Path(__file__).resolve().parents[2]
SOUND_QUESTION = "Did sound from the iPhone play on this PC during the test? (yes/no)"
SOUND_NOTE = "sound played"
EXIT_OK = {runner.OWNER_ENDED, runner.TIMED_OUT, runner.UXPLAY_EXITED}


@dataclasses.dataclass
class Deps:
    work_root: Path
    runs_root: Path
    launcher: object
    clock: object
    owner: object
    upstream: object
    compiler: object
    inventory: Callable[[], prereqs.Inventory | None]

    @classmethod
    def real(cls, **overrides) -> "Deps":
        values = dict(work_root=build.DEFAULT_WORK_ROOT, runs_root=PROJECT / "runs",
                      launcher=runner.SubprocessLauncher(), clock=runner.SystemClock(), owner=runner.ConsoleOwner(),
                      upstream=build.GitUpstream(), compiler=build.MsysCompiler(),
                      inventory=prereqs.inventory_from_host)
        values.update(overrides)
        return cls(**values)


class Failure(Exception):
    """A failure the command reports on stderr and exits 1 for."""


def _parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="s1.py", allow_abbrev=False,
                                description="S1: the PC feasibility test for an AirPlay mirroring receiver.")
    sub = p.add_subparsers(dest="command", metavar="{check,build,run,analyse,evaluate,summary}", required=True)
    sub.add_parser("check", allow_abbrev=False, help="check this PC's build prerequisites")
    sub.add_parser("build", allow_abbrev=False, help="build UxPlay 1.74 and record its provenance")
    sub.add_parser("run", allow_abbrev=False,
                   help="launch the verified build as HU-Test, then analyse, judge and record the run")
    a = sub.add_parser("analyse", allow_abbrev=False, help="analyse a UxPlay log")
    a.add_argument("log", type=Path, help="the UxPlay log to analyse")
    a.add_argument("-o", dest="output", type=Path, help="write the report here instead of printing it")
    e = sub.add_parser("evaluate", allow_abbrev=False,
                       help="analyse and judge a run folder's log, writing report.json and verdict.json beside it")
    e.add_argument("run_dir", type=Path, help="the run folder holding uxplay.log")
    s = sub.add_parser("summary", allow_abbrev=False, help="write a run's redacted summary")
    s.add_argument("run_dir", type=Path, nargs="?", help="the run folder (default: the newest run)")
    s.add_argument("--tier", choices=("offline", "pc"), help="the tier (default: the one the run recorded)")
    s.add_argument("-o", dest="dest", type=Path, help="the folder for the summary (default: docs/evidence)")
    return p


def _err(message: str) -> None:
    print(f"s1.py: {message}", file=sys.stderr)


def _write_json_files(files: dict[Path, object]) -> None:
    """Write every file or none: a location that can't be written leaves nothing behind."""
    for path in files:
        if path.is_dir():
            raise Failure(f"{path} can't be written: a folder is in the way")
    done: list[Path] = []
    try:
        for path, data in files.items():
            tmp = path.with_name(path.name + ".tmp")
            try:
                tmp.write_text(json.dumps(data, indent=2), encoding="utf-8")
                os.replace(tmp, path)
            except OSError as e:
                tmp.unlink(missing_ok=True)
                raise Failure(f"{path} can't be written: {e}") from None
            done.append(path)
    except Failure:
        for path in done:
            path.unlink(missing_ok=True)
        raise


# --- commands ---------------------------------------------------------------------------------

def _inventory(deps: Deps) -> prereqs.Inventory:
    inventory = deps.inventory()
    if inventory is None:
        raise Failure("couldn't take this PC's build inventory")
    return inventory


def cmd_check(args, deps: Deps) -> int:
    result = prereqs.check(_inventory(deps))
    if result.ok:
        print("All build prerequisites are present.")
        return 0
    print("Missing: " + ", ".join(result.missing))
    return 1


def cmd_build(args, deps: Deps) -> int:
    result = build.build(inventory=_inventory(deps), upstream=deps.upstream, compiler=deps.compiler,
                         work_root=deps.work_root, owner=deps.owner)
    if result.status == "built":
        print(f"Built UxPlay {result.provenance.version} at commit {result.provenance.commit}: "
              f"{result.provenance.executable}")
        return 0
    raise Failure(f"build {result.status}: {result.error}")


def _new_run_dir(runs_root: Path) -> Path:
    try:
        runs_root.mkdir(parents=True, exist_ok=True)
    except OSError as e:
        raise Failure(f"the runs folder {runs_root} can't be written: {e}") from None
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    for n in range(100):
        candidate = runs_root / (stamp if n == 0 else f"{stamp}-{n}")
        try:
            candidate.mkdir()
            return candidate
        except FileExistsError:
            continue
        except OSError as e:
            raise Failure(f"the runs folder {runs_root} can't be written: {e}") from None
    raise Failure(f"the runs folder {runs_root} has no free name for a new run")


def _sound_note(owner) -> dict:
    answer = owner.ask(SOUND_QUESTION)
    answer = answer.strip().lower() if isinstance(answer, str) else ""
    value = {"y": "yes", "yes": "yes", "n": "no", "no": "no"}.get(answer)
    return {SOUND_NOTE: value} if value else {}


def _add_tier(run_dir: Path, tier: str) -> None:
    path = run_dir / runner.RECORD
    data = json.loads(path.read_text(encoding="utf-8"))
    data["tier"] = tier
    _write_json_files({path: data})


def cmd_run(args, deps: Deps) -> int:
    try:
        provenance = build.load_verified(deps.work_root)
    except build.NotVerified as e:
        raise Failure(f"no usable build: {e}") from None
    run_dir = _new_run_dir(Path(deps.runs_root))
    record = runner.run(provenance, run_dir, launcher=deps.launcher, clock=deps.clock, owner=deps.owner)
    for error in record.errors:
        _err(error)
    print(f"Run folder: {run_dir}")
    print(f"Run ended: {record.reason}")
    if not record.record_written:
        return 130 if record.reason == runner.INTERRUPTED else 1
    if record.reason not in EXIT_OK:
        _add_tier(run_dir, "pc")
        return 130 if record.reason == runner.INTERRUPTED else 1
    notes = _sound_note(deps.owner)
    report = analyser.analyse(run_dir / runner.LOG, command=record.command)
    judgement = evaluator.judge(report, notes=notes, ended_at=record.ended_at, end_reason=record.reason)
    try:
        found = evidence.write(run_dir, record=dict(record.to_dict(), tier="pc"), report=report,
                               judgement=judgement, owner=deps.owner)
    except OSError as e:
        raise Failure(f"the run's evidence in {run_dir} couldn't be completed: {e}") from None
    print(f"Verdict: {judgement.verdict} ({judgement.reason})")
    if found.incomplete:
        print("Evidence incomplete, missing: " + ", ".join(found.missing))
    return 0


def cmd_analyse(args, deps: Deps) -> int:
    if not args.log.is_file():
        raise Failure(f"no log at {args.log}")
    report = analyser.analyse(args.log)
    if args.output is None:
        print(json.dumps(report.to_dict(), indent=2))
    else:
        _write_json_files({args.output: report.to_dict()})
        print(f"Report: {args.output}")
    return 0


def cmd_evaluate(args, deps: Deps) -> int:
    log = args.run_dir / runner.LOG
    if not log.is_file():
        raise Failure(f"no log at {log}")
    record_path = args.run_dir / runner.RECORD
    record = {}
    if record_path.is_file():
        try:
            record = json.loads(record_path.read_text(encoding="utf-8"))
        except (OSError, ValueError) as e:
            raise Failure(f"can't read the run record {record_path}: {e}") from None
    report = analyser.analyse(log, command=record.get("command"))
    judgement = evaluator.judge(report, notes=record.get("notes") or {}, ended_at=record.get("ended_at"),
                                end_reason=record.get("reason"))
    _write_json_files({args.run_dir / "report.json": report.to_dict(),
                       args.run_dir / "verdict.json": judgement.to_dict()})
    print(f"Verdict: {judgement.verdict} ({judgement.reason})")
    return 0


def cmd_summary(args, deps: Deps) -> int:
    try:
        run_dir = args.run_dir or evidence.latest_run(deps.runs_root)
    except OSError as e:
        raise Failure(str(e)) from None
    tier = args.tier
    if tier is None:
        try:
            tier = json.loads((Path(run_dir) / runner.RECORD).read_text(encoding="utf-8")).get("tier")
        except (OSError, ValueError) as e:
            raise Failure(f"can't read the run record in {run_dir}: {e}") from None
        if tier is None:
            raise Failure(f"the run in {run_dir} records no tier; give --tier")
    try:
        path = evidence.write_summary(args.dest or PROJECT / "docs" / "evidence", run_dir, tier=tier)
    except (OSError, ValueError, KeyError) as e:
        raise Failure(f"couldn't make the summary for {run_dir}: {e}") from None
    print(f"Summary: {path}")
    return 0


COMMANDS = {"check": cmd_check, "build": cmd_build, "run": cmd_run, "analyse": cmd_analyse,
            "evaluate": cmd_evaluate, "summary": cmd_summary}


def main(argv=None, deps: Deps | None = None) -> int:
    try:
        args = _parser().parse_args(list(sys.argv[1:] if argv is None else argv))
    except SystemExit as e:  # --help (0) and usage errors (2)
        return e.code if isinstance(e.code, int) else 2
    try:
        return COMMANDS[args.command](args, deps if deps is not None else Deps.real())
    except Failure as e:
        _err(str(e))
        return 1
    except KeyboardInterrupt:
        _err("interrupted")
        return 130
