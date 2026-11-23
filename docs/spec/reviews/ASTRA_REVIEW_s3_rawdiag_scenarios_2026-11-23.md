# Slice S3 revision 10: coverage review of the diagnostic raw log scenarios

> Reviewer: `gpt-6-astra` via Codex CLI (`codex exec` read-only sandbox, role prompt `tools/astra/roles/astra-scenario-reviewer.md` as developer instructions), reasoning effort medium.
> Staged: docs, specs, tests, tools, package, AGENTS.md + evidence: ring.c, host.c, parkcast.h, raop_handlers.h, utils.c.
> Session `01a20a51-77ef-7211-8892-b0674563db8b`, 1 turn(s), 268 s; tokens in 688073 (cached 623232), out 7560 (reasoning 457).
> Prompt: `tools/astra/prompts/S3_REVIEW_RAWDIAG.md`. Date 2026-11-23.

**External critique, not an owner decision.**

---

## Verdict

**Blocked. Do not write step definitions yet.**

The numeric thresholds are correct. The blockers are the codec-configuration counter setup, missing snapshot triggers, incomplete acceptance coverage, ambiguous size/redaction rules, and assertions whose proposed oracles cannot yet establish the claimed behavior.

This is external critique, not an owner decision. No files were changed.

## Findings

References beginning `F:` mean `specs/features/16_receiver_diagnostics.feature`.

| # | Severity | File:line | Finding | Concrete fix |
|---|---|---|---|---|
| 1 | blocker | F:156–160 | Filling the **64 general slots** does not refuse a codec configuration. Configurations can use all **72 slots**: `evidence/ring.c:17–19,115–126`. Thus the stated setup does not justify `csd_sent=1`. | Test acceptance with general slots full separately. For refused-configuration accounting, explicitly inject a full 72-slot ring as a fault condition; identify the resulting control drop as an OD-190 breach, not normal behavior. |
| 2 | blocker | F:160,168,192 | “Last/next counter line” has no guaranteed emission trigger after the measured actions. The session-end snapshot at line 166 precedes the third AU. A correct implementation may therefore still show `au_in=2`. | Specify clock times and trigger a snapshot after the final measured action. Model an **app restart**, not merely closing/reopening a file, when checking resets. |
| 3 | blocker | F:153–170; `REQUIREMENTS.md:190` | AT-86’s “counts match the calls made” is only partly covered. No exact assertions cover generation, `au_ring_drops`, `control_drops`, successful `raw_written`, or cumulative behavior of all counters. `log_ring_drops` is checked at the ring, not in a snapshot. | Add independent, literal expected counter tuples, including zero initialization, accepted/refused configurations, rejected AUs, log drops, oversized records, session carryover and app reset. |
| 4 | blocker | F:128–137; `REQUIREMENTS.md:159,177` | AT-55 requires the **same callback record in both paths**; this scenario only inspects the ring. AT-73 requires **every fixture above** in its AT row, but the scenario supplies only four. A raw writer that silently omits these records, or modifies another forbidden fixture, passes. | Assert raw output and ring output together; expand the table to the complete AT-73 fixture set, using synthetic values. |
| 5 | blocker | F:120–125; `REQUIREMENTS.md:95` | Full-ring survival proves independence from successful enqueueing, but not **callback-thread execution before the ring**. A writer invoked after `ring_push` would pass. KEYCHECK ordering, independence from a failing filter, and not reading the key file are also unasserted. | Add an external call trace recording thread identity and ordering, including redirect, raw open, `raop_init`, callback entry, raw write, ring submission and return. Record raw-module key-file read attempts separately from legitimate identity initialization. |
| 6 | blocker | F:78–82; `REQUIREMENTS.md:95` | The split-dump fixture does not reproduce UxPlay’s delimiter sequence. UxPlay emits a space after every byte, then an additional LF at wrapping and at the end: `evidence/utils.c:203–215`. AIR-085(c) does not clearly define whitespace spans or which trailing separators survive replacement. | Define maximal dump matching and separator preservation. Add actual `space + LF` fixtures and assert exact output, rather than “nothing … but escaped line breaks.” |
| 7 | blocker | `REQUIREMENTS.md:95`; F:63–101 | The pattern list does **not establish that all key material in plist XML is redacted**. A 16-byte value is 24 padded base64 characters, below 44. The staged XML helper preserves single-line `<data>` contents: `evidence/utils.c:317–327`. | Resolve the treatment of short and wrapped sensitive plist values, then add fixtures. Preserve harmless headers/plist structure as OD-200 requires. Do not silently lower the global threshold: that would contradict the unchanged 43-character boundary. |
| 8 | blocker | F:187–192; `REQUIREMENTS.md:97` | The requirement says “record … after escaping”; the scenario measures **escaped text**. Those differ by the uptime, level, spaces and LF. AIR-087 also does not explain how ordinary counter lines respect the 512-byte reserve. | Define the 65,536-byte measurement explicitly and define reserve handling for every line type. Add a counter-triggered cap crossing and post-full session-end/stop checks. |
| 9 | major | F:43–45; `REQUIREMENTS.md:94,188` | The keep-check truth table covers all combinations, but not all applicable reasons. Both markers present checks only the version reason; library-only checks no disagreement reason. An implementation that never reports that disagreement passes. | Use separate expected version/library/disagreement indicators for all four combinations. Invoke the specified CLI, not an unspecified helper. |
| 10 | major | F:146,215,263–264 | These assert more than the text settles: a periodic counter **before** its triggering record; universal “without blocking”; and a particular symlink diagnostic plus nonzero exit. AIR-086 specifies timing, not ordering within that event. AIR-088 forbids ring-lock/Java coupling, not all synchronous filesystem latency. AIR-089 forbids following a symlink but does not explicitly prescribe its diagnostic/exit policy. | Remove the extra ordering requirement. Define “without blocking” as the required independence from Java/ring locking, with a watchdog only as a test safeguard. Either remove the symlink diagnostic/exit assertions or obtain explicit requirement wording. |
| 11 | blocker | F:222–240,267–279; `REQUIREMENTS.md:193` | AT-89’s “one changing command” and “nothing read” are not asserted. Hash/mode equality permits chmod-and-restore, chown, or unrelated unit changes. No-copy does not mean no-read. The key test permits copying another app-data file. | Assert an operation journal: source reads, copies, mutations and commands. Include another app-data file and descriptor targets. Define “nothing read” to exclude necessary build/boot guard queries and the tool’s own inputs. |
| 12 | blocker | F:228; `tests/steps/receiver_toolbox.py:231–235` | A final filesystem scan cannot prove that unfiltered logcat **never existed**. Existing helpers enumerate surviving files only. The current diagnostic writes `logcat-all.tmp`, filters it, then deletes it: `tools/unit-probes/parkcast-diag/parkcast_diag.sh:87,117–118`. This exact wrong implementation can defeat a final-only oracle. | Observe write history, including shell redirections and `logcat -f`, outside the script. Validate the observer against a create/filter/delete mutant. Command logging alone is insufficient for shell redirections. |
| 13 | major | F:284–294; `REQUIREMENTS.md:166,189` | AT-85’s unit check covers only three redaction kinds, omitting PIN and identity path/name. AT-62 requires absence in the **actual normal-log directory**; checking a copier’s selected output cannot prove that absence. | Extend owner-local inspection to every redacted kind and capture a read-only directory listing for AT-62. Never commit the sensitive inputs or inspection output. |
| 14 | major | F:197–208,250–264 | Failures are concentrated at the first raw write and raw-file copy. Missing boundaries include a later short write, recovery on a new app start, failed copies and symlinks for either receiver log, timestamp collisions, interrupted collection and missing required tools. | Add the scenarios below. For interruption/collision/tool-failure outcomes not yet specified, record the gap for an owner decision instead of inventing exit or cleanup behavior. |

### Arithmetic and counts

- **Correct:** the hex Examples contain **32 and 31** characters; base64 Examples contain **44 and 43**.
- **Correct:** spaced-byte Examples contain **8 and 7** bytes. The surrounding `"before <material> after"` supplies the last byte’s required trailing space.
- **Correct:** `16,777,216 − 512 = 16,776,704`.
- **Correct numbers, ambiguous measurement:** `65,536` accepted versus `65,537` dropped.
- **Correct timing:** opening at 1000 and receiving records at 1400, 1800, 2200 and 2600 produces the first periodic snapshot at **2200**. Add exact 999/1000-ms and event-only boundaries.
- **Correct intended AU arithmetic:** `2 + 1 = 3`; the existing scenario does not guarantee a snapshot containing that result.
- **Unsupported configuration count:** general-slot saturation alone does not produce the refusal needed for `csd_sent=1`.

### What the staged UxPlay sources establish

The spaced-byte rule can cover these dumps if implemented over the actual delimiters:

| Material | Source |
|---|---|
| `client_epk`, `client_authtag` | `evidence/raop_handlers.h:404–408` |
| `aesiv`, encrypted `ekey` | `evidence/raop_handlers.h:813–825` |
| Decrypted 16-byte `aeskey` | `evidence/raop_handlers.h:846–847` |
| 32-byte shared `ecdh_secret` | `evidence/raop_handlers.h:880–881` |
| Hashed 16-byte `aeskey` | `evidence/raop_handlers.h:892–893` |
| Quoted PIN | `evidence/raop_handlers.h:273,700` |

The actual 16-byte dump rows each exceed the eight-byte threshold. Merely mishandling the wrap delimiter need not expose those rows, but it can violate whole-run replacement and leak shorter fragments in other layouts.

**I cannot identify a confirmed private-key leak at an XML logging call from this stage.** The XML logging caller is not supplied. What is demonstrable is that `utils.c:323–327` retains single-line data, and AIR-085(c) leaves a 24-character base64 value unchanged. That includes the XML representation of a 16-byte IV or authentication tag; it would also include a 16-byte key if logged in that representation. These distinctions should not be collapsed into a claim that the supplied source proves a particular private key leaks.

### Oracle feasibility

Host gcc can support independent observations, provided the harness observes external boundaries rather than asking production code whether it behaved correctly:

- **Callback ordering:** wrapper/link instrumentation records actual write and ring-entry calls, thread IDs and callback return.
- **Mode:** a fake file layer verifies the requested `0600` mode and truncation flags. It does **not** establish Android permissions through Windows filesystem metadata.
- **Short write:** inject a return value smaller than the requested count, preserve that prefix, and record subsequent operations.
- **No Java/ring coupling:** suspend Java delivery; instrument lock ownership during the write; use a watchdog to detect hangs. This does not prove zero filesystem latency.
- **Never-written logcat:** requires write-history observation covering shell redirections. Final scans and ordinary fake-command logs alone do not suffice.

## Missing scenarios

These additions supplement the direct corrections above. Steps describe independent observations; they do not require a production “test passed” API.

```gherkin
@S3 @host @normal @AIR-055 @AIR-072 @AIR-085 @AT-55 @AT-72 @AT-85
Scenario: The raw record is written inside the callback before ring submission
  Given raw diagnostics are on and Java delivery is suspended
  And external instrumentation records callback, file and ring calls with thread identities
  When UxPlay's log callback receives level 7 and text "CSeq: 3"
  Then the callback thread writes the raw record before entering ring submission
  And the write completes before the callback returns
  And the raw record preserves "CSeq: 3"
  And the ring receives exactly one unchanged level 7 log event with text "CSeq: 3"
  And no raw write holds the ring lock or calls Java

@S3 @host @normal @AIR-085 @AT-85
Scenario: Raw initialization follows redirect and precedes library initialization
  Given raw diagnostics are on
  And external instrumentation records KEYCHECK operations
  When the native KEYCHECK stage runs successfully
  Then raw-file open occurs after successful stdio redirection and before raop_init
  And the raw-file open requests mode 0600 and truncation
  And the raw-file module performs no read of the identity key file

@S3 @host @boundary @AIR-086 @AT-86
Scenario: A non-record event triggers the exact one-second boundary
  Given raw diagnostics opened at uptime 1000 ms
  When native events occur at 1999 ms and 2000 ms without log records
  Then no counter line is written at 1999 ms
  And exactly one counter line is written at 2000 ms

@S3 @host @normal @AIR-086 @AT-86
Scenario: A codec configuration uses a reserved slot
  Given raw diagnostics opened with zero counters
  And the ring's 64 general slots are occupied without invoking measured callbacks
  And all 8 reserved slots are available
  When the native path submits one codec configuration
  And a session-end snapshot is triggered
  Then the ring contains that configuration
  And the snapshot has "csd_sent=1" and "control_drops=0"

@S3 @host @failure @AIR-086 @AT-86
Scenario: A refused configuration does not count as sent
  Given a fault-injection fixture has occupied all 72 ring slots
  And csd_sent and control_drops are zero
  When the native path submits one codec configuration
  And a session-end snapshot is triggered without submitting another ring event
  Then the snapshot has "csd_sent=0" and "control_drops=1"
  And the harness reports the control drop as an OD-190 breach

@S3 @host @boundary @AIR-086 @AIR-087 @AT-86 @AT-87
Scenario: Raw counts count records rather than counter lines
  Given raw diagnostics opened at uptime 1000 ms with zero counters
  When two accepted raw records arrive at 1100 ms and 1200 ms
  And one oversized raw record arrives at 1300 ms
  And session end triggers a snapshot at 1400 ms
  Then that snapshot has "raw_written=2" and "raw_dropped=1"
  When another session ends without any intervening record
  Then its snapshot still has "raw_written=2" and "raw_dropped=1"
  When a new app start opens raw diagnostics
  Then every count in the opening snapshot is zero

@S3 @host @boundary @AIR-087 @AT-87
Scenario: A full file remains unchanged through later lifecycle events
  Given the raw file has emitted its one "raw log full" line
  And its bytes have been saved by the harness
  When another record arrives
  And a periodic counter event occurs
  And the session ends
  And stop's last phase runs
  Then the raw file exactly matches the saved bytes

@S3 @host @failure @AIR-088 @AT-88
Scenario Outline: A later I/O failure disables writes only until a new app start
  Given raw diagnostics opened successfully and one record was written
  When the fake file layer injects <failure> on the next record
  And more records, session end and stop occur
  Then no later raw write or retry-open occurs in that app start
  And no alternative destination is opened
  When a new app start opens raw diagnostics with successful I/O
  Then a new record is written successfully

  Examples:
    | failure       |
    | a failed write |
    | a short write  |

@S3 @offline @failure @AIR-089 @AT-89
Scenario Outline: Failure of any copy does not suppress the other copies
  Given all three diagnostic source files exist
  And the fake copy operation fails for "<source>"
  When parkcast-diag runs
  Then its log reports the failed copy of "<source>"
  And both other copies are attempted
  And the script exits non-zero

  Examples:
    | source         |
    | uxplay-raw.log  |
    | receiver.log   |
    | receiver.log.1 |

@S3 @offline @failure @AIR-089 @AT-89
Scenario Outline: No diagnostic source symlink is followed
  Given "<source>" is a symlink to a synthetic sentinel file
  When parkcast-diag runs
  Then the operation journal records no read through that symlink
  And no stick output contains the sentinel bytes

  Examples:
    | source         |
    | uxplay-raw.log  |
    | receiver.log   |
    | receiver.log.1 |

@S3 @offline @normal @AIR-083 @AIR-089 @AT-83 @AT-89
Scenario: Only the named raw file is collected from app data
  Given app data contains the raw file, a synthetic key file and another synthetic file
  And descriptor fixtures target each of those files
  When parkcast-diag runs
  Then the copy journal names only uxplay-raw.log as an app-data source
  And descriptor output includes no app-data target except uxplay-raw.log
  And output contains neither the synthetic key path nor its name

@S3 @offline @normal @AIR-089 @AT-89
Scenario Outline: Auto-start is the only permitted unit mutation
  Given ParkCast is <state>
  And all unit commands and source operations are journalled
  When parkcast-diag runs
  Then the unit mutation journal contains <mutations>
  And no source file is changed, deleted, chmodded or chowned

  Examples:
    | state       | mutations                                                        |
    | running     | no mutation                                                      |
    | not running | only one am start -n parkcast.receiver/.ReceiverActivity operation |

@S3 @offline @failure @AIR-089 @AT-89
Scenario: A rejected build prevents diagnostic source reads
  Given the build guard reports an unsupported build
  And diagnostic source reads are journalled
  When parkcast-diag runs
  Then no diagnostic source is read
  And no file is copied
  And no am command runs
  And the script exits non-zero
```

Also revise existing scenarios rather than duplicating them:

- **AT-84:** name `./gradlew :receiver:app:help` and `tools/s3.py keep-check <apk>` explicitly; assert every applicable keep-check reason.
- **AT-73:** expand the unchanged-ring table to every required fixture.
- **AT-85:** add absent-file/off-build coverage, actual wrapped dumps, all unit redaction kinds, and exact replacement bytes.
- **AT-86:** add exact AU/ring-drop/generation tuples and explicit snapshot triggers.
- **AT-62:** inspect the actual normal-log directory.
- **AT-89:** strengthen transient-logcat observation and verify removal of the tool’s own root `NEMESIS.data` at startup.

Interrupted collection, missing tools, timestamp collisions, and short sensitive XML values need settled outcomes before definitive Gherkin can be supplied without inventing requirements.

## Requirement wording

The following are proposed replacements, not owner decisions.

**AIR-084(d), diagnostic portion:**

> The keep check MUST report every applicable condition: a version name ending in `-rawdiag`, a native library containing `parkcast raw diagnostics on`, and disagreement between those two indicators.

Expected indicators:

| Version marker | Library marker | Reasons |
|---|---|---|
| absent | absent | pass |
| present | present | version; library |
| present | absent | version; disagreement |
| absent | present | library; disagreement |

**AIR-085(c), spaced-byte item:**

> A maximal sequence of at least eight two-digit hex bytes separated by one or more spaces, CRs or LFs, with the last byte followed by one of those delimiters. Replace the bytes and their internal separators with `[redacted]`; preserve the delimiter sequence following the last byte.

This resolves the fixture’s preservation expectation and UxPlay’s `space + LF` wrapping.

**AIR-087(b), if the intended limit is escaped text as the scenario states:**

> A record whose redacted, escaped text exceeds 65,536 bytes, excluding its uptime/level prefix and terminating LF, MUST be dropped whole and counted in `raw_dropped`. Text of exactly 65,536 bytes MUST be accepted subject to the file cap.

**AIR-087(a), reserve clarification:**

> The 16,777,216-byte limit includes every byte of framing, records and counter lines. Before any non-final line would take the file past 16,776,704 bytes, native code MUST instead write the single final line in the reserved space. After that line, no record or counter line, including session-end and stop lines, may be written until a new app start.

**AT-88, replace “returns without blocking”:**

> Each callback returns without waiting for Java or delivery progress. External instrumentation confirms that raw I/O does not hold the ring lock. The harness uses a watchdog to detect hangs; this check makes no filesystem-latency guarantee.

**AT-89, replace “another build is refused with nothing read”:**

> Another build is refused before diagnostic source reads, copies or `am` calls. Build/boot guard queries and reads of the tool’s own inputs are permitted.

**Still requires a decision:** whether short sensitive plist values are redacted by field-aware rules or by another explicit mechanism. Generic 44-character matching cannot provide the broader OD-200 guarantee. A replacement must specify the sensitive fields and wrapped-value handling while preserving non-sensitive plist content.

## Traceability

- The current feature has **30 scenario/scenario-outline declarations**.
- Every declaration has a requirement tag, AT tag, one inherited slice, one tier and one kind. No `@pc` evidence is asserted; tier separation is sound.
- Existing AIR-084–089 traceability entries point to the relevant declarations, but tag presence overstates clause coverage.
- Add `@AIR-083 @AT-83` to the existing key-path/name redaction coverage, preferably by splitting those Examples from unrelated redaction kinds.
- Keep `@AT-55` only after asserting the callback record reaches both raw output and the unchanged ring event.
- Keep `@AT-73` after expanding to its required fixture set.
- Regenerate traceability after corrections and additions. The 13 declarations above are supplemental suggestions; replacement outlines should not be double-counted.
- AIR-084’s no-runtime-switch/S4 gate and AIR-089(f)’s retention/evaluator exclusion also lack direct coverage here. Track them explicitly rather than treating the AT-72 unit check as proof of AIR-075/076 exclusion.

## Not verified

- No builds, harnesses, step definitions, unit tests or device runs were executed. Missing revision-10 implementation is expected.
- The staged `tools/s3.py`, `package/`, complete Android build tree and XML logging caller were unavailable.
- Actual Android mode enforcement, filesystem latency, interrupted-run cleanup and owner-unit redaction cannot be established by reading these fixtures.
- A fresh-context critic checked the principal findings, including ring capacity, snapshot timing and UxPlay dump delimiters.
- No credentials, OEM binaries or owner-iPhone artifacts were opened.

---

## Applied (2026-11-23, before any step definition)

All 14 findings were applied. The rulings that needed a choice are fail-safe under OD-86 and recorded as OD-204.

- 1 to 3: the counter scenarios now fill all 72 slots for a refused configuration and 64 for an accepted one, trigger a
  session-end counter line after every measured action, assert every count and `gen=`, and model a new app start.
- 4: AT-55's scenario asserts the raw line and the ring event together; AT-73's table carries the whole fixture set.
- 5: a scenario checks the callback's own thread writes before the ring push (wrapped ring, recorded threads); KEYCHECK's
  order is a static check of `host.c`, since `host.c` needs UxPlay and isn't compiled for the PC; the key file is never
  opened by the module.
- 6, 7: AIR-085(c) defines the byte dump by UxPlay's layout and keeps the separators after it, and redacts every
  `<data>` value (the open question on short plist values, settled fail-safe in OD-204).
- 8: AIR-087 counts every line against the cap, measures 65,536 bytes on the escaped text, and writes nothing after the
  final line; scenarios cover a counter line crossing the reserve and events after the file is full.
- 9: the keep check reports every reason that applies; the Examples cover all four combinations; the CLI is named.
- 10: the counter-before-record ordering is gone; "without blocking" is now no ring lock, no Java, with a watchdog only
  as a safeguard; the symlink rule is now in AIR-089(d).
- 11, 12: the stick-tool scenarios use an operation journal, another app-data file, descriptor targets, a sentinel in
  unfiltered logcat and a fake `rm` that keeps files.
- 13: the unit scenario covers every redacted kind; AT-62's check reads the tool's listing of the log folder.
- 14: later write failures, recovery at a new app start, failures and symlinks for each source, an existing output
  folder, and an interrupted run (`parkcast diag end`) are covered.
