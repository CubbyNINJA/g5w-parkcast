# S1 review: OD-52/OD-53 scenarios

> Reviewer: `gpt-6-astra` via Codex CLI (`codex exec` read-only sandbox, role prompt `tools/astra/roles/astra-scenario-reviewer.md` as developer instructions), reasoning effort medium.
> Staged: docs, specs, tests, tools, package, AGENTS.md.
> Session `01a1e0cd-b587-7241-8242-8e144576ca89`, 1 turn(s), 142 s; tokens in 483166 (cached 423552), out 3573 (reasoning 124).
> Prompt: `tools/astra/prompts/S1_REVIEW_REV4.md`. Date 2026-11-15.

**External critique, not an owner decision.**

---

## Verdict

**Implement with clarifications.** No blocker prevents writing the new step definitions. Add the missing assertions below before claiming complete AT-07/AT-31/AT-33 coverage.

The five Enter examples are decisive against the specified mutation. With checks every 1.5 seconds, Enter at `120.05` is detected at `121.5`; adding the fake’s `0.4 s` stopping delay gives exit at `121.9`, or **1.85 seconds after Enter**. With the current `0.5 s` interval, the five expected intervals are `0.85`, `0.65`, `0.45`, `0.70` and `0.45 s`. This follows the polling and stopping paths at `tools/airplay_s1/runner.py:173` and `tools/airplay_s1/runner.py:191`.

The unsaved-record outline covers **all four endings explicitly required by AT-31/AT-33**, including interruption retaining 130. Missing step definitions are expected and are not a finding.

## Findings

| # | Severity | File:line | Finding | Concrete fix |
|---|---|---|---|---|
| 1 | major | `specs/features/02_firewall.feature:95` | Zero analyser/evaluator/writer calls does not establish zero evidence prompts. Mutation: move `_sound_note(deps.owner)` above the record-written guard; every listed assertion could still pass. The prompt is separate from those components (`tools/airplay_s1/cli.py:145`, `:170`, `:175`). AT-31/AT-33 explicitly prohibit evidence prompts. | Add `And the owner receives no evidence questions`; assert the recording owner’s question list is empty. |
| 2 | major | `specs/features/06_evidence.feature:81` | The existing live scenario asserts eventual termination, but neither retains the Enter-event/process-exit interval nor asserts its 1 s bound. A live input or stopping delay exceeding 1 s can pass. The existing owner wrapper simply delegates input checks (`tests/steps/live_steps.py:124`); its process wrapper records stop requests and exit events without the required Enter timestamp (`:47`). | Extend the live scenario with independent Enter-event and process-exit timestamps, retained timing evidence, and the inclusive 1 s assertion. Do not timestamp Enter only when the runner polls. |
| 3 | minor | `specs/features/02_firewall.feature:99` | The injected write-failure path checks reminder presence, but not its block-entry note, ordering or exclusion from the log. Those checks exist for ordinary endings (`:23`), so a write-failure-specific malformed or premature reminder could escape. | Reuse the reminder-content and ordering assertions in the unsaved-record outline, as shown below. |

**Independent oracles for the new steps**

- **Stop delay:** set the fake launcher’s delay to literal `0.4`. Its process already advances the controlled clock before emitting `proc.exit` (`tests/steps/fakes.py:99`). Do not derive the delay from runner constants.
- **Enter event:** schedule a callback at the literal example time. It records `owner.enter` and latches pending input. `owner.ended()` reads that latch. The clock supports callbacks at their scheduled times even inside a longer sleep (`tests/steps/fakes.py:61`). Never reset the timestamp when detection occurs.
- **Exit bound:** select the launched PID’s actual `proc.exit` event; require that it exists and assert `0 <= exit_time - enter_time <= 1`. Do not use `RunRecord.ended_at`: the runner assigns it **before** stopping (`tools/airplay_s1/runner.py:187`). A residual-process report cannot satisfy this assertion.
- **Call recording:** install spies at `cli.analyser.analyse`, `cli.evaluator.judge` and `cli.evidence.write`, the actual call sites (`tools/airplay_s1/cli.py:176`). Record entry before delegating, so a later exception cannot conceal an invocation. Assert each trace is empty.
- **Failure diagnostic:** inspect captured stderr for the run-record identity and an explicit save/write failure, without importing the production message. The current diagnostic originates at `tools/airplay_s1/runner.py:95` and is emitted by `tools/airplay_s1/cli.py:166`.
- **No reports or announcements:** inspect the actual newly created run directory for `report.json` and `verdict.json`, and inspect captured stdout, stderr and owner messages for report/verdict announcements. These checks complement invocation traces; they do not replace them.
- **No evidence prompts:** assert `FakeOwner.asked == []`; questions are independently recorded at `tests/steps/fakes.py:205`.

The examples do not exercise an exit **exactly** at 1 s. Verify the timing assertion itself accepts exactly `1.0` and rejects a value just above it during step-oracle validation. This is an oracle boundary check, not a reason to prescribe the runner’s polling interval.

## Missing scenarios

Replace the unsaved-record outline with this strengthened version:

```gherkin
  @offline @failure @AIR-031 @AIR-033 @AT-31 @AT-33 @OD-53
  Scenario Outline: An unsaved run record stops the command before analysis and evidence
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And the run will end by <ending>
    And the run record can't be written once UxPlay is running
    And every call to the analyser, evaluator and evidence writer is recorded
    When "s1.py run" runs
    Then it exits with code <code>
    And stderr names the run-record failure
    And the owner is reminded to remove "uxplay.exe" at "C:\uxplay-s1\build\uxplay.exe" from "Windows Defender Firewall > Allowed apps"
    And the reminder says a declined or closed prompt may have left a block entry to remove too
    And the reminder came after UxPlay had stopped and after the log's end marker
    And the log's last line is still the runner's end marker
    And no run record exists
    And the console output doesn't claim a record was made
    And the analyser, evaluator and evidence writer were never called
    And no analyser report or verdict is written or announced
    And the owner receives no evidence questions

    Examples:
      | ending                    | code |
      | the owner ending the test | 1    |
      | the 10-minute timeout     | 1    |
      | UxPlay exiting mid-test   | 1    |
      | a keyboard interrupt      | 130  |
```

The existing record-failure fixture acts at second 10 (`tests/steps/prompt_steps.py:136`), before each of these four endings (`tests/steps/run_steps.py:70`). It therefore suits this outline. It would not exercise an immediate launch/capture failure; adding those examples would require different fault injection.

For the missing live criterion, either add this scenario or fold its timing steps into the existing live early-end scenario:

```gherkin
  @pc @boundary @AIR-007 @AIR-012 @AIR-013 @AT-07 @AT-12 @AT-13 @OD-52
  Scenario: A live Enter event stops UxPlay within one second
    Given the verified build and this PC's network
    And the owner's iPhone on iOS 27 on the same network
    And an independent observer timestamps Enter input and the launched process's exit
    When the runner launches "HU-Test", the owner starts Screen Mirroring to it and then ends the test at the console
    Then the observed process exit is between 0 and 1 second after the observed Enter event
    And the independent Enter and process-exit timestamps are retained under "runs/"
    And no UxPlay process launched by the run is still running
    And the owner's sound note and the iOS version with its build, marked "owner-entered", are recorded
    And the live run's folder, the build tree and the working directory hold no video, image or stream dump file
```

## Requirement wording

**No replacement wording needed.** The reviewed clauses agree with OD-52/OD-53 (`docs/spec/DECISIONS.md:60`). The gaps are in coverage, not owner intent. No new assertion exceeds the requirements.

## Traceability

- `python -B tools/trace_specs.py --check` passed.
- Both reviewed outlines inherit exactly one `@S1` and have one tier and one kind.
- The current traceability lists **78 scenario declarations**, counting each outline once (`docs/spec/TRACEABILITY.md:48`).
- Strengthening existing scenarios leaves that count unchanged. Adding the separate live scenario makes it **79**; folding its steps into the existing live scenario leaves **78**.
- Regenerate traceability after edits. The reviewed outlines contain five and four expanded examples respectively.

## Not verified

- No step definitions, mutation runs or live measurements were executed.
- Offline timing cannot establish the real console/process timing bound.
- `SCOPE.md` was absent from the staged root; no paths outside the stage were read.
- No credentials, iPhone artifacts or OEM binaries were accessed.
- A fresh-context critic checked the principal findings and confirmed the prompt and live-timing gaps.

---

## Applied by the parent (2026-11-15)

| # | Applied |
|---|---|
| 1 | The unsaved-record outline asserts "the owner receives no evidence questions" (`FakeOwner.asked` empty). |
| 2 | Added the `@pc` scenario "A live Enter event stops UxPlay within one second" to `06_evidence.feature`. A test-owned observer thread polls the Enter key (GetAsyncKeyState) and waits on UxPlay's process handle, and its timestamps are saved as `timing-observer.json` in the live run's folder. Not yet run live. |
| 3 | The unsaved-record outline became the review's strengthened version, with the block-entry note, ordering and log checks. `run_dir` and the fake owner now find a folder `s1.py run` created, so those checks see its log. |
| Oracles | Enter is a scheduled, test-recorded event at the literal time; the bound uses the launched PID's own exit event, never `ended_at`. The bound accepts exactly 1.0 s and rejects 1.001 s and negative intervals. Spies on `analyser.analyse`, `evaluator.judge` and `evidence.write` record before delegating. |

Checked after applying: offline suite 164 passed, 8 skipped. Mutants killed: polling every 1.5 s, a 0.7 s pause before stopping, asking the sound question before the record check, analysing despite an unsaved record, and exit codes 1 for an interrupted unsaved run and 0 for an unsaved one.
