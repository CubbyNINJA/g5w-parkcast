Revise slice S2's parking-gate requirements for owner decisions OD-85 and OD-87. The stage root is
STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-85, OD-86 and OD-87 are new), `docs/spec/AMBIGUITY_REGISTER.md` (AMB-62
to AMB-67, now decided), `docs/spec/REQUIREMENTS.md` (the S2 rows, from revision 8
`docs/spec/reviews/CLAUDE_REQUIREMENTS_s2_parking_gate_rev8_2026-11-15.md`), the evidence
`docs/evidence/SLEEP_SIGNAL_FINDINGS.md`, and `specs/README.md`.

Encode exactly, without adding owner policy:
- OD-85: spacing on either side of `=` counts and spaced lines compare exactly; an explicit read logs
  `reason=read from the file`; a record over 2,621,440 bytes is dropped and a `record too large` line naming
  its size is logged instead; a pre-sleep call's reply after resume, or any reply during sleep with either flag
  value, is logged as discarded and never refreshes age; the resume re-read starts at once unless a pre-sleep
  call is still pending, then waits for it, with video blocked.
- OD-87: sleep and resume come from the sticky broadcast `com.hkmc.intent.action.ACTION_ACC_STATUS_CHANGED`
  (`EXTRA_ACC_STATUS`), registered at runtime. Awake: 0, 2 (ADM) and 5. Asleep: 6, 7 and 255. State 1 and any
  other value count as asleep. The receiver's own process start counts as a resume.

Say how an offline test with a synthetic sticky broadcast and controlled clock asserts the ACC mapping,
including ADM staying awake. Draft only the rows that change, keeping their IDs, with their AT rows. Under
OD-86, resolve any remaining parsing, logging or timing edge case with the fail-safe choice and say so in the
rationale; list under Open questions only what touches safety, scope, or what the owner sees or hears.
