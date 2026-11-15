# How the head unit reports Park (gear-probe, 2026-11-15)

A read-only probe (`tools/unit-probes/gear-probe`) ran on the owner's unit, build
CVEV.CAN.0000.V015.011.260206, from 14:44:01 to 14:45:47, with both guards passed. The car was
parked with the brake held. The probe prompted P, R, N, D and P again at +0, 15, 30, 45 and 60 s
(`probe.log`). The output is in `NEMESIS-diag/gear-probe/` on the stick. It answers AMB-32 and
most of AMB-33, for the parking gate (AIR-017, AIR-019, AIR-020).

## The two "getters" return no value

All 84 readings are `Parcel(00000000)` for both calls, in every phase (`readings.txt`). That's a
successful call with nothing returned. Neither BCM call is a getter:

| Call | What happened |
|---|---|
| `com.hkmc.micom.bcm` transaction 14, `getGearStatusInfo` | Each call made the BCM service send a request to the MICOM: `BCM Send packet : devid = 0x3a func = 0x49(REQ_GEAR_STATUS:49)` (`logcat-gear.txt`). The answer comes back later as a notification, not in the reply. |
| `com.hkmc.micom.bcm` transaction 7, `getParkBrakeStatus` | No value. The BCM dump's `mParkBrake` stayed 0 throughout (`dumpsys-bcm-before.txt`, `-after.txt`). |

Polling these calls can't measure the gear.

## The gear arrives as notifications

The stock services all receive the same change within 10 ms (`logcat-gear.txt`):

| Time | Prompt | What the stock services logged |
|---|---|---|
| 14:44:18.79 | R (shown 14:44:17.25) | `VCU_GearPosSta` 7. SystemService `SYS_GEAR_STATUS_M(0x8126)`: 1, "Gear status changed to : NONE(0xff)". `onGearStatus` gear 255. `onGearPStatus` 0. `onRegulationTypeBStatus` Gear P 0. |
| 14:44:31 to 46 | N | Nothing. |
| 14:44:48.38 | D (shown 14:44:46.64) | `VCU_GearPosSta` 6. PgsService "GearP status : false". |
| 14:45:04.34 | P (shown 14:45:01.84) | `VCU_GearPosSta` 5. |
| 14:45:10.66 | P | `VCU_GearPosSta` 0. `SYS_GEAR_STATUS_M`: 0, "Gear status changed to : P(0x0)". `onGearStatus` gear 0. `onGearPStatus` 1. `onRegulationTypeBStatus` Gear P 1. |

What this shows for the parking gate:

- **Park versus not Park is unambiguous** in the stock notifications: `onGearPStatus` 1 or 0,
  `onGearStatus` 0 (P) or 255 (NONE), `SYS_GEAR_STATUS_M` 0 or 1. The system service's gear
  status reports only P or NONE, not R, N or D. That is all the gate needs.
- `VCU_GearPosSta`, an event the camera service receives, carries the selector position: 0 in P
  and 7 after leaving P for R. Which of 6 and 5 is N and which is D isn't established: no event
  came during the N prompt, so the shifts may have lagged the prompts.
- The parking-brake signal is no use on this car: it read 0 the whole time.

## The stock interface (from the framework, 2026-11-15)

The unit's `automotive.odex` and `automotive-service.odex` were read with a stdlib DEX reader
(class and method metadata, static constants and the service methods' bytecode). Nothing was run
on the car.

| Item | Finding |
|---|---|
| Service | `com.hkmc.micom.system`, interface `com.hkmc.micom.ISystemService`. Confirmed on the unit by `service-list.txt` (gear-probe), line 57. |
| Subscribe | `ISystemService.addListener(ISystemListener)`, transaction 1; `removeListener`, transaction 2. |
| Callbacks | `com.hkmc.micom.ISystemListener` (descriptor `com.hkmc.micom.ISystemListener`), one-way: `onGearPStatus(int)` transaction 27, `onGearStatus(int)` transaction 17. |
| Getter | `int ISystemService.getGearPStatus()`, transaction 36. The service returns its cached P state; it sends no request to the MICOM. |
| Public wrapper | `com.hkmc.micom.VehicleInfoManager(Context)` with `addListener`/`removeListener(VehicleInfoListener)` and `getGearPStatus()`. `VehicleInfoListener` is a plain class with `onGearPStatus(int)` and `onGearStatus(int)`. |
| Permission | None found. Neither the framework classes nor `SystemService.addListener`/`getGearPStatus` check one; `addListener` only logs the caller's PID and links to its death. |
| Delivery | `onGearPStatus` is sent only when the P state changes. On `addListener` the service replays the current gear, parking and speed state, but **not** `onGearPStatus`, so a new subscriber reads the starting state with `getGearPStatus`. |
| Not getters | BCM `getGearStatusInfo()` returns `void`: it only requests the data, which then arrives on `IBCMListener.onGearStatusInfoChanged(int, int)`. That's why the probe's readings were empty. |

So the receiver can subscribe to `onGearPStatus` for changes and call `getGearPStatus` for the
starting state, and to re-confirm Park while nothing changes.

## Build identity (AMB-32)

From `build-props.txt`:

| Property | Value |
|---|---|
| `ro.build.product` | `CVEV.CAN.0000.V015.011.260206` |
| `ro.build.product2` | `CVEV.CAN.S5W_M.V015.011.260206` |
| `ro.build.product3` | `CV_E.CAN.S5W_M.V015.011.260206` |
| `ro.product.device` | `wp_daudioplus_cvev_ca` |
| `ro.mobis.onebindir` | `cvev_ca` |

`ro.build.product` holds the `CVEV.*` family string that OD-47 names.

## A correction to the probe's description

The probe's README and header said it "sends nothing to the car". The gear call made the BCM
service send a status request (`REQ_GEAR_STATUS`) to the head unit's MICOM about once a second
for 90 seconds. It's a request for status, not a command, and nothing changed because of it.
The BCM dumps before and after differ only in the door lock state, which fits the car's own
locking when it's shifted out of Park; nothing the probe calls touches the locks.

## Not verified

- Which of `VCU_GearPosSta` 5 and 6 is N and which is D.
- Whether an ordinary app can load `VehicleInfoManager`: that depends on `automotive.jar` being on
  the boot class path or needing `<uses-library>`. Raw binder calls work either way.
- Whether SELinux or the service manager restrict an app's binder calls (Android 4.4).
- What `getGearPStatus` returns on the live unit, in and out of Park.
- Any build other than 260206.
