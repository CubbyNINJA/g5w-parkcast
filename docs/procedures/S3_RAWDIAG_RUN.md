# S3 diagnostic run: the raw UxPlay log

How the owner runs the `-rawdiag` test build (OD-198 to OD-202) to find why the first on-unit run showed no video
(`docs/evidence/S3_FIRST_RUN_FINDINGS.md`). The build writes UxPlay's own log, with key material redacted, to
`uxplay-raw.log` in ParkCast's app data (AIR-085), and `parkcast-diag` copies it to the stick (AIR-089). This run gives
no GO or NO-GO verdict; it only collects evidence.

## Before the run

- Use the owner's own unit only (build 260206, NEMESIS 1.16.7).
- The raw file is emptied every time ParkCast starts, so don't exit or restart ParkCast between casting and step 6.
- `parkcast-diag` copies at once, with no waiting window: cast first, then run it while ParkCast is still open.

## Stick layout

```text
airplay.receiver.zip                       the -rawdiag install zip (version 0.3.0-rawdiag)
NEMESIS.data                               a copy of tools/parkcast-install/NEMESIS.data (step 2)
tools/parkcast-install/                    install-receiver.sh, NEMESIS.data
tools/parkcast-diag/                       parkcast-diag.sh, NEMESIS.data (path=tools/parkcast-diag/parkcast-diag.sh)
```

## Steps

1. Park with the gear in P. Turn the iPhone's Bluetooth off (Settings, Bluetooth) so wireless CarPlay stays off, and
   join the iPhone to the car's Wi-Fi. Settings, Wi-Fi, (i) should show an address starting `192.168.0.`.
2. Insert the stick and tap Yes. The install script installs the `-rawdiag` build and starts ParkCast. Wait for the
   success dialog, then remove the stick.
3. On a phone or laptop, replace the stick's root `NEMESIS.data` with a copy of `tools/parkcast-diag/NEMESIS.data`.
   Don't insert the stick yet.
4. On the iPhone, open Control Centre, tap Screen Mirroring, choose ParkCast, and mirror for about a minute: a portrait
   app, then a landscape video. Note whether a picture appears on the car's screen.
5. Keep ParkCast open. While the iPhone is still mirroring, or right after it stops, insert the stick and tap Yes.
   `parkcast-diag` removes the root `NEMESIS.data` itself, copies `uxplay-raw.log`, `receiver.log` and
   `receiver.log.1`, and writes `NEMESIS-diag/parkcast-diag/<timestamp>/`. Wait for NEMESIS's result dialog.
6. Bring the stick back to the PC. Its output goes under `runs/`, never into git (AIR-089(g)).

Afterwards, turn the iPhone's Bluetooth back on and check that wireless CarPlay connects as before (OD-173).
