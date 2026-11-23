# Slice S3 revision 10: second coverage review of the diagnostic raw log scenarios

> Reviewer: `gpt-6.1-sol` via Codex CLI (`codex exec` read-only sandbox, role prompt `tools/astra/roles/astra-scenario-reviewer.md` as developer instructions), reasoning effort medium.
> Staged: docs, specs, tests, tools, package, AGENTS.md + evidence: ring.c, host.c, parkcast.h, raop_handlers.h, utils.c.
> Session `01a20a5b-0e72-7303-afd8-2d4a3d317c8c`, 1 turn(s), 145 s; tokens in 659373 (cached 598912), out 4575 (reasoning 837).
> Prompt: `tools/astra/prompts/S3_REVIEW_RAWDIAG_2.md`. Date 2026-11-23.

**External critique, not an owner decision.**

---

## Verdict

**Blocked: fix the blocker findings before writing step definitions.**

Prior findings **6–10, 11 and 13 are resolved**. Findings **1–5, 12 and 14 remain partly resolved**, as detailed below. The “Applied” section overstates completion.

References `F:n` mean `specs/features/16_receiver_diagnostics.feature:n`.

## Findings

| # | Severity | File:line | Finding | Concrete fix |
|---|---|---|---|---|
| 1 | blocker | F:195–215 | Prior **1–3**: the slot counts are corrected, but the full-ring snapshot remains ambiguous. A real session end submits another control event (`evidence/host.c:197–202`), which can add another drop (`evidence/ring.c:122–126`). Thus `control_drops=1` is not guaranteed. Prefilling and direct AU submission are also underspecified. | Specify unmeasured fixture injection for prefill and direct ring submission for row 206. Free one slot before the real session-end callback, so it adds no drop. Include uptime and `counters` in the expected line, or explicitly compare only its fields. |
| 2 | blocker | F:217–224,254 | Prior **2–3**: the size test still requests a “next counter line” without triggering it. Carryover checks only `au_in`; other counters could reset at session end unnoticed. Every generation assertion uses 1, allowing a hardcoded generation. | Trigger a timestamped snapshot after the oversized record. Establish nonzero values for every counter, assert their unchanged tuple across session end, then all zeros after restart. Check a generation other than 1. |
| 3 | blocker | F:148–171 | Prior **4**: AT-73’s “every fixture above” is still incomplete: the 31-character hex and 43-character base64 fixtures are absent. `line one LF line two` and `byte 0x01 here` are ASCII labels unless explicitly decoded. | Add both boundary fixtures; specify actual LF and byte 0x01 inputs. Assert their exact unchanged ring bytes and corresponding raw records. |
| 4 | blocker | F:298–307 | Prior **12**: retaining deleted files does not prove unfiltered logcat never existed. A mutant can write unfiltered output through shell redirection, overwrite that file with filtered output, then delete it; the retained file contains no sentinel. | Observe writes/truncations externally, including shell redirections. Validate the observer against that create/overwrite/delete mutant. |
| 5 | blocker | F:331–347 | Prior **14**; AT-89 requires all three sources under every failure. Missing: `receiver.log.1` missing, `receiver.log` copy failure, and `receiver.log.1` symlink. Collision reporting is also unasserted. | Add those three Examples rows. Assert that an existing output folder is reported. |
| 6 | major | F:60–66,276–284 | Prior **5**: callback ordering is covered, but a failing filter is never exercised with raw diagnostics enabled. No assertion observes raw calls into Java. | Add a failing-filter case that still writes the raw record, and independently journal Java calls during raw operations. |
| 7 | major | F:75–115 | Short plist data is fixed, resolving prior **7**. New AIR-085(c) rules for multiple/wrapped `<data>` values and overlapping/touching replacements lack decisive fixtures. | Add exact-byte examples for each; a first-match-only or double-replacement implementation must fail. |
| 8 | major | F:228–246,349–362 | AT-87 preserves only the first record, rather than all preceding lines. AT-89 does not assert root-marker removal **before** guard reads, absence of app-data listings, or interruption recognition. Git-ignore alone does not prove PC output is stored under `runs/`. | Compare the entire pre-full prefix; assert operation order and no app-data listing command; add interrupted-log and actual output-location checks. |
| 9 | minor | `docs/spec/REQUIREMENTS.md:97,192` | AIR-087(b)’s `(c)` and `(b)` references point to the wrong requirement. AT-88 asks for an open failure at a later record, although opening happens at app start. | Apply the wording replacements below. |

**Acceptance coverage:** AT-84’s listed criteria are covered. Remaining gaps in AT-85–89 are above. AT-55’s raw-file clause is covered; AT-62’s listing now addresses the actual log directory; AT-72’s raw/full-ring clause and AT-83’s raw-path/name and collection clauses are covered. AT-73 remains incomplete.

**Arithmetic:** 32/31 hex characters, 44/43 base64 characters, and eight/seven dump bytes are correct. `64 + 8 = 72`; `16,777,216 − 512 = 16,776,704`. All five seven-counter tuples are arithmetically consistent **given direct, unmeasured fixture setup and no extra session-end drop**. Those conditions need explicit steps.

The dump’s exact expected escaped text, `72 byte ekey:\n[redacted] \n`, is correct: the final space and LF survive. UxPlay emits these separators at `evidence/utils.c:203–215`. The other redaction outputs are correct.

**Scope and oracles:** F:199 literally omits required counter-line framing; fix its comparison as above. No production-provided “pass” oracle is specified. The remaining concern is insufficient observation, especially transient file writes.

**Clear keys:** No confirmed key remains clear in the inspected direct key dumps: `evidence/raop_handlers.h:825,847,881,893` use dumps covered by AIR-085(c). At **998–999**, decimal `streamConnectionID` can survive every rule; it is a key/IV **derivation input**, not itself a demonstrated key. Expanding protection to it needs explicit wording.

## Missing scenarios

Supplement these with the direct edits above.

```gherkin
@S3 @host @failure @AIR-072 @AIR-085 @AT-85
Scenario: A failing filter cannot suppress the raw record
  Given raw diagnostics are on and the normal-log filter throws
  And external instrumentation records file writes and Java calls
  When UxPlay's callback receives level 7 and text "CSeq: 3"
  Then the raw file contains that framed record
  And no raw-file operation calls Java

@S3 @offline @failure @AIR-089 @AT-89
Scenario: Transient unfiltered writes are detected
  Given an external observer records stick writes and truncations
  When a fixture writes unfiltered sentinel bytes through shell redirection
  And overwrites that file with filtered bytes and deletes it
  Then the observer reports the unfiltered write

@S3 @offline @boundary @AIR-089 @AT-89
Scenario: A log without the completion marker is interrupted
  Given a synthetic diagnostic log without "parkcast diag end"
  When the diagnostic run is classified
  Then it is classified as interrupted
```

## Requirement wording

In AIR-087(b), replace:

> after (c)'s redaction and (b)'s escaping

with:

> after AIR-085(c)'s redaction and AIR-085(b)'s escaping

In AT-88, replace:

> parametrised over an open failure, a failed write and a short write, each at the first and at a later record

with:

> parametrised over an open failure at initialization, and a failed write and a short write each at the first and at a later record

## Traceability

Regenerate traceability after corrections. Split the all-72-slots fault row out of the `@normal` counter Outline into `@failure`. Every scenario currently inherits one slice and specifies one tier and one kind; no evidence-tier substitution was found.

## Not verified

Read-only review; no implementation or test execution. The stage lacks the receiver source tree, package tree and `tools/s3.py`; supplied native evidence establishes only the cited current paths. Missing revision-10 implementation is expected and is not a finding.
---

## Applied (2026-11-23, before any step definition)

- 1, 2: counter scenarios now fill slots with fixture events pushed straight into the ring and empty them before the
  session-end callback, so that event is never dropped; every expected counter line is compared whole with its uptime;
  the generation is 3 or 2, not 1; the carry-over scenario makes every count non-zero first; the 65,536-byte scenario
  triggers a session-end counter line.
- 3: AT-73's table adds the 31-hex and 43-base64 fixtures, and LF and 0x01 are real bytes.
- 4: adopted in another form. A fake shell on Windows can't observe redirections, so a static check of the script
  requires every `logcat` call to pipe straight into `grep` with no `-f` and no redirect, and a fixture of the exact
  mutant (redirect, overwrite, delete) must fail that check. The sentinel scenario stays.
- 5: the failure table covers every source for missing, failed copy and symlink; an existing folder is reported.
- 6: a scenario with a failing Java-side filter and a recording JNI environment checks the raw write and that no JNI
  call happens during a raw-file call.
- 7: fixtures for two `<data>` values, a multi-line `<data>` value, and touching hex and base64 runs.
- 8: the cap scenario compares every line before the final line; the guard scenario checks the root `NEMESIS.data`
  is removed before the build is queried; no app-data listing; `parkcast diag end` after every copy; git tracks no file
  under `runs/` or named `uxplay-raw.log`. The suggested "interrupted run" classifier is not adopted: no tool classifies
  runs, and AIR-089(f) only requires the end line, now checked for its order.
- 9: both wording fixes applied; the write-failure Examples name the open, the open's counter line and the third
  record's line.
- `streamConnectionID` (decimal, `raop_handlers.h:998-999`) stays unredacted: it is a derivation input, not key
  material, and the key it is combined with is redacted.
