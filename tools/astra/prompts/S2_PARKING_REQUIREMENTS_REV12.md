Revise slice S2's parking-gate requirements for owner decision OD-103. The stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-103 is new; OD-45, OD-83, OD-86, OD-91, OD-100 to OD-102 bear on it),
`docs/spec/AMBIGUITY_REGISTER.md` (AMB-72, now decided), `docs/spec/REQUIREMENTS.md` (AIR-019, AIR-021, AIR-025,
AIR-026, AIR-039, AIR-040 and their AT rows, from revision 11
`docs/spec/reviews/CLAUDE_REQUIREMENTS_s2_parking_gate_rev11_2026-11-15.md`) and `specs/README.md`.

Encode exactly, without adding owner policy:
- OD-103: with the parking check off, (a) an ACC registration failure shows no on-screen notice; (b) it is not
  logged as a failure: one informational line records that registration was unavailable, with no failure
  wording (so not `ACC registration failed`), and later ACC broadcasts in that run write no `ignored` records;
  (c) every app start whose parking check is off writes a "parking check off" line at the start of its logging.
- Keep the check-on behaviour of AIR-019(h) and (i) unchanged. Keep AIR-040's logging-failure notice applying
  with either flag value. Say how the "parking check off" line relates to AIR-025's start record (a separate
  line or part of it, and its order), whether it uses the effective flag value including a fallback, and what
  happens if writing it fails.

Draft only the rows that change, keeping their IDs, with their AT rows. Under OD-86, resolve any parsing, logging or
timing edge case with the fail-safe choice and say so; list under Open questions only what touches safety, scope, or
what the owner sees or hears.
