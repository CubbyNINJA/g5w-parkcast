Revise slice S2's parking-gate requirements for owner decisions OD-112 to OD-115. The stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-112 to OD-115 are new; OD-86, OD-101, OD-104, OD-106, OD-107 and OD-111 bear on
them), `docs/spec/AMBIGUITY_REGISTER.md` (AMB-75 to AMB-78, now decided), `docs/spec/REQUIREMENTS.md` (AIR-019,
AIR-040 and their AT rows, from revision 13
`docs/spec/reviews/CLAUDE_REQUIREMENTS_s2_parking_gate_rev13_2026-11-15.md`) and `specs/README.md`.

Encode exactly, without adding owner policy:
- OD-112: the logging-failure notice's exact text.
- OD-113: a retry due while asleep still fires (revision 13's draft already says so; cite the decision).
- OD-114: each notice's first attempt is at the moment its failure is detected (cite the decision; revision 13's
  timing choice becomes owner policy).
- OD-115: both notices are shown for exactly 5 s as a toast-style window (a toast-type window, needing no extra
  permission on Android 4.4) and removed after 5 s. This replaces revision 13's `Toast.LENGTH_SHORT` text toast
  and its ban on overlays. Say how an offline test double observes the form, the 5 s removal, and a failure to
  show; how the 5 s display relates to the 5 s retry; what happens if the window cannot be removed, if both
  notices are on screen at once, and at receiver exit while a notice is on screen.

Draft only the rows that change, keeping their IDs, with their AT rows. Under OD-86, resolve any parsing, logging or
timing edge case with the fail-safe choice and say so; list under Open questions only what touches safety, scope, or
what the owner sees or hears.
