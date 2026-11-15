# Can the head unit send video to the HUD? (investigation, 2026-11-14)

Asked by the owner (OD-16). This is a read-only investigation of the owner's unit (260206 CA),
using the framework the compat probe pulled (`automotive.odex`, `framework2.odex`), the service
list and the props. Nothing was run on the car.

## Finding: no video path exists

| Evidence | What it shows |
|---|---|
| `com.hkmc.usm.IUSMService` (40 HUD methods, numbered 7 to 26 and 308 to 321) | The head unit's only HUD controls are **settings**, in two kinds. The first is `setHUD_Enable`, `_Height`, `_Rotation`, `_Brightness`, `_SpeedNumberSize`, `_SpeedColor`, `_ARView` and `_ARLeftRight`. The second is one on/off toggle per content type: `setHUD_TurnbyTurn`, `_TrafficInfo`, `_AVInfo`, `_DrivingAssistInfo`, `_LaneSafetyInfo` and `_BlindSpotSafetyInfo`, with a matching `getHUD_*` for each. |
| `com.hkmc.usm.IUSMListener` | It receives HUD status and content-selection events only (`onHUDStatusReceived`, `onHUDContentSelectionReceived`, …). |
| `com.hkmc.cluster.IClusterService` | Content shown in the cluster and HUD is sent as **data**: turn-by-turn fields (see the probe's `TBT Info`) and music text (`updateClusterWithArtistInfo`, `updateClusterInfoWithArtistForMusicStreaming`). |
| `ro.product.opt.hud.artist = true`, `VariantCoding$HUD_CODES`, `USE_HUD_ARTIST_NAME` | This unit's HUD variant can show the media artist name, as text. |
| `service list` | One `display` service (`IDisplayManager`). There's no second-display, presentation or HUD-surface service. |

The HUD draws its own symbols and text from small data messages. The head unit has no frame,
image or video channel to it, so **mirrored video can't be sent to the HUD**: there's no surface
to draw it on.

## What the HUD can show that the receiver could feed

- Turn-by-turn data, through the cluster service. CarPlay's directions now reach it on this unit
  (projection.ca-tbt).
- Media text such as the artist, through `updateClusterWithArtistInfo`, if the HUD's AV-info
  content is turned on. Showing what's playing from a mirrored app would be a separate, text-only
  feature, and nothing has been specified for it.

## Safety

The HUD projects into the driver's line of sight while driving. Even if a video path existed,
video there would be a distraction risk; that's AMB-18. The `setHUD_DrivingAssistInfo`,
`_LaneSafetyInfo` and `_BlindSpotSafetyInfo` toggles control how driver-assistance warnings are
shown. They stay untouched under `SCOPE.md`'s driver-assistance exclusion.

## Side finding: why the HUD showed nothing for the built-in navigation

The HUD has its own turn-by-turn on/off setting (`getHUD_TurnbyTurn`, IUSMService 314). In the
projection probe the cluster showed turn-by-turn and the HUD didn't, which fits that setting
being off. Check the car's settings for the HUD's content selection. A read-only probe could
also read `getHUD_TurnbyTurn` and `getHUD_Enable` by name through the stock USM manager.

## Not verified

- Whether `getHUD_TurnbyTurn` is actually off on this unit. That's inferred from the probe, not
  read.
- How the HUD is wired, whether to the cluster or on its own bus. It isn't visible from Android.
- `dumpsys display` wasn't captured. A read-only probe would confirm that only one display exists.
