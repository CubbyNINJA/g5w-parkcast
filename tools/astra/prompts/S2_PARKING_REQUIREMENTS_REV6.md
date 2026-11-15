Revise slice S2's parking-gate requirements for owner decisions OD-63 and OD-64. The stage root is
STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-63 and OD-64 are new; OD-61 and OD-62 set polling and the timeout),
`docs/spec/AMBIGUITY_REGISTER.md` (AMB-42 and AMB-43, now decided), `docs/spec/REQUIREMENTS.md` (AIR-017,
AIR-019 and their AT rows, from your revision 5 draft
`docs/spec/reviews/ASTRA_REQUIREMENTS_s2_parking_gate_rev5_2026-11-15.md`) and `specs/README.md`.

Encode exactly, without adding owner policy:
- OD-63: after a late call returns, the next poll starts immediately, then every 200 ms from that call.
- OD-64: a reply arriving at exactly the 200 ms timeout counts as late; the timeout wins.

Update the controlled-clock acceptance criteria so they assert both, replacing any wording that
avoided them while they were undecided. Draft only the rows that change, keeping their IDs. List
under Open questions anything still open.
