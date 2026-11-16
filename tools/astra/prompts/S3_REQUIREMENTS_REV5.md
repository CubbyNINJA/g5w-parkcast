Revise slice S3's first-milestone requirements for owner decisions OD-168 to OD-173 and the cross-build spike. The
stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-138 to OD-173), `docs/spec/AMBIGUITY_REGISTER.md`, `docs/spec/REQUIREMENTS.md`
(AIR-042 to AIR-082, AT-42 to AT-82, as revised through
`docs/spec/reviews/CLAUDE_REQUIREMENTS_s3_onunit_receiver_rev4_2026-11-16.md`), `specs/README.md`, and the evidence
`docs/evidence/CROSSBUILD_SPIKE_FINDINGS.md` and `docs/evidence/RECEIVER_ENV_FINDINGS.md`.

Encode exactly, without adding owner policy:
- OD-168: OpenSSL 3.5 LTS (not 3.0); libplist 2.x stands.
- OD-169: listen only on the hotspot address 192.168.0.1 (`wlan0`), never every interface, via a kept UxPlay patch;
  say what happens if that address isn't present at start.
- OD-170: the identity key file in the app's own data folder; never logged or copied to the stick; removed on
  uninstall.
- OD-172: `os=CVEV.CAN.0000.V015.011.260206` and `models=cvev*` in the test zip (replacing rev 4's `models` value).
- OD-173: the first on-unit run's checklist includes exiting ParkCast and confirming wireless CarPlay still connects.
- The spike's facts where they change rows: the `dlopen` name patch to `libmdnssd.so` (AIR-042(c)); the library logs
  local, remote and client IPs, the client's device ID, User-Agent and PIN at INFO, and headers, plists and key
  material at DEBUG, so the AIR-072/AIR-073 log level and filter must keep all of that out of every written record;
  some library code prints to stdout and stderr directly, which must never reach a log or the stick; TCP 35002 is
  unused by the library (reconcile AIR-048's "exactly TCP and UDP 35000-35002"); the host must supply every callback
  the library calls without a NULL check.
Replace each affected row whole, keeping its ID, with its AT row. Under OD-86 resolve parsing, logging or timing
edge cases fail-safe and say so; list under Open questions only safety, scope, or what the owner sees or hears.
Keep the report concise.
