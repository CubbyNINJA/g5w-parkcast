Revise slice S2's parking-gate requirements for owner decisions OD-122 to OD-127. The stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-122 to OD-127 are new; OD-86, OD-117 to OD-121 bear on them),
`docs/spec/AMBIGUITY_REGISTER.md` (AMB-82 to AMB-87, now decided), `docs/spec/REQUIREMENTS.md` (AIR-019, AIR-040,
AIR-041 and their AT rows, from revision 15
`docs/spec/reviews/CLAUDE_REQUIREMENTS_s2_parking_gate_rev15_2026-11-15.md`), `specs/README.md`, and
`docs/evidence/CAMERA_SIGNAL_FINDINGS.md`.

Encode exactly, without adding owner policy:
- OD-124 (safety): `com.hkmc.intent.action.REVERSE_ON` also makes the receiver covered: remove any notice at once and
  hold new ones from `REVERSE_ON` until the receiver's window regains focus. Say how a `REVERSE_ON` that arrives while
  the receiver already lacks focus, or with no later focus loss, is handled, and whether `REVERSE_OFF` plays any part
  (OD-124 doesn't use it). The receiver registers for this broadcast at runtime and unregisters at exit, like the ACC
  broadcast; say what happens if that registration fails.
- OD-125 (safety): a removal that throws while covered is retried every 1 s while covered; once uncovered, OD-119 applies.
- OD-122, OD-123, OD-126, OD-127 confirm revision 15's drafted behaviour or settle its open questions: cite them and
  make the rows match (OD-126: SVM, DRVM and non-focus overlays listed as unexercised in `@unit`; OD-127: the ship gate
  is public release only).

Draft only the rows that change, keeping their IDs, with their AT rows. Under OD-86, resolve any parsing, logging or
timing edge case with the fail-safe choice and say so; list under Open questions only what touches safety, scope, or
what the owner sees or hears.
