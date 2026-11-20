package parkcast.receiver;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import parkcast.core.port.InterfaceQuery;

/** {@link InterfaceQuery} over NetworkInterface: null when the interface is missing, IPv4 addresses only. */
final class AndroidInterfaceQuery implements InterfaceQuery {
    @Override
    public Iface get(String name) throws Exception {
        NetworkInterface ni = NetworkInterface.getByName(name);
        if (ni == null) return null;
        List<String> ipv4 = new ArrayList<String>();
        Enumeration<InetAddress> addrs = ni.getInetAddresses();
        while (addrs != null && addrs.hasMoreElements()) {
            InetAddress a = addrs.nextElement();
            if (a instanceof Inet4Address) ipv4.add(a.getHostAddress());
        }
        return new Iface(ni.isUp(), ipv4);
    }
}
