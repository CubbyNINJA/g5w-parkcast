# Re-review: S1 step definitions

> Reviewer: `gpt-6-astra` via Codex CLI (`codex exec` read-only sandbox, role prompt `tools/astra/roles/astra-scenario-reviewer.md` as developer instructions), reasoning effort medium.
> Staged: docs, specs, tests, tools, package, AGENTS.md.
> Session `01a1dd40-cab1-77c0-b331-5ef01613fe10`, 1 turn(s), 165 s; tokens in 577420 (cached 506240), out 3485 (reasoning 406).
> Prompt: `tools/astra/prompts/S1_REVIEW_STEPS_2.md`. Date 2026-11-14.

**External critique, not an owner decision.**

---

## Verdict

**Blocked. Step 7 must not start until the blockers below are fixed.**

The repairs are substantial, but several assertions still accept the incorrect behavior identified previously. The new live timeout also relies entirely on runner-authored timestamps.

Read-only review, with an independent critic checking the live/evidence assertions. External critique, not an owner decision.

## Findings

Paths are relative to the staged root.

| # | Severity | File:line | Finding and surviving mutation | Concrete fix |
|---|---|---|---|---|
| R1 | blocker | `tests/steps/live_steps.py:222`, `:264` | **Prior 3 remains unresolved for PC execution.** `_live()` invokes the CLI without `RecordingLauncher`; the launch assertion reads only `run.json`. Launching with incorrect flags while recording compliant flags passes. | Instrument the real launcher used by that CLI invocation. Compare captured argv against independent expected arguments and the saved command. |
| R2 | blocker | `tests/steps/live_steps.py:83`, `:158`, `:162`, `:179`, `:198` | **Prior 14 and the live portion of 6 remain incomplete.** Saved snapshots can match *any* observed listing; `_same()` ignores direction, action, profiles and ports. Actual Windows inspection omits application/port filters. A backend can create an incorrectly restricted rule or change another rule’s program filter without detection. | Independently inspect Windows rule and associated filters during the run. Compare complete snapshots with their corresponding lifecycle boundaries. Preserve unrelated rules using independently evaluated name-and-program ownership, rather than excluding every matching display name. |
| R3 | blocker | `tests/steps/run_steps.py:37`; `specs/features/02_firewall.feature:29`, `:82` | **Prior 6 is incomplete for cleanup.** Silent removal failure is tested only before launch. Cleanup tests cover an exception or successful removal. A cleanup implementation that lists rules but ignores a still-present rule passes. | Exercise `remove_no_effect` during cleanup and require residual-rule reporting plus `cleanup_clean=False`. Ready-to-paste scenario below. |
| R4 | blocker | `tests/steps/log_steps.py:350`, `:353`; `specs/features/05_verdict.feature:54` | **Prior 8 remains partially unresolved.** Session citations must contain decisive lines, but run citations need only be a nonempty subset. A GO session citing connection and codec, with the run citing only connection, passes. Aggregation checks neither run reasons nor decisive citations. | Require the run’s citations to include evidence supporting its deciding verdict. For each aggregation row, assert a nonempty appropriate reason and decisive citations from the relevant session(s). Use fixture-derived line numbers. |
| R5 | blocker | `tests/steps/live_steps.py:219`, `:271` | **The new live timeout has a circular timing oracle.** A one-second run that writes `ended_at=deadline=launched_at+600` passes all timing assertions. | Record actual launch and stop-request times through test-owned instrumentation using a monotonic clock. Compare elapsed time with 600 seconds using an explicit measurement tolerance; compare saved timestamps with those observations. |
| R6 | major | `tests/steps/live_steps.py:284` | **Process termination remains weaker than its words.** The check trusts the saved PID and ignores `tasklist`’s return code. Failed inspection with empty stdout passes. | Retain the launched process identity independently; require successful process inspection and confirmed termination of that process. |
| R7 | major | `tests/steps/build_steps.py:125`, `:258`, `:280` | **Prior 5 is only partially resolved.** Offline provenance comparison omits `build_root`. Host checks retain neither requested clean destination as an independent expectation nor equality between saved and returned commit/dependency/command fields; those fields need only be nonempty. Ignoring the fresh destinations and returning other existing roots can pass. | Retain both requested destinations and immutable provenance expectations. Assert each actual source/build location belongs to its requested fresh root; compare every saved provenance field, including `build_root`, against the corresponding expected build. |
| R8 | major | `tests/steps/live_steps.py:363`, `:396` | **Prior 12’s session-membership repair is incomplete.** Observation references are checked against the whole log. GO boundaries come from the analyser being checked. Incorrectly merged sessions or observations attributed across sessions can pass. | Derive connection boundaries independently from retained log text, compare reported boundaries, and require each observation and verdict citation to belong to its independently identified session. |
| R9 | major | `tests/steps/run_steps.py:392`; `specs/features/03_runner.feature:77` | **The new process-creation-error scenario does not prove its stated ordering.** It requires a creation failure and eventual rule cleanup, but never asserts the rule existed before the creation attempt. Attempting launch first, then creating and removing a rule, passes. | Assert `0 <= first("fw.create") < first("proc.create.failed")`, alongside the existing cleanup assertions. |
| R10 | major | `tests/steps/cli_steps.py:130`, `:156` | **Prior 16’s subprocess addition has a narrower oracle than its Then.** `_project_files()` excludes `runs/`; `--help` can create or modify run output and still satisfy “no file in the project is created or changed.” | Include `runs/` in before/after snapshots. Exclude only justified interpreter/test caches, and make that exception explicit in the step wording. |
| R11 | minor | `specs/features/08_cli.feature:4` | **The feature description still repeats the pre-OD-29 blanket restriction.** It says other failures stop before firewall mutation. | Replace that sentence with the wording below. |

Disposition of all **17 previous findings**:

| Previous # | Re-review result |
|---|---|
| 1 | Resolved: refusal Then only inspects events; deferred execution is registered in `world.py:80` and `run_steps.py:85`. |
| 2 | Resolved: default emitted lines and ordered capture comparison exist (`fakes.py:179`, `run_steps.py:335`). |
| 3 | Partially resolved: offline backend arguments are checked; PC execution still has R1. |
| 4 | Resolved: interruption diagnostic and fail-before-output fixture are implemented (`build_steps.py:174`, `:179`; `fakes.py:277`). |
| 5 | Partially resolved: R7. |
| 6 | Partially resolved: delayed process exit and stale silent-removal detection exist; R2–R3 remain. |
| 7 | Resolved: capture failure occurs after actual process creation, with rule-before-process ordering asserted (`fakes.py:202`, `run_steps.py:380`). |
| 8 | Partially resolved: decisive session citations and absence extent are checked; R4 remains. |
| 9 | Resolved for the reported defects: invalid bytes no longer corrupt the connection line; clean evidence is checked first; aggregation Given is renamed (`log_steps.py:88`, `:261`, `:273`). |
| 10 | Resolved: prefixed HEVC, absent audio, exact audio-only/no-session verdicts and precedence row are present. |
| 11 | Core defect resolved: CLI status and exactly one new run folder are checked; fallback selection requires PC provenance (`live_steps.py:219`, `:240`, `:251`). |
| 12 | Partially resolved: stronger version, retention and message checks exist; R6 and R8 remain. |
| 13 | Substantially resolved: full retention comparisons, effective ignore check, complete prompt, planted artifacts and independent inventory are present (`evidence_steps.py:24`, `:102`, `:115`, `:127`, `:185`). |
| 14 | Partially resolved: R2. |
| 15 | Resolved: zero-item collection, distribution-version lookup and retained collection results are implemented (`boundary_steps.py:94`, `:111`, `:137`, `:144`). |
| 16 | Core repairs applied: malformed/read-failure distinction, hashed snapshots and entry-point subprocess exist; R10 remains. |
| 17 | Authority and failure contracts resolved by OD-29 and interface wording (`DECISIONS.md:37`, `S1_INTERFACES.md:6`, `:62`). Stale feature prose remains R11. |

## Missing scenarios

Add this cleanup counterpart to the existing stale-rule scenario:

```gherkin
@S1 @offline @failure @AIR-005 @AT-05
Scenario: Cleanup removal reports success but leaves the run's rule
  Given a test run whose firewall rule was created
  And removing a rule reports success but leaves it in the next listing
  When the run ends by the owner ending the test
  Then the run reports the residual rule "NEMESIS-AirPlay-S1" and its program path
  And the run does not report a clean cleanup
```

All other findings can be addressed by strengthening existing scenarios and bindings.

## Requirement wording

No new owner decision or requirement change is needed.

Replace `08_cli.feature`’s outdated failure sentence with:

> Usage errors change nothing. Detectable provenance and output-location failures stop before firewall changes or receiver launch; failures after rule creation follow cleanup and exit 1 (AIR-031, OD-29).

## Traceability

The added cleanup scenario increases the stated count from **73 to 74 declarations**. Regenerate traceability after changes. Existing tags should remain attached only where the strengthened assertions establish their claimed behavior.

## Not verified

No implementation, tests, build, firewall operation or live iPhone run was executed. The reported red suite and passing structural checks were not independently rerun; import failures do not validate downstream assertions.

`SCOPE.md` is absent from the stage. No paths outside the staged root were inspected, and no files were changed.
---

## Applied by the parent (2026-11-14)

| # | What changed |
|---|---|
| R1 | The live run calls `cli.main(["run"], deps=Deps.real(firewall=..., launcher=...))` with test-owned recording wrappers. The launch check reads the argv the real launcher received, compares it with the flags written out in the steps, and then requires `run.json` to agree. |
| R2 | `tests/steps/firewall_snapshot.ps1` lists every rule with its program and port filters through PowerShell. It runs before the run, just after the rule is created (inside the recording wrapper), and after the run. The saved before, during and after files must match Windows's own owned rules field by field at those points. Ownership is worked out from Windows's own fields (name plus a `uxplay.exe` under the build root). Unrelated rules are compared in full, program and ports included. A non-elevated dry run read 944 rules. The script stops with exit 1 rather than print partial output when it can't read port filters, so these tiers need an elevated terminal (`AGENTS.md`). |
| R3 | Added "Cleanup whose removal reports success but leaves the run's rule is reported". |
| R4 | The run's cites must include the deciding line of the single session. In the aggregation matrix, each session must cite its own deciding line, worked out from the fixture, inside its span. The run must have a reason and cite a deciding line of a session whose verdict matches the run's. |
| R5 | The recording launcher keeps its own monotonic launch and stop-request times. The saved duration must match them within 2 s. A timeout must land 598 to 605 s after launch, and an owner end before that. |
| R6 | The process ID comes from the recording launcher and must equal the saved one. `tasklist` must exit 0, and its CSV output must not hold that PID. |
| R7 | Offline: `build_root` is compared too. Host: each requested fresh directory is kept. Each build's `build_root` must be that directory, with its source, build and executable inside it. Every saved provenance field must equal the returned one. |
| R8 | Session spans come from the log text (connection request to the next one). Report boundaries, every observation line and every verdict cite must fall inside their own text-derived span. |
| R9 | The process-creation scenario asserts `fw.create` comes before `proc.create.failed`. |
| R10 | The entry-point snapshot includes `runs/`. It leaves out only `.venv` and Python caches, and the step now says so. |
| R11 | `08_cli.feature`'s description takes the review's wording. |
