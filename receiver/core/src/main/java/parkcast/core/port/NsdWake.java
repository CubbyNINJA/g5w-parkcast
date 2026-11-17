package parkcast.core.port;

/** Wakes {@code mdnsd} through the public {@code NsdManager} only (AIR-050(a)). */
public interface NsdWake {
    /** {@code getSystemService(NSD_SERVICE)}: null, or throws {@code SecurityException}. */
    NsdHandle get();

    /** The manager. Discovery results are ignored and never logged. */
    interface NsdHandle {
        /** {@code discoverServices(type, PROTOCOL_DNS_SD, l)}. */
        void discover(String type, Listener l);

        /** {@code stopServiceDiscovery(l)}. */
        void stop(Listener l);
    }

    /** The discovery listener's start callbacks; found services never reach it. */
    interface Listener {
        void started();

        void startFailed(int errorCode);
    }
}
