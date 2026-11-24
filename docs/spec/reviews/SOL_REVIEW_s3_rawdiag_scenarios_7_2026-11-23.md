# Slice S3 revision 10: review of the scenarios added after the native mutation check

> Reviewer: `gpt-6.1-sol` via Codex CLI (`codex exec` read-only sandbox, role prompt `tools/astra/roles/astra-scenario-reviewer.md` as developer instructions), reasoning effort medium.
> Staged: docs, specs, tests, tools, package, AGENTS.md + evidence: rawdiag.c, logpath.c.
> Session `01a20b7c-0703-7cc0-b9d7-4171d4689609`, 1 turn(s), 55 s; tokens in 327751 (cached 279168), out 1194 (reasoning 126).
> Prompt: `tools/astra/prompts/S3_REVIEW_RAWDIAG_7.md`. Date 2026-11-23.

**External critique, not an owner decision.**

---

## Verdict

Implement with clarifications. One major prevents readiness as written. The other additions catch their named mutants and agree with AIR-085/AIR-086.

## Findings

| # | Severity | File:line | Finding | Concrete fix |
|---|---|---|---|---|
| 1 | Major | `specs/features/16_receiver_diagnostics.feature:114-124` | Both unterminated rows inherit an expected trailing ` after`. AIR-085(c) requires consuming it through record end. Correct code would fail these expectations (`evidence/rawdiag.c:186-195`). | Move those two rows into the outline below. |

## Missing scenarios

Replacement for the two unterminated rows:

```gherkin
  @host @failure @AIR-085 @AT-85 @OD-207
  Scenario Outline: Unterminated key material is redacted through record end
    Given the raw-file module is opened
    When a record at level 7 arrives with the text "<input>"
    Then the raw file's line for it is "<uptime> L7 <output>"

    Examples:
      | input                                          | output                      |
      | before PIN = "1234 and no closing quote after   | before PIN = [redacted]     |
      | before <data>QUJD with no closing tag after     | before <data>[redacted]     |
```

## Requirement wording

No change needed.

## Traceability

Retain both example cases under AIR-085/AT-85; regenerate traceability after moving them.

## Not verified

This was a reading review, not a mutation rerun. After correcting these expectations, the reviewed additions are ready for step definitions.
---

## Applied (2026-11-23)

Finding 1: the two unterminated rows moved out of the `before ... after` outline into their own outline, "Unterminated
key material is redacted through the end of the record", with the reviewer's inputs and outputs.
