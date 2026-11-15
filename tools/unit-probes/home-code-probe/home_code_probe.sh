#!/system/bin/sh
# home_code_probe.sh - READ-ONLY, under a minute. Follow-up to home-probe (AMB-69, OD-95): the stock
# home-settings database holds no AllMenu rows, so the menu comes from code. This copies the code files
# of the stock launcher and home-settings app to the stick so the PC can read what an AllMenu entry may
# name. It only reads: ls and cp FROM the unit. Nothing on the unit is written or changed.
# Guarded to build 260206.
#
# RUN: any time the head unit is on. Tap Yes. Wait for "Home code probe done".
# Output: NEMESIS-diag/home-code-probe/. Removes the root NEMESIS.data when it starts.
# The copied files are OEM binaries: they stay on the stick and are never committed.
export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin${BUNDLE_TEST_SYS:+:$BUNDLE_TEST_SYS}
export LD_LIBRARY_PATH=/vendor/lib:/system/lib
USB=$(pwd)
P=$USB/NEMESIS-diag/home-code-probe
APP_DIR=${HOME_CODE_PROBE_APP_DIR:-/system/app}
FW_DIR=${HOME_CODE_PROBE_FW_DIR:-/system/framework}
mkdir -p "$P" || exit 1
L=$P/probe.log
note() { echo "[$(date '+%H:%M:%S')] $1" >> "$L"; }
toast() {
    am broadcast -a com.hkmc.intent.action.TOAST -e com.hkmc.intent.extra.TOAST_MESSAGE "$1" \
        --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true >/dev/null 2>&1
}
if [ -f "$USB/NEMESIS.data" ] && cmp "$USB/NEMESIS.data" "$USB/tools/home-code-probe/NEMESIS.data" >/dev/null 2>&1; then
    rm -f "$USB/NEMESIS.data" && note "removed root NEMESIS.data"
fi
if [ "$(getprop sys.boot_completed)" != "1" ]; then
    note "Android is not up; nothing done."
    exit 1
fi
BUILD=$(getprop ro.build.version.incremental)
note "home code probe start; build $(getprop ro.build.product) / $BUILD"
if [ "$BUILD" != "eng.vmadmin.20260206.060005" ]; then
    note "guard FAILED (build=$BUILD): nothing read"
    sync
    exit 1
fi
note "guard passed"

ls -l "$FW_DIR" > "$P/framework-dir.txt" 2>&1
mkdir -p "$P/bin"
for f in HKMC_HomeSetting_WP.odex HKMC_HomeSetting_WP.apk Launcher_WP3.odex RightScreenHome.odex; do
    if [ -f "$APP_DIR/$f" ]; then
        cp "$APP_DIR/$f" "$P/bin/" 2>> "$L" && note "copied $f" || note "copy FAILED: $f"
    else
        note "absent: $f"
    fi
done
toast "Home code probe done"
note "home code probe end"
sync
exit 0
