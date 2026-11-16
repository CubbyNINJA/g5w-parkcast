#!/system/bin/sh
# home_probe.sh - READ-ONLY, under a minute. Can the receiver get an icon on the stock home screen
# without patching the launcher (AMB-69, OD-95)? Records the widget host and providers, what the
# launcher and home-settings packages declare, and copies the stock home-settings database files to the
# stick so their AllMenu and widget tables can be read on the PC. It only reads: dumpsys, pm, ls and cp
# FROM the unit. Nothing on the unit is written or changed. Guarded to build 260206.
#
# RUN: any time the head unit is on. Tap Yes. Wait for "Home probe done".
# Output: NEMESIS-diag/home-probe/. Removes the root NEMESIS.data when it starts.
# The database copy stays on the stick; never commit it (it can hold photo-frame paths).
export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin${BUNDLE_TEST_SYS:+:$BUNDLE_TEST_SYS}
export LD_LIBRARY_PATH=/vendor/lib:/system/lib
USB=$(pwd)
P=$USB/NEMESIS-diag/home-probe
DB_DIR=${HOME_PROBE_DB_DIR:-/data/data/com.hkmc.system.app.homesetting/databases}
mkdir -p "$P" || exit 1
L=$P/probe.log
note() { echo "[$(date '+%H:%M:%S')] $1" >> "$L"; }
toast() {
    am broadcast -a com.hkmc.intent.action.TOAST -e com.hkmc.intent.extra.TOAST_MESSAGE "$1" \
        --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true >/dev/null 2>&1
}
if [ -f "$USB/NEMESIS.data" ] && cmp "$USB/NEMESIS.data" "$USB/tools/home-probe/NEMESIS.data" >/dev/null 2>&1; then
    rm -f "$USB/NEMESIS.data" && note "removed root NEMESIS.data"
fi
if [ "$(getprop sys.boot_completed)" != "1" ]; then
    note "Android is not up; nothing done."
    exit 1
fi
BUILD=$(getprop ro.build.version.incremental)
note "home probe start; build $(getprop ro.build.product) / $BUILD"
if [ "$BUILD" != "eng.vmadmin.20260206.060005" ]; then
    note "guard FAILED (build=$BUILD): nothing read"
    sync
    exit 1
fi
note "guard passed"

dumpsys appwidget > "$P/dumpsys-appwidget.txt" 2>&1
pm list packages -f > "$P/packages-all.txt" 2>&1
grep -i -E "launcher|homesetting|home" "$P/packages-all.txt" > "$P/packages-home.txt" 2>&1
rm -f "$P/packages-all.txt"
for pkg in com.android.launcher3 com.hkmc.system.app.homesetting; do
    dumpsys package "$pkg" > "$P/dumpsys-package-$pkg.txt" 2>&1
done
ls -l "$DB_DIR" > "$P/db-dir.txt" 2>&1
mkdir -p "$P/db"
for f in "$DB_DIR"/*; do
    [ -f "$f" ] && cp "$f" "$P/db/" 2>> "$L"
done
n=0; for f in "$P/db"/*; do [ -f "$f" ] && n=$((n + 1)); done
note "copied $n database file(s)"
toast "Home probe done"
note "home probe end"
sync
exit 0
