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

## Not settled

- Whether an `AllMenu` entry can name an arbitrary package or component, or only items from a fixed
  list in code. The follow-up `home-code-probe` copies `HKMC_HomeSetting_WP.odex`,
  `Launcher_WP3.odex` and `RightScreenHome.odex` to the stick for reading on the PC.
- Writing to `homesetting.db` or its provider would be a change to stock data and needs the owner's
  decision even if the code allows it.

The probe's log line `copied  database file(s)` lacks the count because toolbox has no `wc`; two files
were copied (`homesetting.db`, `homesetting.db-journal`).
