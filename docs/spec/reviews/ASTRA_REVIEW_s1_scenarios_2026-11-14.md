# Coverage review: slice S1 scenarios (AIR-001 to AIR-015)

> Reviewer: `gpt-6-astra` via Codex CLI (`codex exec` read-only sandbox, role prompt `tools/astra/roles/astra-scenario-reviewer.md` as developer instructions), reasoning effort medium.
> Staged: docs, specs, tests, tools, package, AGENTS.md.
> Session `01a1dd12-da5b-71f0-8d50-c59bdc141e26`, 1 turn(s), 198 s; tokens in 410568 (cached 368000), out 5311 (reasoning 212).
> Prompt: `tools/astra/prompts/S1_REVIEW.md`. Date 2026-11-14.

**External critique, not an owner decision.**

---

## Verdict

**Blocked.** Resolve the codec/verdict precedence ambiguity and correct the setup-versus-video-start assertion before writing step definitions. Other findings are coverage corrections.

The read-only tag check passed: **49 scenario declarations across seven features**. An independent critic reviewed the principal findings. No files were changed.

Direct answers:

1. **Log excerpts:** No complete format string demonstrably contradicts the staged signatures. However, `04_analyser.feature:34` expands an abbreviated evidence string whose suffix cannot be verified. The deliberately unknown line at `:85` is appropriate negative input, not a genuine UxPlay excerpt. Codec spelling remains unconfirmed by the evidence. More seriously, `:20` misinterprets mirroring setup as video starting.
2. **PC scenarios:** The live evaluator scenario is not decisive: a constant verdict with plausible citations could pass. Build and firewall PC scenarios have useful assertions but omit required evidence and lifecycle cases. Passing evaluator acceptance must remain separate from passing the H.264 feasibility gate.
3. **Verdict combinations:** Coverage is incomplete. Missing combinations include GO plus INCONCLUSIVE without NO-GO, reversed NO-GO/INCONCLUSIVE ordering, homogeneous multi-session runs, partly unreadable logs containing GO, and damaged logs containing NO-GO. All-INCONCLUSIVE and zero-session aggregation need explicit wording. Missing-codec precedence remains contradictory.
4. **Launch flags:** **All five requested exclusions are present:** `-h265` at `03_runner.feature:15`; `-vdmp`, `-admp`, `-vrtp`, `-artp` at `:26–29`. This does not yet prove that alternate configuration or requested recording cannot enable capture.

## Findings

Paths below are relative to the stage root. Every proposed fix is specification-only; no destructive-action hook is required.

| # | Severity | File:line | Finding | Concrete fix |
|---|---|---|---|---|
| 1 | blocker | `docs/spec/REQUIREMENTS.md:19–20,40`; `04_analyser.feature:37–39`; `05_verdict.feature:18–20` | Missing codec requires INCONCLUSIVE/evidence error, but HEVC-request, no-video and no-connection examples expect other reasons/verdicts. The HEVC analyser fixture explicitly reports unknown codec. OD-17–19 do not settle this applicability conflict. | Obtain an owner ruling on when missing codec is an evidence error: every log, only an observed video stream, or another explicit condition. Rewrite AIR-009(b), AIR-010(a), AT-10 and affected examples together. |
| 2 | major | `04_analyser.feature:14–21`; `docs/evidence/UXPLAY_LOG_SIGNATURES.md:12,35–37` | “Mirroring initialized successfully” proves setup, not video starting. A setup-only implementation can falsely report started video. | Attribute video start to the codec/geometry evidence at fixture line 5, using the staged evidence’s S1 proxy. Add a setup-only negative fixture. |
| 3 | major | `04_analyser.feature:28–35,44–48,68–74,102` | Missing banner is classified as an evidence error, but most positive excerpts omit it. Requirements do not define banner absence as proof of incompleteness. | Explicitly distinguish excerpts from complete log fixtures. Define the evidence used to establish completeness; either justify a banner requirement or remove that example. |
| 4 | major | `06_evidence.feature:60–70` | The live scenario checks that verdicts exist, not that they agree with observations. Constant GO with plausible citations passes. It asserts one session verdict, not every session’s verdict. It also omits effective configuration, parsed-observation comparison, deadline, process-stop verification and exact reference validation required by its tags. | Split evaluator acceptance from the feasibility gate. Independently compare every saved observation/verdict/reference with the retained log and run record. Only an evidenced GO passes the feasibility gate; legitimate NO-GO/INCONCLUSIVE may pass evaluator acceptance. |
| 5 | major | `05_verdict.feature:42–74,77–89` | Aggregation examples leave plausible mutations alive: GO requires a NO-GO companion; last session wins; unreadable GO bypasses the damage override. Notes only assert run-level invariance. | Add the aggregation and damage matrices below. Assert both session and run invariance for sound and YouTube notes, including GO inputs. Assert the run verdict for the missing-codec case after finding 1 is resolved. |
| 6 | major | `docs/spec/REQUIREMENTS.md:19–22,39–40,54` | Parent additions largely express OD-17–20, but leave contradictory “remains open” wording. AIR-010(b)/(d) do not explicitly yield to AIR-009(c). AIR-012(d) specifies the answer format but omits OD-21’s trigger when version is known and build is missing. | Apply the replacement wording below. Remove obsolete unresolved-status statements and update AT-09/10/12. |
| 7 | major | `01_build.feature:24–33,51–56,66–71`; `docs/evidence/S1_PC_FACTS.md:23–27`; `docs/spec/REQUIREMENTS.md:32` | Missing-prerequisite coverage omits the documented `ninja` invocation. Build failure covers stale output but not newly partial output/interruption. PC coverage builds once, whereas AT-02 requires a clean repeat and both provenances/hashes. | Validate the selected generator’s executable dependency and test its absence. Add interrupted/partial build cases and the clean repeated PC build. Assert full provenance, not merely that it was saved. |
| 8 | major | `03_runner.feature:8–16,39–44,54–69`; `docs/spec/REQUIREMENTS.md:16–17,36–37` | No successful diagnostic-capture assertion or capture-establishment failure case. Timer checks connection/reconnection but not video-start reset. Stop-before-firewall-cleanup ordering is unasserted. | Add capture success/failure assertions; introduce video start at second 599; assert stop precedes cleanup on timeout and early termination. |
| 9 | major | `02_firewall.feature:11–16,27–33,67–73`; `docs/spec/REQUIREMENTS.md:13,35` | Stale removal is checked before launch, but AIR-003 requires it before new-rule creation. PC coverage only cancels; induced run failure is absent. Fixed rule naming lacks an explicit ownership/collision contract. “No rule allows any other program” can accidentally encompass unrelated system rules. | Assert stale removal before creation; scope restrictions to rules created by this runner; test unrelated-rule preservation during cleanup and multiple stale rules; add PC failure cleanup and saved before/during/after properties. Define how ownership is established. |
| 10 | major | `06_evidence.feature:15–27,37–40`; `03_runner.feature:19–29` | Version-present/build-missing and incomplete owner answers are untested. The recording scenario checks only the run folder after normal evidence writing. A recorder elsewhere, or configurable capture, survives. | Add missing-build cases. Exercise attempted capture configuration and inspect all declared output locations/effective configuration, not just filename extensions under `runs/`. |
| 11 | major | `07_test_boundary.feature:7–22`; `specs/README.md:14`; `docs/spec/REQUIREMENTS.md:45` | The offline scenarios do not specify synthetic environments/layouts. Reading them literally uses actual host/repository state, contrary to the tier rule. Importability alone does not establish use of the project’s `.venv`. AT-15’s actual-environment evidence is absent. | Make offline fixtures explicitly synthetic, including a fixture that illegally extends Gherkin outside this project and is rejected. Add actual interpreter/environment and main-suite boundary evidence at the agreed host tier; clarify how infrastructure-only PC checks satisfy the current phone-evidence rule. |
| 12 | minor | `04_analyser.feature:34`; `docs/evidence/UXPLAY_LOG_SIGNATURES.md:15,36–40` | The full HEVC advice suffix and exact codec spelling are not established by the staged evidence. Evidence also lists `-nh` without explaining its semantics. | Supply the complete upstream format string and pinned option semantics before treating these as exact-source fixtures. Label codec spellings as synthetic variants until a real run confirms them. |
| 13 | major | `01_build.feature:8`; `03_runner.feature:8`; `06_evidence.feature:8` | No command-line contract or invalid-input scenarios exist. Missing/unreadable provenance, invalid arguments and output-location failures have unspecified observable outcomes. | Specify entry points, arguments, failure reporting and permitted side effects before implementing them; then add help/invalid-argument, missing-provenance and unwritable-output scenarios. Do not invent this surface in step definitions. |

## Missing scenarios

These additions use existing decisions. Cases requiring the owner ruling in finding 1 are intentionally excluded.

```gherkin
@S1 @offline @boundary @AIR-008 @AT-08
Scenario: Successful mirroring setup alone does not prove video started
  Given a complete synthetic UxPlay log containing these lines:
    """
    UxPlay 1.74: An Open-Source AirPlay mirroring and audio-streaming server.
    connection request from Fixture iPhone (iPhone17,1) with deviceID = AA:BB:CC:DD:EE:FF
    Mirroring initialized successfully
    """
  When the analyser reads the log
  Then mirroring video started is not observed
  And the video codec is unknown
  And the stream resolution is unknown

@S1 @offline @boundary @AIR-010 @AT-10 @OD-12 @OD-18
Scenario Outline: Aggregation does not depend on a companion verdict or ordering
  Given an error-free run with complete sessions whose verdicts are <sessions>
  When the evaluator judges the run
  Then the run's verdict is <verdict>
  And every session's verdict is listed unchanged

  Examples:
    | sessions             | verdict |
    | GO, INCONCLUSIVE     | GO      |
    | INCONCLUSIVE, GO     | GO      |
    | GO, GO               | GO      |
    | NO-GO, INCONCLUSIVE  | NO-GO   |
    | INCONCLUSIVE, NO-GO  | NO-GO   |
    | NO-GO, NO-GO         | NO-GO   |

@S1 @offline @failure @AIR-009 @AT-09 @OD-17
Scenario Outline: Whole-log damage overrides otherwise decisive session evidence
  Given a run containing a complete session whose verdict is <session verdict>
  And the log as a whole is <damage>
  When the evaluator judges the run
  Then the run's verdict is INCONCLUSIVE with the reason "evidence error"
  And the limitation "<damage>" is named

  Examples:
    | session verdict | damage            |
    | GO              | truncated         |
    | GO              | partly unreadable |
    | NO-GO           | truncated         |
    | NO-GO           | partly unreadable |

@S1 @offline @failure @AIR-006 @AIR-005 @AT-06 @AT-05
Scenario: Diagnostic capture failure prevents an apparently successful run
  Given a verified build and a newly created temporary firewall rule
  And diagnostic log capture cannot be established
  When the runner attempts to launch the receiver
  Then the run reports the diagnostic capture failure
  And no UxPlay process launched by the run remains running
  And the temporary firewall rule is removed
  And its removal is verified
  And the run is not reported as successfully started

@S1 @offline @boundary @AIR-007 @AIR-011 @AT-07 @AT-11 @OD-08
Scenario: Video starting just before the deadline does not extend the test
  Given the verified build and a controlled clock
  When the runner launches the receiver at second 0
  And an iPhone connects at second 590
  And mirroring video starts at second 599
  Then the runner stops UxPlay at second 600 with the reason "timeout"
  And firewall cleanup occurs after the process-stop operation
  And the run records launch at second 0, deadline at second 600 and endpoint at second 600

@S1 @offline @failure @AIR-002 @AT-02
Scenario Outline: An interrupted build cannot supply a verified executable
  Given a build that has written a partial executable
  When the build ends by <ending>
  Then the failure is reported
  And the partial executable is not recorded as a successful build output
  And it cannot be selected for a test run

  Examples:
    | ending               |
    | a compiler failure   |
    | a keyboard interrupt |

@S1 @offline @boundary @AIR-012 @AT-12 @OD-21
Scenario: A known iOS version without its build still requires owner input
  Given a synthetic run record with iOS version "27.0" and no build number
  When the evidence is written
  Then the owner is asked for the full iOS version and build from Settings > General > About
  When the owner answers "27.0 (24A5260a)"
  Then the exact answer is retained in the private run record
  And its provenance is "owner-entered"

@S1 @offline @failure @AIR-012 @AT-12 @OD-21
Scenario: A version-only owner answer leaves build evidence incomplete
  Given a synthetic run record with no iOS version or build
  When the owner supplies only "27.0"
  Then the build number is identified as missing
  And evidence acceptance is incomplete

@S1 @offline @boundary @AIR-008 @AIR-010 @AT-08 @AT-10 @OD-09 @OD-10
Scenario Outline: Changing an owner observation changes neither verdict level
  Given a complete synthetic run with one session whose verdict is <verdict>
  When only the "<note>" observation changes from "<before>" to "<after>"
  Then the session verdict remains <verdict>
  And the run verdict remains <verdict>
  And the updated observation is retained

  Examples:
    | verdict | note          | before | after |
    | GO      | sound played  | yes    | no    |
    | NO-GO   | sound played  | no     | yes   |
    | GO      | YouTube check | works  | fails |
    | NO-GO   | YouTube check | fails  | works |
```

Also extend the existing PC scenarios rather than adding more permissive duplicates:

- **Build:** repeat from a clean directory at the recorded commit; retain both complete provenances and hashes.
- **Firewall:** cover cancellation and induced runtime failure, with actual program path, rule identifier and before/during/after inspection.
- **Live test:** compare all parsed observations and session verdicts against retained evidence; verify process termination, timing, launch configuration, evidence provenance and capture configuration. Add a separately identified feasibility assertion requiring an evidenced H.264 GO.

## Requirement wording

Apply these replacements without introducing new owner policy:

**AIR-010(b):**

> (b) Subject to the run-wide evidence-error override in AIR-009(c), the run MUST be GO if any complete session is GO, with every session’s verdict listed.

**AIR-010(d):**

> (d) Subject to the run-wide evidence-error override in AIR-009(c), when no session is GO and at least one is NO-GO, the run MUST be NO-GO.

**AIR-012(b):**

> (b) If either the exact iOS version or its build number is unavailable, the runner MUST ask the owner for the full iOS version including its build number, as shown in Settings > General > About. It MUST retain the supplied value in the private run record with provenance `owner-entered`. Any required version or build value that remains unavailable MUST be identified as missing, and evidence acceptance MUST remain incomplete.

**AIR-009 decision-column obsolete sentence:**

> OD-17 resolves the interaction between a complete GO session and a run-wide incomplete or partly unreadable log: the run is INCONCLUSIVE with reason `evidence error`.

**AIR-012 decision-column obsolete sentence:**

> OD-21 requires owner entry of the full version and build when required version/build evidence is unavailable.

**AT-09 final sentence:**

> Whole-log incomplete and partly unreadable fixtures MUST produce run INCONCLUSIVE with reason `evidence error`, including when a separately complete session would otherwise establish GO or NO-GO.

**AT-10 obsolete final sentence:**

> Fixtures MUST cover OD-12 aggregation, OD-17’s run-wide evidence-error override, OD-18’s NO-GO/INCONCLUSIVE aggregation and OD-19’s HEVC-request-then-H.264 case.

AIR-002(d), AIR-009(c) and AIR-010(e) otherwise match OD-20, OD-17 and OD-19 respectively.

**Owner clarification still required:** Specify exactly when absent codec evidence invokes AIR-009(b). Also explicitly settle all-INCONCLUSIVE and zero-session aggregation. No replacement verdict policy is proposed as an adopted decision here.

## Traceability

- Structural tag validation passes; this does **not** validate assertion coverage. The checker checks IDs and tag cardinalities (`tools/trace_specs.py:48–65`).
- `06_evidence.feature:60`: add the omitted assertions supporting AIR-006/007/008 and their AT tags, or remove those tags. Add AIR-005/AT-05 if the firewall-removal assertion remains.
- `04_analyser.feature:7`: add AIR-009/AT-09 if line attribution remains part of the asserted behavior.
- Make `07_test_boundary.feature` explicitly synthetic before retaining `@offline`; add actual environment evidence separately.
- Current **49** is the number of declarations, counting each outline once (`tools/trace_specs.py:39–41,80`). It is not the expanded test-case count.
- Regenerate traceability after changes. The nine additions above would produce **58 declarations** before any other additions or restructuring.

## Not verified

- Full upstream source, exact HEVC advice suffix, actual codec spellings, and detailed option semantics were not available in the staged evidence.
- No real build, firewall operation, iPhone run, parser execution or hardware verification was performed.
- There are no implemented S1 steps to inspect. Existing binding and tier-selection infrastructure does not establish S1 behavior (`tests/test_features.py:5–10`; `tests/conftest.py:25–36`).
- `SCOPE.md` and `package/` are absent from the stage. No paths outside the stage were opened.
- The PC facts file’s “iOS version isn’t known yet” statement (`docs/evidence/S1_PC_FACTS.md:15–16`) predates OD-15; exact version/build still requires private run evidence.
- OD-22 and OD-23 concern later head-unit behavior. S1 PC evidence cannot verify HUD or parked-only video behavior.
---

## Applied by the parent (2026-11-14)

Owner rulings OD-25 (finding 1) and OD-26 (aggregation) came first. Each finding was then applied
as follows. No second review was run.

| # | What changed |
|---|---|
| 1 | OD-25 encoded as AIR-009(b). Stream arrival is defined in the new AIR-029 as the `width_source` line only, because UxPlay logs `accepting client` before any video and logs `width_source` just before an HEVC refusal. An HEVC refusal keeps its NO-GO. |
| 2 | AIR-029(a) and (c): video start comes only from the codec line. A setup-only scenario was added (`04_analyser.feature`). |
| 3 | New AIR-028 defines a complete log (start marker, banner, end marker, final newline) and the order in which limitations are named. Excerpts are wrapped into complete logs by the Given step (`specs/README.md`). The damage outline covers each limitation. |
| 4 | The live test is split into three `@pc` scenarios in `06_evidence.feature`: perform and record, agree with its own log, and the H.264 feasibility gate. |
| 5 | Aggregation matrix (11 rows, including OD-26), damage matrix and notes-invariance outline at both verdict levels (`05_verdict.feature`). |
| 6 | The review's wording was applied to AIR-010(b), AIR-010(d), AIR-012(b), the decision columns, AT-09 and AT-10. |
| 7 | ninja was added to the prerequisites, along with an interrupted-build outline and a `@host` build repeated from two clean directories. |
| 8 | Capture success and failure, video at second 599, and stop-before-cleanup on timeout and early end (`03_runner.feature`). |
| 9 | New AIR-030 sets the ownership contract (name plus program under the recorded build root), adds multiple stale rules, a same-named foreign rule and unrelated-rule preservation, and adds `@host` cancel and induced-failure cases with saved before, during and after rule lists. |
| 10 | Version without build and version-only answers were added. The capture check covers every location the run writes and the recorded command. Passing a capture option to the runner exits 2. |
| 11 | `07_test_boundary.feature` now uses synthetic layouts offline. The real-environment checks moved to the new `@host` tier (this PC, no iPhone; `--run-host`), and the infrastructure halves of AT-01 to AT-05 and AT-15 moved with them. |
| 12 | The codec spelling was confirmed from source as the literals `h264` and `h265`. The full HEVC advice string and the meaning of `-nh` are in `UXPLAY_LOG_SIGNATURES.md`. |
| 13 | New AIR-031 sets the CLI contract (`python tools/s1.py check|build|run|analyse|evaluate|summary`, help exits 0, usage errors exit 2 with no side effects, other failures exit 1). `08_cli.feature` was added. |

The ownership test in AIR-030 and the command surface in AIR-031 are parent proposals, not owner
decisions. The result is 68 scenario declarations in 8 feature files.
