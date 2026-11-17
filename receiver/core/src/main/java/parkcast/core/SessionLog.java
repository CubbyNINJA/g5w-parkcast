package parkcast.core;

/**
 * The session records of AIR-071(b) and the withheld count (AIR-073(c)). Sessions number from 1 at each app
 * start (AIR-071(e)). Native records go through {@link NativeLogFilter}; a record that arrives before the
 * filter is ready, that the filter drops, or that makes the filter throw is counted, never written
 * (AIR-072(b)). Drops outside a session go into the next session's count, or are discarded if none follows
 * (OD-190).
 */
final class SessionLog {
    private final RuntimeLog log;
    private NativeLogFilter filter;
    private boolean ready;
    private int number;
    private boolean open;
    private int withheld;

    SessionLog(RuntimeLog log, NativeLogFilter filter) {
        this.log = log;
        this.filter = filter;
    }

    /** The allowlist is loaded: native records may be written from now on (OD-190). */
    void filterReady() {
        ready = true;
    }

    /** Replaces the filter; for tests that need a filter that fails. */
    void useFilter(NativeLogFilter f) {
        filter = f;
    }

    /** A session started: it takes the next number. */
    void started() {
        number++;
        open = true;
    }

    /** One record from the library's log callback. */
    void nativeRecord(String record) {
        String out;
        if (!ready || filter == null) {
            out = null;
        } else {
            try {
                out = filter.pass(record);
            } catch (RuntimeException e) {
                out = null;
            }
        }
        if (out == null) {
            withheld++;
            return;
        }
        log.record(out);
    }

    /** One drop counted outside the filter, such as a log record the ring had no room for (OD-190). */
    void dropped() {
        withheld++;
    }

    /** The open session ended for {@code reason}; nothing if none is open. */
    void end(String reason) {
        if (!open) return;
        open = false;
        log.record("session " + number + " end reason=" + reason + " withheld=" + withheld);
        withheld = 0;
    }

    boolean isOpen() {
        return open;
    }

    int number() {
        return number;
    }
}
