#!/system/bin/sh
# park_probe.sh - READ-ONLY, 45 seconds. Checks the stock Park getter the AirPlay receiver's parking
# gate would use (AMB-33): once a second it calls ISystemService.getGearPStatus (transaction 36 on
# com.hkmc.micom.system), which returns the system service's cached Park state and sends no request
# to the MICOM (docs/evidence/GEAR_PROBE_FINDINGS.md). It calls nothing else. It runs only if the
# service identifies as com.hkmc.micom.ISystemService AND the build is exactly 260206. It also saves
# BOOTCLASSPATH and the shared-library list, to show whether an app can load VehicleInfoManager.
#
# DO IT PARKED: car on, stationary, foot firmly on the brake the whole time. Then follow the toasts
# (or a stopwatch from the moment you tap Yes):
#   0-15 s  stay in P      15-30 s  shift to R      30-45 s  shift back to P and stay
# Output: NEMESIS-diag/park-probe/. Removes the root NEMESIS.data when it starts.
export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin${BUNDLE_TEST_SYS:+:$BUNDLE_TEST_SYS}
export LD_LIBRARY_PATH=/vendor/lib:/system/lib
USB=$(pwd)
P=$USB/NEMESIS-diag/park-probe
TOTAL=${PARK_PROBE_TOTAL:-45}
mkdir -p "$P" || exit 1
L=$P/probe.log
note() { echo "[$(date '+%H:%M:%S')] $1" >> "$L"; }
toast() {
    am broadcast -a com.hkmc.intent.action.TOAST -e com.hkmc.intent.extra.TOAST_MESSAGE "$1" \
        --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true >/dev/null 2>&1
}
if [ -f "$USB/NEMESIS.data" ] && cmp "$USB/NEMESIS.data" "$USB/tools/park-probe/NEMESIS.data" >/dev/null 2>&1; then
    rm -f "$USB/NEMESIS.data" && note "removed root NEMESIS.data"
fi
if [ "$(getprop sys.boot_completed)" != "1" ]; then
    note "Android is not up; nothing done."
    exit 1
fi
BUILD=$(getprop ro.build.version.incremental)
note "park probe start; build $(getprop ro.build.product) / $BUILD"
echo "BOOTCLASSPATH=$BOOTCLASSPATH" > "$P/classpath.txt"
pm list libraries >> "$P/classpath.txt" 2>&1
service list > "$P/service-list.txt" 2>&1
GUARD_SVC=no
while IFS= read -r _l; do
    case "$_l" in *"com.hkmc.micom.system: [com.hkmc.micom.ISystemService]"*) GUARD_SVC=yes ;; esac
done < "$P/service-list.txt"
if [ "$GUARD_SVC" != yes ] || [ "$BUILD" != "eng.vmadmin.20260206.060005" ]; then
    note "guard FAILED (service=$GUARD_SVC build=$BUILD): nothing called"
    sync
    exit 1
fi
note "guards passed"
START=$(date +%s)
t=0
phase=""
while [ "$t" -lt "$TOTAL" ]; do
    if [ "$t" -lt 15 ]; then want="P"
    elif [ "$t" -lt 30 ]; then want="R"
    else want="P-again"; fi
    if [ "$want" != "$phase" ]; then
        phase=$want
        note "PHASE $phase at +${t}s"
        toast "Park probe: foot on the brake - shift to ${phase%-again} now"
    fi
    _p=$(service call com.hkmc.micom.system 36 2>&1)
    echo "+${t}s phase=$phase getGearPStatus: $_p" >> "$P/readings.txt"
    sleep 1
    t=$(( $(date +%s) - START ))
done
logcat -d -v time > "$P/logcat-all.txt" 2>&1
grep -i -E "onGearPStatus|Gear P :|Gear status changed|SYS_GEAR_STATUS|VCU_GearPosSta|SystemService.addListener" \
    "$P/logcat-all.txt" > "$P/logcat-gear.txt" 2>&1
rm -f "$P/logcat-all.txt"
toast "Park probe done: you can let go of the brake"
note "park probe end"
sync
exit 0
