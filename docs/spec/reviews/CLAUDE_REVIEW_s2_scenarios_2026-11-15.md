# Coverage review: S2 parking-gate scenarios (substitute reviewer)

> Reviewer: a fresh-context Claude subagent standing in for `gpt-6-astra`, which was at its Codex usage limit (OD-65). Same role prompt (`tools/astra/roles/astra-scenario-reviewer.md`) and prompt (`tools/astra/prompts/S2_REVIEW_SCENARIOS.md`), with STAGE_ROOT as the project root. Read-only; it ran `tools/trace_specs.py --check` only.
> Date 2026-11-15.

**External critique, not an owner decision. Substitute for Astra under OD-65.**

---

## Verdict

**Blocked.** There are 31 findings: 2 blockers, 17 majors and 12 minors.

Both blockers can be fixed with a short definitions paragraph in the `09_parking_gate.feature` header and one corrected call list. Once those are in, the rest can be fixed alongside step-definition work, so the verdict becomes "implement with clarifications".

- **Blocker 1.** One step definition for "runs for N ms" cannot pass both line 57 and line 74.
- **Blocker 2.** Most timing scenarios leave the replies of unnamed getter calls undefined, so several Then lines have no single expected value.

## Findings

| # | Severity | File:line | Finding | Concrete fix |
|---|---|---|---|---|
| 1 | blocker | `09_parking_gate.feature:56-57`, `:73-74` | The two cadence scenarios use different endpoint rules. Line 57 lists a call at 1000 for "runs for 1000 ms", so the run includes its end time. Line 74 is also a 1000 ms run; its calls are 0→300 late, 300→600 late, 600→610, 800, then 1000, yet it stops at 800. Whichever rule the step uses, one of the two scenarios fails. Line 66 says "was called at" without "only", so it doesn't rule out extra calls at 800 or 860 (AT-17 requires none). | Add to the header after line 10: `"Runs for N ms" covers clock times 0 to N inclusive. "Was called only at" lists every call started in that span and no others.` Change line 57 to `called only at 0, 200, 400, 600, 800 and 1000 ms`, line 66 to `called only at 0, 650, 850 and 1050 ms`, and line 74 to `called only at 0, 300, 600, 800 and 1000 ms`. |
| 2 | blocker | `09_parking_gate.feature:80`, `:95`, `:105`, `:113`, `:163`, `:238`, `:250`; `10_parking_config.feature:113` | Many scenarios name one or two calls and leave the rest unspecified. Line 105: "never blocked" over 1000 ms needs the calls at 400, 600, 800 and 1000 to reply 1 on time. Lines 116 and 122: "still blocked" has no end time if later calls reply 1. Lines 171-172: "until a later on-time reply of 1" never says when that reply comes. "Leaves Park at 500 ms" relies on the getter mirroring the gear, which is never stated. Lines 64 and 72 do define later replies, so the feature is inconsistent with itself. | Add to the header: `Unless a step says otherwise, the car is in Park, the build is supported, and every Park getter call replies with the car's gear state (1 in Park, 0 out of it) within 10 ms. "Video is permitted after an on-time Park reading at 0 ms" means the call started at 0 ms replied 1 at 10 ms.` Then give every "still blocked" or "until a later" outcome an end time (see #8 and #13). |
| 3 | major | `09_parking_gate.feature:38-50` | The getter replies 0, so video is blocked whatever the other signal says. This kills an OR mutation (`getter==1 \|\| stationary`) but not the more likely fallback mutation: use stationary or brake when the getter is unavailable. Under AIR-017(d), those signals must not establish Park even then. | Turn it into a two-column outline with `<getter>` in `0 within 10 ms`, `with no value within 10 ms` and `with a failed call`, crossed with the stationary and brake rows. Each of the 6 rows expects `blocked`. |
| 4 | minor | `09_parking_gate.feature:50` | The BCM row is vacuous. AT-24 and line 59 say the receiver makes no BCM status request, so a fake BCM that "succeeds with no value" is never called. AIR-017(d)'s "status-request call returning no gear value" could mean this BCM call or the getter's no-value reply, which line 35 already covers. | Drop the row, or reword it to `a BCM gear status request would succeed with no value` and add `And no BCM status request was made`. See Requirement wording 1. |
| 5 | major | `09_parking_gate.feature:77-90` | AT-17 also requires the startup case: an initial call returning at exactly 200 ms gives next calls at 200, 400 and 600. That case is missing. This outline asserts only the next call at 400, not the cadence after it, doesn't assert AT-19's 10 ms pending follow-up, and "blocked from 400 ms" has no end time. | Change lines 80 and 83-85 to: `And the Park getter call starting at 200 ms replies 1 at exactly 400 ms, and the call starting at 400 ms replies 1 at 410 ms` / `Then mirrored video is blocked from 400 ms until 410 ms` / `And the late reply never permits video` / `And the Park getter was called only at 0, 200, 400, 600 and 800 ms`. Add Missing scenario A. |
| 6 | major | `09_parking_gate.feature:92-100` | AT-17 requires a call pending beyond 2 s that then returns, followed by an immediate next call. This scenario never returns. The AT-19 startup pending case is also missing. | Add Missing scenarios B and C. |
| 7 | minor | `09_parking_gate.feature:79`, `:94`, `:104`, `:111` | "An on-time Park reading at 0 ms" doesn't say when the reply was received; AIR-019(b) measures age from receipt. | Covered by the definition in #2. |
| 8 | major | `09_parking_gate.feature:113-122` | Rows and arithmetic are correct. AT-19 also requires the on-time follow-up to leave video blocked for an unknown value, a missing value and a failure; only `0` is present. "Still blocked" has no end time. | Change line 113 to `And the call after it replies <next> after 10 ms, and the call after that replies 1 within 10 ms`, and line 116 to `And after that next reply mirrored video is <after>`. Add rows for `2`, `with no value`, `with a failed call` at 850, and `0` at 401, each `still blocked until the following reply of 1`. Reword row 122 the same way. |
| 9 | major | `09_parking_gate.feature:124-134` | Rows 1999/2000/2001 match AIR-019(b). The Given gives only a receipt time, so a mutation that measures age from invocation passes every row. AIR-019(b) says "unavailable", not "stale". | Change line 126 to `Given a Park reading of 1 from a call started at 1000 ms and received at 1150 ms`, line 128 to `Then the reading is <state> by age`, and the rows to `3149 fresh`, `3150 fresh`, `3151 unavailable`. See Requirement wording 6. |
| 10 | minor | `09_parking_gate.feature:141` | The 210 ms bound is loose, passes the invocation-age mutation, and needs the gate's internal age. | Use `Then consecutive Park replies were received at most 200 ms apart`. |
| 11 | major | AT-19 (missing) | No scenario checks receipt-age freshness of a late value separately from permission. | Add Missing scenario D. |
| 12 | minor | `09_parking_gate.feature:148-157` | For the rows after Park, "from that moment" has no time, so a gate that keeps permission until the 200 ms timeout is caught only by chance. | Change line 148 to `When the Park getter call starting at 200 ms <fault> at 210 ms` (service-loss row: `the Park getter service <fault> at 210 ms`), and line 149 to `Then mirrored video is blocked from 210 ms until the next on-time reply of 1`. |
| 13 | major | `09_parking_gate.feature:159-173` | Rows 2-3 never say when the later on-time reply comes; nothing covers a call pending at resume; nothing says whether polling continues while asleep. | Change the `until` cells to `until 1110 ms, then shown on the next frame`, `until 1360 ms, then shown on the next frame` and `until 1310 ms, then shown on the next frame`. Add Missing scenario E. |
| 14 | major | `09_parking_gate.feature:175-192` | Arithmetic holds. The shift at 400 coincides with a poll but no order is stated (AT-18 asks for explicit ordering): shift first gives a return at 400, poll first at 600. Frozen at 400 is missing. | Remove the `moving \| 400` row and add Missing scenario F. |
| 15 | major | `09_parking_gate.feature:184` | "No frame … after the image is gone" is weaker than AIR-018(b), which applies from the poll return. | Change it to `And no frame, queued or new, is shown from the moment that reply is received`. |
| 16 | major | AT-18 (missing) | Nothing tests the timing oracle itself ("reject a 251 ms removal after return even when the total is below 450 ms"). | Add Missing scenario G. |
| 17 | minor | `09_parking_gate.feature:203-209`, `:211-217` | `contains("CVEV")` and "product2 must also be CVEV" mutations survive. | Add `\| "NE1.CAN.CVEV.260206" \| blocked \|`, and a scenario with product `"CVEV.CAN.0000.V015.011.260206"` and product2 `"NE1.CAN.S5W_M.V015.011.260206"` expecting `permitted`. |
| 18 | major | `09_parking_gate.feature:228-233` | AT-21 lists Park and non-Park, stale, missing, error, unknown gear, and outside or unidentified builds; several have no row. | Add rows: getter replies 1; replies 2; replies with no value; service missing; replies 0 at 10 ms and the call starting at 200 ms never replies (stale from 2010 ms); `ro.build.product` missing. |
| 19 | minor | `09_parking_gate.feature:250` | AIR-037 permits recovery only "under AIR-017–020"; the Given doesn't say the build is supported. | `Given the parking check is on, on a supported build, and video is blocked because the car left Park`. |
| 20 | minor | `09_parking_gate.feature:256-261`; `12_parking_unit.feature:19` | Nothing asserts AT-24's "AIR-038 regulation notifications remain permitted"; the unit line could pass on log silence alone. | Add `And its registration for stock regulation change notifications was allowed`. Change unit line 19 to `And the receiver's own call trace and an integration review, not log silence alone, show no Park listener and no BCM status request`. |
| 21 | minor | `10_parking_config.feature:50-52` | The path leaves out `<id>/`; "it read the flag from" needs a file-access trace; a decoy is a cleaner oracle. | `Given the package is installed in /data/nemesis/apps/<id>/ with config/receiver.conf holding "video.require_park=false", and a decoy package/config/receiver.conf holding "video.require_park=true"` / `Then the parking check is off`. |
| 22 | major | `10_parking_config.feature:99`, `:102-108` | "With the initial gate state and the reason" accepts a wrong initial state; the missing-key and conflicting-duplicate fallbacks have no rows. | Add an `initial` column (`blocked` with the check on, `permitted` off) and assert `the initial gate state as <initial> and a reason naming <cause>`; add rows `holds "other.key=false"` and `holds "video.require_park=false ; video.require_park=true"`, both on, fallback. |
| 23 | minor | `10_parking_config.feature:110-115` | "One record for each gate change" doesn't list the changes; an extra or wrong-reason record could pass. | List the expected records with times and reasons once the startup-transition question is answered. |
| 24 | minor | `10_parking_config.feature:119` | "And its log is gone" implies the previous value could be known from a surviving log; AIR-026 selects no persistence. | Drop `, and its log is gone`, or keep it pending the persistence question. |
| 25 | major | `11_parking_logs.feature:32`, `:40-43` | The arithmetic is consistent, but the split between the two files decides the outcome, and AIR-039 has no rotation trigger: a newer file near 5 MiB can't stay under the cap without trimming. | Give each file's size (e.g. 2621440/2621438, 2621440/2621439, 2621440/2621430) and raise the rotation trigger as an open question. |
| 26 | major | `12_parking_unit.feature:27` | "No frame appears while the car is out of Park" over-asserts: AIR-018 allows the image up to about 450 ms after the shift. | `And no new frame appears from the first poll returning 0 until an on-time reply of 1 after the car is back in Park`. |
| 27 | major | `12_parking_unit.feature:13-19` | AT-17's `@unit` evidence for late returns and the list of unexercised cases are not asserted. | Add `And any late return in the evidence is followed at once by the next call, then calls every 200 ms from it` and `And unexercised delay, hang and exact-200 ms cases are listed as unverified`. |
| 28 | major | `12_parking_unit.feature:76-80` | AT-35 `@unit` covers update, reinstall and a rebuilt `false` package; only update is present. | Add Missing scenario H. |
| 29 | minor | `12_parking_unit.feature:82-87` | AT-38 forbids modifying or spoofing regulation to obtain evidence; the scenario doesn't say how the change is caused. | `When the stock regulation state changes during the test, without the receiver or the owner modifying or spoofing it`. |
| 30 | minor | `12_parking_unit.feature:50-55` | AT-37 `@unit` asks to correlate Park confirmation with frame timing. | Add `And the stick evidence correlates the on-time reply of 1 with the time the next frame appeared`. |
| 31 | minor | `10_parking_config.feature:28` | Identical duplicates resolving to `off` is a reasonable reading of AIR-023(b), but not stated by it. | Keep the row, cite AIR-023(b), confirm with the owner (Requirement wording 3). |

### Independent oracles for the new steps (question 4)

- **Getter calls.** Read a harness-owned getter fake that records invocation and return times on the controlled clock; compare with the scenario's numbers, never with times the gate logs.
- **Clock and timers.** The gate must take its time and 200 ms timeout from a clock and scheduler the harness injects, which must also choose the order of coincident events. A gate using `Thread.sleep` or `Future.get(timeout)` can't be driven through an exact-200 ms race. `tests/steps/fakes.py` `FakeClock` orders by `(t, seq)` and works in seconds. Under OD-66 the clock and scheduler live in the Java harness and Python reads its trace; whatever runs the harness shouldn't also compute the expected values.
- **Permitted/blocked and image gone.** Use a display or frame-sink double that records each frame and the hide event with clock times, not a decision flag. "Next frame" needs a fixed synthetic frame cadence (a fixture choice).
- **Age classification.** Needs a direct age-classification call on the gate, as AT-19 asks for age independently of timeout blocking.
- **Logs.** Parse with a parser written from the AIR-025/026 field list, not the receiver's formatter. No fixed record format was found.
- **Config and log cap.** Decoy files for the install path; a fake filesystem with byte counts, expected totals computed in Python.
- **Vehicle interfaces.** A fake service registry recording every transaction by service and number, allowing only `com.hkmc.micom.system` txn 36 plus regulation status and notifications.
- **Audio and connection.** Fakes recording mute, stop, disconnect and connection state.

### Invented policy (question 3)

- One over-assertion: `12_parking_unit.feature:27` (#26).
- Two interpretations to confirm: `10_parking_config.feature:28` (#31) and `:119` (#24).
- Not invented: the `"CVEV."` prefix; "not reported as a successful replacement" is in AT-35 but conflicts with AIR-035(c) (Requirement wording 4).

## Missing scenarios

A: the first call is answered at exactly 200 ms (AT-17 startup case).
```gherkin
  @offline @boundary @AIR-017 @AIR-019 @AT-17 @AT-19 @OD-62 @OD-63 @OD-64
  Scenario: A first call answered at exactly 200 ms is late, and the cadence restarts from 200 ms
    Given the parking check is on, on a supported build
    And the first Park getter call replies 1 at exactly 200 ms and every later call within 10 ms
    When the receiver runs for 700 ms
    Then the Park getter was called only at 0, 200, 400 and 600 ms
    And the reply at 200 ms never permits video
    And mirrored video is blocked until 210 ms and shown from the next frame after 210 ms
    And at most one Park getter call was active at any time
```

B: a call pending beyond 2 s, then a return (AT-17).
```gherkin
  @offline @boundary @AIR-017 @AIR-019 @AT-17 @AT-19 @OD-62 @OD-63
  Scenario: A call pending past 2 s is never overlapped, and its return starts the next call at once
    Given the parking check is on and video is permitted after an on-time Park reading at 0 ms
    And the Park getter call starting at 200 ms replies 1 at 2500 ms and every later call within 10 ms
    When the receiver runs for 2800 ms
    Then the Park getter was called only at 0, 200, 2500 and 2700 ms
    And at most one Park getter call was active at any time
    And mirrored video is blocked from 400 ms until 2510 ms and shown from the next frame after 2510 ms
```

C: the startup call stays pending (AT-19 "startup").
```gherkin
  @offline @failure @AIR-017 @AIR-019 @AT-17 @AT-19 @OD-62
  Scenario: A first call that never replies keeps video blocked and is never overlapped
    Given the parking check is on, on a supported build
    And the first Park getter call never replies
    When the receiver runs for 2700 ms
    Then mirrored video is blocked throughout
    And the Park getter was called only at 0 ms
```

D: a late value is fresh by age but can't permit video (AT-19).
```gherkin
  @offline @boundary @AIR-019 @AT-19 @OD-64
  Scenario: A late reply counts as a fresh reading by age but still cannot permit video
    Given the parking check is on and video is permitted after an on-time Park reading at 0 ms
    And the Park getter call starting at 200 ms replies 1 at 850 ms
    And the call starting at 850 ms never replies
    When the receiver runs for 1000 ms
    Then the reading received at 850 ms is fresh by age at 1000 ms
    And mirrored video is blocked from 400 ms to the end
```

E: a call pending at resume (AT-19 "without overlapping a pending call"); depends on the sleep question.
```gherkin
  @offline @failure @AIR-017 @AIR-019 @AT-17 @AT-19 @OD-56 @OD-62 @OD-63
  Scenario: A call still pending at resume is not overlapped, and its late reply cannot permit video
    Given the parking check is on and video is permitted after an on-time Park reading at 0 ms
    And the Park getter call starting at 200 ms replies 1 at 1150 ms and every later call within 10 ms
    And the head unit sleeps at 300 ms and resumes at 1100 ms
    When the receiver runs for 1500 ms
    Then no Park getter call started after 200 ms and before 1150 ms
    And the next Park getter call started at 1150 ms
    And mirrored video is blocked from the resume until 1160 ms and shown from the next frame after 1160 ms
    And at most one Park getter call was active at any time
```

F: a shift at the same moment as a poll, in both orders (AT-18).
```gherkin
  @offline @boundary @AIR-018 @AT-18 @OD-46 @OD-61
  Scenario Outline: A shift at the same moment as a poll stays within all three bounds, in either order
    Given the parking check is on and video is showing a <image> image after an on-time Park reading at 0 ms
    And frames are queued for display
    And the Park getter replies at once with the state at the moment it is called
    And at 400 ms the <first> is processed first
    When the car shifts out of Park at 400 ms
    Then the first reply of 0 is received at <return> ms
    And the image is gone at most 250 ms after that reply is received
    And the image is gone at most 450 ms after the shift
    And no frame, queued or new, is shown from the moment that reply is received

    Examples:
      | image  | first | return |
      | moving | shift | 400    |
      | moving | poll  | 600    |
      | frozen | shift | 400    |
      | frozen | poll  | 600    |
```

G: self-test of the AT-18 timing oracle.
```gherkin
  @offline @boundary @AIR-018 @AT-18 @OD-61
  Scenario Outline: The AT-18 timing check rejects a trace that breaks any one bound
    Given a display trace with the shift at <shift> ms, the first reply of 0 at <return> ms and the image gone at <hide> ms
    When the AT-18 timing check judges it
    Then it is <verdict>
    And the bounds it names as broken are <broken>

    Examples:
      | shift | return | hide | verdict  | broken                                  |
      | 399   | 400    | 650  | accepted | none                                    |
      | 399   | 400    | 651  | rejected | the 250 ms removal                      |
      | 401   | 602    | 652  | rejected | the 200 ms polling                      |
      | 400   | 600    | 851  | rejected | the 250 ms removal and the 450 ms total |
```

H: reinstall and a rebuilt `false` package on the unit (AT-35 `@unit`).
```gherkin
  @normal @AIR-035 @AT-35 @OD-54
  Scenario Outline: A reinstall or a rebuilt package puts its shipped receiver.conf in place
    Given the installed receiver.conf was edited to hold "video.require_park=<edited>"
    When the owner <action>
    Then the stick evidence shows the shipped receiver.conf in place and a start record with the parking check <state>

    Examples:
      | edited | action                                                          | state |
      | false  | reinstalls the package                                          | on    |
      | true   | installs a package rebuilt to ship "video.require_park=false"   | off   |
```

## Requirement wording

1. **AIR-017(d).** "A successful status-request call returning no gear value" doesn't name the call. If it means BCM `getGearStatusInfo`, AT-24 already forbids calling it; if it means the getter, AIR-017(c) covers it. Name the call.
2. **AIR-023(b).** "Trim surrounding whitespace" doesn't say whether it applies to the line, the key, the value, or all three. Is `video.require_park = false` recognised? Owner question.
3. **AIR-023(b).** "Conflicting values" doesn't say whether one recognised and one unrecognised value conflict (`false ; maybe`), or whether identical duplicates are accepted.
4. **AIR-035(c) vs AT-35.** AIR-035(c) requires no additional failed-install behaviour, but AT-35 says a partial installation is not reported as a successful replacement. Which component's report is under test? The NEMESIS installer sits outside the receiver. Owner question.
5. **AIR-026.** Is the previous flag value always "unknown" across starts (no persistence), or taken from a surviving prior log? Does the startup blocked→permitted change count as a gate transition?
6. **AIR-019(b).** It says "older than 2 s MUST count as unavailable"; the scenarios say "stale". State that they're the same, or use "unavailable".
7. **AIR-039.** No rotation trigger. With an aggregate cap, two files and whole-file deletion, a newer file near 5 MiB can't stay under the cap without trimming. When does the current file rotate?
8. **AIR-019(d).** Do polling, timeouts and video blocking continue while the head unit sleeps, and what happens to a reply received during sleep? Owner question.

## Traceability

- `tools/trace_specs.py --check` passed.
- `docs/spec/TRACEABILITY.md` lists 131 scenarios in 12 feature files, 52 of them S2 (22 in `09`, 11 in `10`, 5 in `11`, 14 in `12`).
- Each of AIR-016 to AIR-027 and AIR-035 to AIR-040 maps to at least one `@offline` and one `@unit` scenario; each scenario has one slice, one tier and one kind.
- Tags asserted only in part: `09_parking_gate.feature:256` `@AT-24` (#20); `12_parking_unit.feature:13` `@AT-17` (#27) and `@AT-24` (#20); `12_parking_unit.feature:76` `@AT-35` (#28).

## Not verified

- No step definitions, Java gate or harness exist, so none were run.
- The tag-to-behaviour checks are against the AIR and AT rows; not every cited OD's full text was reread.
- No fixed AIR-025/026 log record format was found.
- No synthetic frame cadence is defined yet.
- Ordinary-app access to the getter remains unverified (`docs/evidence/GEAR_PROBE_FINDINGS.md`).
- No credentials, iPhone artifacts or OEM binaries were accessed.
