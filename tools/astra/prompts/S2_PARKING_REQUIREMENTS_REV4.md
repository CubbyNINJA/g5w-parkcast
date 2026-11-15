Revise slice S2's parking-gate requirements for owner decision OD-61. The stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-61 is new; OD-46 and OD-56 set freshness and resume),
`docs/spec/AMBIGUITY_REGISTER.md` (AMB-33, now decided by OD-61), `docs/spec/REQUIREMENTS.md`
(the S2 rows AIR-016 to AIR-027 and AIR-035 to AIR-040, with their AT rows), your revision 3 draft
`docs/spec/reviews/ASTRA_REQUIREMENTS_s2_parking_gate_rev3_2026-11-15.md`, the evidence
`docs/evidence/GEAR_PROBE_FINDINGS.md` (including the park-probe results), and `specs/README.md`.

Encode exactly, without adding owner policy:
- OD-61: the receiver reads Park by polling `VehicleInfoManager.getGearPStatus()` (system service
  `com.hkmc.micom.system`) every 200 ms, with no listener. 1 is Park, 0 is not Park; any other
  value or a failed call is unavailable. Each poll is a fresh reading for OD-46 and OD-56.
- OD-61's stated consequence: a change reaches the receiver when a poll returns it, so OD-46's
  250 ms counts from that poll, and the image is gone within about 450 ms of the actual shift.
  Say how an offline test with a controlled clock can assert both bounds.
- The startup read and the resume re-read (OD-56) use the same getter.
- The read stays read-only (AIR-024): the getter returns the service's cached state.

Draft only the rows that change (AIR-017, AIR-019 and any others OD-61 touches, with their AT
rows), keeping their IDs; add new rows at the next free IDs after AIR-040 and AT-40 only if
needed. Under Open questions, list anything OD-61 still leaves open. One known: whether SELinux
limits these calls from an ordinary app is unverified, because the probes ran as root.
