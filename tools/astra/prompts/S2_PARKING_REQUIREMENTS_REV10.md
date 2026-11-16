Revise slice S2's parking-gate requirements for owner decision OD-91. The stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-91 is new; OD-86 and OD-88 set fail-safe defaults), `docs/spec/AMBIGUITY_REGISTER.md`
(AMB-68, now decided), `docs/spec/REQUIREMENTS.md` (AIR-019, AIR-021 and their AT rows, from revision 9
`docs/spec/reviews/CLAUDE_REQUIREMENTS_s2_parking_gate_rev9_2026-11-15.md`) and `specs/README.md`.

Encode exactly, without adding owner policy:
- OD-91: if registering for the ACC broadcast fails at start, or returns no sticky state, the receiver treats the head
  unit as asleep and logs it: with the Park check on, nothing mirrors until the receiver restarts and registration
  works. Say what happens with the check off, consistent with AIR-021(b) and OD-83.

Draft only the rows that change, keeping their IDs, with their AT rows. Under OD-86, resolve any parsing, logging or
timing edge case with the fail-safe choice and say so; list under Open questions only what touches safety, scope, or
what the owner sees or hears.
