Review the S1 scenarios written for owner decisions OD-52 and OD-53, before their step definitions
are written. The stage root is STAGE_ROOT.

Context:
- OD-52 and OD-53 are in `docs/spec/DECISIONS.md`. `docs/spec/REQUIREMENTS.md` encodes them in
  AIR-007(d), AIR-031(g) and AIR-033(e), with AT-07, AT-31 and AT-33 (your revision 4 draft,
  `docs/spec/reviews/ASTRA_REQUIREMENTS_s1_rev4_od52_od53_2026-11-15.md`).
- New scenario outline in `specs/features/03_runner.feature`: "Enter stops UxPlay within 1 second,
  wherever it falls between the runner's checks". It isn't told the runner's check interval; the
  Enter times are spread across a second, and the fake UxPlay takes 0.4 s to exit.
- Replaced scenario in `specs/features/02_firewall.feature`: "An unsaved run record stops the
  command before analysis and evidence", an outline over four endings (it replaces "Through the
  command, an unwritable run record still gets the reminder and no claim").
- These new steps have no definitions yet, which `tests/test_step_coverage.py` reports; that's
  expected, not a finding: "UxPlay takes 0.4 seconds to exit once asked to stop", "the owner presses
  Enter at second <n>", "UxPlay has exited within 1 second of the key press", "every call to the
  analyser, evaluator and evidence writer is recorded", "stderr names the run-record failure",
  "the analyser, evaluator and evidence writer were never called", "no analyser report or verdict
  is written or announced".
- The runner (`tools/airplay_s1/runner.py`) checks for Enter every 0.5 s and the CLI
  (`tools/airplay_s1/cli.py`) already returns before analysis when the record isn't written.

Check specifically:
1. Do the scenarios cover AT-07's 1 s bound (including a case a 1.5 s check interval would fail)
   and AT-31/AT-33's unsaved-record behaviour for every ending? Name any gap.
2. Can each new step be implemented with an oracle independent of the code under test? Say how
   you would implement any you doubt.
3. Is anything in the requirement rows untested that should be, or tested that isn't required?

Severity: blocker means the step definitions must not be written until it's fixed.
