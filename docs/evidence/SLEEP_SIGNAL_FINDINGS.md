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

## Not verified

- The ACC state sequence on the unit for a short and a long off cycle, and whether uptime resets
  after a long one. The sleep-probe records both.
- Whether the broadcast is declared protected (`framework-res.apk` wasn't available). That would only
  limit who can send it, not who can receive it.
- The `PRE_QUICKBOOT` / `POST_QUICKBOOT` sender.
