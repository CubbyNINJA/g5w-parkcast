#!/system/bin/sh
# coexist_probe.sh - READ-ONLY, about 3 minutes. For the AirPlay receiver's coexistence with the stock
# CarPlay stack (AMB-07, AMB-44): takes two snapshots of the unit's listening TCP ports, bound UDP
# ports, the processes that own them, the network interfaces and the mDNS/CarPlay-related processes.
# Snapshot A is taken at the start with CarPlay disconnected; then the toasts ask the owner to connect
# wireless CarPlay, and snapshot B is taken 2 minutes later. It only reads /proc, ps, netcfg and
# getprop: no service calls, nothing sent to the car, and no Wi-Fi configuration or password is read.
# Guarded to build 260206.
#
# DO IT PARKED, with wireless CarPlay DISCONNECTED when you tap Yes. When the toast says so, connect
# wireless CarPlay and leave it connected until "Coexist probe done".
# Output: NEMESIS-diag/coexist-probe/. Removes the root NEMESIS.data when it starts.
export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin${BUNDLE_TEST_SYS:+:$BUNDLE_TEST_SYS}
export LD_LIBRARY_PATH=/vendor/lib:/system/lib
USB=$(pwd)
P=$USB/NEMESIS-diag/coexist-probe
WAIT=${COEXIST_PROBE_WAIT:-120}
mkdir -p "$P" || exit 1
L=$P/probe.log
note() { echo "[$(date '+%H:%M:%S')] $1" >> "$L"; }
toast() {
    am broadcast -a com.hkmc.intent.action.TOAST -e com.hkmc.intent.extra.TOAST_MESSAGE "$1" \
        --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true >/dev/null 2>&1
}
if [ -f "$USB/NEMESIS.data" ] && cmp "$USB/NEMESIS.data" "$USB/tools/coexist-probe/NEMESIS.data" >/dev/null 2>&1; then
    rm -f "$USB/NEMESIS.data" && note "removed root NEMESIS.data"
fi
if [ "$(getprop sys.boot_completed)" != "1" ]; then
    note "Android is not up; nothing done."
    exit 1
fi
BUILD=$(getprop ro.build.version.incremental)
note "coexist probe start; build $(getprop ro.build.product) / $BUILD"
if [ "$BUILD" != "eng.vmadmin.20260206.060005" ]; then
    note "guard FAILED (build=$BUILD): nothing read"
    sync
    exit 1
fi
note "guard passed"

snapshot() {
    D=$P/$1
    mkdir -p "$D"
    note "snapshot $1 start"
    for f in tcp tcp6 udp udp6; do
        cat /proc/net/$f > "$D/proc-net-$f.txt" 2>&1
    done
    ps > "$D/ps.txt" 2>&1
    netcfg > "$D/netcfg.txt" 2>&1
    getprop | grep -i -E "mdns|carplay|dipo|wifi\.interface|softap|p2p|tcc\.wifi" | grep -v -i -E "pass|psk|key|ssid" \
        > "$D/props.txt" 2>&1
    # Socket inodes per process, so a listening port can be tied to the process that owns it.
    : > "$D/socket-owners.txt"
    for d in /proc/[0-9]*; do
        pid=${d#/proc/}
        socks=$(ls -l "$d/fd" 2>/dev/null | grep "socket:")
        if [ -n "$socks" ]; then
            name=$(cat "$d/cmdline" 2>/dev/null)
            echo "== pid $pid $name" >> "$D/socket-owners.txt"
            echo "$socks" >> "$D/socket-owners.txt"
        fi
    done
    note "snapshot $1 end"
}

toast "Coexist probe: keep wireless CarPlay DISCONNECTED for now"
snapshot A-carplay-off
toast "Coexist probe: connect wireless CarPlay now and leave it connected"
note "asked to connect CarPlay; waiting ${WAIT}s"
sleep "$WAIT"
snapshot B-carplay-on
logcat -d -v time > "$P/logcat-all.txt" 2>&1
grep -i -E "mdns|airplay|_airplay|DipoService|CarPlay_|softap|hostapd" "$P/logcat-all.txt" | \
    grep -v -i -E "pass|psk|ssid" > "$P/logcat-coexist.txt" 2>&1
rm -f "$P/logcat-all.txt"
toast "Coexist probe done"
note "coexist probe end"
sync
exit 0
