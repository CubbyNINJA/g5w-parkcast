# First on-unit S3 run: connected, but no video reached the app (2026-11-23)

The owner ran the S3 test build (app 0.3.0, commit `444e768`, UxPlay `05b0275`) on their unit (build 260206,
`eng.vmadmin.20260206.060005`), parked, with wireless CarPlay disconnected and the iPhone (iPhone16,2) on the
unit's hotspot. The install went through `tools/parkcast-install` twice (16:56 and 16:57, unit clock), because
the root `NEMESIS.data` was left on the stick. `copy-logs` was not completed, so there is no owner note or
checklist, and no run verdict under AIR-075 and AIR-076. A read-only stick tool, `tools/unit-probes/parkcast-diag` (since replaced by `tools/airplay_s3/stick/parkcast-diag.sh`, AIR-089),
then ran at 17:07 (unit clock) while the owner cast again. Its output, ParkCast's `receiver.log` from all three
app starts and the NEMESIS logs are kept in `runs/` and on the stick, not here. They can name the iPhone and
hold its address. For OD-139, AMB-96, AMB-98 and OD-198.

## What the owner saw

The install succeeded and ParkCast opened to a black screen. The iPhone listed ParkCast, connected, and showed
that it was mirroring. No picture appeared and no sound played. No notice appeared on the unit.
No sound is expected: S3's first milestone is video only (OD-139).

## What the logs show

| Item | Finding | Source |
|---|---|---|
| Start | Each of the three starts logged the start marker, `start check=on ... gate=blocked reason=the missing file` (no `receiver.conf`, so the parking check is on, AIR-023), `carplay listener absent`, `mdnsd up` and `advertise ok`. The native library loaded. The identity key file dates from the second start (16:57, after the package update) and the diagnostic's start reused it unchanged | `receiver.log`; `app-data.txt`; logcat `Added shared lib .../libparkcast.so` |
| Discovery and connection | The iPhone found ParkCast and connected: `session 1 connection accepted model=iPhone16,2` at each start. Two sessions in the second start ended `reason=iphone disconnected` after about 60 s and 80 s | `receiver.log` |
| Video | **No session logged an S3 codec line, `decoder OMX.TCC.avcdec configured`, or a first frame, rendered or withheld.** No codec-configuration data reached Java, so the black screen is upstream of the gate and the decoder | `receiver.log` |
| UxPlay's lines | None was written, including the allowlisted `Mirroring initialized successfully` and `Mirroring not initialized at SETUP`. The two finished sessions logged `withheld=1958` and `withheld=1724`. Whether those records were dropped by the AIR-073 filter or by the native ring (64 general slots shared by log records and access units) can't be told from the log | `receiver.log`; `receiver/native/src/ring.c` |
| Ports | During casting, the iPhone held one established connection to TCP 35001 (the RTSP port) for the whole session. **Nothing ever connected to TCP 35000, the mirror data port** (`raop_set_tcp_ports`, `tcp[0]`), which listened throughout. UDP 35000 to 35002 were held. So the session never reached the mirror data stream | `timeline.txt`, sampled every 10 s |
| Park | No `park reading unavailable` record and no `sleep entry` record in any of the three starts. The gate logs only failed Park readings, so this suggests, but doesn't prove, that the app can read `getGearPStatus` | `receiver.log`; AMB-98 stays open |
| Regulation | `regulation state=unrestricted`, then `regulation unavailable reason=InvocationTargetException` at every start: `getStatus()` works but registering the listener throws. It doesn't affect video | `receiver.log` |
| Unit | SELinux `Disabled`. `/data/nemesis` is `drwxr-xr-x root`, `/data/nemesis/logs` is `drwxrwxrwx root`, and the log folder is `drwx------` owned by the app's uid, as AIR-062(c) requires. `receiver.log` is `-rw-------` app-owned, so NEMESIS's own log export copied the folder empty; the root stick scripts can read it | `log-folder-perms.txt`, `selinux.txt` |
| Crashes | No `E AndroidRuntime` or `FATAL` line in the captured logcat, and no tombstone from the run | `logcat-run.txt`, `tombstones.txt` |

## What this answers

- **AMB-96, second half**: the iPhone joins the unit's hotspot with CarPlay off, finds ParkCast and connects.
- **Open on-unit risks** (`docs/design/S3_RECEIVER.md`): the loader accepts the r25c `.so`; an app can start
  `mdnsd`, connect to it and register `_airplay._tcp`; the identity key file is created and reused.
- **Not answered**: why the session never reached the mirror data stream. Candidates include the SETUP exchange
  (pairing, FairPlay or the stream-110 SETUP), and the held TCP 35000 being handed to the mirror listener as a
  `dup`. UxPlay's own log is needed to tell them apart (OD-198).
