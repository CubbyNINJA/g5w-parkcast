Revise slice S3's first-milestone requirements for owner decisions OD-178 and OD-179. The stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-154, OD-158, OD-162, OD-173, OD-178, OD-179), `docs/spec/REQUIREMENTS.md`
(AIR-042 to AIR-083, AT-42 to AT-83) and `specs/README.md`.

Encode exactly: OD-178's marker texts in AIR-071 (and AIR-075/076 where they name markers); OD-179's
`run-checklist.txt` (its place, its `key: yes/no` line format, its keys for each observation the AT rows ask the owner
for: ParkCast listed, letterboxing, sound, the receiver staying behind another app, nothing on the cluster or HUD,
CarPlay after exit, and the template shipped on the stick) in AIR-079(e) and every AT `@unit` criterion that says
"the owner's note" for something other than the picture, keeping `owner-note.txt` to its one line (AIR-076(e)). Say
how a missing or malformed checklist line is treated (fail-safe under OD-86). Replace only the affected rows whole.
Keep the report short.
