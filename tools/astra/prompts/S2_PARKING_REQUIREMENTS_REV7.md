Revise slice S2's parking-gate requirements for owner decisions OD-67 to OD-75. The stage root is
STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-67 to OD-75 are new), `docs/spec/AMBIGUITY_REGISTER.md` (AMB-47 to
AMB-54, now decided), `docs/spec/REQUIREMENTS.md` (the S2 rows AIR-016 to AIR-027 and AIR-035 to
AIR-040 with their AT rows), the review that raised them
`docs/spec/reviews/CLAUDE_REVIEW_s2_scenarios_2026-11-15.md` (Requirement wording 1 to 8), and
`specs/README.md`.

Encode exactly, without adding owner policy:
- OD-67: AIR-017(d)'s no-value status request is the BCM `getGearStatusInfo` request; an empty BCM
  gear-status reply never establishes Park.
- OD-68 and OD-69: `receiver.conf` trimming is of the whole line only (`video.require_park = false` is
  unrecognised); a repeat of the key with any different value, recognised or not, conflicts; an
  identical duplicate is accepted.
- OD-70: drop AT-35's "a partial installation is not reported as successful replacement".
- OD-71: the previous flag value is always logged as unknown across starts (nothing persisted); the
  first gate change after start is a logged transition.
- OD-72: "stale" and "unavailable" are one state, called "unavailable".
- OD-73: the current log file rotates at 2,621,440 bytes and the older file is deleted, so the two
  never exceed 5,242,880 bytes together and nothing is trimmed.
- OD-74: polling stops when the head unit sleeps, video is blocked from then on, a reply arriving
  during sleep is discarded, and on resume the AIR-019(d) re-read rules apply.
- OD-75: start records name a fallback cause as "the missing file", "the unreadable file", "the
  missing key", "the conflicting duplicate key" or "the unrecognised value".

Draft only the rows that change, keeping their IDs, with their AT rows. List under Open questions
anything still left open.
