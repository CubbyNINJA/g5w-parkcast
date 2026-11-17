package parkcast.core;

import parkcast.core.port.InterfaceQuery;

/**
 * Checks, read-only, that wlan0 exists, is up and holds 192.168.0.1 (AIR-048(b), (d)). The interface is
 * queried exactly once per start, with no retry, wait or fallback.
 */
final class HotspotCheck {
    static final String IFACE = "wlan0";
    static final String ADDRESS = "192.168.0.1";
    static final String MISSING = "hotspot address missing";

    private final InterfaceQuery ifaces;

    HotspotCheck(InterfaceQuery ifaces) {
        this.ifaces = ifaces;
    }

    /** @return null when the check passed, else the record to log, which starts with {@link #MISSING} */
    String run() {
        InterfaceQuery.Iface i;
        try {
            i = ifaces.get(IFACE);
        } catch (Exception e) {
            return MISSING + " " + e.getClass().getName();
        }
        if (i == null || !i.up || !i.ipv4.contains(ADDRESS)) return MISSING;
        return null;
    }
}
