home-probe - READ-ONLY, under a minute. Can the receiver get an icon on the stock home screen without
patching the launcher? Records the widget host and providers, what the launcher and home-settings
packages declare, and copies the stock home-settings database files to the stick (read from the unit,
never written back). Guarded to build 260206.
RUN: any time the head unit is on. Copy this folder's NEMESIS.data to the drive root, insert, tap Yes,
and wait for "Home probe done". Removes the root NEMESIS.data when it starts.
Output: NEMESIS-diag/home-probe/. The database copy stays on the stick; never commit it.
