Revise slice S2's parking-gate requirements for owner decision OD-62. The stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-62 is new; OD-61 sets 200 ms polling), `docs/spec/AMBIGUITY_REGISTER.md`
(AMB-41, now decided), `docs/spec/REQUIREMENTS.md` (AIR-017, AIR-019 and their AT rows, from your
revision 4 draft `docs/spec/reviews/ASTRA_REQUIREMENTS_s2_parking_gate_rev4_2026-11-15.md`) and
`specs/README.md`.

Encode exactly, without adding owner policy:
- OD-62: a `getGearPStatus()` call that hasn't returned within 200 ms counts as unavailable, which
  blocks video while the parking check is on.
- Only one call runs at a time; the next poll waits for a pending call.

Say how an offline test with a controlled clock and a synthetic getter that can delay or hang its
reply asserts both, including a reply that arrives after the 200 ms timeout. Draft only the rows
that change, keeping their IDs. List under Open questions anything OD-62 still leaves open.
