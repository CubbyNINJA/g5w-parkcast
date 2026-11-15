Re-review slice S2's parking-gate scenarios before any step definition is written. The stage root is
STAGE_ROOT.

Context:
- Requirements: `docs/spec/REQUIREMENTS.md`, S2 rows AIR-016 to AIR-027 and AIR-035 to AIR-040 with
  AT-16 to AT-27 and AT-35 to AT-40, now at revision 11 (drafts under `docs/spec/reviews/`, revisions
  7 to 11 since the last review). Decisions OD-23, OD-24, OD-43 to OD-49, OD-51 to OD-88 and OD-91,
  OD-100 to OD-102 are in `docs/spec/DECISIONS.md`. Open owner questions AMB-71 to AMB-74 are in
  `docs/spec/AMBIGUITY_REGISTER.md`; scenarios must not assert anything they leave open. Evidence:
  `docs/evidence/GEAR_PROBE_FINDINGS.md`, `docs/evidence/SLEEP_SIGNAL_FINDINGS.md`.
- The previous review is `docs/spec/reviews/CLAUDE_REVIEW_s2_scenarios_2026-11-15.md`. Its findings
  4, 23, 24, 25, 31 and missing scenario E were open and have now been addressed.
- Scenarios: `specs/features/09_parking_gate.feature`, `10_parking_config.feature`,
  `11_parking_logs.feature` (all `@offline`) and `12_parking_unit.feature` (`@unit`).
  `docs/spec/TRACEABILITY.md` is regenerated.
- No S2 step definitions or receiver code exist yet, so `tests/test_step_coverage.py` and the S2
  scenarios fail. That's expected, not a finding. OD-66 decides how the offline steps drive the gate:
  plain Java with no Android imports, compiled with `javac` and driven through a small Java harness
  (controlled clock, fake getter, fake ACC registration and broadcasts, frame sink, log sink, notice
  sink). Flag any scenario that this harness could not drive.

Check specifically:
1. Does every AT row's `@offline` and `@unit` criterion have a scenario that asserts it? Pay most
   attention to what changed in revisions 7 to 11: the ACC status mapping and ADM (AIR-019(f)), discarded
   records (g), registration pending, failure and ignored broadcasts (h), the registration-failure notice
   and its separate allowance (AIR-019(i), AIR-040(c)), the flag-off counterparts (AIR-021(b)), rotation
   at 2,621,440 bytes before a crossing write (AIR-039), literal `reason=` phrases and duplicate/spaced
   keys (AIR-023, AIR-025), and transition records (AIR-026). Name each gap with the scenario to add.
2. Were the previous review's open findings resolved correctly?
3. Is any scenario's timing or byte arithmetic wrong for the requirement it cites? Check each Examples row.
4. Does any scenario assert more than the requirements and decisions say (invented policy), including
   anything AMB-71 to AMB-74 leave open?
5. Are the step phrasings implementable with oracles independent of the code under test?
6. The scenario author raised these; give a ruling with the row and clause that settles each, or say
   it needs the owner: (a) the AT-39 "single 2,621,440-byte record" clause, empty current file only or
   also a non-empty one (two rotations); (b) whether "either flag value" in AIR-019(f)/(h) requires
   unregister at exit with the check off; (c) whether the fallback-notice scenario's unreadable and
   invalid-value `receiver.conf` rows are in scope beyond AT-19's missing-file case; (d) whether
   splitting AT-19's 0/5/missing broadcast sequence into one outline row per broadcast is acceptable.

Severity: blocker means step definitions must not be written until it's fixed.
