# Can the receiver get an icon on the stock home screen? (home-probe, 2026-11-15)

From `tools/unit-probes/home-probe` run on the owner's unit (build 260206,
`eng.vmadmin.20260206.060005`) at 17:01. Read-only. For AMB-69 (OD-95). The copied database stays on
the stick and is not committed; only its structure and non-identifying values are recorded here.

## What the unit showed

| Item | Finding | For AMB-69 |
|---|---|---|
| Home packages | `com.android.launcher3` (`/system/app/Launcher_WP3.apk`), `com.hkmc.system.app.homesetting` (`HKMC_HomeSetting_WP.apk`), `com.mobis.splitscreen.right` (`RightScreenHome.apk`). Launcher and home settings run as `android.uid.system`. | Both are odex'd system apps, so their menu logic is in `.odex` files. |
| App widgets | `dumpsys appwidget` lists no providers, no widget ids and **no hosts**. The launcher holds `BIND_APPWIDGET` but hosts nothing. | A third-party app widget has nowhere to appear. Ruled out. |
| `homesetting.db` tables | `AllMenu(userName, menu_items)`, `MenuIcon(userName, first..fourth)`, `Basic_Widget(userName, middle, right)`, `Advanced_Widget(userName, left, middle, right)`, `HomeType(userName, home_type)`, `Common(edit_widget_available, allmenu_revision)`, `PhotoFrame`. | |
| Rows | `AllMenu`, `MenuIcon`, `Basic_Widget`, `Advanced_Widget` and `PhotoFrame` are **empty**. `HomeType` is `2` for `User_0`..`User_2`. `Common` is `edit_widget_available=0`, `allmenu_revision=2`. | The All Menus order comes from defaults in code; a row is written only after the owner edits the menu. What `menu_items` may name is unknown from the database alone. |
| Provider | `com.hkmc.system.app.homesetting.provider` (`.provider.HomeSettingProvider`). Activities `AllMenuEdit`, `EditWidgetActivity`, `MainListActivity`. Receiver `.receiver.HomeSettingReceiver` (backup, restore, profile and locale actions). | The launcher probably reads the menu through this provider. |

## What the launcher code shows (home-code-probe, 17:24)

`home-code-probe` copied `Launcher_WP3.odex`, `HKMC_HomeSetting_WP.odex`, `HKMC_HomeSetting_WP.apk` and
`RightScreenHome.odex` to the stick (OEM binaries, not committed). Read on the PC with a stdlib DEX
reader; class names are the launcher's obfuscated ones.

| Item | Finding |
|---|---|
| Stored menu | The launcher reads `content://com.hkmc.system.app.homesetting.provider/all_menu`, takes the last row for the user (`aY.gF`), and parses `menu_items` as JSON `{"menuItems":[{"itemId":<int>,"packageName":<str>,"className":<str>},...]}` (`ce.U`). |
| Built-in entries | Every All Menus entry is its own hard-coded class (subclasses of `view/a/Q`, built in `view/a/g.load()`), each with a fixed package and activity, for example `com.daudio.app.mirrorlink` / `.ui.MirrorLinkActivity` (`view/a/N`) and `com.daudio.av.app.usbvideo` / `.ui.VideoActivity` (`view/a/z`). |
| Matching | A stored item is resolved by `view/a/g.a(packageName, className, …)`, which only searches that built-in entry list and returns nothing for anything else. |
| Installed apps | The launcher never queries installed apps (`queryIntentActivities`, `getInstalledPackages` and `getInstalledApplications` are not called anywhere in it). |
| Visibility | An entry's visibility comes from region and variant checks and `Settings.System` flags (MirrorLink: `mirrorlink_information`, `_dr1`, `_dr2`), not from whether its app is installed. |

## Conclusion for AMB-69

The stock home screen cannot show an icon for a third-party app without patching the launcher. There
is no widget host, and an `AllMenu` row can only reorder or hide the built-in entries; one naming
another package is not resolved. The only other route found is to take over a built-in entry: install
the receiver under a stock package and activity name that this unit doesn't have (such as MirrorLink's)
and set that entry's visibility flag. That shows the stock entry's label and icon, impersonates an OEM
package, and changes a stock system setting, so it is not proposed; it is listed for the owner only.

The probe's log line `copied  database file(s)` lacks the count because toolbox has no `wc`; two files
were copied (`homesetting.db`, `homesetting.db-journal`). The probe now counts without `wc`.
