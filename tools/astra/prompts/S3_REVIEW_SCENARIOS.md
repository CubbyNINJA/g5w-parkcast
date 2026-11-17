Review slice S3's scenarios before any step definition is written. The stage root is STAGE_ROOT.

Context:
- Requirements: `docs/spec/REQUIREMENTS.md` AIR-042 to AIR-083 with AT-42 to AT-83 (S3 first milestone, revisions 1
  to 8 under `docs/spec/reviews/CLAUDE_REQUIREMENTS_s3_onunit_receiver*_2026-11-16.md`, plus the editorial clauses
  noted at the end of the file). Decisions OD-138 to OD-184 in `docs/spec/DECISIONS.md`. Evidence:
  `docs/evidence/RECEIVER_ENV_FINDINGS.md`, `CROSSBUILD_SPIKE_FINDINGS.md`, `CAMERA_SIGNAL_FINDINGS.md`,
  `GEAR_PROBE_FINDINGS.md`, `SLEEP_SIGNAL_FINDINGS.md`.
- Scenarios: `specs/features/13_receiver_runtime.feature` (Cucumber-JVM: the receiver's runtime as plain Java behind
  Android adapters with doubles), `14_receiver_tools.feature` (pytest-bdd: build checks, stick scripts, the PC
  evaluator), `15_receiver_unit.feature` (`@unit` evidence). OD-177 and `specs/java-features.txt` route them.
  `docs/spec/TRACEABILITY.md` is regenerated.
- No S3 step definitions or code exist; failing scenarios are expected, not a finding.

Check:
1. Does every AT row's `@offline`, `@host` and `@unit` criterion have a scenario that asserts it? Name each gap with
   the scenario to add.
2. Is any timing, byte or count arithmetic wrong (5,000 ms windows, 250 ms retries, the five ports, 64/65 and 256/257
   characters, the 600 s deadline, 1260 ms clears, letterbox geometry)? Check each Examples row.
3. Does any scenario assert more than the requirements and decisions say (invented policy)?
4. Are the steps implementable with oracles independent of the code under test, given the runner each file uses?
   Flag anything a plain-Java double or a pytest fake couldn't observe.
5. Rule on these points raised by the scenario writers (say which row and clause settles each, or that it needs the
   owner; for parsing, logging and timing edge cases propose the fail-safe choice under OD-86):
   (a) start-check order: hotspot check before key check, both run; (b) unfixed tokens: `port busy <proto> <port>`,
   `advertise failed` reason/code wording, the URL/HLS/DRM refusal token, the withheld-count token, exception classes
   for a malformed `/proc/net/tcp` and a getter timeout; (c) AT-50's missing EPERM / "otherwise unreachable" cases and
   "discovery is stopped" when it never started; (d) AIR-073(g) and header lines like `CSeq: 3`, and raw key bytes;
   (e) when the decoder is created; (f) AIR-074(d) keep-existing vs (g) existing-folder refusal; (g) timestamp format
   in the log for the evaluator's deadline, and which session UxPlay's own lines belong to; (h) the `<reason>`
   vocabulary for session end and stop; (i) where the `Checklist:` line sits among S1's summary fields and whether
   offline summaries carry it; (j) OD-179's mention of a shipped template, superseded by OD-181.

Severity: blocker means step definitions must not be written until it's fixed.
