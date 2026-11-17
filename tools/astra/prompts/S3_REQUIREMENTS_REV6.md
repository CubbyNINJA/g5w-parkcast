Revise slice S3's first-milestone requirements for owner decisions OD-175 and OD-176. The stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-150, OD-161, OD-167, OD-174 to OD-176), `docs/spec/REQUIREMENTS.md` (AIR-048,
AIR-069, AIR-083 and AT-48, AT-69, AT-83, as revised in
`docs/spec/reviews/CLAUDE_REQUIREMENTS_s3_onunit_receiver_rev5_2026-11-16.md`) and `specs/README.md`.

Encode exactly: `hotspot address missing` (OD-175) and `identity key unavailable` (OD-176) become start-failure
causes on AIR-069(c)'s path, with the same notice form, held while covered, at most one per app start, and the text
"ParkCast can't start: <cause>. Exit and try again." with the logged marker as `<cause>` (OD-167). Update AIR-048(d),
AIR-069(c) and (d), AIR-083(c) and their AT rows; change nothing else. Under OD-86 resolve edge cases fail-safe and
say so. Keep the report short.
