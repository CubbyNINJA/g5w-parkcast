package parkcast.core.port;

/**
 * The read-only face of the stock regulation service (AIR-066, OD-195): its current status, and a listener for
 * changes. There is deliberately no setter, no popup and no register method here, so the receiver cannot change
 * or spoof the regulation state through this port. Every call may fail.
 */
public interface RegulationBinder {
    /** The current status: 0 is off, 1 is on; any other value is passed on as it is. */
    int getStatus() throws Exception;

    /** Starts change notifications to {@code l}, and tells it if the service dies. */
    void addListener(Listener l) throws Exception;

    /** Stops the notifications {@link #addListener} started. */
    void removeListener(Listener l) throws Exception;

    /** Change notifications from the service. */
    interface Listener {
        /** The status changed to {@code status}. */
        void onStatusChanged(int status);

        /** The service died; no further notification will come. */
        void onServiceLost();
    }
}
