Revise slice S2's parking-gate requirements (AIR-016 to AIR-027, AT-16 to AT-27) for the owner's
answers to their open questions. The stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-23 and OD-24 set the gate; OD-43 to OD-49 are new),
`docs/spec/AMBIGUITY_REGISTER.md` (AMB-24 to AMB-29 are now decided), `docs/spec/REQUIREMENTS.md`
(the current S2 rows, with AIR-023 conditional on AMB-24), your earlier draft
`docs/spec/reviews/ASTRA_REQUIREMENTS_s2_parking_gate_2026-11-14.md`, and `specs/README.md`
(tiers).

Encode exactly, without adding owner policy:
- OD-43 and OD-44: the flag's file, key, values and fallback. A change takes effect at the next
  app start. An update or reinstall restores the shipped file, so the check is on again.
- OD-45: with the check off, nothing is shown on screen; the setting is logged at every start.
- OD-46: image gone within 250 ms of a gear change reaching the receiver; a reading older than
  2 s is unavailable; re-read on every resume.
- OD-47: supported builds are the `CVEV.*` family in any region, with the 260206 gear mapping
  assumed across it; outside it, video stays blocked while the check is on. The stock regulation
  state is logged but neither blocks nor allows video.
- OD-48: video returns by itself on the next frame once Park is confirmed again.
- OD-49: the log location, the 5 MB cap with the oldest dropped, and a logging failure that
  neither blocks nor allows video and is reported once on screen.

Draft the changed rows (AIR-016 to AIR-027 and AT-16 to AT-27), plus any new rows at the next
free IDs after AIR-034 and AT-34. Mark AIR-023 as decided, not conditional. List under Open
questions anything these decisions still leave open. Two to consider: which Android property
holds the `CVEV...` build string (the gear-probe on the stick will record the build properties),
and how the receiver reads gear (by stock manager method name or by binder transaction number).
