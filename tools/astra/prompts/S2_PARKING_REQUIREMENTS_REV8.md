Revise slice S2's parking-gate requirements for owner decisions OD-79 to OD-84. The stage root is
STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-79 to OD-84 are new), `docs/spec/AMBIGUITY_REGISTER.md` (AMB-56 to
AMB-61, now decided), `docs/spec/REQUIREMENTS.md` (the S2 rows, from revision 7
`docs/spec/reviews/CLAUDE_REQUIREMENTS_s2_parking_gate_rev7_2026-11-15.md`) and `specs/README.md`.

Encode exactly, without adding owner policy:
- OD-79 and OD-80: a line with whitespace around `=` is logged as "the unrecognised value" and counts
  as an occurrence of the key for duplicate checks; duplicate values are compared exactly and
  case-sensitively after whole-line trimming.
- OD-81: start records contain the fallback-cause phrases literally in a `reason=` field.
- OD-82: when a write would carry the current log past 2,621,440 bytes, the file rotates before the
  write; no file exceeds 2,621,440 bytes and no record is split.
- OD-83: with the check off, polling still stops at sleep and the gate blocks nothing.
- OD-84: the resume re-read is always a getter call started after resume.

The head-unit event that marks sleep and resume is still being identified; don't name one.
Draft only the rows that change, keeping their IDs, with their AT rows. List under Open questions
anything still left open.
