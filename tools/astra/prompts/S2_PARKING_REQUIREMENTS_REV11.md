Revise slice S2's parking-gate requirements for owner decision OD-101. The stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-101 is new; OD-86, OD-88, OD-91 and OD-100 set the registration-failure
behaviour), `docs/spec/AMBIGUITY_REGISTER.md` (AMB-70, now decided), `docs/spec/REQUIREMENTS.md` (AIR-019, AIR-021,
AIR-040 and their AT rows, from revision 10 `docs/spec/reviews/CLAUDE_REQUIREMENTS_s2_parking_gate_rev10_2026-11-15.md`)
and `specs/README.md`.

Encode exactly, without adding owner policy:
- OD-101: with the parking check on, an ACC registration failure (AIR-019(h)) is also reported on screen, at most
  once per app start. Keep it consistent with AIR-040's once-per-app-start logging-failure notice, and say how the two
  notices relate (separate allowances or shared). Say what happens with the check off without inventing a notice
  the owner did not ask for; if that is the owner's call, list it as an open question.

Draft only the rows that change, keeping their IDs, with their AT rows. Under OD-86, resolve any parsing, logging or
timing edge case with the fail-safe choice and say so; list under Open questions only what touches safety, scope, or
what the owner sees or hears.
