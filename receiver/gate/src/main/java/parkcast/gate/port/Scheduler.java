package parkcast.gate.port;

/** The event-thread clock and timer. The gate never reads the system clock or sleeps. */
public interface Scheduler {
    /** Current time in milliseconds on the event thread's clock. */
    long now();

    /** Runs {@code r} on the event thread at absolute time {@code ms}. */
    Task at(long ms, Runnable r);

    /** A scheduled task. */
    interface Task {
        void cancel();
    }
}
