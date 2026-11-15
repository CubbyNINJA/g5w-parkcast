Revise slice S1's requirements for two owner decisions made after the live test. The stage root is
STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-52 and OD-53 are new), `docs/spec/AMBIGUITY_REGISTER.md`
(AMB-30 and AMB-31, now decided), `docs/spec/REQUIREMENTS.md` (AIR-007, AIR-031, AIR-033, AT-07,
AT-31, AT-33), `docs/spec/reviews/ASTRA_REVIEW_s1_od50_2026-11-15.md` (findings 4 and 5, which
raised them), `specs/README.md` and `docs/spec/S1_INTERFACES.md`.

Encode exactly, without adding owner policy:
- OD-52: the runner must stop UxPlay within 1 s of the owner pressing Enter. The acceptance test
  ends the test between two of the runner's checks. Say where the 1 s starts and ends in terms an
  offline controlled clock can assert.
- OD-53: adopt the AIR-033(e) wording proposed in the review: if the run record can't be written,
  `s1.py run` reports the failure on stderr and returns before analysis, evaluation and evidence.
  It exits 1, or 130 if interrupted, and this takes precedence over AIR-031(g)'s exit codes for
  otherwise completed runs.

Draft the changed rows (AIR-007, AIR-033, and AIR-031 if its exit-code clause needs a pointer to
the new precedence) and the matching acceptance tests (AT-07, AT-31, AT-33). Keep the IDs. List
under Open questions anything these decisions still leave open.
