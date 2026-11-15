#!/system/bin/sh
# camera_probe.sh - READ-ONLY, about two minutes. How can the receiver tell the reversing camera is on
# screen (OD-117)? While the owner shifts to R and back, it records the camera-related broadcasts and log
# lines and which window is on top each second. It only reads: logcat, dumpsys and service list. Nothing on
# the unit is written or changed. Guarded to build 260206.
#
# RUN: car in Park and switched on (ready), in a safe place, foot on the brake. Tap Yes. When the toast says
# "shift to R", shift to R for about 10 s, then back to P. Wait for "Camera probe done".
# Output: NEMESIS-diag/camera-probe/. Removes the root NEMESIS.data when it starts.
export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin${BUNDLE_TEST_SYS:+:$BUNDLE_TEST_SYS}
export LD_LIBRARY_PATH=/vendor/lib:/system/lib
USB=$(pwd)
P=$USB/NEMESIS-diag/camera-probe
SECONDS_TOTAL=${CAMERA_PROBE_SECONDS:-120}
mkdir -p "$P" || exit 1
L=$P/probe.log
note() { echo "[$(date '+%H:%M:%S')] $1" >> "$L"; }
toast() {
    am broadcast -a com.hkmc.intent.action.TOAST -e com.hkmc.intent.extra.TOAST_MESSAGE "$1" \
        --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true >/dev/null 2>&1
}
PAT="REVERSE|REARVIEW|RearView|REAR_CAMERA|CAMERA STATUS|CAMERA_SVM|AROUNDVIEW|AVM_STATE|PgsService|ModeManagerService|ACC_STATUS"
if [ -f "$USB/NEMESIS.data" ] && cmp "$USB/NEMESIS.data" "$USB/tools/camera-probe/NEMESIS.data" >/dev/null 2>&1; then
    rm -f "$USB/NEMESIS.data" && note "removed root NEMESIS.data"
fi
if [ "$(getprop sys.boot_completed)" != "1" ]; then
    note "Android is not up; nothing done."
    exit 1
fi
BUILD=$(getprop ro.build.version.incremental)
note "camera probe start; build $(getprop ro.build.product) / $BUILD"
if [ "$BUILD" != "eng.vmadmin.20260206.060005" ]; then
    note "guard FAILED (build=$BUILD): nothing read"
    sync
    exit 1
fi
note "guard passed"

service list 2>&1 | grep -i -E "mode|pgs|camera|micom" > "$P/services.txt"
logcat -v time -f "$P/logcat-all.tmp" &
LOGPID=$!
note "logcat filter started"
toast "Camera probe: shift to R for 10 s, then back to P"
i=0
while [ $i -lt $SECONDS_TOTAL ]; do
    t=$(date '+%H:%M:%S')
    f=$(dumpsys window windows 2>&1 | grep -E "mCurrentFocus|mFocusedApp")
    echo "$t $f" >> "$P/focus.txt"
    case $((i % 5)) in
        0) echo "== $t" >> "$P/windows.txt"
           dumpsys window windows 2>&1 | grep -E "^  Window #" >> "$P/windows.txt" ;;
    esac
    sleep 1
    i=$((i + 1))
done
kill $LOGPID 2>/dev/null
sleep 1
grep -E "$PAT" "$P/logcat-all.tmp" > "$P/logcat-camera.txt" 2>> "$L"
logcat -d -v time 2>&1 | grep -E "$PAT" > "$P/logcat-camera-dump.txt"
rm -f "$P/logcat-all.tmp"
dumpsys activity broadcasts 2>&1 | grep -E "$PAT" > "$P/broadcasts.txt"
note "sampled $i seconds"
toast "Camera probe done"
note "camera probe end"
sync
exit 0
