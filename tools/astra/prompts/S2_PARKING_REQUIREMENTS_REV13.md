Revise slice S2's parking-gate requirements for owner decisions OD-106 and OD-107. The stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-106 to OD-109 are new; OD-49, OD-58, OD-86, OD-101 to OD-105 bear on them),
`docs/spec/AMBIGUITY_REGISTER.md` (AMB-71, AMB-73 and AMB-74, now decided), `docs/spec/REQUIREMENTS.md` (AIR-019,
AIR-021, AIR-040 and their AT rows, from revision 12
`docs/spec/reviews/CLAUDE_REQUIREMENTS_s2_parking_gate_rev12_2026-11-15.md`) and `specs/README.md`.

Encode exactly, without adding owner policy:
- OD-106: the registration-failure notice is a short toast in the same form and for the same duration as the
  logging-failure notice, with the owner's exact text. The logging-failure notice's own text is not decided; if a
  requirement now needs it, list that as an open question rather than inventing it.
- OD-107: a notice that fails to show is tried once more 5 s later in that run, then given up, within its
  once-per-start allowance. Say what "fails to show" means in a way an offline test double can observe, and how the
  retry interacts with the other notice's allowance, sleep, and receiver exit.
- OD-108 defers the early-start case; OD-91 stands. Note it in the rows' decision column only; no behaviour change.

Draft only the rows that change, keeping their IDs, with their AT rows. Under OD-86, resolve any parsing, logging or
timing edge case with the fail-safe choice and say so; list under Open questions only what touches safety, scope, or
what the owner sees or hears.
