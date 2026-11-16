# S2 scenario coverage review 3 (requirements revision 16)
> Reviewer: fresh-context Claude subagent standing in for `gpt-6-astra` under OD-78 (role file `tools/astra/roles/astra-scenario-reviewer.md`), read-only. **Substitute for Astra.**
> Prompt: `tools/astra/prompts/S2_REVIEW_SCENARIOS_3.md`. Date 2026-11-15.

External critique, not an owner decision.

## Verdict

**Implement with clarifications.** No blocker. Step definitions can start once findings 1 to 3 are applied.

- Arithmetic: every Examples row and timed assertion I checked in 09 and 11 is correct. That includes S+5000, F+5000, the 1 s cover grid, uncover+5000, Y/Y+H stacking, the TOP x=40 fallback, and the exit and tie rows (listed under Traceability).
- Coverage: AT-19 and AT-40 `@offline` criteria all have asserting scenarios. AT-41 `@offline` has one decisive gap: an intent returned by registration counting as a receipt (finding 2). It also has one ordering clause no scenario asserts: AIR-041(j), registration before the first focus gain (finding 3).
- Tags: there is exactly one slice (`@S2`, at feature level), and every scenario I read has one tier and one kind. `trace_specs.py --check` exits 0.
- Evidence tiers: kept apart. `12_parking_unit.feature:244` states that an offline pass is not the unit run.
- Failure and boundary cases: present for throwing create, measure, add and remove; record-write failure; exit at ties; registration throwing or returning an intent; never focused.
- Invented policy: none found. `status=255` (09 L524-540) is valid under AIR-019(h) ("decimal of any status").
- Harness (OD-66): every scenario can be driven, with two caveats. Finding 3 needs a lifecycle double. Finding 8 (`built manifest`) may need the PC toolchain.

## Findings

| # | Severity | File:line | Finding (mutation that slips) | Concrete fix |
|---|---|---|---|---|
| 1 | major | `09_parking_gate.feature:12-13`; `11_parking_logs.feature:528-534`, `:595-601` | "Was called only at lists every call started in that span and no others" does not fix how many calls happen at each listed time. Mutation: keep the AIR-040(e) timed removal running alongside the cover grid, so two removal calls are made at 6000 (S+5000) in L528 and two at 11000 (the pending AIR-040(h) retry plus the grid) in L595. Both pass, and this breaks AIR-041(b) "exactly one call". L504-515 catches the tie only for an un-thrown loss at 6000. | Replace 09 L12-13 with: `"Runs for N ms" covers clock times 0 to N inclusive. "Was called only at" and "were made only at" list every call started in that span, exactly one call at each listed time, and no others.` |
| 2 | major | `11_parking_logs.feature:697-706` | The receiver is covered from process start until the first gain anyway (AIR-041(a)), so this scenario passes when the intent returned by registration is ignored. Mutation: discard `registerReceiver`'s return value. AIR-041(f)/(j) make the only observable a `reverse_on received` record at the registration time. | After L706 add: `And exactly one record containing "reverse_on received" was written, at 0 ms` and `And no record containing "notice cover" was written before 3000 ms` |
| 3 | major | `11_parking_logs.feature:700`; no scenario | No scenario asserts AIR-041(j) "called and returned before its activity window can receive its first onWindowFocusChanged(true)". Every AT-41 scenario relies on the harness ordering (the L700 Given and the 09 default focus). Mutation: register REVERSE_ON lazily (in `onPostResume`, a posted Runnable, or on the first focus gain), so a REVERSE_ON can be missed in that gap. Ruling (b): the Given is acceptable only as a harness precondition, and only if missing scenario A exists. | Add missing scenario A. |
| 4 | minor | `REQUIREMENTS.md:97` (AT-41); `11:528-534` | "no call at `S+5000 ms`; removal calls at exactly ... and every 1,000 ms after" contradicts itself, because S+5000 is on that grid. The scenario (one grid call at 6000) is right under AIR-041(b): the pending AIR-040(e) removal is not made, and a tie gives exactly one call. Ruling (a): one grid call at S+5000 and no extra timed removal is correct. | Requirement wording 1. |
| 5 | minor | `REQUIREMENTS.md:97`; `11:586-592`, `:595-601` | "one removal call at `S+7000 ms` and none at `S+10000 ms`" is correct only when the S+7000 call returns, and the AT text does not say so. Both scenarios are correct. | Requirement wording 2. |
| 6 | minor | `09_parking_gate.feature:28-44` | The UI-double contract lists only add, removal and updateViewLayout as recorded. Yet 09:742 and 11:188 assert that "no Toast.show() call, other window type, dialog, notification or activity was recorded", which AT-40 requires. Mutation: on add failure, fall back to `Toast.show()`. That passes if the double does not record such calls. | In L33-34, after "updateViewLayout call", insert: `, and it also records any Toast.show() call, any window of another type, and any dialog, notification or activity start` |
| 7 | minor | `09_parking_gate.feature:36-37`; `11_parking_logs.feature:14-24` | The 09 default "has focus throughout" does not give the order that AIR-041(j) and AT-19 need. The REVERSE_ON registration returns first, then focus is gained, then the ACC registration call returns, all at 0 ms. The 11 "Covered" definition leaves out "from process start until the first focus gain" and "for the whole run with no activity window". It also says held attempts are made "at the next focus gain" instead of at the uncover time. These are oracle ambiguities: a step writer could deliver focus before the REVERSE_ON registration returns. | 09 L36-37: `Unless a step says otherwise, at 0 ms the REVERSE_ON registration call returns, then the receiver's window gains focus, then the ACC registration call returns; the window keeps focus, so it is not covered (AIR-041).` 11 L20: put `from process start until the first focus gain, ` before "from a focus loss". 11 L23-24: replace "at the next focus gain" with `at the time the receiver stops counting as covered (AIR-041(d))`. |
| 8 | minor | `11_parking_logs.feature:889-894` | "the sources contain no REVERSE_OFF or isRearViewOn reference used for notice covering": a static scan cannot decide purpose. Mutation: read `isRearViewOn` in a helper that the scanner judges unrelated. Also, "built manifest" may need the PC's Android toolchain, which would make it `@host`, not `@offline`. | L890: `the receiver's sources and source AndroidManifest.xml are scanned`. L893: `And the sources contain no REVERSE_OFF or isRearViewOn reference` (AIR-041(g) leaves no other use). Requirement wording 3. |
| 9 | minor | `12_parking_unit.feature:253-254` | (f) The anchors partly hold. The PgsService `REVERSE_ON` line is a precise log timestamp (CAMERA_SIGNAL_FINDINGS.md, 18:33:50.34 row), so the lower bound is sound. "Before AvmCamera takes focus" has no log line: focus was sampled about once a second, and `dumpsys` took over 1 s per sample (same file, "Not settled"). AIR-041 does not require "`covered=no` after the AvmMode REVERSE_OFF line". `covered=no` is caused by a focus gain and REVERSE_OFF is ignored (AIR-041(g)). A unit whose focus returns before AvmMode logs REVERSE_OFF would fail a correct receiver. The scenario also does not assert `cause=focus`. | L253: `And those records are timed at or after the PgsService REVERSE_ON log line and no later than the first focus sample showing AvmCamera`. L254: `And the receiver's records hold a "notice cover" record with "covered=no" and "cause=focus", timed no earlier than the last focus sample showing AvmCamera and no later than the first focus sample back on another window after it`. Requirement wording 4. |
| 10 | minor | `11_parking_logs.feature:517` | Tagged `@AIR-019 @AIR-040`, but the steps assert only cover-start removal (AIR-041). The registration failure and logging failure are setup. specs/README says to tag only IDs whose behaviour the steps assert. | Change L517 to `@offline @normal @AIR-041 @AT-41 @OD-117 @OD-120`. |

Rulings not raised as findings:
- **(c)** OD-104's "texts differ" is superseded by OD-106/OD-112. The AIR-019 decision column reads "Tests assert both notice texts exactly (OD-104, OD-112)". No "differ" step remains in 09, 10 or 11, and the exact-text steps (09:741, 11:187) imply that the texts differ. The `@OD-104` tags stay valid, because OD-104 also covers literal log tokens and the power-service static scan (09:365).
- **(d)** "Asleep" needs no step of its own. It is observable through the getter schedule and blocked video (09 L454-464), and AT-19 states the retry is made while asleep.
- **(e)** The startup flag record is `parking check off`, written first and only for an explicit false (OD-105; 09 L571-581 is consistent). `status=255` is right.

## Missing scenarios

A. Paste into `11_parking_logs.feature` after L706. This needs a lifecycle double. If the OD-66 harness has none, replace it with a static check that the registration call is made from `onCreate`.
```gherkin
  @offline @normal @AIR-041 @AT-41 @OD-124 @OD-128
  Scenario Outline: The REVERSE_ON registration returns before the window can first gain focus
    Given the parking check is <flag>, on a supported build
    And the lifecycle double runs onCreate, onStart and onResume at 0 ms and delivers the first focus gain as soon as onResume returns
    When the receiver starts
    Then the recorder shows the REVERSE_ON registration call returned before onResume returned
    And the recorder shows it returned before the first onWindowFocusChanged(true) was delivered
    And exactly one REVERSE_ON registration call was made

    Examples:
      | flag |
      | on   |
      | off  |
```

B. Paste after scenario A. It covers AIR-041(a) "with no gain after a REVERSE_ON, covered until exit", reached from a focused state, plus the record that finding 2 requires.
```gherkin
  @offline @boundary @AIR-041 @AT-41 @OD-124 @OD-128
  Scenario: A REVERSE_ON with no focus change after it keeps notices off until exit
    Given the parking check is on, on a supported build
    And REVERSE_ON is delivered at 500 ms
    When a log write fails at 1000 ms
    And the receiver runs for 60000 ms and then exits
    Then no view was created, measured or added at any time
    And no removal call was made, before or at exit
    And exactly one record containing "notice cover", "covered=yes" and "cause=reverse" was written, at 500 ms
    And no record containing "covered=no" was written
```

## Requirement wording

1. AT-41 (`REQUIREMENTS.md:97`): replace `no call at \`S+5000 ms\`; removal calls at exactly` with `exactly one removal call at \`S+5000 ms\` (the AIR-040(e) removal is not made in addition); removal calls at exactly`.
2. AT-41: replace `one removal call at \`S+7000 ms\` and none at \`S+10000 ms\`;` with `one removal call at \`S+7000 ms\`, and if it returns, none at \`S+10000 ms\`;`.
3. AT-41 static scan: replace `contain no \`REVERSE_OFF\` or \`isRearViewOn\` reference used for notice covering` with `contain no \`REVERSE_OFF\` or \`isRearViewOn\` reference`.
4. AT-41 `@unit`: replace `before \`AvmCamera\` takes focus; a \`covered=no\` record after the \`AvmMode\` \`REVERSE_OFF\` log line, when focus returns;` with `no later than the first focus sample showing \`AvmCamera\`; a \`covered=no\` record with \`cause=focus\`, timed between the last focus sample showing \`AvmCamera\` and the first sample showing another window after it;`.

## Traceability

- `.venv/Scripts/python tools/trace_specs.py --check` exits 0. Scenario counts: 09 = 75 (47 outlines), 10 = 19 (10), 11 = 72 (29), 12 = 33 (1).
- Arithmetic checked, all correct:
  - 09: L94, 103, 111, 121, 154, 186-202, 257-260, 267, 274-275, 289-290, 311, 390, 405-407, 443-444, 458, 463, 604-605, 762-763, 785-786 (G=3000 to 8000), 795-799, 833-849 (exit at 5000 drops the retry), 860-867, 987, 1011-1014.
  - 11: every timed row from L205 to L823, including:
    - the 2000-9000 grid (L528);
    - gain at 5500 or 5000 with the retry at 10500 or 10000 (L546-561);
    - 6000/8000/9000/10000/11000 (L595);
    - the intent at 0 with removal at 8000 (L697);
    - the held retry made at the 11000 gain (L783-792).
- After the fixes, regenerate:
  - AIR-019 and AIR-040 rows lose `11_parking_logs.feature:518` (finding 10);
  - AIR-041 gains the two new scenarios.
  - The counts change to 11 = 74 scenarios (30 outlines) and 12 unchanged.

## Not verified

- Feature files 10 and 12 were only partly read. I grepped 12 for AT-40/AT-41 and read L239-282 in full. 10 was checked only through traceability.
- Whether the OD-66 harness has a lifecycle double (scenario A), and whether the "built manifest" scan in 11:890 needs the Android toolchain.
- AT-41 cites `CAMERA_SIGNAL_FINDINGS.md:16-39` and AIR-041(g) cites `:11,16-47`. I read the file but did not check those line ranges against its current numbering.
- No step definitions or receiver code exist, so no current behaviour is cited.

## Corrections after this review (2026-11-16)

Found while implementing the gate for 09; both are arithmetic errors this review's arithmetic check missed.
No behaviour changed.

- `09_parking_gate.feature:460`: "called only at 300 and 500 ms" after running for 700 ms is now "300, 500
  and 700 ms". Runs include time N (09:13) and the 200 ms schedule from the 300 ms re-read puts a call at 700.
- `09_parking_gate.feature:687`: the run is now 6000 ms, not 2000 ms, so the removal calls at exactly 5000 ms
  that the scenario asserts can happen within it.
