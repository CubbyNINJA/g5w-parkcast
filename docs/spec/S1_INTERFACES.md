# Slice S1: the interfaces the step definitions expect

The S1 implementation lives in `tools/airplay_s1/`, and `tools/s1.py` is its command (AIR-031,
OD-28, OD-29). This page lists what `tests/steps/` calls, so step 7 builds to the tests.

Owner decisions and reviewed requirements govern behaviour. Reviewed scenarios express that
behaviour; step definitions verify it. A disagreement among these sources is resolved through
the specification review process before implementation continues.

Every backend the runner and the build touch is passed in. Offline scenarios pass the fakes in
`tests/steps/fakes.py`; `@host` and `@pc` scenarios pass the real ones named below.

## `prereqs`

- `REQUIRED_PACKAGES`: the MSYS2 package names the build needs, including
  `mingw-w64-ucrt-x86_64-ninja`.
- `Inventory(ucrt64: bool, packages: dict[name, version], gst_elements: set[str])`.
- The check reports missing items by these exact names: each package name, `the UCRT64
  environment`, and `a GStreamer H.264 decoder`.

## `build`

- `build(inventory, upstream, compiler, work_root, owner, pinned=None) -> BuildResult` with
  `status` (`built`, `failed`, `stopped`, `blocked`), `check` (`ok`, `missing`), `provenance`
  and `error`.
  - It checks prerequisites first and compiles nothing if they fail.
  - With `pinned` it builds the pinned commit.
  - If master's version isn't 1.74, it asks `owner.ask()` naming the version and stops.
  - It removes any saved provenance before compiling, so a failed or interrupted build leaves
    nothing selectable.
  - It catches a keyboard interrupt during the compile and returns `status="interrupted"` with
    an `error` that says so.
- `Provenance` (a dataclass) has `commit`, `version`, `build_root`, `source_dir`, `build_dir`,
  `executable`, `sha256`, `dependencies` and `commands`.
- `save_provenance(prov, work_root)`, `load_provenance(work_root)` (raises `ProvenanceError`),
  and `load_verified(work_root)` (raises `NotVerified` if there's no provenance or the hash
  differs).
- `BuildError`, and `DEFAULT_WORK_ROOT` (outside the repository).
- The upstream backend has `master_commit()`, `version_at(commit)` and `checkout(commit, dest)`.
  The compiler backend has `compile(source_dir, build_dir) -> exe` and `commands`.
- `HostBuild()` is the real thing for `@host`. It has `check_and_save(dest)`, which writes
  `dest/inventory.json` (`ucrt64`, `packages`, `gst_elements`), and `build_clean(dest, pinned=None)`,
  which builds into a new `dest` and saves that build's provenance there, with `build_root=dest`.

## `firewall`

- `RULE_NAME = "NEMESIS-AirPlay-S1"`.
- `Rule` is a frozen dataclass: `name`, `program`, `direction` (`in`), `action` (`allow`),
  `profiles` (tuple), `ports` (`None` means none named), and `id`.
- `is_owned(rule, build_root)` applies OD-27. `FirewallError` is the error type.
- The backend has `active_profile()`, `list_rules()`, `create(rule) -> Rule` (with its id) and
  `remove(id)`. `WindowsFirewall()` is the real one.

## `logfile`

- `start_marker(t)`, `end_marker(reason, t)`, `is_start_marker(line)` and `is_end_marker(line)`
  (AIR-028).

## `runner`

- `run(provenance, run_dir, firewall, launcher, clock, owner, timeout=600) -> RunRecord`.
  - **Preflight (OD-29):** validate the provenance and the hash, and check that the run's output
    locations can be written, before any firewall change.
  - Remove stale owned rules, then confirm with a fresh listing that they're gone. A removal
    that reports success but leaves the rule in that listing stops the run, as does a foreign
    rule with the same name.
  - Create the rule, open the log, write the start marker, and launch.
  - If process creation raises, or capture fails after the rule exists (`CaptureError`, which
    carries the started `proc`), report it, stop any launched process, and remove the rule and
    verify its removal.
  - Otherwise tell the owner what to do, and wait until the owner ends the test, UxPlay exits,
    or the deadline passes.
  - Stop UxPlay and wait for it to exit. Only after it has exited, write the end marker, then
    remove the rule and verify its removal with a fresh listing.
- It saves `run.json` and `firewall-before.json`, `firewall-during.json` and
  `firewall-after.json` in `run_dir`.
- `RunRecord` has `started`, `command`, `launched_at`, `deadline`, `ended_at`, `reason`,
  `exit_code`, `errors`, `rule`, `cleanup_ran`, `cleanup_clean`, `residual_rule`,
  `residual_pid`, `pid` and `to_dict()`. `run.json` holds `to_dict()`; the CLI's `run` command
  adds `tier: "pc"`.
- `launched_at` is the clock time when the process started, and `ended_at` is when the run
  decided to end. Errors name the rule, its program, or the process ID they concern.
- `reason` is one of `owner ended`, `timeout`, `failed to start`, `uxplay exited`,
  `interrupted`, `capture failed` or `refused`.
- `build_command(executable)` gives `[exe, -n, HU-Test, -nh, -s, 1920x720@60, -d]`, in any
  order, with no `-h265` or capture options.
- The launcher has `start(argv, log) -> proc`, where `log` is a binary file. A proc has `pid`,
  `poll()`, `stop(timeout) -> bool` and `alive`.
- The clock has `now()` and `sleep(s)`.
- The owner has `tell(msg)`, `ended(now) -> bool` and `ask(question) -> str | None`. The
  message to end the test mentions Enter.
- Real backends are `SubprocessLauncher()`, `SystemClock()` and the console owner, plus
  `process_running(pid)`.

## `analyser`

- `analyse(log_path, command=None) -> Report`.
- `Report` has `complete`, `limitation`, `lines_read`, `sessions`, `advertised_size`,
  `advertised_source` (`launch configuration`) and `to_dict()`.
- `Session` has `start_line`, `model`, `connection`, `arrived`, `started`, `codec` (value
  `H.264` or `HEVC`), `resolution` (value `(w, h)`), `audio_format` (value
  `ct=<n> audioFormat=<hex>`), `hevc_request`, `hevc_stream` and `ios_version`.
- Each observation has `value` and `line`, where `line` is the 1-based line in the log file. In
  `to_dict()`, an observation also carries the matched `message` text.

## `evaluator`

- `judge(report, notes, ended_at, end_reason) -> Judgement`.
- `Judgement` has `verdict`, `reason`, `limitation`, `cites`, `sessions`, `notes`,
  `examined_to_line`, `endpoint` and `to_dict()`.
  - The run's `cites` are the lines its deciding sessions rest on.
  - An evidence error never cites a line that doesn't exist or can't be read.
- Each session verdict has `verdict`, `reason`, `limitation` and `cites` (log line numbers
  inside that session).
  - GO cites the codec line, and an HEVC NO-GO cites the refusal line.
  - A verdict that rests on something missing cites the connection line, and its reason says
    `read to line <N>` and `ended at second <T>`.

## `evidence`

- `write(run_dir, record, report, judgement, owner, ios=None) -> Evidence`.
  - It writes `report.json` and `verdict.json`, and adds `ios`
    (`{value, provenance}`), `notes` and `evidence_complete` to `run.json`.
  - It asks the owner for the full version and build unless both are known.
- `Evidence` has `ios`, `ios_provenance`, `missing` (`ios version`, `ios build`) and
  `incomplete`.
- `summary(run_dir, tier) -> str` and `write_summary(dest_dir, run_dir, tier) -> Path`.
  - Summaries never contain client names, deviceIDs or addresses.
  - They name the tier (`Tier: <tier>`) and say the head unit is untested.
  - An offline summary calls itself a synthetic fixture result that is not evidence about an
    iPhone, and makes no compatibility claim.
- `screen_content(dirs)` lists video, image and stream-dump files. `latest_run(runs_root)`
  returns the newest run folder.

## `boundary`

- `check(repo_root) -> list[str]`: a `.feature` file or a `pytest_bdd` import outside
  `Sources/airplay-receiver/`, each listed by its repository-relative path.

## `cli`

- `main(argv, deps=None) -> int`, and
  `Deps(work_root, runs_root, firewall, launcher, clock, owner, upstream, compiler, inventory)`.
- With no `deps`, it uses the real backends.
- Commands: `check`, `build`, `run`, `analyse <log> [-o report]` and `evaluate <run-dir>`.
  `evaluate` writes `report.json` and `verdict.json` beside the log, or neither. The last command
  is `summary`.
