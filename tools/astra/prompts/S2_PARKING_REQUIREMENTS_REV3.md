Revise slice S2's parking-gate requirements for the owner's answers to the open questions of your
revision 2 draft. The stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-51 and OD-54 to OD-60 are new), `docs/spec/AMBIGUITY_REGISTER.md`
(AMB-32 and AMB-34 to AMB-40 are now decided; AMB-33 is partly answered), `docs/spec/REQUIREMENTS.md`
(the S2 rows AIR-016 to AIR-027 and AIR-035 to AIR-040, with AT-16 to AT-27 and AT-35 to AT-40),
your draft `docs/spec/reviews/ASTRA_REQUIREMENTS_s2_parking_gate_rev2_2026-11-15.md`, the gear-probe
results `docs/evidence/GEAR_PROBE_FINDINGS.md`, and `specs/README.md`.

Encode exactly, without adding owner policy:
- OD-51: the supported family (OD-47) is identified from `ro.build.product`.
- OD-54: a package deliberately rebuilt with `video.require_park=false` starts with the check off;
  a normal update or reinstall of a package shipping `true` turns it back on.
- OD-55: strict parsing of `receiver.conf`: trimmed whitespace, lowercase `true`/`false` only, and
  a key duplicated with conflicting values counts as unrecognised (check on).
- OD-56: a reading's age counts from receiver receipt; after a resume, video stays blocked until
  the fresh re-read confirms Park, and a pre-sleep Park reading never allows video.
- OD-57: the log cap is 5,242,880 bytes across all receiver logs, two rotated files, oldest whole
  file deleted.
- OD-58: the logging-failure notice is shown at most once per app start.
- OD-59: regulation state is logged at app start and on every change; unavailable is logged as
  `unavailable` with the reason.
- OD-60: a failed update or reinstall needs nothing extra; a missing or unreadable file already
  means the check is on.

The gear-probe also changes the evidence behind AIR-017 and AIR-019: the stock BCM gear "getters"
return no value, and Park arrives as stock notifications (`onGearPStatus` 1/0, `onGearStatus` 0/255).
Reflect that only as far as the decisions and evidence support it: AMB-33 (which interface, which
permission) is still open, so don't name an interface as decided.

Draft the changed rows only, keeping their IDs, plus any new rows at the next free IDs after
AIR-040 and AT-40. List under Open questions anything still left open.
