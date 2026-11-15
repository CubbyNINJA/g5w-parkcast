# UxPlay log messages that slice S1's analyser reads

Read from UxPlay's source at upstream `master`, commit
`05b0275cb1b726592700e47a6e0307544b3d3a52` (2026-11-06, `VERSION "1.74"`), on 2026-11-14.
These are the format strings in the code. The exact text of the values (such as the codec name)
is confirmed only by a real run. Line numbers are at that commit.

| What it tells the analyser | Format string | Level | Source |
|---|---|---|---|
| An iPhone asked to connect, with its model and name | `connection request from %s (%s) with deviceID = %s` (name, model, deviceID) | INFO | `uxplay.cpp:2767` (`report_client_request`) |
| The client's AirPlay software build | `Client identified as User-Agent: %s` | INFO | `lib/raop_handlers.h:854` |
| A mirroring stream was set up | `Mirroring initialized successfully` | DEBUG | `lib/raop_handlers.h:1004` |
| The iPhone opened the mirror data connection (before any video) | `raop_rtp_mirror accepting client` | DEBUG | `lib/raop_rtp_mirror.c:243` |
| Mirroring setup failed | `Mirroring not initialized at SETUP, playing will fail!` | ERR | `lib/raop_handlers.h:1006` |
| **Video codec chosen, with the stream geometry** | `*** codec is %s: geometry is (w,h)=(%d,%d) (source); (x,y)=(%d,%d); (w,h)=(%d,%d)` | INFO | `renderers/video_renderer.c:1077` |
| **The iPhone sent HEVC, but HEVC wasn't offered** | One message on three lines: `raop_rtp_mirror: received type 0x01 packet with no payload:` / `this indicates non-h264 video but Airplay features bit 42 (SupportsScreenMultiCodec) is not set` / `use startup option "-h265" to set this bit and support h265 (4K) video` | ERR | `lib/raop_rtp_mirror.c:608` |
| HEVC arrived without `-h265` (renderer side) | `video is h265 but the -h265 option was not used` | ERR | `renderers/video_renderer.c:1084` |
| **The first video packet arrived** (codec configuration), with source and target geometry | `raop_rtp_mirror width_source = %f height_source = %f x_offset = %f y_offset = %f width = %f height = %f` | DEBUG | `lib/raop_rtp_mirror.c:603` |
| Audio stream format | `ct=%d spf=%d usingScreen=%d isMedia=%d  audioFormat=0x%lx` | INFO | `uxplay.cpp:2938` (`audio_get_format`) |
| The video stream stopped | `This packet indicates video stream is stopping` | DEBUG | `lib/raop_rtp_mirror.c:576` |
| The mirroring socket closed | `raop_rtp_mirror tcp socket was closed by client (recv returned 0)` | ERR | `lib/raop_rtp_mirror.c:383` |
| The connection was lost | `*** ERROR lost connection with client (network problem?)` | INFO | `uxplay.cpp:2749` |
| Advertised features | `register_dnssd: advertised AirPlay service with "Features" code = 0x%llX` | DEBUG | `uxplay.cpp:2428` |
| Version banner | `UxPlay %s: An Open-Source AirPlay mirroring and audio-streaming server.` | INFO | `uxplay.cpp:3488` |

## What this means for slice S1

- **Run UxPlay with `-d` (debug).** Several signals the analyser needs are DEBUG-only: the
  mirroring setup, the geometry and the advertised features.
- **The log doesn't give the iOS version.** The User-Agent is the AirPlay build
  (`AirPlay/<build>`), and the request carries the device *model*, not the OS version. So OD-11
  applies: the owner types in the iOS version, and the record marks it owner-entered.
- **The log does name the client.** `connection request from` includes the iPhone's name and
  deviceID, which is private (AIR-012), so it stays in `runs/` and is redacted before anything
  is committed.
- **GO signal:** `*** codec is h264` after a connection request. **NO-GO signal:** the
  `-h265` ERR lines. The codec value is a string literal, `video_is_h265 ? "h265" : "h264"`
  (`renderers/video_renderer.c:1078`), so `h264` and `h265` are the only spellings. A JPEG
  stream logs no codec line.
- **Arrival, setup and start are different lines (AIR-029).** `Mirroring initialized
  successfully` is setup. `raop_rtp_mirror accepting client` is logged when the mirror socket is
  accepted, before any video packet. The `width_source` line is logged when the first
  codec-configuration packet arrives. In the HEVC-refused case the same packet logs the
  `width_source` line (`:603`) and then the refusal (`:608`), and no codec line follows.
- **Logger prefix:** these are the message texts. Whatever prefix UxPlay's logger adds is
  confirmed only by a real run, so the analyser matches the text anywhere in a line.
- CLI flags at this commit: `-n` (name, `uxplay.cpp:1600`), `-nh` (don't append `@hostname`
  to the name, so the iPhone shows exactly `HU-Test`; `:1616`, help text `:1107`), `-s` (display size,
  `:1744`), `-d` (debug, `:1816`), `-vs` and `-as` (video and audio sinks, `:1847` and `:1857`),
  `-h265` (`:2223`).
