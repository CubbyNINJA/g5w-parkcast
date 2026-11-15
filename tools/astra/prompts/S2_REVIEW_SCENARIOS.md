Review slice S2's parking-gate scenarios before any step definition is written. The stage root is
STAGE_ROOT.

Context:
- Requirements: `docs/spec/REQUIREMENTS.md`, S2 rows AIR-016 to AIR-027 and AIR-035 to AIR-040 with
  AT-16 to AT-27 and AT-35 to AT-40 (your revisions 2 to 6 under `docs/spec/reviews/`). Decisions
  OD-23, OD-24, OD-43 to OD-49 and OD-51 to OD-64 are in `docs/spec/DECISIONS.md`. Evidence:
  `docs/evidence/GEAR_PROBE_FINDINGS.md`.
- New scenarios: `specs/features/09_parking_gate.feature`, `10_parking_config.feature`,
  `11_parking_logs.feature` (all `@offline`) and `12_parking_unit.feature` (`@unit`).
  `docs/spec/TRACEABILITY.md` is regenerated.
- No S2 step definitions or receiver code exist yet, so `tests/test_step_coverage.py` and the S2
  scenarios fail. That's expected, not a finding. How the offline steps will drive the receiver's
  gate (the receiver is an Android app) is not decided yet; don't decide it, but flag any scenario
  whose wording would only be testable one way.

Check specifically:
1. Does every AT row's `@offline` and `@unit` criterion have a scenario that asserts it, including
   AT-17's cadence cases (650/660/850/1050 ms, the exact 200 ms return, consecutive late calls,
   a call pending beyond 2 s), AT-18's three bounds and coincident-event ordering, AT-19's 199,
   200, 201 and 650 ms replies, both processing orders at exactly 200 ms, the 10 ms pending
   follow-up call, age at 2 s, and resume? Name each gap with the scenario to add.
2. Is any scenario's timing arithmetic wrong for the requirement it cites? Check each Examples row.
3. Does any scenario assert more than the requirements and decisions say (invented policy)?
4. Are the step phrasings implementable with oracles independent of the code under test?

Severity: blocker means step definitions must not be written until it's fixed.
