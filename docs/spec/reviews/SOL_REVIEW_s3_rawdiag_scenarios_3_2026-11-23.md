# Slice S3 revision 10: third coverage review of the diagnostic raw log scenarios

> Reviewer: `gpt-6.1-sol` via Codex CLI (`codex exec` read-only sandbox, role prompt `tools/astra/roles/astra-scenario-reviewer.md` as developer instructions), reasoning effort medium.
> Staged: docs, specs, tests, tools, package, AGENTS.md + evidence: ring.c, host.c, parkcast.h, raop_handlers.h, utils.c.
> Session `01a20a60-2e24-75c0-adcc-4d3e0071c961`, 1 turn(s), 175 s; tokens in 638358 (cached 576128), out 3573 (reasoning 677).
> Prompt: `tools/astra/prompts/S3_REVIEW_RAWDIAG_3.md`. Date 2026-11-23.

**External critique, not an owner decision.**

---

## Verdict

**Blocked before step definitions.** Most previous findings are fixed; second-review findings **7, 8 and 9 remain partly resolved**.

`F:n` below means `specs/features/16_receiver_diagnostics.feature:n`.

## Findings

| # | Severity | File:line | Finding | Concrete fix |
|---|---|---|---|---|
| 1 | blocker | F:212 | Explanatory prose follows scenario steps, where Gherkin cannot accept description text. | Comment lines 212–216 with `#`, or move them into the feature description. |
| 2 | blocker | F:304; `docs/spec/REQUIREMENTS.md:192` | Prior finding **9** remains: opening-counter failures do not exercise AT-88’s **first-record** write failures. | Retain initialization cases; add failed and short writes at the first record. |
| 3 | major | F:97 | Prior finding **7**: the touching example is one 76-character base64 run. A matcher that handles maximal base64 runs but never merges separate touching replacements passes. | Add the distinct touching-spans fixture below. |
| 4 | major | F:415 | The evaluator comparison leaves its baseline unspecified and adds bare, incomplete markers. A mutant that reads the raw file but ignores those particular lines can pass. Current ingestion scans every `*.log` (`tools/airplay_s3/evaluator.py:38`). | Use complete, valid, adversarial marker and session-evidence lines; assert unchanged verdict, summary and citations. |
| 5 | major | F:409 | Prior finding **8**: git checks establish exclusion from git, not actual storage under `runs/`. | Exercise the PC output path and inspect its destination. Existing `write_result` provides the relevant path (`tools/airplay_s3/evaluator.py:337`). |
| 6 | major | F:13, F:317 | A wrapper around `ring_push` alone cannot observe its internal lock ownership. The mutex is static and acquired inside the function (`evidence/ring.c:30,116`). | Specify acquisition/release instrumentation, such as wrapping the pthread mutex calls, and observe ownership at each raw operation. |
| 7 | minor | F:379–386 | Several exact diagnostic phrases exceed AIR-089(d), which requires reporting the source name; only missing raw-file wording is prescribed. | Assert failure reporting containing the source name; reserve exact wording for `no uxplay-raw.log`. |

**Previous nine findings:** **1–6 are addressed**, subject to instrumentation clarification #6; **7** retains the merging gap; **8** retains the actual PC destination gap; **9** retains first-record failures. Both wording corrections requested by finding 9 were applied.

The **static logcat check is a sound alternative** if test-owned and based on shell command structure, including redirects and `-f`; its negative fixture covers the reported overwrite/delete mutant. A line-matching shortcut would not establish the claimed guarantee. The **no-classifier choice is sound**: AIR-089(f) requires the completion marker, not another classification tool.

**Coverage:** AT-84–87 and the raw-file clauses of AT-55, AT-62, AT-72, AT-73 and AT-83 are represented. AT-88 lacks first-record failures. AT-89’s raw-evidence exclusion fixture needs strengthening as above. Actual PC storage remains an AIR-089(g) gap.

**Arithmetic:** All numeric Examples are correct, under the stated fixture setup. The counter Outline’s seven-count tuples, including fixed `control_drops=0`, are:

- `(3,0,0,0,0,0,0)`
- `(0,1,0,0,0,0,0)`
- `(1,0,1,1,0,1,0)`
- `(0,0,0,0,0,2,1)`

The refusal and carryover tuples are also correct. `64 + 8 = 72`; `16,777,216 − 512 = 16,776,704`. Hex/base64 lengths are 32/31 and 44/43; dump lengths are eight/seven bytes. All specified redaction outputs are correct, including `72 byte ekey:\n[redacted] \n`: the final space and LF survive (`evidence/utils.c:203–215`).

**Clear keys:** No confirmed key remains clear in the supplied debug logging. AES/ECDH dumps at `evidence/raop_handlers.h:814,825,847,881,893` satisfy the dump rule. `streamConnectionID` at **998–999** remains a derivation input, consistent with the recorded ruling.

## Missing scenarios

```gherkin
@S3 @host @failure @AIR-088 @AT-88 @OD-201
Scenario Outline: Failure at the first record disables subsequent raw writes
  Given the raw-file module is opened successfully
  And the fake file layer will produce <failure> at the first record's line
  When five records arrive, the session ends and stop's last phase runs
  Then the fake file layer recorded no write, reopen or other open after the failure
  When a new app start opens the raw-file module with the fake file layer working
  And a record arrives
  Then that record is written

  Examples:
    | failure        |
    | a failed write |
    | a short write  |

@S3 @host @boundary @AIR-085 @AT-85 @OD-200
Scenario: Distinct touching replacements produce one marker
  Given the raw-file module is opened with the fake key file path "/fake/files/key.fixture"
  When a record at level 7 arrives with the text "before 0123456789abcdef0123456789abcdefkey.fixture after"
  Then the raw file's line for it is "<uptime> L7 before [redacted] after"

@S3 @offline @boundary @AIR-089 @AIR-075 @AIR-076 @AT-89 @AT-75 @AT-76
Scenario: Raw evidence cannot repair missing receiver evidence
  Given a synthetic unit run whose receiver log lacks required session evidence
  And its baseline verdict, summary and citations are saved
  And "uxplay-raw.log" supplies complete valid markers and the missing session evidence
  When the evaluator judges the folder again
  Then its verdict, summary and citations equal the saved baseline
```

## Requirement wording

No behavioral replacement is needed. In AT-86, change the all-72-slots refusal case from `@normal` to `@failure`, matching its scenario and OD-190 breach status.

## Traceability

The read-only tag checker returned **no errors**. Regenerate traceability after additions; include AIR-076/AT-76 on the strengthened evaluator scenario.

## Not verified

No files changed. Gherkin parsing, compilation and execution were unavailable; the syntax finding follows Gherkin’s description placement rules. No revision-10 implementation was expected. No iPhone, head-unit or runtime redaction evidence was examined.
---

## Applied (2026-11-23, before any step definition)

- 1: the counter note is now `#` comments.
- 2: the failure Outline adds a failed and a short write at the first record's line.
- 3: a touching-spans scenario (a hex run ending where the key file's name begins) must give one `[redacted]`.
- 4: the evaluator scenario now starts from a run missing its first-frame record, saves verdict, summary and cited
  lines, and adds a raw file with complete, receiver-log-form markers and session evidence; all three must be equal.
  This exposes a real defect: `evaluator._records` reads every `*.log`, so `uxplay-raw.log` would be read. The fix
  comes with the implementation.
- 5: a scenario runs `write_result` into a fake root and checks the raw file lands under `runs/` and only the summary
  under `docs/evidence/`, with no raw text in it.
- 6: the harness wraps `pthread_mutex_lock` and `pthread_mutex_unlock`; the check is that no raw-file operation runs
  while its thread holds any mutex but the raw-file module's own.
- 7: failure reports are checked for the source's name only; `no uxplay-raw.log` keeps its exact words in its own
  scenario.
- AT-86's refused-configuration case is `@failure`. Traceability regenerated; the feature parses with the Gherkin parser.
