# Coverage review: S1 step definitions

> Reviewer: `gpt-6-astra` via Codex CLI (`codex exec` read-only sandbox, role prompt `tools/astra/roles/astra-scenario-reviewer.md` as developer instructions), reasoning effort medium.
> Staged: docs, specs, tests, tools, package, AGENTS.md.
> Session `01a1dd31-bf5c-7d73-96bd-3048c7f8b08d`, 1 turn(s), 251 s; tokens in 900581 (cached 820736), out 6581 (reasoning 438).
> Prompt: `tools/astra/prompts/S1_REVIEW_STEPS.md`. Date 2026-11-14.

**External critique, not an owner decision.**

---

## Verdict

**Blocked. Step 7 must not start until the blockers below are fixed.**

The step bindings are not yet a reliable implementation gate. Some reject correct behavior; others permit incorrect launch configuration, unsupported verdict citations, or stale live evidence.

The changed `unreadable` limitation agrees with AIR-028(c). The renamed boundary steps and added OD-27/OD-28 tags are appropriate. Blocking pytest-bdd during main-suite collection strengthens the boundary scenario. However, the three renamed PC steps did not eliminate the shared offline “owner ends the test” binding.

This review is read-only. An independent critic checked the principal findings. External critique, not an owner decision.

## Findings

Paths are relative to the staged root. “Blocker” means fix before implementation.

| # | Severity | File:line | Finding | Concrete fix |
|---|---|---|---|---|
| 1 | blocker | `tests/steps/run_steps.py:70`; `tests/steps/cli_steps.py:23`; `specs/features/03_runner.feature:35` | **The refusal assertion launches another run.** After `_cli()` rejects a capture option, “UxPlay is never launched” calls `run(world)`. The CLI result never populates `world.record`, so this starts an unrelated runner with valid provenance. A correct implementation fails this scenario. | Make the Then inspect recorded launch events only. Execute the intended action once in its When; do not invoke production actions from negative assertions. |
| 2 | blocker | `tests/steps/fakes.py:144`; `tests/steps/run_steps.py:304`; `specs/features/03_runner.feature:9` | **The normal capture scenario expects output its fake never supplies.** `FakeLauncher.lines` defaults to empty, but the Then requires `BANNER` in the log. This encourages the runner to fabricate UxPlay output. | Explicitly configure the fake to emit the banner and a distinct diagnostic line before owner termination. Assert both captured lines, in order, against the emitted bytes. |
| 3 | blocker | `tests/steps/run_steps.py:35`, `:197`, `:214`; `tests/steps/fakes.py:97`; `tests/steps/live_steps.py:157` | **Configuration assertions inspect claims rather than backend calls.** Launch checks use `record.command`; rule checks use `record.rule`. A runner can launch with `-h265` or create an unrestricted rule while recording compliant values. The live expected command also comes from production `build_command()`. | Record immutable copies of complete rule arguments in fake events. Check actual `launcher.argv` and rule creation arguments against independent expected values, then compare those values with persisted records. Apply the same approach to the live launcher. |
| 4 | blocker | `tests/steps/build_steps.py:173`, `:192`; `tests/steps/fakes.py:218` | **Interrupted reporting is untested, and the stale-output fixture destroys its own stale output.** “The failure is reported” returns immediately after `KeyboardInterrupt`. The purported already-partial Given only changes compiler mode. Every compiler failure overwrites the previous executable, missing failures that leave an old successful binary intact. | Capture and assert the interruption diagnostic. Seed the stated partial state or reword the Given. Add a compiler failure before output is touched, preserving the earlier executable and provenance, then prove neither remains selectable as the failed build’s output. |
| 5 | major | `tests/steps/build_steps.py:117`, `:124`, `:232`, `:237`, `:254` | **Build provenance and clean-repeat evidence are incomplete.** Offline persistence checks verify only the commit. Host steps neither inspect the saved prerequisite inventory nor establish two distinct initially clean directories. Both host provenance reads can resolve to the same overwritten record. | Compare every persisted provenance field with independent inputs. Retain each build’s separate record and hash. Assert distinct clean destinations and actual saved prerequisite/decoder inspection evidence. |
| 6 | blocker | `tests/steps/run_steps.py:28`, `:151`, `:424`; `tests/steps/fakes.py:106`, `:129`, `:163`; `tests/steps/live_steps.py:96` | **Verification and ordering can be simulated without being effective.** A list call proves neither that its result was checked nor that failed removal was detected. The fake always removes successfully unless it raises. Stop is instantaneous. Live before/during snapshots cannot prove removal preceded creation. The launcher silently discards output when capture closes early. | Add remove-without-effect and delayed-stop modes; record immutable list results and process exit events. Assert verified absence before creation, actual process exit before cleanup/end marker, and live operation order through a recording backend. Make premature capture closure fail visibly. |
| 7 | blocker | `tests/steps/run_steps.py:52`; `tests/steps/world.py:102`; `docs/spec/S1_INTERFACES.md:56`; `docs/spec/REQUIREMENTS.md:41` | **The capture-failure setup conflicts with output preflight.** The Given says a rule already exists but only creates provenance. The fixture makes the log path unwritable before invocation, while the Then requires a newly created rule to be removed. This rewards creating a firewall rule before detecting an already-invalid output destination, contrary to AIR-031(e). | Separate preflight failure from failure after rule creation. Preflight failure must touch no firewall rule. For the cleanup scenario, inject a capture failure after successful preflight and actual rule creation. Update the interface order accordingly. |
| 8 | blocker | `tests/steps/log_steps.py:305`, `:353`, `:361`; `specs/features/05_verdict.feature:13` | **Verdict citations are weaker than their words.** Any nonempty subset of observed lines passes, so a GO citing only the connection line succeeds. The absence-reason check does not require the reason to identify examined extent. Its “no video citation” check is vacuous when the fixture contains no video lines. | Require the decisive codec line for GO, refusal evidence for HEVC NO-GO, and connection plus examined extent/endpoint for absence conclusions. Validate citation bounds and session membership. Check run-level reasons/references too. |
| 9 | major | `tests/steps/log_steps.py:85`, `:243`, `:254`; `tests/steps/world.py:47` | **Two Givens misdescribe their data.** Partial unreadability is injected into the connection line of the supposedly intact decisive session. “An error-free run” can contain `SESSION["INCONCLUSIVE"]`, which intentionally represents a codec evidence error. | Put invalid bytes outside an otherwise intact, delimited decisive session; verify that session’s clean counterpart yields the expected verdict. Rename aggregation setup to “a run with a complete readable log…” so session evidence errors are not denied. |
| 10 | major | `specs/features/04_analyser.feature:44`, `:102`; `specs/features/05_verdict.feature:29`; `tests/steps/log_steps.py:300` | **Important parser/verdict mutations remain uncovered.** No positive observed `h265` stream, prefixed message, or absent-audio assertion exists. Audio-only evaluation merely requires “not GO,” allowing INCONCLUSIVE despite AIR-010(a)’s NO-GO rule. | Add positive HEVC observation, prefixed-log, and absent-audio cases. Assert exact audio-only and capability-only verdicts and reasons. Test precedence when multiple completeness defects coexist. |
| 11 | blocker | `tests/steps/live_steps.py:141`, `:146`, `:151`, `:213`; `tests/steps/run_steps.py:411`; `docs/spec/S1_INTERFACES.md:115` | **PC steps can examine an unrelated run.** The live CLI result is ignored and evidence is selected through generic `latest_run()`. A failed invocation can reuse an old run; the interface selects the newest folder without requiring PC provenance. The live owner-end step only changes the offline `FakeOwner` after the CLI has returned. | Bind the scenario to the exact newly created run ID/path and assert CLI completion. Require explicit PC provenance for later evidence scenarios. Give the live termination step a real live interaction, or make owner termination part of the interactive When and assert its recorded outcome. |
| 12 | blocker | `tests/steps/live_steps.py:193`, `:200`, `:242`, `:267`, `:274` | **The live evidence checks do not establish their claims.** An opening parenthesis passes as an exact iOS version/build. A verdict file containing only `sessions` passes the retention check. Observation-message checks accept empty strings and invalid/nonpositive line conventions. The summary needs only the substring `untested`. A GO citation need not belong to its session. | Validate a complete owner-entered iOS 27 version/build and evidence completeness; compare saved report and verdict contents; require nonempty recognized messages, positive bounded references and session ownership; assert explicit PC tier and head-unit limitation. Establish connection and H.264 evidence in the same GO session independently of the evaluator’s claims. |
| 13 | major | `tests/steps/evidence_steps.py:66`, `:77`, `:83`, `:138`, `:174`; `tests/steps/live_steps.py:207` | **Retention, privacy and tier checks have weak or circular oracles.** A nonempty report session list need not preserve the report. A `.gitignore` line does not prove effective exclusion. The version prompt need not mention iOS/version. `screen_content()` can always return `[]`. A summary can say “not evidence” and still claim compatibility elsewhere. | Compare saved report/log contents with expected data; verify effective ignore behavior in an isolated Git fixture; require the full requested version/build prompt; independently inventory output artifacts and seed forbidden artifacts to test detection; assert an explicit synthetic-evidence limitation and reject positive compatibility claims. |
| 14 | major | `tests/steps/live_steps.py:33`, `:90`, `:122` | **Host firewall evidence is entirely runner-authored JSON.** An implementation can serialize idealized snapshots while changing unrelated real rules. | Wrap the real firewall backend in test-owned instrumentation, retain actual query results at lifecycle boundaries, and independently inspect the final firewall. Compare saved snapshots with those observations. |
| 15 | major | `tests/steps/boundary_steps.py:76`, `:89`, `:115`, `:123` | **Boundary assertions omit stated evidence.** “Collects nothing” accepts successful collection of tests. Environment evidence saves versions/path but no collection result. Main-suite collection is not retained. The version probe assumes a package `__version__` attribute without staged evidence that it exists. | Assert collected-item count zero explicitly. Obtain installed distribution versions through `importlib.metadata`. Save command, interpreter, return code, stdout/stderr and item count for both collections. Keep the pytest-bdd-blocked main-suite check. |
| 16 | major | `tests/steps/cli_steps.py:23`, `:81`, `:118`; `tests/steps/world.py:160`; `specs/features/08_cli.feature:9` | **CLI coverage does not exercise the named command, and one Given is mislabeled.** Calling `cli.main()` cannot detect a broken or missing `tools/s1.py`. “Unreadable” provenance is readable malformed JSON. Metadata-only snapshots can miss content changes, and “no partial output” ignores modifications to existing files. | Add a subprocess smoke scenario for the actual entry point. Separate malformed provenance from read failure. Snapshot file contents/hashes as well as paths, and assert existing outputs remain intact on failure. |
| 17 | blocker | `docs/spec/DECISIONS.md:36`; `docs/spec/REQUIREMENTS.md:15`, `:41`; `docs/spec/S1_INTERFACES.md:5`, `:56` | **The authority and failure contracts need correction.** “The steps are the authority” can override reviewed requirements. OD-28 says *any* other failure occurs before firewall mutation, which cannot literally include receiver/runtime/cleanup failures after rule creation. AIR-031(f) does not repeat that blanket restriction. | Restore requirements/decisions as authority. Obtain a focused owner clarification distinguishing preflight failures from failures after mutation. Do not silently resolve OD-28 by implementation order. |

**Per-feature assessment:** `01` needs findings 4–5; `02` needs 3, 6 and 14; `03` needs 1–3, 6–7; `04`’s existing observation assertions generally match their text, with coverage gaps in 10; `05` needs 8–10; `06` needs 11–13; `07` needs 15; `08` needs 16–17.

**Log-fixture correspondence:** The banner, connection, setup, arrival, codec, three-line HEVC refusal, audio and disconnect strings in `world.py:23–41` agree with the supplied signature document (`docs/evidence/UXPLAY_LOG_SIGNATURES.md:10–24`). The `+2` wrapper offset matches `specs/README.md:20–23` and `world.py:151–153`. The deficiencies are fixture placement, missing emitted output and missing cases—not those copied message strings.

**Interface versus owner decisions:** Module names, dataclasses, binary capture handles, JSON filenames, marker syntax and dependency injection are implementation details. OD-27 and OD-28 already authorize ownership and command choices. Findings 7 and 17 concern behavioral conflicts, not grounds to ask the owner to approve ordinary API design. Selecting the newest arbitrary run as PC evidence is not an authorized evidence policy.

## Missing scenarios

These supplement repairs to existing scenarios; new steps require review before implementation.

```gherkin
@S1 @offline @failure @AIR-003 @AIR-030 @AT-03 @AT-30 @OD-27
Scenario: Removal that reports success but leaves a stale rule blocks launch
  Given an owned stale firewall rule
  And removing it returns successfully but leaves it in the next rule listing
  When a test run starts
  Then the remaining rule identifier and program are reported
  And no new firewall rule is created
  And UxPlay is never launched

@S1 @offline @failure @AIR-002 @AT-02
Scenario: A failed rebuild leaves an old executable physically intact but unusable
  Given a successful recorded build with its executable intact
  And the compiler will fail before changing any output
  When the build procedure runs
  Then the build failure is reported
  And the earlier executable bytes are unchanged
  And no successful output is recorded for the failed build
  And the earlier executable cannot be selected for a test run

@S1 @offline @failure @AIR-006 @AIR-005 @AT-06 @AT-05
Scenario: Process creation raises after the temporary rule was established
  Given a verified build and writable run outputs
  And process creation will raise an operating-system error
  When the runner attempts to launch the receiver
  Then the process-creation error is reported
  And the run is not reported as successfully started
  And no process remains running
  And the created firewall rule is removed and its absence verified

@S1 @offline @normal @AIR-008 @AIR-009 @AIR-029 @AT-08 @AT-09 @AT-29
Scenario: A prefixed codec message identifies an observed HEVC stream
  Given a UxPlay log excerpt with these lines:
    """
    [fixture] connection request from Fixture iPhone (iPhone17,1) with deviceID = AA:BB:CC:DD:EE:FF
    [fixture] *** codec is h265: geometry is (w,h)=(1920,886) (source); (x,y)=(0,0); (w,h)=(1920,886)
    """
  When the analyser reads the log
  Then mirroring video started is observed at line 2
  And the video codec is "HEVC", observed at line 2
  And an HEVC stream is observed at line 2
  And the audio format is unknown

@S1 @offline @boundary @AIR-010 @AT-10
Scenario: Audio-only playback has the required NO-GO verdict
  Given a complete log in which only audio played
  When the evaluator judges the run
  Then the session's verdict is NO-GO with the reason "connected but no H.264 mirroring"
  And the run's verdict is NO-GO

@S1 @offline @normal @AIR-031 @AT-31 @OD-28
Scenario: The documented script exposes the command interface
  When the project interpreter executes "tools/s1.py --help" as a subprocess
  Then the subprocess exits 0
  And stdout names check, build, run, analyse, evaluate and summary
  And no build, firewall, receiver or output-file operation occurs

@S1 @pc @boundary @AIR-007 @AIR-011 @AIR-012 @AIR-013 @AT-07 @AT-11 @AT-12 @AT-13 @OD-08
Scenario: A live run reaches the original timeout
  Given the verified build and the owner's iPhone on the same network
  When the owner starts Screen Mirroring and leaves the run until timeout
  Then that invocation's retained record identifies timeout at launch plus 600 seconds
  And the launched process is confirmed stopped
  And the exact tested iOS version and build are retained with their provenance
  And independent artifact inspection finds no saved screen content
```

Also extend existing scenarios to assert end-marker emission **after actual process exit**, unchanged existing outputs on write failure, and run-level evidence errors without fabricated citations.

## Requirement wording

No change is needed to AIR-028(c)’s `unreadable` label or the OD-25/OD-26 verdict rules.

Replace `S1_INTERFACES.md:5–6` with:

> Owner decisions and reviewed requirements govern behavior. Reviewed scenarios express that behavior; step definitions verify it. A disagreement among these sources is resolved through the specification review process before implementation continues.

Replace the aggregation Given in `05_verdict.feature:58` with:

> Given a run with a complete readable log whose sessions have verdicts `<sessions>`

Replace the interface’s unconditional capture ordering with:

> Validate required provenance and output destinations before any firewall change. If capture subsequently fails after rule creation, report the failure, stop any launched process, and remove and verify removal of the run’s rule. Write an end marker only after the receiver has stopped.

**Owner clarification required:** Does OD-28’s “before the firewall is touched” apply to detectable preflight failures, with later failures following AIR-005 cleanup?

If approved, replace its final sentence with:

> Exit 0 on successful completion, including a NO-GO verdict; 2 on a usage error, with nothing changed; and 1 on any other failure. Detectable provenance and output-location failures stop before any firewall change or receiver launch. Failures after rule creation follow AIR-005 cleanup and report any residual rule or process.

This proposed clarification is not an adopted owner decision.

## Traceability

- Read-only tag validation passed: **68 scenario declarations across eight files**—60 offline, five host, three PC. This counts each outline once.
- The checker validates tag structure, not behavioral coverage (`tools/trace_specs.py:48–65`).
- Add `@AIR-005 @AT-05` to the stop-failure cleanup scenario at `03_runner.feature:115`.
- The host build’s AIR-001/AT-01 coverage needs the saved-inventory assertions in finding 5.
- The PC recording scenario’s AIR-012/013 coverage needs findings 11–13; its renamed steps alone do not establish coverage.
- AT-07 explicitly requires live timeout evidence (`REQUIREMENTS.md:53`); the current live recording scenario covers owner termination only.
- AT-08’s absent audio-format behavior needs an assertion; AT-11’s citation obligations need finding 8.
- If all seven proposed additions are adopted without replacing existing scenarios, the total becomes **75 declarations**. Regenerate traceability after final edits.
- Preserve the previous review verbatim; add a dated follow-up noting that OD-27/OD-28 supersede its final “parent proposals” status.

## Not verified

- No S1 implementation, real build, firewall operation, iPhone run or hardware behavior was executed.
- The reported red suite and step-coverage pass were not independently rerun. Import failures alone do not demonstrate that later assertions are correct.
- `SCOPE.md`, `.gitignore`, `.venv`, the main repository suite and `package/` are absent from this stage. Their absence is a staging limitation, not a finding about the working checkout.
- Upstream log behavior was compared with the supplied signature document, not independently fetched source.
- Actual pytest-bdd version-attribute availability was not established.
- No path outside the staged root was inspected; no files were edited.
---

## Applied by the parent (2026-11-14)

The owner answered the clarification in finding 17 with "Preflight only", recorded as OD-29.
AIR-031(f) gained a sentence encoding it.

| # | What changed |
|---|---|
| 1 | Then steps never start an action. A When step sets `world.pending`, and a `pytest_bdd_before_step` hook runs it once before the first Then. "UxPlay is never launched" only reads launch events. |
| 2 | The fake launcher prints the banner and a diagnostic line by default and records what it wrote. The capture step compares the log with those lines in order, and fails on any write after the log closed. |
| 3 | Launch checks read `launcher.argv`, and rule checks read the `fw.create` event's copy of the rule, then require the run record to agree. Expected flags are written out in `world.py`, not taken from `build_command()`. The live check does the same. |
| 4 | An interrupted build must return `status="interrupted"` with an error saying so. The partial-output Given was reworded to "a compiler that writes a partial executable before it stops". A new scenario has the compiler fail before touching output, leaving the old executable's bytes intact but unselectable. |
| 5 | Every provenance field is compared with independent inputs (commit, version, inventory packages, compiler commands, executable, hash, directories). The host build uses two new, distinct directories, each holding its own provenance, and checks the saved inventory. |
| 6 | The fake firewall records each listing's result, and has a "remove reports success but has no effect" mode with its own scenario. Processes take time to exit after a stop, and record `proc.exit` along with the log contents at that moment. Steps require the exit before cleanup and before the end marker. The live run uses a recording firewall wrapper. |
| 7 | The capture-failure scenario now sets up writable outputs, and the launcher fails to attach output after the rule exists (`CaptureError`). A process-creation-error scenario was added. Preflight failure is covered by `08_cli.feature`. The interface's order now follows OD-29. |
| 8 | Each one-session fixture names the lines its verdict must cite (codec, refusal or connection). Cites must lie inside the session, and the run's cites must come from them. The absence reason must give the last line read and the end second. The vacuous no-video check became "cites only the connection line". |
| 9 | The unreadable bytes are a separate line outside the session, and the clean log is judged first to prove the stated verdict. The aggregation Given was renamed "a run with a complete readable log whose sessions have verdicts". |
| 10 | Added the prefixed HEVC-stream scenario, absent-audio assertions, an exact audio-only NO-GO row, an exact capability-only INCONCLUSIVE row, and a no-start-and-no-end precedence row. The "never GO" outline was replaced by these exact verdicts. |
| 11 | A live scenario uses only the one new run folder that its own `run` invocation created, and requires exit 0. Later `@pc` scenarios use that folder, or else the newest `tier: pc` run with complete evidence. Owner termination is part of the live When. A live timeout scenario was added. |
| 12 | The iOS value must match `27.x (build)` with complete evidence. The report and verdict files must equal a re-derivation. Observations need a positive in-bounds line and a recognised message. The summary must say `Tier: pc` and that the head unit is untested. A GO citation must lie inside a session that independently holds a connection line. |
| 13 | Saved report and verdict files are compared in full. `runs/` exclusion is checked with `git check-ignore` in a scratch repository. The prompt must mention iOS, version, build and the Settings path. `screen_content()` must first find planted artifacts, and the steps keep their own inventory. Offline summaries must call themselves synthetic and make no positive claim. |
| 14 | Host firewall evidence comes from a test-owned recording wrapper, plus PowerShell `Get-NetFirewallRule` listings taken by the steps before and after. Saved snapshots must match listings the backend actually returned. |
| 15 | "Collects nothing" requires exit 5, zero items and "no tests collected". Versions come from `importlib.metadata`. Both collections save their command, interpreter, exit code, output and item count. |
| 16 | Added a subprocess scenario for `tools/s1.py --help`. The provenance outline separates unreadable (a directory) from not valid. Snapshots hash file contents, so a changed file counts as a change. |
| 17 | The review's authority wording replaces the interface page's. OD-29 is encoded. |

Traceability: `@AIR-005 @AT-05` were added to the stop-failure scenario. Totals and coverage are
regenerated in `TRACEABILITY.md`.
