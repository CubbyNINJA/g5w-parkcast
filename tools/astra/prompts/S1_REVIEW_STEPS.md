Review slice S1's step definitions, and the scenario changes made since your last review, before
any S1 implementation is written. The stage root is STAGE_ROOT.

Context:
- Your previous review is `docs/spec/reviews/ASTRA_REVIEW_s1_scenarios_2026-11-14.md`. Its last
  section says how each finding was applied. Owner decisions OD-25 to OD-28 followed it
  (`docs/spec/DECISIONS.md`).
- The parent then changed scenarios while writing steps. The damage matrix in
  `05_verdict.feature` now names the limitation `unreadable` for a partly unreadable log
  (AIR-028(c)). The host boundary scenario in `07_test_boundary.feature` now collects the main
  suite with pytest_bdd blocked. The boundary steps were renamed "the boundary check passes/fails
  naming". Three `@pc` steps in `06_evidence.feature` were reworded so they don't share text with
  offline steps. `@OD-27` and `@OD-28` tags were added.
- Step definitions are in `tests/steps/`: `world.py` (fixture, log wrapping, standard session
  excerpts), `fakes.py`, and one module per area. `tests/test_step_coverage.py` proves every
  rendered step has exactly one definition. `docs/spec/S1_INTERFACES.md` lists the interface the
  steps expect from the not-yet-written `tools/airplay_s1/`.
- Current state: every offline scenario fails with `ModuleNotFoundError: No module named
  'airplay_s1'`, the tag and step-coverage tests pass, and `@host`/`@pc` scenarios are skipped by
  default.

Answer these specifically, along with the role's usual checks:
1. For each feature file, does each step definition assert what its Gherkin text says? Name
   any Then step that is weaker than its words (would pass for a wrong implementation), and any
   Given or When that sets up something other than what it says.
2. Do the fakes allow an implementation to pass while breaking a requirement? For example: event
   ordering that a wrong order would still satisfy, or a fake that hides a failure.
3. Do the log fixtures in `world.py` and `log_steps.py` match `docs/evidence/UXPLAY_LOG_SIGNATURES.md`?
4. Is anything in `S1_INTERFACES.md` a decision the owner should make, rather than an
   implementation detail?
5. Do the changed scenarios stay consistent with the requirements and decisions?

Severity: blocker means step 7 must not start until it's fixed.
