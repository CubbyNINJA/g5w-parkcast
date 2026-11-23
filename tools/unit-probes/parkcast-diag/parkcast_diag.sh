#!/system/bin/sh
# parkcast_diag.sh - about 2.5 minutes. Why the first S3 run showed a black screen and left no receiver
# log (2026-11-23): the iPhone connected and cast, but no picture appeared and
# /data/nemesis/logs/airplay-receiver/ stayed empty.
# It reads: logcat (filtered to ParkCast, the decoder, Park and permission lines), ps, ls -l of the log
# folders and ParkCast's data folder (names only), ParkCast's open files, its ports in /proc/net, the
# SELinux mode, the focused window, kernel avc lines and the tombstone list. It copies receiver.log and
# receiver.log.1 if they exist. It never copies the identity key or reads Wi-Fi config files.
# The one thing it does besides reading: if ParkCast isn't running, it starts it exactly as the install
# script does (am start -n parkcast.receiver/.ReceiverActivity). It changes nothing else. Guarded to
# build 260206.
#
# RUN: parked in P, CarPlay disconnected (iPhone Bluetooth off), iPhone on the car's Wi-Fi. Tap Yes.
# When the toast says "cast now", mirror from the iPhone to ParkCast and keep casting until
# "ParkCast diag done" (120 s). Output: NEMESIS-diag/parkcast-diag/. Removes the root NEMESIS.data
# when it starts.
export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin${BUNDLE_TEST_SYS:+:$BUNDLE_TEST_SYS}
export LD_LIBRARY_PATH=/vendor/lib:/system/lib
USB=$(pwd)
P=$USB/NEMESIS-diag/parkcast-diag
SYS=${PARKCAST_DIAG_SYS:-}
WAIT=${PARKCAST_DIAG_SECONDS:-120}
STEP=${PARKCAST_DIAG_STEP:-10}
PKG=parkcast.receiver
LOGDIR=$SYS/data/nemesis/logs/airplay-receiver
mkdir -p "$P" || exit 1
L=$P/probe.log
note() { echo "[$(date '+%H:%M:%S')] $1" >> "$L"; }
toast() {
    am broadcast -a com.hkmc.intent.action.TOAST -e com.hkmc.intent.extra.TOAST_MESSAGE "$1" \
        --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true >/dev/null 2>&1
}
if [ -f "$USB/NEMESIS.data" ] && cmp "$USB/NEMESIS.data" "$USB/tools/parkcast-diag/NEMESIS.data" >/dev/null 2>&1; then
    rm -f "$USB/NEMESIS.data" && note "removed root NEMESIS.data"
fi
if [ "$(getprop sys.boot_completed)" != "1" ]; then
    note "Android is not up; nothing done."
    exit 1
fi
BUILD=$(getprop ro.build.version.incremental)
note "parkcast diag start; build $(getprop ro.build.product) / $BUILD"
if [ "$BUILD" != "eng.vmadmin.20260206.060005" ]; then
    note "guard FAILED (build=$BUILD): nothing read"
    sync
    exit 1
fi
note "guard passed"

# ParkCast's pid, or empty. Toolbox has no awk or cut: split the ps line with set.
pid_of() {
    line=$(ps 2>/dev/null | grep -E " $PKG\$")
    [ -n "$line" ] || return 0
    set -- $line
    echo "$2"
}
# Everything about ParkCast's state at one moment, appended to $1.
snapshot() {
    out=$1
    echo "== $(date '+%H:%M:%S')" >> "$out"
    pid=$(pid_of)
    echo "pid=${pid:-none}" >> "$out"
    if [ -n "$pid" ]; then
        grep -E "^(State|Uid|Gid|Threads):" "$SYS/proc/$pid/status" >> "$out" 2>&1
        echo "-- open files" >> "$out"
        ls -l "$SYS/proc/$pid/fd" 2>&1 | grep -v -E "> (/dev/(null|binder|ashmem|log/.*|alarm)|pipe:|anon_inode:)" >> "$out"
    fi
    echo "-- ports 35000-35002 (tcp, then udp)" >> "$out"
    grep -i -E ":88B[89A] " "$SYS/proc/net/tcp" >> "$out" 2>&1
    grep -i -E ":88B[89A] " "$SYS/proc/net/udp" >> "$out" 2>&1
    echo "-- log folder" >> "$out"
    ls -l "$LOGDIR" >> "$out" 2>&1
    echo "-- focus" >> "$out"
    dumpsys window windows 2>&1 | grep -E "mCurrentFocus|mFocusedApp" >> "$out"
}

# Before anything starts: who may enter the log folders, SELinux, and what the logcat buffer still holds.
ls -ld "$SYS/data/nemesis" "$SYS/data/nemesis/logs" "$LOGDIR" > "$P/log-folder-perms.txt" 2>&1
ls -l "$LOGDIR" >> "$P/log-folder-perms.txt" 2>&1
getenforce > "$P/selinux.txt" 2>&1
ls -ld "$SYS/data/data/$PKG" > "$P/app-data.txt" 2>&1
ls -lR "$SYS/data/data/$PKG" >> "$P/app-data.txt" 2>&1
grep -E " $PKG\$|^$PKG " "$SYS/data/system/packages.list" > "$P/package-uid.txt" 2>&1
PAT="parkcast|AndroidRuntime|FATAL|Permission Denial|SecurityException|avc:|libparkcast|linker|JNI|OMX|ACodec|MediaCodec|TCC|VehicleInfo|GearP|micom|egulation|ACC_STATUS|REVERSE_ON|mdns|NsdService|WindowManager.*(toast|Toast|BadToken)|DEBUG|tombstone"
logcat -d -v threadtime 2>&1 | grep -E "$PAT" > "$P/logcat-before.txt"
snapshot "$P/timeline.txt"

logcat -v threadtime -f "$P/logcat-all.tmp" &
LOGPID=$!
note "logcat capture started"
PIDS=$(pid_of)
if [ -z "$PIDS" ]; then
    am start -n "$PKG/.ReceiverActivity" > "$P/am-start.txt" 2>&1
    note "ParkCast was not running; started it (exit $?)"
    sleep 5
else
    note "ParkCast already running, pid $PIDS"
fi
toast "ParkCast diag: cast now from the iPhone, keep casting until 'done'"
i=0
while [ $i -lt $WAIT ]; do
    sleep $STEP
    i=$((i + STEP))
    snapshot "$P/timeline.txt"
    pid=$(pid_of)
    case " $PIDS " in
        *" $pid "*) ;;
        *) [ -n "$pid" ] && PIDS="$PIDS $pid" && note "ParkCast pid now $pid" ;;
    esac
done
kill $LOGPID 2>/dev/null
sleep 1
note "ParkCast pids seen: ${PIDS:-none}"

# ParkCast's own lines by pid (threadtime puts the pid in the third column), plus the pattern above.
PIDPAT=""
for p in $PIDS; do PIDPAT="$PIDPAT|^[0-9-]+ [0-9:.]+ +$p "; done
grep -E "$PAT$PIDPAT" "$P/logcat-all.tmp" > "$P/logcat-run.txt" 2>> "$L"
rm -f "$P/logcat-all.tmp"
dmesg 2>&1 | grep -E "avc:|parkcast" > "$P/kernel-avc.txt"
ls -l "$SYS/data/tombstones" > "$P/tombstones.txt" 2>&1
ps 2>&1 | grep -E "parkcast|mediaserver|mdnsd|USER " > "$P/ps.txt"
for f in receiver.log receiver.log.1; do
    if [ -f "$LOGDIR/$f" ]; then
        cp "$LOGDIR/$f" "$P/$f" && note "copied $f"
    else
        note "no $f"
    fi
done
toast "ParkCast diag done"
note "parkcast diag end"
sync
exit 0
