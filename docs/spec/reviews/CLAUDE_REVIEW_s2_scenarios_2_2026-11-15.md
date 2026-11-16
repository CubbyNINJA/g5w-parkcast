# S2 scenario coverage review 2 (requirements revision 11)
> Reviewer: fresh-context Claude subagent standing in for `gpt-6-astra` under OD-78 (role file `tools/astra/roles/astra-scenario-reviewer.md`), read-only. **Substitute for Astra.**
> Prompt: `tools/astra/prompts/S2_REVIEW_SCENARIOS_2.md`. Date 2026-11-15.

External critique, not an owner decision.

## Verdict

**Implement with clarifications.** I found no blockers. Every Examples row I checked has correct timing and byte arithmetic. The previous review's findings 4, 23, 24, 25, 31 and missing scenario E are resolved. Fix the gaps below while the step definitions are being written:

- one major coverage gap: the AT-39 two-rotation path;
- one major harness gap: the transaction 14 absence check;
- three oracle definitions for free-text records and notices.

## Findings

| # | Severity | File:line | Finding | Concrete fix |
|---|----------|-----------|---------|--------------|
| 1 | major | `11_parking_logs.feature:37-49` | AIR-039 / AT-39 "a single record of exactly 2,621,440 bytes … is written whole" is tested only into an empty current file (row L49). Into a non-empty current file the same record is a crossing write: it rotates first, fills the new file to exactly 2,621,440, then rotates again. That double-rotation path, the most likely place for an implementation to split or lose the record, has no scenario. | Add missing scenario A. |
| 2 | major | `09_parking_gate.feature:346-357` | "Every call to the synthetic micom power service is recorded" / "no registerSystemOffResponder call (transaction 14) was recorded". The OD-66 harness (clock, fake getter, fake ACC registration and broadcasts, frame, log and notice sinks) has no power-service fake. In plain Java with no Android imports the gate cannot reach one either, so the assertion is vacuous as written. | Pick one. (i) Add a recording power-service port to the OD-66 harness and route the gate's system dependencies through injected ports only. (ii) Reword L349/L351 to: `And a static scan of the receiver's sources and compiled classes finds no registerSystemOffResponder reference and no micom power-service transaction 14`. The 12:25 integration review stays the `@unit` counterpart. |
| 3 | minor | `09_parking_gate.feature:291-319` | Missing and non-integer asleep extras are tested only after awake 0. For awake 2 and 5 only numeric asleep statuses appear, so a mapping that handles a missing extra only from state 0 would pass. AIR-019(f) maps every status the same whatever the previous status. | Add the four Examples rows in missing scenario B. |
| 4 | minor | `09_parking_gate.feature:331-335` | The ADM outline has the `5 → 0` change with the check on only. AT-21 asks for the same polling with false. There is no change into 5 (`0 → 5`, `2 → 5`). | Add the rows in missing scenario C. |
| 5 | minor | `09_parking_gate.feature:401`, `:467` | "that record says the reply arrived <case>" and "it gives status 0 / says the status extra is missing / says the status extra is not an integer" are paraphrases. No literal is defined, so the oracle would come from the code under test. AIR-019(g)/(h) say what the record must convey but give no literal. | In the feature preamble (L20-32), define the substrings the steps match. For example: `discarded` plus `during sleep` / `after resume`; `ignored` plus `status=0`, `status=5`, `status=missing`, `status=non-integer`. Record them as test-only contract text in OD-88's notes, or ask the owner (see Requirement wording). |
| 6 | minor | `10_parking_config.feature:152-156` | The gate-change table's `reason` column ("the on-time Park reading received at 10 ms", "the poll that saw the car leave Park") isn't a literal phrase AIR-026(a) mandates. The step cannot match it independently. This is left over from previous finding 23. | Either reword the column to observable facts the step checks structurally (`reason names a Park reading of 1 received at 10 ms`, `reason names a Park reading of 0 received at 610 ms`, `reason names a Park reading of 1 received at 1610 ms`), or define a `reason=` field format in the preamble. The car leaves Park at 500, so the next poll is at 600 and replies at 610. It returns at 1500, so the next poll is at 1600 and replies at 1610. |
| 7 | minor | `10_parking_config.feature:128` | "a failed read is named without logging any other contents of the file" has no concrete oracle. Only row L136 carries extra content (`pin=1234`), and the unreadable row L135 has no content to leak. | Replace with `And no record contains "pin=1234" or "1234"`. Add a row `is unreadable, and its bytes are "video.require_park=false ; pin=1234"` so the failed-read case has a canary too. |
| 8 | minor | `11_parking_logs.feature:73-79` | Rotating before the "record too large" line relies on that line being at least 11 bytes (2621430 + 11 > 2621440). That holds for "record too large 2621441\n" (about 25 bytes), but the scenario doesn't state it. A terser implementation line would make the scenario wrong. | Change L75 to `Given the current log file holds 2621439 bytes …`. Any line of two bytes or more then crosses, and only the newline-only case remains. Or add `And that line is longer than 10 bytes`. |
| 9 | minor | `09_parking_gate.feature:530-538` | Ruling 6(d) accepts splitting the broadcasts, but this sequence scenario is the only one that sees all three broadcasts together. It asserts only the notice count. An implementation that logs only the first ignored broadcast passes every per-row scenario at 09:459-475, which each send one broadcast. | Add the lines in missing scenario D. |
| 10 | minor | `09_parking_gate.feature:521-522` | "names the ACC registration failure as the reason" and "can be told apart from the logging-failure notice" need an oracle. AMB-73 leaves the wording open, so a text match would invent policy. A structured "kind" field from the notice sink would come from the code under test. | Inequality is safe to assert: `its text differs from the logging-failure notice's text`. For "names the reason", see Requirement wording: the owner should fix one required token, or accept the notice sink's kind field as the oracle. |
| 11 | minor (optional) | `09_parking_gate.feature:564-568` | Ruling 6(c) puts every AIR-023(c) fallback cause in scope. The rows cover missing file, unreadable file and unrecognised value, but not the missing key or the conflicting duplicate key. | Add the rows in missing scenario E. |
| 12 | minor (optional) | `09_parking_gate.feature:469-475` | With the check off, ignored broadcasts are tested only with status 0. AT-21 needs only "a later 0 writes an ignored record" with false, so this meets the requirement, but the off mapping of missing and non-integer extras is untested on this path. | Add the rows in missing scenario F. |
| 13 | minor (optional) | `09_parking_gate.feature:744-760` | With the check off, a mid-run transition into sleep is tested only with status 6. The sticky-start mapping at 09:722-742 covers every value, which satisfies AT-21's "each mapping case", so this is optional. | Add rows with the same columns for 7, 255, 1, 3, missing and non-integer, or leave it and note that 722-742 carries the mapping. |

## Missing scenarios

**A. A crossing write of exactly 2,621,440 bytes rotates twice** (finding 1, ruling 6(a)). Paste into `11_parking_logs.feature` after L63.

```gherkin
  @offline @boundary @AIR-039 @AT-39 @OD-57 @OD-73 @OD-82
  Scenario: A record of exactly 2,621,440 bytes into a non-empty current file rotates before and after it is written
    Given the current log file holds 1000 bytes and the older log file holds 1000 bytes
    When a record of 2621440 bytes is logged
    Then the current log file was rotated before the record was written
    And the older log file that held 1000 bytes was deleted as a whole file
    And the whole record is in one file, which holds exactly 2621440 bytes and was then rotated
    And the file that was current before the record was deleted as a whole file at that second rotation
    And the rotated files contain no part of the record
    And at no point did the log files together hold more than 5242880 bytes
```

Do not assert whether the new empty current file exists on disk after the second rotation. AIR-039 doesn't say.

**B. Missing and non-integer extras after awake 2 and 5** (finding 3). Add these rows to the Examples at `09_parking_gate.feature:301-319`:

```gherkin
      | 2     | with the status extra missing         |
      | 2     | with a non-integer status extra "on"  |
      | 5     | with the status extra missing         |
      | 5     | with a non-integer status extra "on"  |
```

**C. ADM rows** (finding 4). Add these rows to the Examples at `09_parking_gate.feature:331-335`. The arithmetic: polling stays every 200 ms from 0 to 6000. The "after 10 ms" form holds because the call at 0 replies at 10.

```gherkin
      | off   | 5     | to 0 at 1000 ms                      | never blocked video                  |
      | on    | 0     | to 5 at 1000 ms                      | never blocked video after 10 ms      |
      | on    | 2     | to 5 at 1000 ms                      | never blocked video after 10 ms      |
```

**D. Three ignored records in order** (finding 9, ruling 6(d)). Append to `09_parking_gate.feature:530-538`. The tags stay as they are, plus `@AIR-019 @AT-19 @OD-100`:

```gherkin
    And exactly three records contain "ignored"
    And in order they give status 0, then status 5, then say the status extra is missing
    And the Park getter was never called
```

**E. The rest of the fallback causes for the notice** (finding 11). Add these rows to the Examples at `09_parking_gate.feature:564-568`:

```gherkin
      | holds "other.key=false"                                   |
      | holds "video.require_park=false ; video.require_park=true" |
```

**F. Ignored broadcasts with the check off** (finding 12, optional). Add these rows to the Examples at `09_parking_gate.feature:469-475`:

```gherkin
      | off   | with the status extra missing        | says the status extra is missing         | the parking gate never blocked video  |
      | off   | with a non-integer status extra "on" | says the status extra is not an integer  | the parking gate never blocked video  |
```

## Requirement wording

- **Rulings on question 6:**
  - **(a) Settled by AIR-039 and AT-39; no owner decision is needed.** AIR-039 rotates before a write that would carry the current file past 2,621,440 bytes, and rotates on reaching exactly 2,621,440. AT-39's "a single record of exactly 2,621,440 bytes … is written whole" doesn't depend on the current file being empty. With 1000 bytes current, 1000 + 2,621,440 crosses, so the file rotates first and then reaches the threshold and rotates again. That is two rotations, the record ends up whole in one file, and the record is never split. Add missing scenario A.
  - **(b) Yes.** AIR-019(f) requires unregistering at exit "subject to clause (h)". AIR-021(b) requires the same AIR-019(f) broadcast mechanism with the flag off. AIR-019(h)'s exit rules apply to either flag value: a throw leaves nothing to unregister, and no sticky state is unregistered once. So 09:346-357 and 09:477-490 are correct with the check off.
  - **(c) In scope.** AIR-019(i) takes the flag as "the effective flag value under AIR-023, including a fallback to true". That covers every AIR-023(c) cause, not only AT-19's missing-file example. Add rows E.
  - **(d) Acceptable.** Each row asserts one ignored record per broadcast, which proves AIR-019(h) per broadcast. The order and count across a sequence need the extra lines in D.
- **AIR-019(g)/(h): the wording of records.** The requirements say what a "discarded" or "ignored" record must convey (the case; the status, missing or non-integer) but give no literal. The literals for "ACC registration failed", "discarded", "ignored" and "record too large" are fixed, and the qualifiers aren't. Ask the owner to fix a minimum token for each qualifier, as AIR-025 already does for `reason=`. Otherwise findings 5 and 6 can only be met with test-only contract text, which risks becoming invented policy.
- **AIR-019(i) against AMB-73.** The notice "MUST identify the ACC registration failure as the reason", but its wording is open. Until the owner fixes a required token (for example "ACC"), or accepts a structured notice kind as the oracle, the "names the reason" step at 09:521 can't have an independent oracle. 09:520 itself is not invented policy.
- **AIR-026(a).** It requires "the reason" but sets no format. See finding 6.
- **Invented policy and AMB-71 to AMB-74:** none found.
  - Nothing asserts notice wording, timing, duration or retry. 09:584-600 deliberately doesn't assert a retry.
  - Nothing asserts a registration-failure notice with the check off. 11:134-146 asserts only the logging notice.
  - 09:28-32 and 12:7-9 state these exclusions.

## Traceability

- `.venv/Scripts/python.exe tools/trace_specs.py --check` exits 0 with no output.
- Every S2 AT row from AT-16 to AT-27 and AT-35 to AT-40 has at least one `@offline` scenario and one `@unit` scenario.
- Previous review, by item:
  - **Finding 4:** resolved. 09:56-70 uses only stationary and parking-brake rows, and 09:84 and 12:25 assert no Park listener and no BCM request.
  - **Finding 23:** resolved in structure (10:147-157, three ordered records plus the transition clause). The reason oracle is still open (finding 6).
  - **Finding 24:** resolved. 10:159-174 has the earlier-log column and "read no state from the earlier run or its log".
  - **Finding 25:** resolved. The AIR-039 trigger is in the requirement, and 11:37-63 gives exact sizes.
  - **Finding 31:** resolved. 10:42-43 and 10:45-56 carry OD-80.
  - **Missing scenario E:** resolved under OD-84. 09:369-392 and 09:412-427 cover it.
- The revision 7 to 11 areas are covered:
  - ACC mapping and ADM: 09:257-335 and 722-742.
  - Discarded records: 09:394-427.
  - Pending, failed and ignored registration: 09:429-504.
  - Registration-failure notice and separate allowance: 09:515-613 and 11:118-146.
  - Flag-off counterparts: 09:700-760 and 11:134-146.
  - Rotation trigger: 11:37-79.
  - Literal `reason=` and duplicate or spaced keys: 10:15-56 and 10:122-145.
  - Transition records: 10:147-174.
  - The gaps are in findings 1, 3, 4, 9 and 11.
- Arithmetic checked row by row:
  - **09:**
    - AT-17 timing: 09:86-199.
    - Startup and sticky: 09:257-289.
    - Sleep transition (0, 200, 400) and ADM: 09:291-335.
    - Resume-pending (150 → 160, 650 → 660): 09:369-383.
    - Age (fresh at 2010, unavailable at 2011): 09:412-427.
    - Pending (300 and 500; 310): 09:429-440.
    - New process start (50; 60): 09:492-504.
    - AT-18 (shift 399/401, poll at 400/600, checker rows 650/651/652/851): 09:615-664.
    - Flag-off calls (50 to 1450 by 200; 1000, 1200, 1400): 09:722-742.
    - Flag-off re-reads (1000, 350, 850, 350): 09:744-760.
  - **11:**
    - Rotation: 2621430 + 9 = 2621439; +10 = 2621440; 0 + 2621440.
    - Crossing: 2621430 + 11 = 2621441; 2621439 + 2 = 2621441.
    - Oversize: 2621441.
  - All are correct.
- **Harness drivability:** all `@offline` scenarios can be driven by the OD-66 harness except 09:346-357 (finding 2).
  - 09:84's "no Park listener registered and no BCM request" holds by construction only if the fake vehicle port exposes nothing but `getGearPStatus`. State that in the step file.
  - 10:159-174 "read no state from the earlier run or its log" needs the harness to record file reads, and steps 10:64-88 need that too. The harness should therefore include a file-access recorder or a fake filesystem port as well as the sinks OD-66 lists.

## Not verified

- `specs/README.md` tag rules, including whether a feature-level `@unit` tag (`12_parking_unit.feature:1`) counts as each scenario's tier tag. `trace_specs.py --check` passes, which suggests it does.
- The full text of OD-66, OD-88 and OD-100 to OD-102 in `docs/spec/DECISIONS.md`, beyond what the feature tags and requirement rows quote.
- `docs/spec/TRACEABILITY.md` content beyond `--check` passing.
- The evidence files `docs/evidence/GEAR_PROBE_FINDINGS.md` and `docs/evidence/SLEEP_SIGNAL_FINDINGS.md`. I didn't check the ACC status values against them.
- `tests/` and step definitions. None exist yet, as the prompt expects.
- Whether the 09:86-199 AT-17 rows depend on an age limit other than the 2000 ms implied by 09:420 and 09:717. I checked them against that limit.
