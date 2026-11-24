# Slice S3 revision 10: review of the scenarios added after the mutation checks

> Reviewer: `gpt-6.1-sol` via Codex CLI (`codex exec` read-only sandbox, role prompt `tools/astra/roles/astra-scenario-reviewer.md` as developer instructions), reasoning effort medium.
> Staged: docs, specs, tests, tools, package, AGENTS.md.
> Session `01a20b48-7a00-77b0-be08-ffa36efa94ca`, 1 turn(s), 112 s; tokens in 403143 (cached 360192), out 1893 (reasoning 92).
> Prompt: `tools/astra/prompts/S3_REVIEW_RAWDIAG_6.md`. Date 2026-11-23.

**External critique, not an owner decision.**

---

## Verdict

Blocked pending three major fixes.

## Findings

| # | Severity | File:line | Finding and concrete fix |
|---|---|---|---|
| 1 | major | `specs/features/16_receiver_diagnostics.feature:383` | The final logcat row breaks both redirection and pipe rules, so disabling either alone still rejects it (`tools/airplay_s3/stickcheck.py:158–163`). Use `if …; then logcat -d > file \| grep …; fi` to isolate redirection while exercising recognition after `then`. |
| 2 | major | `specs/features/16_receiver_diagnostics.feature:382` | Requiring `-d` exceeds AIR-089(c), which prohibits unfiltered writes but does not require snapshot mode (`docs/spec/REQUIREMENTS.md:99`). Remove this row unless an owner decision adds that requirement. |
| 3 | major | `specs/features/16_receiver_diagnostics.feature:434` | The partial-copy fixture must explicitly make `cp` **return 0** after copying half. Otherwise a nonzero `cp` still causes rejection when comparison is disabled, masking that mutant (`tools/airplay_s3/stick/parkcast-diag.sh:181–186`). |

## Missing scenarios

None beyond those corrections.

## Requirement wording

No change needed to the added AIR-089(d) and (e) clauses.

## Traceability

Remove the unsupported `-d` Examples row; regenerate traceability.

## Not verified

Oracle independence depends on the forthcoming step definitions: expected files, bytes and diagnostics must come from fixture constants.

The split-log scenario **does catch both requested mutants**: ignoring `.1` loses the start marker; reversing order leaves the older fragment last and fails completeness (`tools/airplay_s3/evaluator.py:62–67`). Its explicit GO expectation prevents equal erroneous verdicts from passing.

No other blocker or major found in the named scenarios.
---

## Applied (2026-11-23)

1. The last logcat row now isolates the redirect: inside an `if`, after `then`, redirected before its pipe into grep.
2. The `-d` row is removed, and so is the `-d` rule in `tools/airplay_s3/stickcheck.py`: AIR-089(c) doesn't require it.
3. The partial-copy fake now copies half and exits 0, so only the comparison can catch it.
