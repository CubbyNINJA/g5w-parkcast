#!/system/bin/sh
# ParkCast S3 parkcast-diag (AIR-089, OD-202 to OD-204). A NEMESIS `execute` run script: run as root from
# the stick's root after the owner taps Yes. It copies the receiver's raw file (uxplay-raw.log, in app data)
# and both receiver logs to NEMESIS-diag/parkcast-diag/<timestamp>/ on the stick, with a listing of the
# receiver's log folder and a few read-only state notes (ParkCast's process, its open files, ports
# 35000-35002, the focused window, SELinux, avc lines and tombstones) and ParkCast's logcat lines.
# The only change it makes on the unit is starting ParkCast when it isn't running, exactly as the install
# does. It reads no other file in app data, lists no app-data folder, and never copies the identity key
# (AIR-083(d), (e)). Every logcat call pipes straight into grep, so unfiltered logcat never reaches the
# stick, even for a moment (tools/airplay_s3/stickcheck.py checks this). Guarded to build 260206.
# Toolbox only: no awk, sed, printf, busybox or here-documents (mksh writes those to a temp file on the
# unit); plain `mv` if ever needed, since toolbox `mv` takes no options.
#
# Stick layout: tools/parkcast-diag/parkcast-diag.sh (this file) and tools/parkcast-diag/NEMESIS.data:
#   [nemesis]
#   version=1.0
#   type=execute
#   path=tools/parkcast-diag/parkcast-diag.sh
# RUN: parked in P, iPhone Bluetooth off (no CarPlay), iPhone on the car's Wi-Fi. Copy
# tools/parkcast-diag/NEMESIS.data to the stick's root, insert the stick and tap Yes. The script removes
# the root NEMESIS.data first, so the next insert does not run it again. Output:
# NEMESIS-diag/parkcast-diag/<timestamp>/ and the log parkcast-diag-<timestamp>.txt beside it; the log's
# last line is "parkcast diag end". On the PC, keep the output under runs/ and never commit it: the
# receiver's records can name the iPhone.
#
# PARKCAST_TEST_ROOT is for the host tests only. It is unset on the unit, so every path below is the stock
# one. The tests set it to a fake unit root holding fake toolbox commands.
ROOT=${PARKCAST_TEST_ROOT:-}
PATH=$ROOT/data/nemesis/bin:$ROOT/sbin:$ROOT/vendor/bin:$ROOT/system/sbin:$ROOT/system/bin:$ROOT/system/xbin
LD_LIBRARY_PATH=/vendor/lib:/system/lib
export PATH LD_LIBRARY_PATH

USB=$(pwd)

# AIR-089(e): the root NEMESIS.data goes first, before anything is queried, so the tool runs once per copy.
REMOVED=""
if [ -f "$USB/NEMESIS.data" ] && cmp -s "$USB/NEMESIS.data" "$USB/tools/parkcast-diag/NEMESIS.data"; then
  if rm -f "$USB/NEMESIS.data"; then
    REMOVED="removed the root NEMESIS.data"
  else
    REMOVED="could not remove the root NEMESIS.data"
  fi
fi

PKG=parkcast.receiver
BUILD_OK=eng.vmadmin.20260206.060005
APP=$ROOT/data/data/$PKG/files
LOGDIR=$ROOT/data/nemesis/logs/airplay-receiver
PARENT=$USB/NEMESIS-diag/parkcast-diag
TS=$(date +%Y%m%d-%H%M%S)
RUN=$PARENT/$TS
LOG=$PARENT/parkcast-diag-$TS.txt
# Lines naming anything in app data are dropped from every note, except the raw file's own descriptor.
NOAPP="/data/data/|/data/user/"
PAT="parkcast|AndroidRuntime|FATAL|Permission Denial|SecurityException|avc:|libparkcast|linker|JNI|OMX|ACodec|MediaCodec|TCC|VehicleInfo|GearP|micom|egulation|ACC_STATUS|REVERSE_ON|mdns|NsdService|WindowManager.*(toast|Toast|BadToken)|DEBUG|tombstone"

say() {
  echo "$*"
  echo "$*" >> "$LOG" 2>/dev/null
}

# AIR-089(f): every run that has a log ends it with this line, after every copy and listing.
finish() {
  say "parkcast diag end"
  sync
  exit "$1"
}

if [ -z "$TS" ]; then
  echo "parkcast diag failed: no timestamp from date"
  exit 1
fi
if ! mkdir -p "$PARENT"; then
  echo "parkcast diag failed: cannot make $PARENT on the stick"
  exit 1
fi
[ -n "$REMOVED" ] && say "$REMOVED"

# AIR-089(e): the build guard, as the probes do it. On another build nothing is read, copied or started.
if [ "$(getprop sys.boot_completed)" != "1" ]; then
  say "build guard failed: Android is not up; nothing read"
  finish 1
fi
BUILD=$(getprop ro.build.version.incremental)
if [ "$BUILD" != "$BUILD_OK" ]; then
  say "build guard failed: build $BUILD is not $BUILD_OK; nothing read"
  finish 1
fi
say "build guard passed: $BUILD"

# AIR-089(d): a run folder that is already there is never copied into, changed or added to.
if [ -e "$RUN" ] || [ -L "$RUN" ]; then
  say "run folder $TS already exists on the stick; nothing copied"
  finish 1
fi
if ! mkdir "$RUN"; then
  if [ -e "$RUN" ]; then
    say "run folder $TS already exists on the stick; nothing copied"
  else
    say "parkcast diag failed: cannot make the run folder $TS on the stick"
  fi
  finish 1
fi
say "run folder $TS made"

pid_of() {
  line=$(ps 2>/dev/null | grep -E " $PKG\$")
  [ -n "$line" ] || return 0
  set -- $line
  echo "$2"
}

# AIR-089(b): start ParkCast only if it isn't running; nothing else on the unit changes.
pid=$(pid_of)
if [ -z "$pid" ]; then
  am start -n "$PKG/.ReceiverActivity" > "$RUN/am-start.txt" 2>&1
  say "ParkCast was not running; started it (exit $?)"
  i=0
  while [ -z "$pid" ] && [ $i -lt 10 ]; do
    sleep 1
    i=$((i + 1))
    pid=$(pid_of)
  done
  [ -n "$pid" ] || say "ParkCast is still not running"
else
  say "ParkCast already running, pid $pid"
fi

# Read-only notes. Nothing naming app data is kept, apart from the raw file's descriptor (AIR-089(c)).
STATE=$RUN/unit-state.txt
{
  echo "-- build $BUILD"
  echo "-- selinux"
  getenforce 2>&1
  echo "-- processes"
  ps 2>&1 | grep -E "parkcast|mediaserver|mdnsd|USER "
  if [ -n "$pid" ]; then
    echo "-- ParkCast pid $pid"
    grep -E "^(State|Uid|Gid|Threads):" "$ROOT/proc/$pid/status" 2>&1
    echo "-- open files (app-data targets dropped, apart from uxplay-raw.log)"
    ls -l "$ROOT/proc/$pid/fd" 2>&1 | while read -r fdline; do
      case "$fdline" in
        *" -> /data/data/$PKG/files/uxplay-raw.log") echo "$fdline" ;;
        */data/data/*|*/data/user/*) ;;
        *) echo "$fdline" ;;
      esac
    done
  fi
  echo "-- ports 35000-35002 (tcp, then udp)"
  grep -i -E ":88B[89A] " "$ROOT/proc/net/tcp" 2>&1
  grep -i -E ":88B[89A] " "$ROOT/proc/net/udp" 2>&1
  echo "-- focus"
  dumpsys window windows 2>&1 | grep -E "mCurrentFocus|mFocusedApp" | grep -v -E "$NOAPP"
  echo "-- kernel avc"
  dmesg 2>&1 | grep -E "avc:|parkcast" | grep -v -E "$NOAPP"
  echo "-- tombstones"
  ls -l "$ROOT/data/tombstones" 2>&1
} > "$STATE" 2>/dev/null
say "wrote unit-state.txt"

# AIR-089(c): logcat goes straight into grep; only the filtered lines are written.
logcat -d -v threadtime | grep -E "$PAT" | grep -v -E "$NOAPP" > "$RUN/logcat.txt"
say "wrote logcat.txt"

# AIR-089(a), (d): each source on its own. A symlink is never followed; a missing file or a copy that
# isn't whole is reported by name, and a partial copy is removed.
FAIL=0
copy_one() {
  src=$1
  name=${src##*/}
  if [ -L "$src" ]; then
    say "$name not copied: it is a symlink"
    FAIL=1
    return
  fi
  if [ ! -f "$src" ]; then
    say "no $name"
    FAIL=1
    return
  fi
  if cp "$src" "$RUN/$name" && cmp -s "$src" "$RUN/$name"; then
    say "copied $name"
  else
    rm -f "$RUN/$name"
    say "$name not copied whole: copy failed"
    FAIL=1
  fi
}
copy_one "$APP/uxplay-raw.log"
copy_one "$LOGDIR/receiver.log"
copy_one "$LOGDIR/receiver.log.1"

# AIR-089(a): the listing of AIR-062's log folder.
if ls -l "$LOGDIR" > "$RUN/log-folder.txt" 2>&1; then
  say "wrote log-folder.txt"
else
  say "listing of the log folder failed"
  FAIL=1
fi

if [ "$FAIL" = 0 ]; then
  say "parkcast diag complete: NEMESIS-diag/parkcast-diag/$TS"
else
  say "parkcast diag incomplete: see the lines above"
fi
finish "$FAIL"
