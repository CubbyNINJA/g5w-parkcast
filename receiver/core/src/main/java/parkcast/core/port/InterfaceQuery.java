package parkcast.core.port;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reads one network interface, read-only (AIR-048(b)). On the unit: {@code NetworkInterface.getByName}. */
public interface InterfaceQuery {
    /** The named interface, or null when it does not exist. May throw, such as {@code SocketException}. */
    Iface get(String name) throws Exception;

    /** What the query returned: whether the interface is up, and its IPv4 addresses in dotted form. */
    final class Iface {
        public final boolean up;
        public final List<String> ipv4;

        public Iface(boolean up, List<String> ipv4) {
            this.up = up;
            this.ipv4 = Collections.unmodifiableList(new ArrayList<String>(ipv4));
        }
    }
}
