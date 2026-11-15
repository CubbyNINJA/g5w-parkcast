# Third review: S1 step definitions (substitute reviewer)

> Reviewer: a fresh-context Claude subagent standing in for `gpt-6-astra`, which had hit its Codex usage limit (OD-30). Same role prompt (`tools/astra/roles/astra-scenario-reviewer.md`) and prompt (`tools/astra/prompts/S1_REVIEW_STEPS_3.md`), with STAGE_ROOT as the project root. Read-only; it ran the default offline suite only.
> Date 2026-11-14.

**External critique, not an owner decision. Substitute for Astra under OD-30.**

---

## Verdict

**Not blocked. Step 7 may start, with the clarifications below.**

R3, R4, R5, R7, R8, R9, R10 and R11 are fixed in the code. R1, R2 and R6 are now much narrower than before, but each still has a gap of the same kind it had before. The checks still trust two things that come from the code under test: the process ID and the command line that `SubprocessLauncher` reports. And the saved firewall lists are compared with Windows only for owned rules. All of these are oracle problems in the `@host` and `@pc` tiers. The offline tier, which step 7 implements against, has no remaining blocker. Fix F1 to F3 before any `--run-host` or `--run-pc` result counts as evidence. They only change tests, apart from the one interface sentence under Requirement wording.

I ran the default tier: 127 failed, 3 passed and 9 skipped (5 `@host`, 4 `@pc`). Every failure but one is `ModuleNotFoundError: airplay_s1`. The remaining one is the entry-point scenario, which fails because `tools/s1.py` doesn't exist yet. That is the right reason. Substitute reviewer under OD-30. External critique, not an owner decision.

## Findings

Paths are relative to the stage root.

| # | Severity | File:line | Finding | Concrete fix |
|---|---|---|---|---|
| F1 | major | `tests/steps/live_steps.py:96-98`, `:344-350`, `:192` | **R6 is only partly fixed.** The "independent" PID is `proc.pid` on the object that the real `SubprocessLauncher` returns, and that launcher is code under test. Surviving mutation: the launcher starts UxPlay through a wrapper such as `cmd /c` and reports the wrapper's PID. Stopping kills the wrapper, `uxplay.exe` keeps running, and `tasklist` on the wrong PID passes. The kill scenario (`:192`) kills that same untrusted PID. | Just after `self.inner.start(...)` in `RecordingLauncher.start`, query Windows: `powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='uxplay.exe'\" \| Select-Object ProcessId,ExecutablePath,CommandLine \| ConvertTo-Json"`. Save the result as `self.os_procs`. Require exactly one row whose `ExecutablePath` equals `provenance.executable` (case-insensitive) and whose `ProcessId == proc.pid`. In the Then at `:341`, run the same query again and require that no row has that `ExecutablePath`, as well as the PID check that's there now. |
| F2 | major | `tests/steps/live_steps.py:93-95`, `:312-319` | **R1 is only partly fixed.** The check now sees the argv the runner hands to the real launcher, which closes the runner-level mutation. It never sees the command line Windows actually ran. `SubprocessLauncher` is code under test, and no tier tests it, since offline uses `FakeLauncher`. Surviving mutation: `SubprocessLauncher.start` adds or drops a flag, such as `-nh` or `-s`, before it calls `Popen`. | Using the F1 query, assert that `CommandLine` for `proc.pid` equals `subprocess.list2cmdline(argv)`, or that the parsed tokens equal `argv`. Store it as `self.os_cmdline` and check it in the Then at `:312`, next to the existing argv checks. |
| F3 | major | `tests/steps/live_steps.py:200-207` | **R2 is partly fixed, and the Then promises more than it checks.** "The firewall rule lists from before, during and after the run are saved" is compared with Windows only through `_saved_mine`, which keeps owned rules only. Surviving mutation: the runner writes `firewall-before.json` and `firewall-after.json` as `[]`, or leaves out every unrelated rule. With no leftover, both sides are `[]` and the check passes. The saved evidence then can't show what `:239` claims "they show". | Inside the loop at `:205`, also assert `Counter(r["name"] for r in saved[k]) == Counter(w["DisplayName"] for w in windows[k])`. This checks that the saved list is complete, not just its owned subset. If ids map to Windows `Name`, compare `{r["id"]}` with `{w["Name"]}` as well. |
| F4 | minor | `tests/steps/live_steps.py:56-60` | The oracle runs inside `create()`. If PowerShell fails or times out (600 s), `CalledProcessError` or `TimeoutExpired` reaches the runner as a failed rule creation, even though the rule now exists. That can cause misleading cleanup or a rule left behind, and the test blames the code under test. | `try: self.ps_during = _powershell_rules()` / `except Exception as e: self.ps_error = e`, then always `return made`. Add `assert world.real_fw.ps_error is None` at `:204`. |
| F5 | minor | `tests/steps/live_steps.py:126-128`, `:220-228` | `Enabled` isn't part of the owned-rule tuple. A rule created disabled passes "…inbound, for only the built uxplay.exe and on the Public profile while the run was active". | At the end of the Then at `:220`, add `assert [r["Enabled"] for r in world.real_fw.ps_during if _ps_owned(r, world.build_root)] == ["True"]`. |
| F6 | minor | `tests/steps/live_steps.py:327`, `:336` | With a real clock, `ended_at == deadline` and `ended_at <= deadline` can only pass if the runner writes the scheduled deadline instead of the time it saw. A runner that records `now()` at 600.2 s fails, so the check pushes implementations toward the synthesized timestamp R5 was about. | Replace `:327` with `assert rec["launched_at"] <= rec["ended_at"]`. For timeout, replace `:336` with `assert rec["deadline"] <= rec["ended_at"] <= rec["deadline"] + 5`. Otherwise assert `rec["ended_at"] <= rec["deadline"]`. Or get an owner ruling that a timeout's `ended_at` is the deadline, and write that into `S1_INTERFACES.md:82`. |
| F7 | minor | `tests/steps/log_steps.py:337-341`, `:388` | Offline session spans still come from the analyser's own `start_line` (code under test), the problem R8 fixed for the live tier only. Surviving mutation: the analyser shifts session 2's `start_line` later, and session 1 then cites session 2's lines and passes `:388`. | In the Given at `:266-269`, also record `world.starts.append(log_line(world, start))`. In the Then at `:382`, assert `[s.start_line for s in analyse(world).sessions] == world.starts`, and build each span from `world.starts`. |
| F8 | minor | `tests/steps/live_steps.py:444-445` | `zip(verdict sessions, spans)` truncates without warning. A re-derived verdict that drops sessions still passes "every cited line…". | Before `:444`, add `assert len(world.rederived_verdict["sessions"]) == len(spans)`. |
| F9 | minor | `tests/steps/live_steps.py:109` | Empty output from the snapshot is read as "zero rules". If it happens before and after, `:241` and `:356` ("every rule… unchanged") pass with nothing compared. | Replace with `assert out.strip(), "firewall_snapshot.ps1 listed no rules"` and `rows = json.loads(out)`. |
| F10 | minor | `tests/steps/live_steps.py:138`; `docs/spec/S1_INTERFACES.md:75` | The steps assume each `firewall-*.json` row is `Rule(**row)`, and that `during` is listed while the run's rule exists. The interface specifies neither, so step 7 could choose a different shape and fail with a `TypeError`. | Add the interface sentence under Requirement wording. |
| F11 | minor | `tests/steps/live_steps.py:138` | `_saved_mine` uses `fw.is_owned`, which is code under test, to pick the saved owned rows. That is an oracle taken from the code under test, even though the Windows side is independent. | Use the test's own predicate: `_ps_owned({"DisplayName": r["name"], "Program": r["program"]}, build_root)`. |
| F12 | minor | `tests/steps/live_steps.py:211-217` | "They show the leftover rule removed…" checks only the event log: the runner's remove call and the backend's own listing. It never shows the leftover in Windows's before-list and missing from its during-list. If the Given's `WindowsFirewall().create` (`:167`) did nothing, the scenario has no proof the leftover ever existed. | Add `assert world.leftover.program.lower() in [m[1] for m in _ps_mine(world.ps_before, world.build_root)]` and the same `not in` check against `world.real_fw.ps_during`. |
| F13 | minor | `AGENTS.md:55-58`; `tests/steps/firewall_snapshot.ps1:3` | The docs say port filters "can't" be read without admin rights, so non-elevated runs "fail at once". But the parent's dry run read 944 rules without elevation. Either the claim is wrong or the dry run didn't read port filters. In both cases, nothing enforces elevation. | In `tests/conftest.py` `pytest_collection_modifyitems`, when `--run-host` or `--run-pc` is given and `ctypes.windll.shell32.IsUserAnAdmin() == 0`, add `pytest.mark.skip(reason="needs an elevated terminal")`, or fail the run. Then reword `AGENTS.md:57-58` to match what was actually seen. |
| F14 | minor | `tests/steps/build_steps.py:293-294` | On the host, saved `dependencies` are compared only with the returned provenance, which also comes from the code under test, and need only be nonempty. They aren't checked against the saved inventory. | Load `world.inventory_dir / "inventory.json"` and assert `saved.dependencies == {p: inv["packages"][p] for p in s1("prereqs").REQUIRED_PACKAGES}`, or that it is a subset holding every required package. |

## Disposition of R1 to R11

| # | Result | Evidence |
|---|---|---|
| R1 | Partially resolved | The live run now goes through `cli.main(["run"], deps=Deps.real(firewall=…, launcher=…))` (`live_steps.py:270-271`). The argv received is checked against flags the test writes out itself (`:35`, `:314-318`), and `run.json` must agree (`:319`). The command Windows actually ran is not observed (F2). |
| R2 | Partially resolved | Windows is snapshotted on its own before, during and after (`firewall_snapshot.ps1:5-22`; `live_steps.py:59`, `:149`, `:154`, `:268`, `:272`). Ownership is worked out from Windows fields (`:113-118`). Unrelated rules are compared as full rows (`:121-122`, `:241`, `:356`). Gaps: the saved lists are compared for owned rules only (F3), the saved side uses `is_owned` (F11), and `Enabled` isn't checked (F5). |
| R3 | Resolved | The scenario is at `02_firewall.feature:89-95`. `remove_no_effect` keeps the rule (`fakes.py:123-124`). The Then requires `residual_rule.id` to equal the created id, both name and program in the errors, and `cleanup_clean is False` (`run_steps.py:282-293`). The scenario fails red for the right reason. |
| R4 | Resolved | Single session: the deciding lines must be in the run's cites, which must be a subset of the session's (`log_steps.py:356-361`). Matrix: each deciding line is worked out from the fixture (`:265-269`) and cited (`:386-388`), the run has a reason (`:389`), and the run cites a deciding line of a session with a matching verdict (`:390-391`). The leftover span-oracle problem is F7. |
| R5 | Resolved | The test keeps its own monotonic launch and stop times (`live_steps.py:77-79`, `:95`). The saved duration must be within 2 s of them (`:332-333`), and a timeout must land 598 to 605 s after launch (`:334-335`). The over-strict `ended_at == deadline` is F6. |
| R6 | Partially resolved | `tasklist` must exit 0 (`live_steps.py:349`) and its CSV must not hold the PID (`:350`). But the PID comes from the code-under-test proc (`:96`, `:346`), see F1. |
| R7 | Resolved | Offline compares `build_root` (`build_steps.py:128`). Host keeps each requested directory (`:258`, `:265`), requires `build_root` to equal that directory with source, build and executable inside it (`:287-289`), requires every saved field to equal the returned one (`:283-292`), and recomputes the SHA-256 (`:295`). The dependency check is F14. |
| R8 | Resolved | Spans come from the log text (`live_steps.py:423-427`). Boundaries are compared (`:435`), every observation must fall in its own span and match its message (`:440-442`), and verdict cites must fall in their span (`:444-445`, `:478`). The zip gap is F8. |
| R9 | Resolved | `run_steps.py:394-395` asserts `0 <= first("fw.create") < first("proc.create.failed")`. |
| R10 | Resolved | The snapshot covers the whole project, `runs/` included. It leaves out only `.venv`, `__pycache__` and `.pytest_cache` (`cli_steps.py:130-133`), and the step says so (`08_cli.feature:71`, `cli_steps.py:156`). |
| R11 | Resolved | `08_cli.feature:3-6` uses the wording the review gave. |

## Missing scenarios

None. Every finding above can be fixed inside existing step bindings.

## Requirement wording

Add after `docs/spec/S1_INTERFACES.md:76`:

> Each `firewall-<k>.json` is a JSON list with one object per firewall rule, holding every `Rule` field (`name`, `program`, `direction`, `action`, `profiles` as a list, `ports`, `id`). It lists every rule, not only owned ones. `firewall-during.json` is listed after the run's rule is created and before UxPlay is stopped. `SubprocessLauncher` starts `uxplay.exe` directly, with no shell or wrapper process, so `proc.pid` is `uxplay.exe`'s own process ID.

If the owner keeps the current timeout behaviour, add to `:82`: "On a timeout, `ended_at` is the deadline."

## Traceability

No changes. 74 scenario declarations across the eight feature files match `docs/spec/TRACEABILITY.md:39`. `test_tag_rules.py` passes in the default run.

## Not verified

- I didn't run `firewall_snapshot.ps1`, any `--run-host` or `--run-pc` tier, or any firewall or process query, so I didn't check the 944-rule dry run or the exit 1 on unreadable port filters.
- From memory, and not checked: the `Get-NetFirewall*` cmdlets read `PersistentStore` by default, and filter `InstanceID` matches rule `InstanceID`. If either is wrong, owned rules created in another store would not be seen.
- Comparing all 944 unrelated rules over a run of up to 10 minutes may fail spuriously if Windows or another app changes a rule meanwhile. I didn't assess how likely that is.
- No implementation exists, so the step bindings past the `airplay_s1` import were checked by reading only. I didn't look outside the stage root, and changed no files.

---

## Applied by the parent (2026-11-14)

| # | What changed |
|---|---|
| F1 | Just after launch, `RecordingLauncher` asks Windows (`Win32_Process`) for every running `uxplay.exe`. Exactly one must have this build's executable path and the launcher's PID. The termination check and the kill scenario use that Windows-confirmed PID, and termination also requires that no process with this build's executable path is still running. |
| F2 | Windows's `CommandLine` for that process must equal `subprocess.list2cmdline(argv)`. |
| F3 | Each saved list must name the same rules, with the same counts, as Windows's listing at that point, not only the same owned rules. |
| F4 | A failure of the test's own snapshot inside `create()` is kept in `ps_error` and asserted on. It never reaches the runner. |
| F5 | The run's rule must be `Enabled = True` in Windows's listing. |
| F6 | Real-clock timestamps: `launched_at <= ended_at`. A timeout ends within 5 s after the deadline, and an owner end before it. The interface page says `ended_at` is the clock time the run saw. |
| F7 | Offline session spans and starts come from the fixture. The aggregation Then requires the analyser's start lines to match them. |
| F8 | The re-derived verdict must have one session per text-derived span before they're paired. |
| F9 | An empty snapshot is an error, not "no rules". |
| F10 | The interface page specifies the `firewall-<k>.json` shape and timing, and that `SubprocessLauncher` starts `uxplay.exe` directly. |
| F11 | The saved owned rows are chosen with the test's own predicate, not `is_owned`. |
| F12 | Windows's own listings must show the leftover rule before the run and not after the run's rule is created. |
| F13 | The suite refuses `--run-host` and `--run-pc` without elevation (exit 4). `AGENTS.md` now says exactly what the dry run saw: `Get-NetFirewallPortFilter -All` was refused without admin rights, and a copy with the port filters left out read 944 rules. |
| F14 | The host build's saved dependencies must match the saved inventory for every required package. |
