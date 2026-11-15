#!/system/bin/sh
# gear_probe.sh - READ-ONLY, 90 seconds. Measures how this build reports Park, for the AirPlay
# receiver's parking gate (AIR-017, AMB-27). Once a second it calls two body-controller GETTERS
# only: getGearStatusInfo (transaction 14) and getParkBrakeStatus (7), numbers parsed from this
# unit's own automotive.odex. They run only if the service identifies as com.hkmc.bcm.IBCMService
# AND the build is exactly 260206; otherwise nothing is called. Also keeps the gear-related logcat
# lines and a dumpsys of the BCM service before and after. Commands nothing, but transaction 14
# makes the BCM service send a REQ_GEAR_STATUS request to the MICOM; neither call returns a value
# (docs/evidence/GEAR_PROBE_FINDINGS.md).
#
# DO IT PARKED: car on, stationary, foot firmly on the brake the whole time. Then follow the toasts
# (or a stopwatch from the moment you tap Yes):
#   0-15 s  stay in P      15-30 s  shift to R      30-45 s  shift to N
#   45-60 s shift to D     60-90 s  shift back to P and stay
# Output: NEMESIS-diag/gear-probe/. Removes the root NEMESIS.data when it starts.

export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin${BUNDLE_TEST_SYS:+:$BUNDLE_TEST_SYS}
export LD_LIBRARY_PATH=/vendor/lib:/system/lib

USB=$(pwd)
P=$USB/NEMESIS-diag/gear-probe
TOTAL=${GEAR_PROBE_TOTAL:-90}
mkdir -p "$P" || exit 1
L=$P/probe.log
note() { echo "[$(date '+%H:%M:%S')] $1" >> "$L"; }
toast() {
    am broadcast -a com.hkmc.intent.action.TOAST -e com.hkmc.intent.extra.TOAST_MESSAGE "$1" \
        --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true >/dev/null 2>&1
}

if [ -f "$USB/NEMESIS.data" ] && cmp "$USB/NEMESIS.data" "$USB/tools/gear-probe/NEMESIS.data" >/dev/null 2>&1; then
    rm -f "$USB/NEMESIS.data" && note "removed root NEMESIS.data"
fi
if [ "$(getprop sys.boot_completed)" != "1" ]; then
    note "Android is not up; nothing done."
    exit 1
fi

BUILD=$(getprop ro.build.version.incremental)
note "gear probe start; build $(getprop ro.build.product) / $BUILD"
# Build and version properties, for the firmware-family check (OD-47). Read-only; keys that
# could identify the unit or car (serial, IMEI, MAC, VIN) are left out.
getprop | grep -i -E "build|version|cvev|firmware|\.sw\." | grep -v -i -E "serial|imei|mac|vin|bt\.addr|wifi" > "$P/build-props.txt" 2>&1
service list > "$P/service-list.txt" 2>&1
GUARD_SVC=no
while IFS= read -r _l; do
    case "$_l" in *"com.hkmc.micom.bcm: [com.hkmc.bcm.IBCMService]"*) GUARD_SVC=yes ;; esac
done < "$P/service-list.txt"
if [ "$GUARD_SVC" != yes ] || [ "$BUILD" != "eng.vmadmin.20260206.060005" ]; then
    note "guard FAILED (service=$GUARD_SVC build=$BUILD): nothing called"
    sync
    exit 1
fi
note "guards passed"
dumpsys com.hkmc.micom.bcm > "$P/dumpsys-bcm-before.txt" 2>&1

START=$(date +%s)
t=0
phase=""
while [ "$t" -lt "$TOTAL" ]; do
    if [ "$t" -lt 15 ]; then want="P"
    elif [ "$t" -lt 30 ]; then want="R"
    elif [ "$t" -lt 45 ]; then want="N"
    elif [ "$t" -lt 60 ]; then want="D"
    else want="P-again"; fi
    if [ "$want" != "$phase" ]; then
        phase=$want
        note "PHASE $phase at +${t}s"
        toast "Gear probe: foot on the brake - shift to ${phase%-again} now"
    fi
    _g=$(service call com.hkmc.micom.bcm 14 2>&1)
    _b=$(service call com.hkmc.micom.bcm 7 2>&1)
    echo "+${t}s phase=$phase getGearStatusInfo: $_g | getParkBrakeStatus: $_b" >> "$P/readings.txt"
    sleep 1
    t=$(( $(date +%s) - START ))
done

dumpsys com.hkmc.micom.bcm > "$P/dumpsys-bcm-after.txt" 2>&1
logcat -d -v time > "$P/logcat-all.txt" 2>&1
grep -i -E "gear|onGearStatus|parkbrake|ParkBrake|getGearPStatus|BCMManager" "$P/logcat-all.txt" > "$P/logcat-gear.txt" 2>&1
rm -f "$P/logcat-all.txt"
toast "Gear probe done: you can let go of the brake"
note "gear probe end"
sync
exit 0
