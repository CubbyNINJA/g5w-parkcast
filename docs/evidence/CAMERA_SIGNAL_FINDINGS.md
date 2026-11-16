# How the head unit signals the reversing camera (framework, 2026-11-15)

Read from the unit's own framework (`automotive.odex`, `automotive-service.odex`, `framework2.odex`, build 260206,
pulled earlier by the compat probe) with a stdlib DEX reader. Nothing was run on the car. For OD-117 and OD-120.
Confirmation on the unit is `camera-probe`'s job (`tools/unit-probes/camera-probe`).

## What the code shows

| Signal | Where | Notes |
|---|---|---|
| Broadcast `com.hkmc.intent.action.REVERSE_ON` | Sent by `com.hkmc.micom.PgsService$ServiceListenerProxy.handleRearViewStatus(Z)`, which also logs `BROADCASTED - ` and `CAMERA STATUS CHANGED : ` (tag `PgsService`) and calls `notifyRearViewStatus(Z)` | No matching `REVERSE_OFF` sender was found in `automotive-service.odex`; the constant exists. `REVERSE_READY` is sent from `PgsService.cameraHandover()`. |
| `com.hkmc.mode.IModeManager.isRearViewOn()` | Implemented by `com.hkmc.micom.ModeManagerService`, returning its `mIsRearView` field, updated by `onRearViewStatusChanged(Z)` | A binder getter; the service name was not found statically (the probe records `service list`). |
| Broadcasts `SYSTEM_STATE_REARVIEW_ON` / `_OFF`, `AVM_STATE_AROUNDVIEW_ON` / `_OFF`, `CAMERA_SVM_BY_REVERSE_ON` / `_OFF` | Constants in `automotive.odex`; `PgsService` receives `CAMERA_SVM_BY_REVERSE_*` | Senders not found in these files. |
| Features `FEATURE_REARCAMERA_FOR_AUTOMOTIVE`, `FEATURE_DRVM` | `framework2.odex` | The unit may also have a digital rear-view mirror camera (DRVM). |

## What the unit showed (camera-probe, 18:33, owner shifted P to R to P)

From `tools/unit-probes/camera-probe` on build 260206. Read-only; raw output stays on the stick. Times are
the unit's clock.

| Time | Event |
|---|---|
| 18:33:50.34 | `PgsService` logs `BROADCASTED - Intent { act=com.hkmc.intent.action.REVERSE_ON }` and `CAMERA STATUS CHANGED : true`; `ModeManagerService` logs `onRearViewStatusChanged() : true` |
| 18:33:51 to 18:34:00 | Focused window (`mCurrentFocus`) is `AvmCamera`, sampled every second. Before and after, it is the launcher. |
| 18:34:00.75 | `PgsService` logs `CAMERA STATUS CHANGED : false`; `ModeManagerService` logs `onRearViewStatusChanged() : false` |
| 18:34:00.94 | `AvmMode` (the camera app) logs `BROADCASTED - Intent { act=com.hkmc.intent.action.REVERSE_OFF }` |
| by 18:34:01 | Focus is back on the launcher |

While the camera is up, the window stack has `AvmCamera` at position 5 of 12, with system windows above it
(`SS_Button_Window`, `ScreenSaver(BlackScreen)`, `ScreenSaver(SystemUI)`, `SS_OSD_Window`, `Heads Up`) and the
right split-screen pane at position 6. None of those took focus. A toast-type window sits in a high layer, so a
notice would very likely draw over the camera image; that is why OD-117 hides it.

## Conclusion for OD-117 and OD-120

**The reversing camera takes window focus**, about 1 s after `REVERSE_ON`, and gives it back about 1 s after it
closes. So OD-120's rule, no notice while the receiver's own window lacks focus, covers the reversing camera. The
broadcasts are a second signal: `REVERSE_ON` (sent by `PgsService`) and `REVERSE_OFF` (sent by the camera app,
`AvmMode`). Neither is sticky (the sticky list held only `ACTION_ACC_STATUS_CHANGED`).

## Not settled

- The ~1 s gap between `REVERSE_ON` and the camera taking focus: a notice already on screen would stay up to about
  1 s over the camera if only focus is used. `REVERSE_ON` arrives first.
- Surround view (SVM) and the digital rear-view mirror camera (DRVM) were not exercised; this unit may not have
  them.
- `dumpsys` took over 1 s per sample on the unit, so focus times are to about a second.
