Review the S1 scenario and step changes for owner decisions OD-31 to OD-39 before the runner is
written. The stage root is STAGE_ROOT.

Context:
- OD-31 to OD-39 are in `docs/spec/DECISIONS.md`. The S1 tools no longer touch the firewall.
  Windows asks the owner the first time `uxplay.exe` listens, the runner gives a notice before
  launch, and every run that launched UxPlay ends with a reminder.
- `docs/spec/REQUIREMENTS.md` encodes them. AIR-003, AIR-004, AIR-005 and AIR-030 are retired,
  and AIR-032 to AIR-034 and AT-32 to AT-34 are new. The draft is
  `docs/spec/reviews/CLAUDE_REQUIREMENTS_s1_firewall_rev3_2026-11-14.md`, and the parent's
  changes are in the "Wording changes" section.
- Changed scenarios: `specs/features/02_firewall.feature` (rewritten), plus `03_runner.feature`,
  `06_evidence.feature` and `08_cli.feature`.
- Changed steps: `tests/steps/prompt_steps.py` (new), `run_steps.py`, `fakes.py`, `world.py`,
  `cli_steps.py` and `live_steps.py`. `docs/spec/S1_INTERFACES.md` was updated.
- The runner and CLI aren't written yet. Every scenario that needs them fails with
  `ModuleNotFoundError: airplay_s1.runner` or `airplay_s1.cli`. That's expected, not a finding.

Check specifically:
1. Do the scenarios cover AIR-032 to AIR-034 and the changed AIR-006, AIR-007 and AIR-031, as
   OD-31 to OD-39 decide them? Name any gap or any scenario that contradicts a decision.
2. Does each changed or new step assert what its Gherkin text says? Name any Then that is weaker
   than its words, or that takes its oracle from the code under test.
3. Is anything left over from the scripted firewall design (steps, fakes, tags, docs) that should
   have gone?
4. Is anything a blocker for writing the runner?

Severity: blocker means the runner must not be written until it's fixed.
