#!/system/bin/sh
# hud_probe.sh - READ-ONLY. HUD settings and the display layout, for the AirPlay receiver project
# (docs/evidence/HUD_VIDEO_FINDINGS.md). Reads, never sets:
#   - the HUD's settings through IUSMService *getters only* (transactions 308-327, getHUD_*),
#     numbers parsed from this unit's own automotive.odex (build 260206). They run only if the service
#     still identifies as com.hkmc.usm.IUSMService AND the build is exactly 260206; otherwise skipped.
#   - dumpsys of the USM and cluster services, the display service and SurfaceFlinger (screens, sizes)
#   - framebuffer modes, HUD props
# Output: NEMESIS-diag/hud-probe/ on the stick. Removes the root NEMESIS.data when it starts.

export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin${BUNDLE_TEST_SYS:+:$BUNDLE_TEST_SYS}
export LD_LIBRARY_PATH=/vendor/lib:/system/lib

USB=$(pwd)
P=$USB/NEMESIS-diag/hud-probe
mkdir -p "$P" || exit 1
L=$P/probe.log
note() { echo "[$(date '+%H:%M:%S')] $1" >> "$L"; }

if [ -f "$USB/NEMESIS.data" ] && cmp "$USB/NEMESIS.data" "$USB/tools/hud-probe/NEMESIS.data" >/dev/null 2>&1; then
    rm -f "$USB/NEMESIS.data" && note "removed root NEMESIS.data"
fi
if [ "$(getprop sys.boot_completed)" != "1" ]; then
    note "Android is not up; nothing done."
    exit 1
fi

capture() {
    _t=$1; _f=$2; shift 2
    echo "\$ $*" > "$P/$_f"
    "$@" >> "$P/$_f" 2>&1 &
    _pid=$!
    ( sleep "$_t"; kill "$_pid" 2>/dev/null ) &
    _k=$!
    wait "$_pid"
    kill "$_k" 2>/dev/null
}

BUILD=$(getprop ro.build.version.incremental)
note "hud probe start; build $(getprop ro.build.product) / $BUILD"

# ---- HUD settings through getters only --------------------------------------
service list > "$P/service-list.txt" 2>&1
GUARD_SVC=no
while IFS= read -r _l; do
    case "$_l" in *"com.hkmc.micom.usm: [com.hkmc.usm.IUSMService]"*) GUARD_SVC=yes ;; esac
done < "$P/service-list.txt"
if [ "$GUARD_SVC" = yes ] && [ "$BUILD" = "eng.vmadmin.20260206.060005" ]; then
    note "guards passed: reading HUD getters 308-327"
    for _pair in 308:Type 309:Enable 310:ModeStatus 311:Height 312:Rotation 313:Brightness \
                 314:TurnbyTurn 315:TrafficInfo 316:DrivingAssistInfo 317:LaneSafetyInfo \
                 318:BlindSpotSafetyInfo 319:AVInfo 320:SpeedNumberSize 321:SpeedColor \
                 322:InSportMode 323:Speed 324:ARLeftRight 325:ARView 326:InSportMode2 327:IMSAutoSaveStatus; do
        _code=${_pair%%:*}; _name=${_pair##*:}
        echo "getHUD_$_name ($_code): $(service call com.hkmc.micom.usm "$_code" 2>&1)" >> "$P/hud-getters.txt"
    done
else
    note "guard FAILED (service=$GUARD_SVC build=$BUILD): HUD getters skipped, nothing called"
fi

# ---- services and display layout --------------------------------------------
capture 15 dumpsys-usm.txt dumpsys com.hkmc.micom.usm
capture 15 dumpsys-cluster.txt dumpsys com.hkmc.micom.cluster
capture 15 dumpsys-display.txt dumpsys display
capture 20 dumpsys-surfaceflinger.txt dumpsys SurfaceFlinger
capture 10 graphics-ls.txt ls -l /dev/graphics /sys/class/graphics
for _fb in /sys/class/graphics/fb*; do
    [ -d "$_fb" ] || continue
    for _a in name modes mode virtual_size bits_per_pixel; do
        [ -f "$_fb/$_a" ] && echo "$_fb/$_a: $(cat "$_fb/$_a" 2>&1)" >> "$P/framebuffers.txt"
    done
done
getprop > "$P/getprop-all.txt" 2>&1
while IFS= read -r _l; do
    case "$_l" in *hud*|*HUD*|*display*|*lcd*|*density*) echo "$_l" >> "$P/props-hud-display.txt" ;; esac
done < "$P/getprop-all.txt"
rm -f "$P/getprop-all.txt"

note "hud probe end"
sync
exit 0
