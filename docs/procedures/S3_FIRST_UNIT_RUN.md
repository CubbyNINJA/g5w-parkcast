# S3 first on-unit run

How the owner runs the first S3 test build on the head unit and brings the evidence back to the PC. This is
the video-only GO/NO-GO run (OD-139). The requirements are AIR-074 to AIR-079; the design is
`docs/design/S3_RECEIVER.md`. This procedure is checked again, step by step, just before the run, once the
signed APK exists; anything that changed by then is fixed here first.

## Before the run

- Use the owner's own unit only: build `CVEV.CAN.0000.V015.011.260206` with NEMESIS 1.16.7. The install zip
  refuses any other build (AIR-078(c)).
- Never put the identity key, `local.properties` or the keystore on the stick (AIR-083(e), OD-188).
- The stick holds no live root `NEMESIS.data` until a step below says to copy one there, so nothing runs on
  insert by accident.

## Stick layout

Built on the PC by the S3 tools (`tools/airplay_s3/`):

```text
airplay.receiver.zip                         the install zip (zipbuild.py, checked by zipcheck.py)
tools/parkcast-install/install-receiver.sh
tools/parkcast-install/NEMESIS.data          type=execute, path=tools/parkcast-install/install-receiver.sh
tools/parkcast-logs/copy-logs.sh
tools/parkcast-logs/NEMESIS.data             type=execute, path=tools/parkcast-logs/copy-logs.sh
```

## Steps of the first on-unit S3 run

1. Park the car with the gear in P, and leave it in P for the whole run (AIR-079(a)).
2. Disconnect wireless CarPlay, and keep it disconnected until step 10. Note whether it worked before the
   run; step 10 compares against that.
3. Join the iPhone to the unit's own Wi-Fi hotspot (AIR-079(a), OD-146). Don't use any other network.
4. Copy `tools/parkcast-install/NEMESIS.data` to the stick's root, insert the stick, and tap Yes. The
   script installs the zip through NEMESIS, checks it, prepares the log folder and starts ParkCast. Wait for
   the success dialog. On a failure dialog, stop the run, remove the root `NEMESIS.data`, and bring the stick's
   `airplay-receiver-install.log` back to the PC.
5. Remove the root `NEMESIS.data` from the stick.
6. On the iPhone, open Control Centre, tap Screen Mirroring, and choose ParkCast. Mirror a portrait app for
   about a minute, then a landscape video. The observation window is 600 s from the launch (AIR-076(h)).
7. While a session is live, open another app on the unit, then come back to ParkCast.
8. Stop mirroring on the iPhone, then exit ParkCast with its own exit control.
9. Copy `tools/parkcast-logs/NEMESIS.data` to the stick's root, insert the stick, tap Yes, and wait for the
   success dialog. The script makes `NEMESIS-diag/airplay-receiver/<timestamp>/` on the stick, with the logs,
   an empty `owner-note.txt` and a blank `run-checklist.txt`. Then remove the root `NEMESIS.data` again.
   In that folder, write one line in `owner-note.txt`: `picture: yes` if the iPhone's picture appeared on
   the unit, otherwise `picture: no` (AIR-076(e)). In `run-checklist.txt`, answer the first five keys with
   `yes` or `no`: `parkcast listed`, `letterboxed`, `sound on unit`, `stayed behind other app` and
   `nothing on cluster or hud` (AIR-079(e)).
10. Exit ParkCast if it is still open, then confirm wireless CarPlay connects as it did before the run.
    Record `carplay after exit: yes` or `carplay after exit: no` in `run-checklist.txt` in the same run
    folder. Do not record it in `owner-note.txt`.

## After the run

On the PC, copy the run folder from the stick into `runs/` (git-ignored, because the logs can name the
iPhone), then run the S3 evaluator on it (`tools/airplay_s3/evaluator.py`). A `carplay after exit: no`, or a
`not recorded`, is logged as a finding, and no further on-unit S3 run starts until that finding is closed
(AIR-079(g)).
