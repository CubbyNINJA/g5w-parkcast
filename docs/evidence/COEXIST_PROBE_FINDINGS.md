# What the unit listens on, with and without wireless CarPlay (coexist-probe, 2026-11-15)

A read-only probe (`tools/unit-probes/coexist-probe`) ran on the owner's unit, build
CVEV.CAN.0000.V015.011.260206, at 15:50 on 2026-11-15, guard passed. It took snapshot A with
wireless CarPlay disconnected and snapshot B two minutes after the owner connected it. It read
`/proc/net/{tcp,tcp6,udp,udp6}`, each process's socket inodes, `ps`, `netcfg` and filtered
properties. No service was called. The output is in `NEMESIS-diag/coexist-probe/` on the stick. An
earlier attempt, `coexist-probe-try1`, never had CarPlay connected; its snapshot A matches this one.
For AMB-07 and AMB-44 to AMB-46.

## Listening and bound ports

| Port | Owner | CarPlay off (A) | CarPlay on (B) |
|---|---|---|---|
| TCP 53, UDP 53, UDP 67 | `dnsmasq`: the hotspot's DNS and DHCP (range 192.168.0.2 to 192.168.0.254) | yes | yes |
| TCP 5353 | `sxm65hd` (a vendor daemon, not mDNS) | yes | yes |
| UDP 5353, plus one high UDP port, IPv4 and IPv6 | `mdnsd` | yes | yes |
| **TCP 7000**, IPv4 and IPv6, all addresses | **`com.daudio.app.dipo`** (the stock CarPlay receiver) | no | **yes** |
| **UDP 6000, 6011, 6020, 5020**, and six random high ports | `com.daudio.app.dipo` | no | **yes** |
| Established TCP on local ports 5001, 6030 and 7000 | `com.daudio.app.dipo`, with the iPhone | none | 3 |

The hotspot interface `wlan0` is up as 192.168.0.1/24 in both snapshots; `eth0` is down.

## What this means for the receiver

- **DiPO takes AirPlay's standard port, TCP 7000, only while a CarPlay session runs.** A receiver
  holding TCP 7000 when CarPlay connects would stop DiPO from opening it, so the receiver must never
  use TCP 7000, whether or not CarPlay is connected at the time.
- **UxPlay's port choices clash.** `-p` alone selects its "legacy" ports TCP 7000, 7001, 7100 and
  UDP 6000, 6001, 7011, which overlaps DiPO on TCP 7000 and UDP 6000. Without `-p`, UxPlay chooses
  ports at random, which could land on one of DiPO's random high ports. A fixed set of ports clear
  of DiPO's (`-p n` gives n, n+1 and n+2 for TCP and UDP) avoids both.
- **One mDNS responder already runs.** `mdnsd` holds UDP 5353. The receiver should advertise
  through it rather than start a second responder on the same port.

## Not verified

- What DiPO advertises over mDNS (service type, name, port). The logcat showed DiPO's session
  activity but no advertisement lines.
- Whether DiPO's random high UDP ports fall in a fixed range.
- Whether iOS offers Screen Mirroring to a second receiver during a CarPlay session (AMB-07). Only a
  live test with the iPhone can show that.
- Any build other than 260206.
