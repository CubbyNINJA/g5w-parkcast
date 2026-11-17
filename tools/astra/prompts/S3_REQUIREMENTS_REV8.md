Revise slice S3's first-milestone requirements for owner decisions OD-181 to OD-183. The stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-178 to OD-183), `docs/spec/REQUIREMENTS.md` (AIR-074, AIR-075, AIR-077, AIR-079 and
their AT rows) and `specs/README.md`.

Encode exactly: OD-181 (the log-copy script places an empty `owner-note.txt` and an empty six-key `run-checklist.txt`
in each new run folder; it never overwrites an existing one, fail-safe); OD-182 (keys confirmed; drop "drafted for
owner review"); OD-183 (the summary carries one `Checklist:` line with each key and `yes`, `no` or `not recorded`, in
addition to S1's fields). Replace only the affected rows whole. Keep the report short.
