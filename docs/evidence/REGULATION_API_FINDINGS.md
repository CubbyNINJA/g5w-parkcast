# Stock regulation service: API findings

Static analysis of the owner's unit framework (build `CVEV.CAN.0000.V015.011.260206`), done on 2026-11-19
to settle how AIR-038 and AIR-066 reach the stock regulation state. Read-only: class and method names
only; no framework file is copied into this repository. Nothing here has been exercised on the unit yet.

## Where it lives

- Client classes in `automotive.odex`, package `com.hkmc.regulation`: `IRegulationManager` (AIDL, with
  `Stub` and `Proxy`), `IRegulationStatusListener`, `RegulationActivityManager`, `RegulationStatusListener`.
- Server in `automotive-service.odex`: `com.hkmc.micom.RegulationService extends IRegulationManager.Stub`,
  created by `MicomService` and added to `ServiceManager`.
- Binder name: `com.hkmc.regulation`. `RegulationActivityManager.getInstance(Context)` wraps
  `ServiceManager.getService("com.hkmc.regulation")` with `IRegulationManager.Stub.asInterface`.

## `IRegulationManager` transactions

| # | Method | Kind |
|---|---|---|
| 1 | `int getStatus()` | read |
| 2 | `int getRegulationBStatus()` | read |
| 3 | `int getOccupationConsentState()` | read |
| 4 | `void setOccupationConsentState(int)` | **set** |
| 5 | `int getOccupationPopupState()` | read |
| 6 | `void addListener(IRegulationStatusListener)` | register |
| 7 | `void removeListener(IRegulationStatusListener)` | register |
| 8 | `void showRegulationPopup(String, String)` | **acts** (system popup) |

`IRegulationStatusListener` (oneway): `onStatusChanged(int)`, `onRequlationBStatusChanged(int)` (sic),
`onOccupationPopupStateChanged(int)`. The server holds listeners with `linkToDeath`.

Status values (`RegulationStatusResult`): `REGULATION_OFF = 0`, `REGULATION_ON = 1`.

## Permissions

Only the server's `setServiceOn(boolean)` (not an AIDL transaction) enforces
`com.hkmc.permission.AUTOMOTIVE_REGULATION`. The getters, `addListener` and `removeListener` check no
permission and no uid, so an app holding only `INTERNET` can read the state and register a listener. The
classes are hidden from the SDK, so the app reaches them by reflection or by its own copy of the AIDL
descriptor and transaction numbers.

## Broadcasts

`android.mobis.regulation.ACTION_ENTER_REGULATION` / `ACTION_EXIT_REGULATION` and the `_B_STATUS`
variants are sent with plain `sendBroadcast`: not sticky, no permission, no extras, and not declared
protected, so any app can also forge them.

## Consequence for the receiver (OD-195)

The Android `RegulationSource` reads `getStatus()` once at start and then listens through `addListener`.
It never uses the broadcasts (forgeable), and never calls transactions 4 or 8, `setServiceOn` or any
register method. Open on the unit: whether `getStatus()` reflects the `hkmc_reg_off` setting, and whether
the binder calls work from the receiver's uid; file 15's run answers both.
