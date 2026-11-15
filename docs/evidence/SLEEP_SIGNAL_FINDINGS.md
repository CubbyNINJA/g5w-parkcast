# How an app learns the head unit is going to sleep and has woken (framework, 2026-11-15)

Read from the unit's own framework (`automotive.odex`, `automotive-service.odex`, `services.odex`,
`framework2.odex`, build 260206) with a stdlib DEX reader. Nothing was run on the car. For AMB-62
(AIR-019(d), AIR-021(b)). Confirmation on the unit is the sleep-probe's job.

## The signals

| Signal | Sent by | What it carries | For the receiver |
|---|---|---|---|
| Sticky broadcast `com.hkmc.intent.action.ACTION_ACC_STATUS_CHANGED` | `com.hkmc.micom.MicomPowerService.processAccStatus(I)`, only when the state changes; logged as `ACC Status Changed : <prev> -> <new>` (tag `MicomPowerService`) | `android.ibox.mode.EXTRA_ACC_STATUS` (int, new state), `android.ibox.mode.EXTRA_PREVIOUS_ACC_STATUS` (int), `android.ibox.mode.TIMESTAMP` (long, uptime ms) | **Best single signal.** No receiver permission. Being sticky, registering also returns the current state. A stock receiver already uses it (`WifiService`). |
| Binder listener on `com.hkmc.micom.power` (`MicomPowerManager.registerPowerStatusListener`, txn 12) | `MicomPowerService.notifyPrepareSystemOff` / `notifySystemOff` | `onPrepareSystemOff()`, `onSystemOff()`, `onPowerStatusChanged(int)` | Earliest "going to sleep" warning. No permission check found. Must never use `registerSystemOffResponder` (txn 14), which makes shutdown wait for the app. |
| `com.hkmc.intent.action.SYSTEM_POWERON` / `SYSTEM_POWEROFF` | `MicomPowerService.changeAvPwrStatus` | timestamp | The AV power button, not ACC. Not sleep. |
| `mobis.intent.action.SCREEN_OFF` / `SCREEN_ON`, `com.mobis.intent.action.SCREEN_OFF` | `PowerManagerService`, `BlackScreenService` | none | Display state and the black screen; the car stays awake. Not sleep. |
| `android.intent.action.PRE_QUICKBOOT` / `POST_QUICKBOOT` | no sender found in the framework (native or a QuickBoot app, inferred) | none | Possibly the snapshot-boot path. Unconfirmed. |

ACC states (`com.hkmc.power.MicomPowerConstants$AccStatus`): `0` ACC on, `1` logic on, `2` ADM,
`5` system ready, `6` delay mode, `7` preparing system off, `255` off. On regions 1 and 4, a status
of 1 is remapped before sending.

## What it means for the receiver

- Stop polling, and treat the unit as asleep, on `EXTRA_ACC_STATUS` 6, 7 or 255. Treat 0 and 5 as
  resumed, and do the AIR-019(d) re-read. Treat 1 and 2 as not-yet-resumed until the probe shows
  otherwise. This mapping is inferred from the constant names.
- After a long time off, `MicomPowerService` times out into a full shutdown ("System Power Off Time
  out! Shut down Start"). A later start is then a fresh boot or QuickBoot restore, not a resume, so
  the receiver also treats its own process start as a resume.

## sleep-probe results (2026-11-15 16:24, 260206 CA, a 30-second off cycle)

Run 1 at 16:24:47 (uptime 30.15 s), car on; the owner then switched the car off for about 30 s and
on again; run 2 at 16:25:58 (uptime 100.80 s). Output in `NEMESIS-diag/sleep-probe/` on the stick.

| Evidence | What it shows |
|---|---|
| `run1`/`run2` `uptime.txt`, `wallclock.txt` | Uptime advanced 70.65 s over 71 s of clock time: **the unit stayed up**; no reboot or QuickBoot. |
| `run2/broadcasts.txt` (broadcast history) | `EXTRA_ACC_STATUS` 5→0 at start-up, then **0→2** when the car was switched off, then **2→0** when it was switched on. No 6, 7 or 255. |
| `run2/logcat-power.txt` | 16:25:46.640 `onAccOnRequest (reason = ACCON(0x0))`, `ACC Status Changed : ADM(0x2) -> ACCON(0x0)`, `Broadcasting ACC Status (not buffering) : ACCON(0x0)`, delivered at once to stock receivers. The off transition had scrolled out of the log buffer; the broadcast history records it. |
| `getPowerStatus.txt`, `isPreparingSystemOff.txt` | Both read 0 in both runs, taken with the car on. |

So switching the car off for a short time puts the head unit into **ADM (2)**: the car is off but the
unit stays up. A longer off presumably goes on to 7 or 255 and a full shutdown (not tested).

## Not verified

- The ACC sequence for a long off cycle (several minutes), and whether the unit then shuts down.
- Whether the broadcast is declared protected (`framework-res.apk` wasn't available). That would only
  limit who can send it, not who can receive it.
- The `PRE_QUICKBOOT` / `POST_QUICKBOOT` sender.
