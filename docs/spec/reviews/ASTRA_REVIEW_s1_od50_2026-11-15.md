# S1 review: OD-50 cut-off log fix

> Reviewer: `gpt-6-astra` via Codex CLI (`codex exec` read-only sandbox, role prompt `tools/astra/roles/astra-scenario-reviewer.md` as developer instructions), reasoning effort medium.
> Staged: docs, specs, tests, tools, package, AGENTS.md.
> Session `01a1e0b9-ece7-76b3-a85e-f0362ac431f0`, 1 turn(s), 133 s; tokens in 505426 (cached 445568), out 3288 (reasoning 87).
> Prompt: `tools/astra/prompts/S1_REVIEW_OD50.md`. Date 2026-11-15.

**External critique, not an owner decision.**

---

## Verdict

**Implement with clarifications. No blocker before the next live run identified.**

OD-50’s regression is covered: the fake writes an unterminated fragment, and the scenario requires that exact fragment on the line immediately before a newline-terminated end marker. Removing the separator newline would fail these assertions (`tests/steps/fakes.py:163`, `:169`; `tests/steps/run_steps.py:291`, `:313`).

The cut-off output is itself the tested failure path. Coverage does not yet establish correct stopping for this particular scenario or exercise cut-off output during interruption.

External critique, not an owner decision. Paths below are relative to the stage root.

## Findings

| # | Severity | File:line | Finding | Concrete fix |
|---|---|---|---|---|
| 1 | minor | `tests/steps/run_steps.py:295` | The no-exit fallback accepts **any** missing exit event. The new OD-50 scenario could pass a mutation that skips stopping UxPlay for unterminated output, then writes the marker. | When no exit exists, require the fixture’s `ignores_stop` condition, an actually live fake process, its recorded residual PID, and its owner-facing failure report. Otherwise require an exit event. |
| 2 | major | `tests/steps/prompt_steps.py:120`; `specs/features/03_runner.feature:123` | OD-42 coverage does not prove **report → marker → reminder** ordering. Moving the marker before the residual-process report, or the reminder before the marker, can escape the relevant assertions. AIR-028(a) and AIR-033(c) require this ordering. Current production code follows it (`tools/airplay_s1/runner.py:194`, `:202`, `:208`). | Inspect the fake owner’s log snapshots: no marker at the residual-process report; marker present as the last line at the reminder. Also require the report event before the reminder. |
| 3 | minor | `tests/steps/run_steps.py:293` | Marker recognition calls production `logfile.is_end_marker`; therefore the entire marker oracle is not independent. The fragment-preservation and newline assertions **are** independent and decisive for OD-50. | Use a test-owned marker grammar to check the prefix and exact reason field. Continue comparing the fragment literally. |
| 4 | minor | `specs/features/03_runner.feature:117`; `docs/spec/REQUIREMENTS.md:17` | Gap (a) warrants a timing boundary case, but a 1.5-second polling mutant is not automatically wrong: AIR-007 specifies no maximum owner-response latency. Production polls at 0.5 seconds (`tools/airplay_s1/runner.py:24`, `:184`). | Obtain an owner decision on response latency before asserting an arbitrary new limit. Then test an owner termination between polling boundaries under AIR-007/AT-07; include AIR-011/AT-11 only when checking recorded times. |
| 5 | major | `specs/features/02_firewall.feature:91`; `tools/airplay_s1/cli.py:170` | Gap (b) is real: the CLI failure scenario checks the reminder and record claim, but neither exit status nor downstream work. Removing the early return can reach analysis/evidence before a later failure. However, “skip analysis and evidence” is not explicit in AIR-033(e). AIR-031(f)’s failure rule also needs explicit precedence over (g)’s normal-ending exit codes. | Extend the existing CLI scenario to assert the failure status. Seek owner confirmation before making “no analysis/evidence” a normative requirement; then assert no downstream calls, files, prompts or verdict claim. |

**Does OD-42 weaken existing callers?** Not demonstrably. Existing normal callers separately require an exit event through `run_steps.py:280` or `prompt_steps.py:102`; OD-42 callers intentionally retain a live process. The newly added OD-50 caller lacks that independent protection.

## Missing scenarios

Replace the OD-50 scenario with this outline. It retains its existing case and adds interruption coverage without introducing a timing policy. The final step needs a new binding.

```gherkin
  @offline @failure @AIR-028 @AT-28 @OD-50
  Scenario Outline: Cut-off output remains separate from the end marker
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And UxPlay's output stops partway through a line, with no final newline
    When the run ends by <ending>
    Then the log's last line is the runner's end marker naming "<reason>"
    And the cut-off output stays on its own line just before the end marker
    And UxPlay exited before the end marker was written

    Examples:
      | ending                    | reason      |
      | the owner ending the test | owner ended |
      | a keyboard interrupt      | interrupted |
```

Strengthen the existing OD-42 scenario rather than duplicating it:

```gherkin
  @offline @failure @AIR-007 @AIR-028 @AIR-033 @AT-07 @AT-28 @AT-33 @OD-42
  Scenario: A UxPlay process that won't stop is reported before the marker
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And the UxPlay process ignores the stop request
    When the runner launches the receiver
    And the owner ends the test
    Then the run reports the UxPlay process that is still running, with its process ID
    And the log's last line is the runner's end marker naming "owner ended"
    And the end marker names the process ID still running
    And the residual-process report precedes the end marker
    And the firewall reminder follows the residual-process report and the end marker
```

The following replacement for the existing CLI failure scenario is **conditional on approval of the wording below**:

```gherkin
  @offline @failure @AIR-031 @AIR-033 @AT-31 @AT-33
  Scenario: An unsaved run record stops the command before analysis and evidence
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And the run will end by the owner ending the test
    And the run record can't be written once UxPlay is running
    When "s1.py run" runs
    Then the command exits 1 and stderr names the run-record failure
    And the owner is reminded to remove "uxplay.exe" at "C:\uxplay-s1\build\uxplay.exe" from "Windows Defender Firewall > Allowed apps"
    And no run record exists
    And the console output doesn't claim a record was made
    And analysis and evidence collection are not invoked
    And no analyser report or verdict is written or announced
```

Do not add a timing-limit scenario until the owner supplies the limit.

## Requirement wording

**AIR-028(a): no change needed.** Its existing wording covers the separator fix.

For gap (b), proposed replacement for **AIR-033(e)**, requiring owner confirmation:

> (e) If the run record cannot be written, the reminder MUST still be given, and the command MUST NOT claim a record was made. `s1.py run` MUST report the record-write failure on stderr and return before analysis, evaluation or evidence collection. It MUST exit 1, except that an interrupted run MUST retain exit 130. This failure rule takes precedence over AIR-031(g)’s exit codes for otherwise completed runs.

No timing wording is proposed: selecting a maximum response delay would be an owner decision.

## Traceability

- The new scenario’s tags are valid: inherited `@S1`, one tier, one kind, AIR-028 and AT-28 (`specs/README.md:11`).
- Traceability already includes `03_runner.feature:135` and reports **77 scenario declarations** (`docs/spec/TRACEABILITY.md:34`, `:42`).
- The replacements above preserve declaration count; the outline adds one executable example.
- Add AIR-031/AT-31 to the strengthened CLI scenario. Update AT-33 if the proposed requirement is approved.
- Regenerate traceability after edits. No new requirement ID is necessary.

## Not verified

- No tests or mutations were executed; the reported passing suite and killed newline mutant were not independently reproduced.
- No live logs, iPhone data, credentials, OEM binaries or hardware were accessed. Offline coverage establishes no live compatibility result.
- `SCOPE.md` was absent from the staged root; no paths outside that root were read.
- A fresh-context critic independently checked the shared-step callers and newline oracle. No files were changed.
---

## Applied by the parent (2026-11-15)

| # | Applied |
|---|---|
| 1 | The end-marker Then accepts a missing exit only in the reported OD-42 case: the fake ignored the stop, is still alive, the run recorded its PID, and an owner message names it. Any other ending needs an exit event. |
| 2 | The OD-42 scenario in `03_runner.feature` gained "the residual-process report precedes the end marker" and "the firewall reminder follows the residual-process report and the end marker", checked against the log snapshot at each owner message. |
| 3 | `run_steps.py` checks the end marker with its own grammar (`END_MARKER`), including the exact reason field, instead of `logfile.is_end_marker`. |
| 4 | Not applied: needs an owner decision on response latency. Raised as AMB-30. |
| 5 | Not applied: needs owner confirmation of the AIR-033(e) wording. Raised as AMB-31. |
| Missing scenarios | The OD-50 scenario became the review's outline, with the keyboard-interrupt row and "UxPlay exited before the end marker was written". The CLI replacement waits on AMB-31. |

Checked after applying: offline suite 156 passed, 7 skipped. Mutants that report the leftover process after the end marker, give the reminder before the end marker, or never stop UxPlay are all killed.
