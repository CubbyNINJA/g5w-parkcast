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

## Not settled

- Which window the camera uses and whether it takes focus from the app on top. If it does, OD-120's focus rule
  covers the camera too; `camera-probe` records the focused window each second during a shift to R and back.
- Whether an off signal is broadcast when the camera closes, and whether any of these broadcasts is sticky.
