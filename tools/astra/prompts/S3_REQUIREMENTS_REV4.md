Revise slice S3's first-milestone requirements for owner decisions OD-164 to OD-166. The stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-138 to OD-167), `docs/spec/AMBIGUITY_REGISTER.md`, `docs/spec/REQUIREMENTS.md`
(AIR-042 to AIR-082, AT-42 to AT-82, as revised through
`docs/spec/reviews/CLAUDE_REQUIREMENTS_s3_onunit_receiver_rev3_2026-11-16.md`), `specs/README.md`, and the new
evidence `docs/evidence/RECEIVER_ENV_FINDINGS.md` (`mdnsd` not running with CarPlay off; only netd's
`/dev/socket/mdns`, `root system 0660`; `libmdnssd.so` present; `OMX.TCC.avcdec` listed).

Encode exactly, without adding owner policy:
- OD-164: advertising wakes `mdnsd` through the public `NsdManager` only, then registers the AirPlay services with
  their TXT records over `mdnsd`'s own socket with the `dns_sd` client protocol; no root; no system setting changed.
  Say which `NsdManager` call wakes it so that no spurious or TXT-less service is ever advertised under ParkCast or any
  other name, how the receiver learns `mdnsd` is up, what counts as `advertise failed` (including the socket being
  unreachable or permission-denied), and that the `NsdManager` use is released at stop. AIR-049(c) and AIR-050 change
  accordingly. The socket's accessibility is checked in the first on-unit run (`@unit`).
- OD-165: the test zip's `os` and `models` name the owner's unit (build 260206 CA, `CVEV.CAN.0000.V015.011.260206`),
  not `any`; state the exact values and how they're checked offline against NEMESIS 1.16's limits.
- OD-166: `carplay listener unknown` or no `carplay listener` record counts as absent in the verdict (AIR-076(g)).
Replace each affected row whole, keeping its ID, with its AT row. Under OD-86 resolve parsing, logging or timing
edge cases fail-safe and say so; list under Open questions only safety, scope, or what the owner sees or hears.
Keep the report concise.
