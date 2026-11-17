package parkcast.core;

/**
 * The runtime's view of the gate's log and notices: one record through the receiver's log, and the one
 * start-failure notice for this app start (AIR-069(c)).
 */
interface RuntimeLog {
    /** Writes one record, with the gate log's rotation and logging-failure handling. */
    void record(String line);

    /** Shows the start-failure notice for {@code cause}, or nothing if one was already asked for. */
    void startFailed(String cause);
}
