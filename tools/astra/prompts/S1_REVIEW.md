Review slice S1's scenarios for coverage before any step definitions or code are written. The
stage root is STAGE_ROOT.

Under review:
- Requirements AIR-001 to AIR-015 and acceptance tests AT-01 to AT-15 in
  `docs/spec/REQUIREMENTS.md`. Note its "Wording changes" section: the parent added clauses
  encoding OD-17 to OD-21 after your revision 2. Check that wording too.
- The feature files `specs/features/01_build.feature` to `07_test_boundary.feature` (49 scenarios).
- Owner decisions OD-01 to OD-23 in `docs/spec/DECISIONS.md`, and the register in
  `docs/spec/AMBIGUITY_REGISTER.md`.
- Evidence: `docs/evidence/UXPLAY_LOG_SIGNATURES.md` holds the UxPlay 1.74 log format strings
  (commit 05b0275) that the analyser scenarios' log excerpts are built from, and
  `docs/evidence/S1_PC_FACTS.md`.

Nothing is implemented yet: `tests/steps/` is empty, and there's no S1 code under `tools/`.
Judge the scenarios as specifications of behaviour that will be implemented; where you would
check against code, check against the evidence files instead.

Answer these specifically, along with the role's usual checks:
1. Do the analyser log excerpts match the format strings in `UXPLAY_LOG_SIGNATURES.md`? Flag
   any excerpt that couldn't come from UxPlay 1.74.
2. Is any `@pc` scenario not decisive, or does any blur the offline and pc tiers?
3. Do the verdict scenarios cover every combination OD-12 and OD-17 to OD-19 define, and is any
   combination still unspecified?
4. Is the launch-flag list complete for AIR-006 and AIR-013 (no `-h265`, no `-vdmp`, `-admp`,
   `-vrtp` or `-artp`)?
