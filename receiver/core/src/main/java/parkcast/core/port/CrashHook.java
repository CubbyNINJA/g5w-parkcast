package parkcast.core.port;

/** The default uncaught-exception handler; the process ends after the sink returns (AIR-069(a), (b)). */
public interface CrashHook {
    void install(CrashSink sink);

    /** Receives an uncaught exception before the process ends. */
    interface CrashSink {
        void crashed(Throwable t);
    }
}
