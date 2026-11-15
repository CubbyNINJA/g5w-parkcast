Revise slice S1's requirements to encode the owner's new decisions. The stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-07 to OD-15 are new), `docs/spec/AMBIGUITY_REGISTER.md`
(AMB-11 to AMB-17 are now decided), `docs/spec/REQUIREMENTS.md` (the current S1 rows, AIR-001 to
AIR-014 and AT-01 to AT-14), and your earlier draft
`docs/spec/reviews/ASTRA_REQUIREMENTS_s1_pc_feasibility_2026-11-14.md`.

Encode each decision exactly:
- OD-08: the timeout is 10 minutes from receiver launch, and the owner can end the test earlier
  (AIR-007, AIR-011).
- OD-09: Screen Mirroring decides GO or NO-GO. A YouTube check is an extra observation only and
  never changes the verdict.
- OD-10: S1 records the logged audio format only. The owner notes whether sound played; that note
  never changes the verdict.
- OD-11: if the log lacks the iOS version, the runner asks the owner to type it in, and the record
  marks it "owner-entered" (AIR-012).
- OD-12: a verdict per session, and the run is GO if any complete session is GO, with every
  session's verdict listed (AIR-010).
- OD-13: an unreadable or incomplete log, or one that doesn't identify the codec, gives
  INCONCLUSIVE with the reason "evidence error" and the limitation named. It is never GO
  (AIR-009, AIR-010).
- OD-14: pin the upstream `master` commit at build time, recorded in the provenance (AIR-002), and
  the receiver name is "HU-Test" (AIR-006).
- OD-15: the owner's iPhone runs iOS 27. The record keeps the exact version.

Return:
1. `## Changed requirements`: the full replacement row for every AIR row that changes, and any new
   rows from AIR-015 up.
2. `## Changed acceptance tests`: the full replacement row for every AT row that changes, and any
   new rows from AT-15 up, with tiers.
3. `## Unchanged`: the IDs that don't change.
4. `## Open questions`: anything these decisions still leave open. Don't resolve it yourself.
