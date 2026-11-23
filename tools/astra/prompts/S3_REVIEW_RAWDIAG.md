Review the scenarios for slice S3 revision 10, the diagnostic raw log, before any step definition is written. The
stage root is STAGE_ROOT.

Context:
- Requirements: `docs/spec/REQUIREMENTS.md` AIR-084 to AIR-089 with AT-84 to AT-89, and the raw-file clauses added to
  AIR-055(d), AIR-062(a), AIR-072(c), AIR-073(h) and AIR-083(d), (e) and to their AT rows. The fold-in note at the end
  of the file says what changed from the draft `docs/spec/reviews/ASTRA_REQUIREMENTS_s3_onunit_receiver_rev10_2026-11-23.md`
  and why. Decisions OD-198 to OD-203 in `docs/spec/DECISIONS.md`. Evidence: `docs/evidence/S3_FIRST_RUN_FINDINGS.md`.
  Native sources under `evidence/` (`ring.c`, `host.c`, `parkcast.h`), and UxPlay's `raop_handlers.h` and `utils.c`,
  which show how UxPlay logs key material.
- Scenarios: `specs/features/16_receiver_diagnostics.feature` (pytest-bdd). `@host` scenarios compile the native
  raw-file module, log path and ring for the PC with MSYS2's gcc and drive them with a harness, a fake clock and a
  fake file layer. `@offline` scenarios run the stick script against fake toolbox commands, as file 14 does.
  `docs/spec/TRACEABILITY.md` is regenerated.
- No step definitions or code for this revision exist; failing scenarios are expected, not a finding.

Check:
1. Does every AT-84 to AT-89 criterion, and each raw-file clause added to AT-55, AT-62, AT-72, AT-73 and AT-83, have a
   scenario that asserts it? Name each gap with the scenario to add.
2. Is any byte, count or timing arithmetic wrong: the 32-hex, 44-base64 and 8-spaced-byte thresholds and their
   one-below boundaries in the Examples; 16,777,216, 16,776,704 and the 512-byte reserve; 65,536 and 65,537; the
   1,000 ms counter rule with records 400 ms apart; the counts in the counter scenarios?
3. Does any scenario assert more than the requirements and decisions say?
4. Are the steps implementable with oracles independent of the code under test? In particular: can a harness built
   with host gcc observe "written in the callback, before the ring", a file's mode, a short write and "returns without
   blocking"; can a fake toolbox observe "no unfiltered logcat file ever existed on the stick"?
5. Does the redaction in AIR-085(c) actually cover what UxPlay logs at debug level (`raop_handlers.h` byte dumps,
   `client_epk`, `ecdh_secret`, plist XML), and is anything it logs still a key in clear? Say which line.
6. Is the keep check's Examples table consistent with AIR-084(d)'s "naming which" when two conditions hold at once?

Severity: blocker means step definitions must not be written until it's fixed.
