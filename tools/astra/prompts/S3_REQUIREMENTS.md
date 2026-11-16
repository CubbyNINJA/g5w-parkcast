Draft slice S3's requirements: the AirPlay receiver running on the head unit, first milestone only. The stage root is
STAGE_ROOT.

Read first: `docs/spec/DECISIONS.md` (especially OD-76, OD-77, OD-92 to OD-99, OD-131 to OD-146; OD-138 renumbered the
slices), `docs/spec/AMBIGUITY_REGISTER.md`, `docs/spec/REQUIREMENTS.md` (S1's AIR-001 to AIR-015 for the GO/NO-GO and
evidence style, S2's AIR-016 to AIR-041 for the gate the receiver must use), `docs/design/S2_GATE.md` (the gate's
ports, which S3 implements on Android), `specs/README.md` (tags and evidence rules), and the evidence in
`docs/evidence/` (`S1_PC_FACTS.md`, `HUD_VIDEO_FINDINGS.md`, `COEXIST_PROBE_FINDINGS.md`, `SLEEP_SIGNAL_FINDINGS.md`,
`CAMERA_SIGNAL_FINDINGS.md`, `GEAR_PROBE_FINDINGS.md`).

Scope (OD-139): video only, judged GO/NO-GO from evidence on the USB stick, like S1. An iPhone on the unit's own Wi-Fi
hotspot (OD-146) starts Screen Mirroring to the receiver; the picture appears on the unit's screen through
`OMX.TCC.avcdec` (OD-96, OD-141), behind the S2 parking gate from the first build (OD-140). Audio is out of scope for
this milestone (OD-94 is the next one). The NEMESIS package is S4: say how S3's test builds get onto the unit and are
started without inventing S4's packaging.

Cover at least: building UxPlay's protocol library without GStreamer for API 19 armeabi-v7a with NDK r25c (OD-141,
OD-143) and its dependencies; the Android Gradle module and signing (OD-144, OD-145); advertising through the unit's
`mdnsd` on the fixed ports and never TCP 7000 or DiPO's ports (OD-76); the advertised display (OD-92) and mirroring
only (OD-93); the JNI boundary and MediaCodec decoding to the screen; the Android implementations of the S2 gate's
ports (Park getter, ACC and REVERSE_ON broadcasts, display, notices, log files, config, build info); what the receiver
logs on the stick for the verdict and what it must never record (phone screen contents, the iPhone's name, addresses
and identifiers, Wi-Fi details); the GO/NO-GO rule; and what happens on failure (no crash loop, nothing changed on the
unit). Number new rows from AIR-042 and AT-42.

Draft rows and AT rows with tiers (`@offline`, `@host`, `@unit`) under `specs/README.md`'s rules. Under OD-86 resolve
parsing, logging or timing edge cases with the fail-safe choice and say so; list under Open questions everything that
touches safety, scope, what the owner sees or hears, or facts only the unit can answer (a read-only `receiver-env-probe`
is already on the stick for the hotspot, `mdnsd` socket and codec list).
