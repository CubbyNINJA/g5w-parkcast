Third coverage review of slice S3 revision 10, the diagnostic raw log, before any step definition is written. The stage
root is STAGE_ROOT.

Context:
- Two reviews came first: `docs/spec/reviews/ASTRA_REVIEW_s3_rawdiag_scenarios_2026-11-23.md` (14 issues) and
  `SOL_REVIEW_s3_rawdiag_scenarios_2_2026-11-23.md` (9 issues); each "Applied" section says how they were folded in, including where a suggestion was adopted in another form and why. The rulings are OD-204 in `docs/spec/DECISIONS.md` (with OD-198 to OD-203).
- Requirements: `docs/spec/REQUIREMENTS.md` AIR-084 to AIR-089 with AT-84 to AT-89, and the raw-file clauses in AIR-055(d),
  AIR-062(a), AIR-072(c), AIR-073(h), AIR-083(d), (e) and their AT rows.
- Scenarios: `specs/features/16_receiver_diagnostics.feature` (pytest-bdd; `@host` compiles the native raw-file module,
  log path and ring for the PC with MSYS2's gcc; `@offline` runs the stick script against journalling fake toolbox
  commands). Native sources and UxPlay's `raop_handlers.h` and `utils.c` are under `evidence/`.
- No step definitions or code for this revision exist; failing scenarios are expected, not a finding.

Check:
1. Was each of the second review's 9 findings actually fixed, and are the two "another form" choices (the static logcat check, no run classifier) sound? Name any that wasn't, or was fixed in a way that creates a new problem.
2. Does every AT-84 to AT-89 criterion, and each raw-file clause in AT-55, AT-62, AT-72, AT-73 and AT-83, now have a
   scenario that asserts it? Name each remaining gap.
3. Is the arithmetic in every Examples row right, including the counter Outline's seven values per row, the 64/72 slot
   counts, and the redaction outputs (exact bytes, separators kept after a byte dump)?
4. Does any scenario assert more than the requirements and decisions say, or rely on an oracle the code under test
   provides?
5. Is anything UxPlay logs at debug level still a key in clear under AIR-085(c) as now worded? Name the line.

Severity: blocker means step definitions must not be written until it's fixed. Keep the report short.
