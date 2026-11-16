Revise slice S2's parking-gate requirements for owner decisions OD-117 to OD-120. The stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-117 to OD-120 are new; OD-86, OD-101, OD-106, OD-107, OD-111 and OD-112 to OD-116
bear on them), `docs/spec/AMBIGUITY_REGISTER.md` (AMB-79 to AMB-81, now decided), `docs/spec/REQUIREMENTS.md`
(AIR-019, AIR-040 and their AT rows, from revision 14
`docs/spec/reviews/CLAUDE_REQUIREMENTS_s2_parking_gate_rev14_2026-11-15.md`), `specs/README.md`, and the framework
reading in `docs/evidence/CAMERA_SIGNAL_FINDINGS.md`.

Encode exactly, without adding owner policy:
- OD-117 with OD-120 (safety): while the reversing camera or any stock window other than the receiver's is on top,
  no notice is shown and any notice on screen is removed at once. Detection is the receiver's own window focus
  (OD-120's proposal), and the reversing camera is covered by it only if `camera-probe` shows the camera takes
  focus; say what the requirement is if it doesn't (for example, an additional camera signal from the evidence
  file), without choosing that signal. State that the receiver does not ship until the probe evidence confirms the
  detection (an `@unit` acceptance criterion). Say how a hidden notice relates to its 5 s display, its allowance,
  its retry (OD-107) and the other notice, and what happens when focus returns.
- OD-118: two notices on screen together are stacked, the logging-failure notice just above the
  registration-failure notice, both shown at once (replacing revision 14's "positions not all equal").
- OD-119: a removal that fails is tried once more 5 s later, then the window stays until exit (revision 14's draft
  already says so; cite the decision).

Draft only the rows that change, keeping their IDs, with their AT rows. Under OD-86, resolve any parsing, logging or
timing edge case with the fail-safe choice and say so; list under Open questions only what touches safety, scope, or
what the owner sees or hears.
