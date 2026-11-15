#!/system/bin/sh
# sleep_probe.sh - READ-ONLY. How the head unit reports going to sleep and waking (AMB-62): records
# the ACC-status sticky broadcast, the power service's state getters and the uptime, once before the
# car is switched off and once after it is switched back on, so the two can be compared.
# It reads: getprop, /proc/uptime, `dumpsys activity broadcasts` (the sticky ACC intent), logcat,
# and two read-only getters on com.hkmc.micom.power: getPowerStatus (txn 1) and
# isPreparingSystemOff (txn 18) (docs/evidence/SLEEP_SIGNAL_FINDINGS.md). Nothing else is called and
# nothing is sent to the car. Guarded to build 260206 and to com.hkmc.micom.power being listed.
#
# RUN 1: car on, parked. Tap Yes. When it says so, switch the car OFF, wait 30 seconds, switch it ON.
# RUN 2: once the car is back on, unplug the stick and plug it back in, then tap Yes again.
# Run 2 removes the root NEMESIS.data when it finishes. Output: NEMESIS-diag/sleep-probe/.
export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin${BUNDLE_TEST_SYS:+:$BUNDLE_TEST_SYS}
export LD_LIBRARY_PATH=/vendor/lib:/system/lib
USB=$(pwd)
P=$USB/NEMESIS-diag/sleep-probe
mkdir -p "$P" || exit 1
L=$P/probe.log
note() { echo "[$(date '+%H:%M:%S')] $1" >> "$L"; }
toast() {
    am broadcast -a com.hkmc.intent.action.TOAST -e com.hkmc.intent.extra.TOAST_MESSAGE "$1" \
        --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true >/dev/null 2>&1
}
if [ "$(getprop sys.boot_completed)" != "1" ]; then
    note "Android is not up; nothing done."
    exit 1
fi
BUILD=$(getprop ro.build.version.incremental)
service list > "$P/service-list.txt" 2>&1
GUARD_SVC=no
while IFS= read -r _l; do
    case "$_l" in *"com.hkmc.micom.power: ["*) GUARD_SVC=yes ;; esac
done < "$P/service-list.txt"
if [ "$GUARD_SVC" != yes ] || [ "$BUILD" != "eng.vmadmin.20260206.060005" ]; then
    note "guard FAILED (service=$GUARD_SVC build=$BUILD): nothing read"
    sync
    exit 1
fi

if [ -f "$P/run1-done" ]; then RUN=2; else RUN=1; fi
D=$P/run$RUN
mkdir -p "$D"
note "sleep probe run $RUN start; build $(getprop ro.build.product) / $BUILD"
cat /proc/uptime > "$D/uptime.txt" 2>&1
date '+%Y-%m-%d %H:%M:%S' > "$D/wallclock.txt" 2>&1
dumpsys activity broadcasts > "$D/broadcasts-all.txt" 2>&1
grep -i -E "ACC_STATUS|SYSTEM_POWER|QUICKBOOT|SCREEN_O" "$D/broadcasts-all.txt" > "$D/broadcasts.txt" 2>&1
grep -A6 "ACTION_ACC_STATUS_CHANGED" "$D/broadcasts-all.txt" > "$D/acc-sticky.txt" 2>&1
rm -f "$D/broadcasts-all.txt"
service call com.hkmc.micom.power 1 > "$D/getPowerStatus.txt" 2>&1
service call com.hkmc.micom.power 18 > "$D/isPreparingSystemOff.txt" 2>&1
getprop | grep -i -E "quickboot|avpowerstatus|disppwr|boot_completed|sys\.boot" > "$D/props.txt" 2>&1
logcat -d -v time > "$D/logcat-all.txt" 2>&1
grep -E "MicomPowerService|ACC_STATUS|ACC Status|Preparing System Off|onAccO|System Power Off|QUICKBOOT|ShutdownThread" \
    "$D/logcat-all.txt" > "$D/logcat-power.txt" 2>&1
rm -f "$D/logcat-all.txt"
logcat -b events -d 2>/dev/null | grep "1500003" > "$D/events-power.txt" 2>&1

if [ "$RUN" = 1 ]; then
    : > "$P/run1-done"
    note "run 1 done; root NEMESIS.data kept for run 2"
    toast "Sleep probe 1 of 2 done: switch the car OFF, wait 30 s, switch it ON, then replug the stick"
else
    if [ -f "$USB/NEMESIS.data" ] && cmp "$USB/NEMESIS.data" "$USB/tools/sleep-probe/NEMESIS.data" >/dev/null 2>&1; then
        rm -f "$USB/NEMESIS.data" && note "removed root NEMESIS.data"
    fi
    rm -f "$P/run1-done"
    note "run 2 done"
    toast "Sleep probe done"
fi
sync
exit 0
