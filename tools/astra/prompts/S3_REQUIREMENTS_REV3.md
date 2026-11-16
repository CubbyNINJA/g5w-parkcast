Revise slice S3's first-milestone requirements for owner decisions OD-160 to OD-162. The stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-138 to OD-162; OD-160 corrects OD-149), `docs/spec/AMBIGUITY_REGISTER.md`
(AMB-108 to AMB-112 decided; AMB-95 to AMB-98 open for the receiver-env-probe), `docs/spec/REQUIREMENTS.md`
(AIR-042 to AIR-082, AT-42 to AT-82, as revised in
`docs/spec/reviews/CLAUDE_REQUIREMENTS_s3_onunit_receiver_rev2_2026-11-16.md`), `specs/README.md`, and
`C:\Users\Gaming\hmg_nemesis\AGENTS.md` sections 3, 4.2, 4.3 and 4.5 for NEMESIS's install route, `kind=apk` apps and
the 1.16 metadata limits (`os`, `models`, `short-desc` at most 64 characters, `detailed-desc` at most 256).

Encode exactly, without adding owner policy:
- OD-160: the stick install script runs `nemesis install --update` on a minimal NEMESIS zip with id `airplay.receiver`
  carrying only the APK as `[app.0] kind=apk`, declaring `os` and `models` and meeting the 1.16 limits, then
  `am start`; say how a failed `nemesis install` is detected and that nothing starts after it; how the log directory
  step (AIR-078(e)) fits; and how the owner removes it (`nemesis uninstall airplay.receiver`). It is not S4's package.
- OD-161: the start-failure notice text and its hold-while-covered behaviour; no notice for a mid-session decoder error.
- OD-162: the owner note's location, evaluator-only deadline, and `carplay present` as INCONCLUSIVE.
Replace each affected row whole, keeping its ID, with its AT row. Under OD-86 resolve parsing, logging or timing
edge cases fail-safe and say so; list under Open questions only safety, scope, or what the owner sees or hears.
Keep the report concise.
