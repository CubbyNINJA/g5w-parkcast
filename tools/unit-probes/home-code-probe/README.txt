home-code-probe - READ-ONLY, under a minute. Follow-up to home-probe (AMB-69): copies the stock
launcher and home-settings code files (.odex, plus the home-settings .apk) to the stick so the PC can
read what an All Menus entry may name. Guarded to build 260206.
RUN: any time the head unit is on. Copy this folder's NEMESIS.data to the drive root, insert, tap Yes,
and wait for "Home code probe done". Removes the root NEMESIS.data when it starts.
Output: NEMESIS-diag/home-code-probe/. The copied files are OEM binaries; never commit them.
