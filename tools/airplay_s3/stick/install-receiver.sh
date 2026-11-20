#!/system/bin/sh
# ParkCast S3 receiver install (AIR-078). A NEMESIS `execute` run script: run as root from the stick's root
# after the owner taps Yes. It installs airplay.receiver.zip from the stick root through NEMESIS (never
# `pm install` directly), checks that NEMESIS and Android both list it, prepares the receiver's log
# directory (/data/nemesis/logs/airplay-receiver, owned by the app's uid, mode 0700), then starts the
# receiver. Any failure stops the script there and exits non-zero. Everything every command prints is
# kept in airplay-receiver-install.log on the stick. Toolbox only: no awk, sed, printf, busybox or
# here-documents (mksh writes those to a temp file on the unit).
#
# PARKCAST_TEST_ROOT is for the host tests only. It is unset on the unit, so every path below is the stock
# one. The tests set it to a fake unit root holding fake toolbox commands.
ROOT=${PARKCAST_TEST_ROOT:-}
PATH=$ROOT/data/nemesis/bin:$ROOT/sbin:$ROOT/vendor/bin:$ROOT/system/sbin:$ROOT/system/bin:$ROOT/system/xbin
LD_LIBRARY_PATH=/vendor/lib:/system/lib
export PATH LD_LIBRARY_PATH

ID=airplay.receiver
PKG=parkcast.receiver
# The one activity in receiver/app/src/main/AndroidManifest.xml (not exported; started by name as root).
COMPONENT=$PKG/.ReceiverActivity
DIR=$ROOT/data/nemesis/logs/airplay-receiver
PLIST=$ROOT/data/system/packages.list
USB=$(pwd)
ZIP=$USB/$ID.zip
LOG=$USB/airplay-receiver-install.log
TMP=$USB/.airplay-receiver-install.tmp

say() {
  echo "$*"
  echo "$*" >> "$LOG"
}

fail() {
  say "$*"
  rm "$TMP" 2>/dev/null
  say "install script failed"
  exit 1
}

# Run one command, keep its stdout and stderr in $TMP and in the stick log, and echo it to the console.
run() {
  "$@" > "$TMP" 2>&1
  rc=$?
  echo "\$ $*" >> "$LOG"
  cat "$TMP"
  cat "$TMP" >> "$LOG"
  echo "exit=$rc" >> "$LOG"
  return $rc
}

say "install script started"
[ -f "$ZIP" ] || fail "install failed: $ID.zip is not at the root of the stick"

# 1. Install through NEMESIS. Its exit code alone is not enough: a 1.16 metadata refusal or a pm Failure
# can come back with exit 0.
run nemesis install --update "$ZIP" || fail "install failed: nemesis install exited non-zero"
out=$(cat "$TMP")
case "$out" in
  *"invalid package metadata"*) fail "install failed: NEMESIS refused the package metadata" ;;
  *Failure*) fail "install failed: the package manager reported a Failure" ;;
esac

# 2. NEMESIS must list the package: a pkg-list line whose first field is exactly the id.
run nemesis pkg-list || fail "install check failed: nemesis pkg-list exited non-zero"
found=0
while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in
    "$ID"|"$ID|"*) found=1 ;;
  esac
done < "$TMP"
[ "$found" = 1 ] || fail "install check failed: nemesis pkg-list does not list $ID"

# 3. Android must list the receiver's package: a line exactly "package:<package>".
run pm list packages || fail "install check failed: pm list packages exited non-zero"
found=0
while IFS= read -r line || [ -n "$line" ]; do
  [ "$line" = "package:$PKG" ] && found=1
done < "$TMP"
[ "$found" = 1 ] || fail "install check failed: pm list packages does not list $PKG"

# 4. Directory step: the receiver's uid from packages.list, then the log directory owned by it, mode 0700.
uid=
count=0
if [ -f "$PLIST" ]; then
  while read -r name puid rest || [ -n "$name" ]; do
    if [ "$name" = "$PKG" ]; then
      uid=$puid
      count=$((count + 1))
    fi
  done < "$PLIST"
fi
case "$uid" in
  ""|*[!0-9]*) uid= ;;
esac
if [ "$count" != 1 ] || [ -z "$uid" ]; then
  fail "directory step failed: the uid of $PKG is not in /data/system/packages.list"
fi
say "uid of $PKG is $uid"
if [ ! -d "$DIR" ]; then
  run mkdir -p "$DIR" || fail "directory step failed: cannot make /data/nemesis/logs/airplay-receiver"
fi
run chown "$uid.$uid" "$DIR" || run chown "$uid:$uid" "$DIR" \
  || fail "directory step failed: cannot give /data/nemesis/logs/airplay-receiver to uid $uid"
run chmod 0700 "$DIR" || fail "directory step failed: cannot set mode 0700 on /data/nemesis/logs/airplay-receiver"

# 5. Start the receiver. KitKat am prints "Error: ..." and still exits 0 when the activity is missing.
run am start -n "$COMPONENT" || fail "start failed: am start exited non-zero"
out=$(cat "$TMP")
case "$out" in
  *Error*) fail "start failed: am start reported an error" ;;
esac

rm "$TMP" 2>/dev/null
say "install script complete: $ID installed, log directory ready, receiver started"
exit 0
