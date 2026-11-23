# Slice S3 revision 10: fourth coverage review of the diagnostic raw log scenarios

> Reviewer: `gpt-6.1-sol` via Codex CLI (`codex exec` read-only sandbox, role prompt `tools/astra/roles/astra-scenario-reviewer.md` as developer instructions), reasoning effort medium.
> Staged: docs, specs, tests, tools, package, AGENTS.md + evidence: ring.c, host.c, utils.c.
> Session `01a20a64-89f4-7eb0-8ad5-689c448fcf06`, 1 turn(s), 102 s; tokens in 414171 (cached 371072), out 2154 (reasoning 384).
> Prompt: `tools/astra/prompts/S3_REVIEW_RAWDIAG_4.md`. Date 2026-11-23.

**External critique, not an owner decision.**

---

## Verdict

**Blocked: one major remains.** Findings 1–3 and 5–7 are fixed; finding 4 remains ineffective.

## Findings

| # | Severity | File:line | Finding | Concrete fix |
|---|---|---|---|---|
| 1 | major | `specs/features/16_receiver_diagnostics.feature:442` | The added start marker clears the evaluator’s session map (`tools/airplay_s3/evaluator.py:86–89`). Without a subsequent connection-accepted record, its codec/frame evidence is ignored (`:108–111`). I confirmed in memory that the current evaluator reads the raw file yet passes all three equality assertions. | Add a complete accepted session, valid timestamps and `picture: yes`; make the baseline otherwise capable of GO except for its missing frame. |

## Missing scenarios

Replace the ineffective comparison with:

```gherkin
@S3 @offline @boundary @AIR-089 @AIR-075 @AIR-076 @AT-89 @AT-75 @AT-76
Scenario: Raw evidence cannot repair missing receiver evidence
  Given a complete synthetic receiver run with owner note "picture: yes"
  And its session has all GO evidence except its first-frame record
  And its INCONCLUSIVE verdict, summary and citations are saved
  And "uxplay-raw.log" holds complete start and stop markers enclosing an accepted session with H.264 codec, hardware decoder and first-frame records, all within the observation window
  When the evaluator judges the folder again
  Then its verdict, summary and citations equal the saved ones
```

## Requirement wording

No change needed.

## Traceability

No uncovered AT-84–AT-89 criterion besides the ineffective AT-89 exclusion check above. No other major tag, unused Examples-column or arithmetic regression found. The read-only tag checker passes.

## Not verified

Gherkin parsing and scenario execution were unavailable. The evaluator counterexample used mocked reads and wrote no files.
---

## Applied (2026-11-23)

Finding 1: the evaluator scenario is replaced with the reviewer's version: a complete run, GO but for its missing
first-frame record, with `picture: yes`, and a raw file holding a complete accepted session inside the window. It also
asserts that the evaluator as it stood (reading every `*.log`) gives GO on that folder, so the check is shown to be
able to fail.
