package parkcast.core.port;

/** The two threads the Android adapters use: a worker for blocking stock calls, and the main looper. */
public interface Threads {
    /** Runs {@code r} on the worker thread, one job at a time. */
    void background(Runnable r);

    /** Posts {@code r} to the main looper, the gate's event thread. */
    void main(Runnable r);
}
