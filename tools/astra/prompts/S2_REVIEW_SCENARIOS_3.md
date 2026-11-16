Re-review slice S2's parking-gate scenarios before any step definition is written. The stage root is STAGE_ROOT.

Context:
- Requirements: `docs/spec/REQUIREMENTS.md`, S2 rows AIR-016 to AIR-027 and AIR-035 to AIR-041 with AT-16 to AT-27 and
  AT-35 to AT-41, now at revision 16 (drafts under `docs/spec/reviews/`; revisions 13 to 16 since the last review
  added the notice form, text, timing, retry, stacking and removal (AIR-019(i), AIR-040) and the cover rule (AIR-041)).
  Decisions up to OD-130 are in `docs/spec/DECISIONS.md`; no S2 question is open in `docs/spec/AMBIGUITY_REGISTER.md`.
  Evidence: `docs/evidence/GEAR_PROBE_FINDINGS.md`, `SLEEP_SIGNAL_FINDINGS.md`, `CAMERA_SIGNAL_FINDINGS.md`.
- The previous review is `docs/spec/reviews/CLAUDE_REVIEW_s2_scenarios_2_2026-11-15.md`; its findings were applied
  (finding 12 and missing scenario F dropped by OD-103).
- Scenarios: `specs/features/09_parking_gate.feature`, `10_parking_config.feature`, `11_parking_logs.feature` (all
  `@offline` apart from tagged exceptions) and `12_parking_unit.feature` (`@unit`). `docs/spec/TRACEABILITY.md` is
  regenerated.
- No S2 step definitions or receiver code exist yet; failing S2 scenarios are expected, not a finding. OD-66: the
  offline steps drive a plain-Java gate through a Java harness with a controlled clock, fake getter, fake ACC and
  REVERSE_ON registration and broadcasts, a focus double, frame, log and notice/window-manager doubles, and a file
  recorder. Flag any scenario that such a harness could not drive.

Check specifically:
1. Does every AT row's `@offline` and `@unit` criterion have a scenario that asserts it? Focus on AT-19, AT-40 and
   AT-41. Name each gap with the scenario to add.
2. Is any timing or byte arithmetic wrong? Check each Examples row, especially the S+5000, F+5000, 1 s cover grid,
   uncover+5000 and stacking (Y, Y+H) rows.
3. Does any scenario assert more than the requirements and decisions say (invented policy)?
4. Are the step phrasings implementable with oracles independent of the code under test? Is the preamble's UI-double
   contract (gravity, x, y, H, recorded calls) consistent with AIR-040 and OD-118/OD-121/OD-123?
5. The scenario author raised these; give a ruling with the row and clause that settles each, or say it needs the
   owner: (a) AT-41 asks for 1 s removal calls after a cover-start removal throws and also "no call at S+5000", which
   falls on that grid: is one grid call at S+5000 and no extra timed removal right? (b) is "no focus gain is delivered
   before the registration call returns" acceptable as a Given? (c) does OD-104's "texts differ" survive now that
   OD-106 and OD-112 fix both texts? (d) does "asleep" need its own observable step? (e) which record is the startup
   flag record, and is `status=255` right in the check-on ignored outline? (f) are AT-41 `@unit` timings rightly
   anchored on the PgsService and AvmMode log lines in `CAMERA_SIGNAL_FINDINGS.md`?

Severity: blocker means step definitions must not be written until it's fixed.
