#!/system/bin/sh
# receiver_env_probe.sh - READ-ONLY, under a minute. Facts S3 needs (OD-141, OD-146): the unit's Wi-Fi
# interfaces and addresses, whether the hotspot daemons run when CarPlay is off, whether an app could reach
# mdnsd's socket, the mDNS client library, and whether OMX.TCC.avcdec is registered for apps.
# It only reads: netcfg, ps, ls and cat of codec lists. It never reads Wi-Fi config files (they hold the
# hotspot passphrase) and never calls dumpsys wifi. Guarded to build 260206.
#
# RUN: head unit on, CarPlay NOT connected. Tap Yes. Wait for "Receiver env probe done".
# Output: NEMESIS-diag/receiver-env-probe/. Removes the root NEMESIS.data when it starts.
export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin${BUNDLE_TEST_SYS:+:$BUNDLE_TEST_SYS}
export LD_LIBRARY_PATH=/vendor/lib:/system/lib
USB=$(pwd)
P=$USB/NEMESIS-diag/receiver-env-probe
SYS=${RECEIVER_ENV_SYS:-}
mkdir -p "$P" || exit 1
L=$P/probe.log
note() { echo "[$(date '+%H:%M:%S')] $1" >> "$L"; }
toast() {
    am broadcast -a com.hkmc.intent.action.TOAST -e com.hkmc.intent.extra.TOAST_MESSAGE "$1" \
        --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true >/dev/null 2>&1
}
if [ -f "$USB/NEMESIS.data" ] && cmp "$USB/NEMESIS.data" "$USB/tools/receiver-env-probe/NEMESIS.data" >/dev/null 2>&1; then
    rm -f "$USB/NEMESIS.data" && note "removed root NEMESIS.data"
fi
if [ "$(getprop sys.boot_completed)" != "1" ]; then
    note "Android is not up; nothing done."
    exit 1
fi
BUILD=$(getprop ro.build.version.incremental)
note "receiver env probe start; build $(getprop ro.build.product) / $BUILD"
if [ "$BUILD" != "eng.vmadmin.20260206.060005" ]; then
    note "guard FAILED (build=$BUILD): nothing read"
    sync
    exit 1
fi
note "guard passed"

netcfg > "$P/netcfg.txt" 2>&1
ps > "$P/ps-all.tmp" 2>&1
grep -E "hostapd|dnsmasq|mdnsd|wpa_supplicant|dipo|DiPO|USER " "$P/ps-all.tmp" > "$P/ps-net.txt" 2>&1
rm -f "$P/ps-all.tmp"
ls -l "$SYS/dev/socket" > "$P/dev-socket.txt" 2>&1
ls -l "$SYS/system/lib/libmdnssd.so" "$SYS/system/lib/libdns_sd.so" > "$P/mdns-libs.txt" 2>&1
for f in "$SYS/system/etc/media_codecs.xml" "$SYS/vendor/etc/media_codecs.xml" "$SYS/etc/media_codecs.xml"; do
    if [ -f "$f" ]; then
        echo "== $f" >> "$P/codecs.txt"
        grep -i -E "MediaCodec |avc|TCC" "$f" >> "$P/codecs.txt" 2>&1
    fi
done
getprop ro.build.version.sdk > "$P/sdk.txt" 2>&1
toast "Receiver env probe done"
note "receiver env probe end"
sync
exit 0
