#!/system/bin/sh
# ParkCast S3 log copy (AIR-074, AIR-079(e), AIR-083(e)). A NEMESIS `execute` run script: run as root
# from the stick's root after the owner taps Yes. Read-only on the unit: it copies the receiver's logs to
# NEMESIS-diag/airplay-receiver/<timestamp>/ on the stick, places an empty owner-note.txt and the blank
# run-checklist.txt beside them, and writes nothing on the unit. Toolbox only: no awk, sed, printf,
# busybox or here-documents (mksh writes those to a temp file on the unit).
#
# PARKCAST_TEST_ROOT is for the host tests only. It is unset on the unit, so every path below is the stock
# one. The tests set it to a fake unit root holding fake toolbox commands.
ROOT=${PARKCAST_TEST_ROOT:-}
PATH=$ROOT/data/nemesis/bin:$ROOT/sbin:$ROOT/vendor/bin:$ROOT/system/sbin:$ROOT/system/bin:$ROOT/system/xbin
LD_LIBRARY_PATH=/vendor/lib:/system/lib
export PATH LD_LIBRARY_PATH

SRC=$ROOT/data/nemesis/logs/airplay-receiver
USB=$(pwd)
PARENT=$USB/NEMESIS-diag/airplay-receiver
TS=$(date +%Y%m%d-%H%M%S)
RUN=$PARENT/$TS
STAGE=$PARENT/.blank-$TS
LOG=$PARENT/copy-logs-$TS.txt

say() {
  echo "$*"
  echo "$*" >> "$LOG" 2>/dev/null
}

if [ -z "$TS" ]; then
  echo "log copy failed: no timestamp from date"
  exit 1
fi
if [ ! -d "$SRC" ]; then
  echo "log copy failed: no receiver log directory at /data/nemesis/logs/airplay-receiver"
  exit 1
fi
if ! mkdir -p "$PARENT"; then
  echo "log copy failed: cannot make $PARENT on the stick"
  exit 1
fi

# AIR-074(g): a run folder that is already there is never copied into, changed or added to.
if [ -e "$RUN" ]; then
  say "run folder $TS exists on the stick; nothing copied into it"
  exit 1
fi
if ! mkdir "$RUN"; then
  if [ -e "$RUN" ]; then
    say "run folder $TS exists on the stick; nothing copied into it"
  else
    say "log copy failed: cannot make the run folder $TS on the stick"
  fi
  exit 1
fi
say "run folder $TS made"

# AIR-074(a), (b): copy each regular log file. A symlink is never followed, so nothing outside the log
# directory (such as the identity key in app data, AIR-083(e)) can reach the stick through it.
INCOMPLETE=0
COPIED=0
for f in "$SRC"/*; do
  name=${f##*/}
  if [ -L "$f" ]; then
    say "skipped $name: a link, not a log file"
    continue
  fi
  [ -f "$f" ] || continue
  if cp "$f" "$RUN/$name"; then
    COPIED=$((COPIED + 1))
    say "copied $name"
  else
    INCOMPLETE=1
    say "log copy incomplete: copying $name failed; the copy stopped here"
    break
  fi
done
NOLOGS=0
if [ "$INCOMPLETE" = 0 ] && [ "$COPIED" = 0 ]; then
  NOLOGS=1
  say "log copy failed: no log files in /data/nemesis/logs/airplay-receiver"
fi

# AIR-074(d) to (f): place the two blank files, even after a part-way copy. A file that appeared in the
# run folder during the run is left exactly as it is.
PLACEFAIL=0
mkdir "$STAGE" 2>/dev/null
: > "$STAGE/owner-note.txt" 2>/dev/null
{
  echo "parkcast listed: "
  echo "letterboxed: "
  echo "sound on unit: "
  echo "stayed behind other app: "
  echo "nothing on cluster or hud: "
  echo "carplay after exit: "
} > "$STAGE/run-checklist.txt" 2>/dev/null
for name in owner-note.txt run-checklist.txt; do
  if [ -e "$RUN/$name" ]; then
    say "left $name: it was already in the run folder"
  elif [ -f "$STAGE/$name" ] && cp "$STAGE/$name" "$RUN/$name"; then
    say "placed $name"
  else
    PLACEFAIL=1
    say "could not place $name in the run folder"
  fi
done
rm -r "$STAGE" 2>/dev/null

if [ "$INCOMPLETE" = 1 ]; then
  say "log copy incomplete: $COPIED file(s) copied before the failure"
  exit 1
fi
if [ "$NOLOGS" = 1 ] || [ "$PLACEFAIL" = 1 ]; then
  say "log copy failed"
  exit 1
fi
say "log copy complete: $COPIED file(s) in NEMESIS-diag/airplay-receiver/$TS"
exit 0
