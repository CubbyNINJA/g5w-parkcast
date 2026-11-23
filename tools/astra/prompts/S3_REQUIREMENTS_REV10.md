Revise slice S3's first-milestone requirements for OD-198. The stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-139, OD-170, OD-178, OD-185, OD-190 and OD-198), `docs/spec/REQUIREMENTS.md`
(AIR-042 to AIR-083, AT-42 to AT-83), `docs/evidence/S3_FIRST_RUN_FINDINGS.md`, `docs/design/S3_RECEIVER.md`
("JNI contract", "Native build", "Open on-unit risks"), `tools/unit-probes/parkcast-diag/` and, under `evidence/`,
the native `ring.c` and `host.c`.

The first on-unit run connected but no video reached Java, and every UxPlay line was withheld, so the cause can't be
seen. OD-198 adds a diagnostic setting to the S3 test build. Draft the requirement and acceptance-test rows for it:

- What the setting writes: UxPlay's raw log records, written from native code as they arrive (not through the
  ring, so ring pressure can't hide them), to one file in the app's own data folder; and counters, at least: access
  units received from UxPlay, access units and log records dropped by the ring, control events dropped, and
  codec-configuration events sent. Say when the counters are written and where.
- How the setting is switched on and off at build time, how a build with it on is told apart from one without it
  (for example in the start marker and the APK's version name), and what makes it "off in any build the owner keeps".
- What stays forbidden even with it on. The identity key file's path, name and contents (AIR-083(d), (e)) and
  video frames (AIR-073(a)) are the obvious candidates; say whether anything else from AIR-073(b) still applies to
  the raw file, given OD-198 accepts that it can hold the iPhone's name and addresses.
- Size limits and rotation for the raw file, what happens if it can't be opened or written (it must never block or
  allow video, and must never stop the normal log), and that the normal log and its filter (AIR-071 to AIR-073) are
  unchanged.
- The `parkcast-diag` stick tool copying the raw file to the stick, read-only on the unit, and the PC side keeping
  it under `runs/` only. Name the AIR rows whose wording must change, such as AIR-055(d), AIR-072(b) and AIR-083(d),
  and replace those rows whole.

Number new rows from AIR-084 and AT-84. Mark any choice you make that OD-198 doesn't settle as a proposal, and list
it as an open question for the owner. Keep the report short.
