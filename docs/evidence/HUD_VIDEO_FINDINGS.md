# Can the head unit send video to the HUD? (investigation, 2026-11-14)

Asked by the owner (OD-16). This is a read-only investigation of the owner's unit (260206 CA),
using the framework the compat probe pulled (`automotive.odex`, `framework2.odex`), the service
list and the props. Nothing was run on the car.

## Finding: no video path exists

| Evidence | What it shows |
|---|---|
| `com.hkmc.usm.IUSMService` (HUD methods numbered 7 to 26 and 308 to 327; the probe read getters 308 to 327) | The head unit's only HUD controls are **settings**, in two kinds. The first is `setHUD_Enable`, `_Height`, `_Rotation`, `_Brightness`, `_SpeedNumberSize`, `_SpeedColor`, `_ARView` and `_ARLeftRight`. The second is one on/off toggle per content type: `setHUD_TurnbyTurn`, `_TrafficInfo`, `_AVInfo`, `_DrivingAssistInfo`, `_LaneSafetyInfo` and `_BlindSpotSafetyInfo`, with a matching `getHUD_*` for each. |
| `com.hkmc.usm.IUSMListener` | It receives HUD status and content-selection events only (`onHUDStatusReceived`, `onHUDContentSelectionReceived`, …). |
| `com.hkmc.cluster.IClusterService` | Content shown in the cluster and HUD is sent as **data**: turn-by-turn fields (see the probe's `TBT Info`) and music text (`updateClusterWithArtistInfo`, `updateClusterInfoWithArtistForMusicStreaming`). |
| `ro.product.opt.hud.artist = true`, `VariantCoding$HUD_CODES`, `USE_HUD_ARTIST_NAME` | This unit's HUD variant can show the media artist name, as text. |
| `service list` | Two display services: `display` (`IDisplayManager`) and the vendor's `telechips_display` (`ITelechipsDisplayManager`), which the props tie to the chip's external video output, with nothing connected (see the probe results below). There's no presentation or HUD-surface service. |

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

## Probe results (2026-11-15, 260206 CA)

A read-only probe (`tools/unit-probes/hud-probe`) ran on the owner's unit at 08:41 on
2026-11-15, on build CVEV.CAN.0000.V015.011.260206 with both guards passed (`probe.log` L2-3). It
read the HUD getters 308 to 327 and dumped the USM, cluster, display and SurfaceFlinger services,
the framebuffers and the display props. The output is in `NEMESIS-diag/hud-probe/` on the stick;
the line numbers below refer to those files, and were spot-checked against them a second time.

### Only one display exists

| Evidence | What it shows |
|---|---|
| `dumpsys-display.txt` L61-62, L74-81 | One display device, "Built-in Screen", 1920x720 at 60 fps, density 240, secure, and one logical display. |
| `dumpsys-display.txt` L8 | `mNextNonDefaultDisplayId=1`: no second display has been created since boot. |
| `dumpsys-display.txt` L19-59 | The overlay, Wi-Fi display and virtual adapters are present but hold no displays. Wi-Fi display is off. |
| `dumpsys-surfaceflinger.txt` L98-100, L144 | One SurfaceFlinger display and one hardware display, both 1920x720. Every layer, past and present, is on that screen (L169-219). |
| `framebuffers.txt` L1-5, `graphics-ls.txt` L2-3 | One framebuffer, `fb0` (`tccfb`, 1920x720, 32 bpp). |
| `dumpsys-surfaceflinger.txt` L83-88 | The layer called "Heads Up" is Android's pop-up notification bar, not the HUD. |

No HUD or cluster screen is visible to Android, so an app can't draw to either. A virtual display
or presentation only draws to the app's own buffers or the main screen.

### The HUD getters return settings only

| Evidence | What it shows |
|---|---|
| `hud-getters.txt` L1-20 | Each getter returns one number: Type 2, Enable 3, ModeStatus 3, Height 3, Rotation 0, Brightness 19, TurnbyTurn 1, TrafficInfo 3, DrivingAssistInfo 1, LaneSafetyInfo 250, BlindSpotSafetyInfo 1, AVInfo 3, SpeedNumberSize 250, SpeedColor 250, InSportMode 250, Speed -1, ARLeftRight 2, ARView 20, InSportMode2 250, IMSAutoSaveStatus 3. |
| `dumpsys-usm.txt` L3-23 | The MICOM's cached HUD values match the getters one for one, which confirms the transaction numbers. |
| `dumpsys-cluster.txt` L31-165 | Cluster content is structured fields: turn-by-turn type and direction, road and route data, cruise-control info. There's no image or frame field. |

The HUD getters run to 327. The ones after 321 are `InSportMode`, `Speed`, `ARLeftRight`,
`ARView`, `InSportMode2` and `IMSAutoSaveStatus`.

### A second display service

`service list` shows `telechips_display` (`ITelechipsDisplayManager`, `service-list.txt` L11) as
well as `display`. The props suggest it handles the chip's external video output, and nothing is
connected there (`tcc.extenddisplay.detected [0]`, `props-hud-display.txt` L10). It wasn't
dumped.

The probe supports the finding that no video path exists, for everything Android can see (OD-22).

## Not verified

- What the setting values mean. TurnbyTurn reads 1 while Enable, TrafficInfo and AVInfo read 3,
  so the HUD's turn-by-turn setting may be off, but 1, 2, 3 and 250 haven't been decoded. Decode
  them from `automotive.odex`, or re-read 314 after changing the setting in the car.
- What `telechips_display` does. A later probe can run `dumpsys telechips_display`.
- How the HUD is wired and what it can draw itself. Android can't see either.
