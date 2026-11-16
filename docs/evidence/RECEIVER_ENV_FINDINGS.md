# The head unit's network, mDNS and decoder, as an app would find them (receiver-env-probe, 2026-11-16)

From `tools/unit-probes/receiver-env-probe` run at 18:16 on the owner's unit (build 260206,
`eng.vmadmin.20260206.060005`, SDK 19), with wireless CarPlay not connected. Read-only. Interface MAC addresses in
the raw output are not recorded here. For S3 (OD-141, OD-146; AMB-95 to AMB-97).

| Item | Finding | For S3 |
|---|---|---|
| Wi-Fi | `wlan0` UP at `192.168.0.1/24`; `rmnet0`-`rmnet3` and `eth0` down | The receiver listens on the hotspot address (OD-146) |
| Hotspot daemons | `hostapd` (user `wifi`) and `dnsmasq` (user `nobody`) running; `com.daudio.app.dipo` running | The access point is up with CarPlay disconnected (AMB-96, first part) |
| `mdnsd` | **Not running** with CarPlay disconnected. The coexist probe saw it holding UDP 5353 while CarPlay was active. Android 4.4 starts `mdnsd` on demand (netd, for `NsdService`). | Something must start it before the receiver can advertise (AMB-95) |
| mDNS sockets | Only netd's `/dev/socket/mdns`, `srw-rw---- root system`. No `/dev/socket/mdnsd` while `mdnsd` is stopped. | An app (not `system`) can't open netd's socket |
| mDNS client library | `/system/lib/libmdnssd.so` present (25,704 bytes); no `libdns_sd.so` | The `dns_sd` client exists on the unit |
| H.264 decoder | `OMX.TCC.avcdec` (`video/avc`) listed in `/system/etc/media_codecs.xml` (and `/etc/media_codecs.xml`), plus `.secure`; `OMX.google.h264.decoder` also listed | AMB-97: listed. `OMX.TCC.hevcdec` is listed too, though S1's sources say it never registers |

## Not settled

- The permissions of `/dev/socket/mdnsd` while `mdnsd` runs (stock 4.4: `0660 mdnsr inet`, which apps holding
  `INTERNET` could open), and what starts `mdnsd` without CarPlay.
- Android 4.4's `NsdManager` can't publish TXT records (`NsdServiceInfo` TXT support arrived in API 21), and AirPlay
  discovery needs them.
