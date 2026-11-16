# UxPlay's protocol library cross-built for Android 4.4 ARMv7 (spike, 2026-11-16)

A throwaway feasibility build outside the repository (`C:\Users\Gaming\parkcast-spike\`), not production code. For
S3 (OD-141, OD-143, OD-157). Nothing ran on the head unit.

## Result: feasible

`libairplay.so` (UxPlay `lib/` with `llhttp` and `playfair`, no GStreamer, no `renderers/`, no `uxplay.cpp`) compiles
with 0 errors and links with `-Wl,--no-undefined` against NDK r25c's API 19 stubs (clang 14.0.7,
`armv7-none-linux-androideabi19`). `NEEDED`: `libdl.so`, `libm.so`, `libc.so` only. `DT_HASH` present, no
`TEXTREL`, no OpenSSL or libplist symbols exported. Stripped size 2,899,760 bytes, mostly libcrypto (not yet trimmed).

| Source | Version | SHA-256 | Checksum check |
|---|---|---|---|
| OpenSSL | 3.0.22 | `67ebca7e50d17383028045486653492195b83db95f8558709701bb47b5c1ef81` | matches the published `.sha256`; GPG signature not checked |
| libplist | 2.8.1 | `2a1792e5820e1657c173735fde75baeebd6dd967d2ac47be1e3891ddc64ebefa` | upstream publishes none |
| UxPlay | 1.74, commit `05b0275` | `e9698b16b2be25d7cbf314e50f3c6f762de5886a6c7de093796fcec6b5305041` (GitHub archive) | tree compared with a git clone of the commit |

Building OpenSSL on Windows needed small stubs for MSYS Perl modules (`IPC::Cmd`, `Pod::Usage`) and an in-tree build.
libplist and UxPlay's library were built with hand-written CMake files using the NDK toolchain file.

## Patch

`lib/dns_sd/dns_sd.c:177`: `dlopen("libdns_sd.so")` becomes a configurable name, set to `libmdnssd.so`, the client
library this unit ships (`RECEIVER_ENV_FINDINGS.md`). The external `dns_sd` backend is used; UxPlay's embedded
responder (`lib/mdnsd`) does not build for API 19 (`getifaddrs`, API 24) and is not wanted (OD-76, OD-164).

## The library's interface (paths in UxPlay's source)

- Start: `raop_init`, log callback and level before `raop_init2(raop, nohold, device_id, keyfile)`, `raop_set_plist`
  (width, height, refreshRate, maxFPS), `raop_set_tcp_ports` / `raop_set_udp_ports`, `raop_start_httpd`,
  `raop_set_dnssd`, then `dnssd_register_raop` and `dnssd_register_airplay` (`uxplay.cpp:3184-3270`).
- Ports: `-p 35000` means TCP mirror 35000 and RTSP 35001 (TCP 35002 unused), UDP timing 35000, control 35001, audio
  data 35002 (`uxplay.cpp:1261-1292`, `raop.c:782-790`).
- Video: `video_process` receives Annex-B H.264 with SPS and PPS prepended to the next frame after each codec packet
  (no separate configuration callback; `raop_rtp_mirror.c:441-551, 726-770`). Geometry arrives in
  `video_set_codec_and_size` (`raop_rtp_mirror.c:589-711`); a negative return drops the connection.
- Audio arrives decrypted but compressed (ALAC, AAC or AAC-ELD) in `audio_process`; the host must still supply it.
- All of `video_process`, `video_set_codec_and_size`, `video_pause`, `video_resume`, `audio_process`, `conn_feedback`
  and `video_reset` are called without a NULL check.
- Logging: one callback `(cls, level, msg)`, syslog levels, default WARNING.

## Privacy and exposure

- At INFO the library logs local, remote and client IP addresses, the client's device ID, its User-Agent and the PIN;
  at DEBUG it logs full headers and plists (the client's name, device ID and model) and key material (`aesiv`,
  `ekey`, `aeskey`, `ecdh`). The receiver's log filter (AIR-073) has to drop or redact all of these.
- Some code prints straight to stdout or stderr, bypassing the log callback (`srp.c`, `utils.c`, the HLS path in
  `airplay_video.c`); the receiver must route those streams away from any log.
- The HTTP server binds every interface (`INADDR_ANY` and `in6addr_any`, `netutils.c:157, 183`).
- Without a key file, a new identity key is made at every start, so iPhones would see a new receiver each time.

## Not settled until it runs on the unit

Whether Android 4.4's loader accepts the library; runtime correctness of OpenSSL 3 and FairPlay (`playfair`) on
ARMv7, including an uninitialized-variable warning in `modified_md5.c:74`; CPU load and latency; whether `mdnsd`
accepts the `_raop._tcp` and `_airplay._tcp` registrations with their TXT records; and whether `OMX.TCC.avcdec` takes
the iPhone's H.264 profile and level.
