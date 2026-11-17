package parkcast.core.doubles;

import java.net.SocketException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import parkcast.core.port.InterfaceQuery;

/**
 * The interface double (file 13 preamble): answers the receiver's read-only interface query from a table of
 * interfaces and records each query and the time it returned. By default wlan0 is up holding only
 * 192.168.0.1.
 */
public final class FakeInterfaces implements InterfaceQuery {
    private final Trace trace;
    public final Map<String, Iface> table = new HashMap<>();
    /** When set, every query throws a {@link SocketException}. */
    public boolean throwSocketException;

    public FakeInterfaces(Trace trace) {
        this.trace = trace;
        set("wlan0", true, "192.168.0.1");
    }

    public void set(String name, boolean up, String... ipv4) {
        table.put(name, new Iface(up, Arrays.asList(ipv4)));
    }

    /** Applies one of the scenario phrases for what the double reports. */
    public void report(String phrase) {
        table.clear();
        throwSocketException = false;
        switch (phrase) {
            case "no wlan0":
                break;
            case "wlan0 down":
            case "wlan0 down, holding 192.168.0.1":
                set("wlan0", false, "192.168.0.1");
                break;
            case "wlan0 up, holding only 192.168.0.2":
                set("wlan0", true, "192.168.0.2");
                break;
            case "wlan0 up with no address, and 192.168.0.1 on interface ap0":
                set("wlan0", true);
                set("ap0", true, "192.168.0.1");
                break;
            case "a query that throws java.net.SocketException":
                throwSocketException = true;
                break;
            case "wlan0 up holding 192.168.0.1":
                set("wlan0", true, "192.168.0.1");
                break;
            default:
                throw new IllegalArgumentException("no interface phrase: " + phrase);
        }
    }

    @Override
    public Iface get(String name) throws Exception {
        if (throwSocketException) {
            trace.add("iface", "get", name + " threw java.net.SocketException");
            throw new SocketException("synthetic interface failure");
        }
        Iface i = table.get(name);
        trace.add("iface", "get", name + (i == null ? " absent" : " up=" + i.up + " ipv4=" + i.ipv4));
        return i;
    }

    public List<String> names() {
        return Collections.unmodifiableList(new java.util.ArrayList<>(table.keySet()));
    }
}
