# S3 scenario coverage review (requirements revision 8)
> Reviewer: fresh-context Claude subagent standing in for `gpt-6-astra` under OD-78 (role file `tools/astra/roles/astra-scenario-reviewer.md`), read-only. **Substitute for Astra.**
> Prompt: `tools/astra/prompts/S3_REVIEW_SCENARIOS.md`. Date 2026-11-16.

External critique, not an owner decision.

## Verdict

**Blocked**, but the blockers are small and scoped. Four groups of rows must be fixed before step definitions are written: findings 1, 2, 3 and 4. All four can be fixed by wording (OD-86 fail-safe proposals below, owner to confirm as with OD-178). Everything else can be implemented with the clarifications below. `trace_specs.py --check` exits 0 on the current tree.

## Findings

| # | Severity | File:line | Finding | Fix |
|---|---|---|---|---|
| 1 | blocker | 13:95, 13:206, 13:213-234, 13:289, 13:441, 13:864 | **Unfixed tokens (point b).** The failure outline asserts "the null-manager reason", "the discovery-timeout reason" and similar prose. No requirement fixes the wording, so a step can't check it, and any wording passes. The port-busy, URL/HLS/DRM refusal and exception-class tokens are not fixed either. | Adopt the token wording in Requirement wording (b), then rewrite the `reason` column as the exact token, e.g. `reason=null-manager code=none`. |
| 2 | blocker | 13:579-597 (593-594); 14:462-463, 14:549, 14:558-563, 14:585; 15:112, 15:120 | **AT-71's byte-for-byte table has `<reason>` placeholders (point h).** 14 asserts `iphone disconnected` and `owner exit`, which no requirement or decision defines. That is invented policy, and a placeholder can't be checked byte for byte. | Add the closed vocabulary in Requirement wording (h) to AIR-071. Replace each `<reason>` with a concrete row per value. |
| 3 | blocker | 13:423-429 | **AIR-058 cadence contradicts 09_parking_gate.feature:101-106 and 140-158** ("A late reply is followed at once by the next call, which restarts the 200 ms cadence"). The call started at 400 ms returns at 750 ms. Under 09 the next calls are at 750 and 950. The scenario asserts 800 and 1000, which keeps a fixed grid. | Change line 427 to `Then VehicleInfoManager.getGearPStatus() was called only at 0, 200, 400, 750 and 950 ms`. If AIR-058 intends a fixed grid instead, the owner must rule, because 09 and 13 cannot both hold. |
| 4 | blocker | 14:253-264 | **AT-74 boundary (point f).** "the target folder already holds `<existing file>`" contradicts AIR-074(g), which refuses a run whose `<timestamp>/` folder already exists (14:281-289 covers that refusal). As written, no implementation can satisfy both scenarios. | Use the rewrite in Missing scenarios §9. Reword AT-74's `@boundary` clause (Requirement wording f). |
| 5 | major | 13:178-189 | AT-50 / AIR-050(b): the 5,000 ms boundary row is missing. Rows are at 4999, never and 5001, so a `<=` vs `<` mutation slips through. The 4999 row allows "4999 ms or later", so a mutation that delays the connect slips through too. | Add the row `\| at 5000 ms \| the log has "advertise failed" reason=discovery-timeout, and no dns_sd connect was made \|` (fail-safe under OD-86: exactly 5,000 ms is late; confirm against OD-164). Change the 4999 row to `a dns_sd connect attempt was made at 4999 ms and the log has "advertise ok"`. |
| 6 | minor | 13:200-207 | A socket missing for the whole window asserts only "no attempt later than 5000 ms". A mutation that stops at 4750 ms (20 attempts) slips through. 13:191-198 is correct. | Replace line 204 with `Then dns_sd connect attempts were made at 0, 250, 500, … 4750 and 5000 ms of the connect window, 21 attempts in all`. |
| 7 | major | 13:209-234 | AIR-050(d) also lists EPERM and "otherwise unreachable" as failures, but the outline has no row for either (point c). Line 215 ("stopped … if discoverServices had returned") and 13:770 (a quiet skip when discovery never started) already settle the "never started" half. | Add the two Examples rows in Missing scenarios §1, and list both cases in AT-50. |
| 8 | major | 13:796-836 | AIR-073(g) redaction (point d). There is no row for a bare header line (`CSeq: 3`) or for raw key bytes, so a redactor that filters only known key names passes. | Add the rule in Requirement wording (d) and the outline in Missing scenarios §6. |
| 9 | major | 13:485-500 | The AT-60 `@offline` clause "AIR-041's existing scenarios run against the Android adapter" has no scenario. Lines 485 and 496 are only static and lifecycle checks. | Add Missing scenarios §2. |
| 10 | major | 13:527-538 | The AT-62 `@offline` clause "AIR-039's rotation scenarios pass against the Android LogFiles" has only a `@failure` scenario. | Add Missing scenarios §2. |
| 11 | major | 13:614-618 | The AT-66 `@offline` clause "S2 RegulationLog scenarios pass against the Android adapter with a fake service" has only the static scan. | Add Missing scenarios §2. |
| 12 | major | 14 (`@host` exists only at 65-217 and 814) | There is no `@host` scenario for AT-55 (JNI lookup scan, unguarded-callback list, host callback table), AT-63 (APK has no `receiver.conf`; 14:327 checks the NEMESIS zip under AT-78) or AT-68 (APK permissions). | Add Missing scenarios §3, §4 and §5. |
| 13 | minor | 13:327-338 | The AT-55 callback Examples hard-code 7 callbacks: video_process, video_set_codec_and_size, video_pause, video_resume, audio_process, conn_feedback and video_reset. AT-55 says "each listed callback", meaning the `@host` list. A callback added upstream and left out of the Examples slips through. | Add the step `And the Examples' callbacks equal the list produced by the @host unguarded-callback check` (Missing scenarios §3 produces the list). |
| 14 | minor | 14:379-389 | os/models 256-byte limit. There is no 255-byte accept row. A 256-byte value of junk is also refused for not being exact, so dropping the length check slips through. | Add Missing scenarios §8. Assert the length-finding text, and state in AIR-078 that every failed rule is reported (Requirement wording, AIR-078). |
| 15 | minor | 14:345-356 | 64/65 and 256/257 are correct, but there is no quoted 64-character `short-desc` row ("surrounding quotes not counted"). Counting the quotes slips through. | Add the row `\| short-desc \| "<64 chars>" (66 with quotes) \| accepted \|`. |
| 16 | minor | 14:391-406 | The install-order check observes "creates", but the fakes list (14:45-47) has no `mkdir`. | Add `mkdir` to the fakes at 14:45-47. |
| 17 | minor | 14:33-39, 14:748-791 | The evaluator's "at `<t>` s in the logger's prefix" depends on a timestamp format no requirement fixes (point g). 14:765 needs millisecond precision (599.999). | Requirement wording (g). |
| 18 | minor | 14:479-495 | The summary doesn't say where `Checklist:` sits among S1's fields, or whether offline summaries carry it (point i). | Requirement wording (i). Add `Then the line after "Firewall:" is "Checklist: …" and it is the last line` to both the offline and unit summary scenarios. |
| 19 | minor | 14:543-563 | NO-GO outline cells contain `\n`. pytest-bdd passes it through literally. | State in the step docstring that `\n` in a cell is unescaped to LF, or split the record into separate rows. |
| 20 | minor | 14:700-711, 14:741-746 | The fixture records `advertise failed` (bare) and `port busy tcp 35000` must take the fixed token form once finding 1 is ruled. | Update them to `advertise failed reason=… code=…` and `port busy tcp 35000`. The second one already matches. |

**Arithmetic checked and correct:**
- **Letterbox:**
  - 1080x1920 gives 405x720 at x 757 or 758.
  - 1920x1080 gives 1280x720 at x 320.
  - 1920x720 gives x 0.
- **13 timings:**
  - The 1260 ms clear at 13:410 and 13:612.
  - 650 ms blocked at 13:431-444.
  - The 4,750 ms socket case: 20 attempts.
  - AT-69 rows at 13:686-692: 0/4999/5000, 10/5009/5010 and 1000/5999/6000.
  - Held to 3000, then 7999/8000, at 13:524-525.
  - withheld 3 at 13:810.
- **14 length limits:** 64/65 and 256/257.
- **600 s deadline, 14:748-791:**
  - A frame at 120 s with the end at 700 s gives endpoint 600 and reason timeout.
  - 599.999 is GO; 600.000 is a timeout.
  - A second start at 300 s with a frame at 610 s still times out against the first start.

## Missing scenarios

Slice and tier tags follow each file's Feature-line convention. Scenarios below carry the per-scenario tier, kind, AIR, AT and OD tags.

**§1 — 13, append to the Examples at 13:220-234** (column order fault | reason | withdrawn):
```gherkin
      | /dev/socket/mdnsd refuses with EPERM for the whole connect window           | reason=EPERM code=none                    | no registration was made                                   |
      | every dns_sd connect throws ETIMEDOUT for the whole connect window          | reason=unreachable code=none              | no registration was made                                   |
```

**§2 — 13, AT-60 / AT-62 / AT-66 `@offline @normal`:**
```gherkin
  @offline @normal @AIR-060 @AIR-041 @AT-60
  Scenario: AIR-041's reverse scenarios pass against the Android REVERSE_ON adapter
    Given the AIR-041 scenarios are bound to the Android REVERSE_ON receiver adapter driven by a fake broadcast source
    When those scenarios run
    Then every AIR-041 scenario passes against that adapter

  @offline @normal @AIR-062 @AIR-039 @AT-62
  Scenario: AIR-039's rotation scenarios pass against the Android LogFiles
    Given the AIR-039 rotation scenarios are bound to the Android LogFiles adapter over a fake file system rooted at /data/nemesis/logs/airplay-receiver/
    When those scenarios run
    Then every AIR-039 rotation scenario passes against that adapter
    And the fake file system recorded no write outside /data/nemesis/logs/airplay-receiver/

  @offline @normal @AIR-066 @AT-66
  Scenario: S2's RegulationLog scenarios pass against the Android adapter with a fake regulation service
    Given the S2 RegulationLog scenarios are bound to the Android regulation adapter backed by a fake com.hkmc.regulation service
    When those scenarios run
    Then every S2 RegulationLog scenario passes against that adapter
    And the fake service recorded no setter call
```

**§3 — 14, AT-55 `@host @normal`:**
```gherkin
  @host @normal @AIR-055 @AT-55
  Scenario: The JNI sources look up no Android framework, vehicle, audio or display class
    When the static check scans the receiver's JNI sources for FindClass, GetMethodID and GetStaticMethodID arguments
    Then no argument names a class under android., com.hkmc., com.mobis., or any audio or display class

  @host @normal @AIR-055 @AT-55
  Scenario: Every callback the pinned UxPlay source calls without a NULL check is listed and set
    Given the pinned, patched UxPlay source
    When the static check lists every raop_callbacks_t member the source calls without a preceding NULL check
    Then the list is written to the build log as "unguarded callbacks: <comma list>"
    And the host callback table sets every member on that list to a non-NULL function
```

**§4 — 14, AT-63 `@host @normal`:**
```gherkin
  @host @normal @AIR-063 @AT-63
  Scenario: The built APK carries no receiver.conf
    When the build check lists the built APK's entries
    Then no entry is named receiver.conf, in any folder
```

**§5 — 14, AT-68 `@host @normal`:**
```gherkin
  @host @normal @AIR-068 @AT-68
  Scenario: The built APK asks for INTERNET and nothing else
    When the build check runs "aapt dump permissions" on the built APK
    Then the uses-permission lines are exactly "android.permission.INTERNET"
```

**§6 — 13, AIR-073(g):**
```gherkin
  @offline @boundary @AIR-073 @AT-73
  Scenario Outline: Header lines and raw key material never reach the log
    Given the library double emits the log line <line>
    When the receiver runs for 1000 ms
    Then the log has no record containing <line>
    And the withheld count for the session rises by 1

    Examples:
      | line                                                                 |
      | "CSeq: 3"                                                            |
      | "X-Apple-ProtocolVersion: 1"                                         |
      | a line holding the byte 0x01                                         |
      | a line holding a 32-character run of hex digits                      |
      | a line holding a 44-character run of base64 characters               |

  @offline @boundary @AIR-073 @AT-73
  Scenario: A 31-character hex run and a 43-character base64 run are kept
    Given the library double emits a line holding a 31-character hex run and, separately, one holding a 43-character base64 run
    When the receiver runs for 1000 ms
    Then the log has both lines unchanged
```

**§7 — 13:178-189, Examples (replaces the table):**
```gherkin
      | when        | outcome                                                                                 |
      | at 4999 ms  | a dns_sd connect attempt was made at 4999 ms and the log has "advertise ok"             |
      | at 5000 ms  | the log has "advertise failed reason=discovery-timeout code=none", and no dns_sd connect was made |
      | at 5001 ms  | the log has "advertise failed reason=discovery-timeout code=none", and no dns_sd connect was made |
      | never       | the log has "advertise failed reason=discovery-timeout code=none", and no dns_sd connect was made |
```

**§8 — 14, os/models:**
```gherkin
  @host @boundary @AIR-078 @AT-78
  Scenario Outline: os and models are limited to 255 bytes
    Given the NEMESIS.data <key> is <length> bytes of valid, exact entries
    When the package check runs
    Then <result>

    Examples:
      | key    | length | result                                                    |
      | os     | 255    | it reports no finding for os                              |
      | os     | 256    | it reports "os: longer than 255 bytes"                    |
      | models | 255    | it reports no finding for models                          |
      | models | 256    | it reports "models: longer than 255 bytes"                |
```

**§9 — 14:253-264, AT-74 boundary (replaces the Given):**
```gherkin
  @pc @boundary @AIR-074 @AT-74 @OD-184
  Scenario Outline: A file that appears in the run's folder during the run is kept, and the copy goes on
    Given the run's <timestamp>/ folder does not exist before the run
    And the fake "cp" creates <existing file> in the run's folder before it copies the first log
    When the collect script runs
    Then <existing file> is unchanged
    And every other log was copied
    And the script exits 0
```
(Keep the existing Examples column for `<existing file>`.)

## Requirement wording

- **(a) AIR-069(f), start-check order.** AT-69's wording ("`hotspot address missing` followed by `identity key unavailable`") already settles the order. Make it explicit: "The hotspot-address check runs first and the identity-key check second. Both always run, and each failure is logged in that order before the start-failure notice."
- **(b) Tokens (OD-86 fail-safe proposal; owner to confirm, as with OD-178):**
  - **AIR-050 advertise failures.** Log `advertise failed reason=<token> code=<n|none>`. `<token>` is one of null-manager, security-exception, discovery-failed, discovery-timeout, ENOENT, EACCES, EPERM, ECONNREFUSED, unreachable, txt-build, register, name-conflict. Any other connect error is logged as `unreachable`.
  - **Port busy.** `port busy <tcp|udp> <port>`, matching 13:95.
  - **URL/HLS/DRM refusal.** `refused url`, `refused hls`, `refused drm`.
  - **Withheld count.** Already `withheld=<n>` under OD-178/AIR-071. Settled.
  - **Exception classes.** A getter timeout logs `java.util.concurrent.TimeoutException`. A malformed `/proc/net/tcp` logs `java.lang.NumberFormatException`. Any other exception logs its class's fully qualified name.
- **(c) AT-50.** "…including EPERM and any other connect error (`unreachable`). Discovery is stopped only if discoverServices had returned." Line 215 and 13:770 already settle the never-started half.
- **(d) AIR-073(g).** "A record is withheld if it matches `^[A-Za-z][A-Za-z0-9-]*: ` (a protocol header line), contains a byte outside 0x20-0x7E, or contains a run of 32 or more hex digits or 44 or more base64 characters. Each withheld record counts toward `withheld=<n>`."
- **(e) AIR-056.** "The decoder is created when a session's H.264 codec configuration arrives (video_set_codec_and_size), and not before."
- **(f) AT-74 `@boundary`.** "A file that appears in the run's folder during the run is kept and the copy goes on." OD-184 / AIR-074(g) settle precedence: a folder that exists before the run is refused, and (d) keep-existing applies only to files that appear during the run.
- **(g) Timestamps (OD-86 fail-safe).**
  - "Every receiver log record starts with the uptime in milliseconds as decimal digits followed by a space."
  - "The evaluator treats a record with no parseable prefix as at or after the deadline, so it never counts toward GO."
  - "UxPlay's own lines belong to the most recent session opened before them, or to no session if none is open."
- **(h) AIR-071 vocabulary.**
  - Session end reasons: `iphone disconnected`, `hevc refused`, `decoder unavailable`, `decoder error`, `stop`.
  - App stop reasons: `owner exit`, `destroy`.
  - "Each is a fixed lowercase phrase and never contains `withheld=`."
- **(i) Summary.**
  - "`Checklist:` is the last line, after S1's fields in order: `# AirPlay receiver …`, Tier:, Verdict:, Sessions:, Device model:, iOS:, UxPlay build commit:, Head unit:, Firewall:."
  - "Offline summaries carry it too, with the value `not run (offline)`."
  - "There are no blank lines between fields."
- **AIR-078.** "Every failed rule is reported, one line each, not just the first."
- **(j) OD-179.** Add an editorial note: "The shipped template mentioned here is superseded by OD-181."

## Traceability

- New `@host` tags for AT-55 (2 scenarios), AT-63 (1) and AT-68 (1) add host coverage those rows lacked.
- New `@offline @normal` tags for AT-60, AT-62 and AT-66 (1 each). AIR-041 and AIR-039 gain S3 references.
- AT-50 gains 3 Examples rows, AT-73 gains 1 outline (5 rows) and 1 scenario, and AT-78 gains 1 outline (4 rows).
- Regenerate `docs/spec/TRACEABILITY.md` and rerun `.venv/Scripts/python tools/trace_specs.py --check` after the edits.
- §2's meta-scenarios need Cucumber-JVM glue that reruns the AIR-041, AIR-039 and S2 features against the Android adapters. If `specs/java-features.txt` routes by file, those features need a second run profile, not duplicated text.

## Not verified

- The exact content of AIR-041, AIR-039 and S2's RegulationLog scenarios. §2 assumes they can be rebound to an adapter.
- 09_parking_gate.feature's cadence beyond lines 101-106 and 140-158, and whether AIR-058 intends a fixed grid.
- The OD-164 text on whether exactly 5,000 ms is inside the window.
- The OD-179, OD-181 and OD-184 texts beyond the lines I read.
- The S1 summary field order, checked against S1 scenarios but not against docs/evidence files.
- No S3 code or step definitions exist, so this review makes no claim about current behaviour.

## Applied (2026-11-16)

Applied against requirements revision 9, OD-185 and OD-186. Line numbers are the scenario lines
after the change (13 = `13_receiver_runtime.feature`, 14 = `14_receiver_tools.feature`,
15 = `15_receiver_unit.feature`).

### Findings

- **1** Applied. 13:94 asserts `port busy <proto> <port>`, with tcp/udp rows. 13:204 asserts `advertise failed reason=ENOENT code=none`. 13:213 has full `reason=<token> code=<n|none>` cells. 13:290 asserts `refused url|hls|drm`. 13:439 asserts `java.util.concurrent.TimeoutException`. 13:932 asserts `java.lang.NumberFormatException`. All carry @OD-185.
- **2** Applied. 13:599 checks the marker texts. The 13:620 session-end outline and the 13:635 stop outline each carry @OD-185 and @OD-186. On the evaluator side: 14:501 (complete log) and a new outline at 14:522 covering the five end reasons and two stop reasons. On the unit side: 15:110 (exact `parkcast stop reason=owner exit|destroy`) and 15:117 (one of five end reasons, one stop marker, uptime prefix).
- **3** Applied with the caller's correction. 13:431 uses OD-63's rule (`09_parking_gate.feature:101-106`): the next call starts at once after a late reply. The cadence is 0/200/400/750/950 ms, tagged @OD-63, not the review's fixed grid.
- **4** Applied as section 9 below (14:284).
- **5** Applied as section 7 below (13:181).
- **6** Applied. 13:204 now asserts 21 attempts, the last at 5,000 ms, then the failure record.
- **7** Applied as section 1 below (EPERM and unreachable rows in 13:213).
- **8** Applied as section 6 below (13:893, 13:909).
- **9-11** Applied as section 2 below (13:510, 13:554, 13:668).
- **12** Applied as sections 3-5 below (14:229, 14:235, 14:242, 14:248).
- **13** Applied. The 13:333 Examples now have a step saying they equal the list produced by the @host unguarded-callback check (14:235).
- **14** Applied with a change. See section 8 below.
- **15** Applied as a separate scenario at 14:392 (a quoted 64-character short-desc is accepted), not as a table row. A row read badly in the outline's steps. It has no @OD tag, because quote handling is not an OD-185 ruling.
- **16** Applied. The fakes list at 14:51 includes `mkdir`.
- **17** Applied. The 14:43 header uses the uptime-ms prefix, and an unparseable record counts as at or after the deadline. New scenarios: 14:921 (no parseable prefix never counts toward GO), 14:935 (unparseable first start marker gives `evidence error`, @OD-186) and 14:942 (UxPlay lines belong to the latest open session).
- **18** Applied with the caller's correction. In 14:547 the unit summary puts `Checklist:` last, after `Firewall:`, with no blank lines. 14:964 is an offline summary (Tier offline) and carries `Checklist: not run (offline)`. Fixtures that emulate unit runs keep the six-key line.
- **19** Applied. The NO-GO outline is split into five scenarios (14:613, 14:629, 14:646, 14:662, 14:676), so no cell contains `\n`.
- **20** Applied. 14:828 rows use `port busy tcp 35000` and `advertise failed reason=discovery-timeout code=none`, and 14:868 uses `port busy tcp 35000`.

### Missing scenarios

- **§1** Applied. The EPERM, ECONNREFUSED and ETIMEDOUT→`unreachable` rows are in the 13:213 Examples.
- **§2** Applied, rephrased so it can be implemented. The scenarios at 13:510 (AT-60), 13:554 (AT-62) and 13:668 (AT-66) each rerun the S2 scenarios selected by a tag expression against the Android adapter: `@S2 and @AIR-041 and @offline`, `@S2 and @AIR-039 and @offline` and `@S2 and @AIR-038 and @offline`. S2's text is not duplicated. AT-66 takes @AIR-038 as S2's RegulationLog requirement. The Cucumber glue needs a second run profile that selects S2 features with the Android adapter bound (see Traceability).
- **§3** Applied at 14:229 and 14:235 (@host AT-55). The callback list is written after "unguarded callbacks: " rather than as `<comma list>`, which pytest-bdd would read as a placeholder.
- **§4** Applied at 14:242 (@host AT-63). It adds a Given for the clean gradlew-built APK.
- **§5** Applied at 14:248 (@host AT-68), with the same Given.
- **§6** Applied at 13:893 (header lines and raw key material outline) and 13:909 (31-character hex and 43-character base64 runs are kept).
- **§7** Applied at 13:181. The rows are 4999, 5000 (late), 5001 and never.
- **§8** Applied with a change, at 14:419 and 14:432. The tier stays @offline, not @host, because it uses synthetic package fixtures. AIR-078(c) refuses any os or models value that is not exact, so a 255-byte value of junk is still refused. The 255-byte rows therefore assert only that there is no "longer than" line, and the 256-byte rows assert "longer than 255 bytes". 14:432 checks that every failed rule is reported.
- **§9** Applied at 14:284, tagged @offline, not @pc, per the caller's correction. The Given says the run's timestamped folder does not exist before the run, so there is no unbound `<timestamp>` placeholder. The fake cp creates a file during the run, and the steps check four things: its hash is unchanged, the script reports leaving it, every log was copied, and the script exits 0.

### Requirement wording and traceability

- (a)-(i) and the AIR-078 every-failed-rule wording are in REQUIREMENTS revision 9 under OD-185 and OD-186. No feature edit was needed beyond the scenarios above.
- (j) is a DECISIONS editorial note, not a feature change.
- The tags @OD-185 and @OD-186 were added where those rulings are asserted.
- In 14, `<reason>` appears only in the header's description, which is not a byte-exact assertion. The vocabulary is listed there.
- The second Cucumber run profile for section 2 is noted, not built, because no step definitions exist yet.
- `tools/trace_specs.py --check` is clean, and `docs/spec/TRACEABILITY.md` is regenerated.

### Not changed

- The AT-69 table in 13 was already misaligned before this change and is left as it was.
