package parkcast.core.doubles;

import parkcast.core.port.CrashHook;

/** The default uncaught-exception handler; records install and each crash as trace cause "crash". */
public final class FakeCrashHook implements CrashHook {
    private final Trace trace;
    public CrashSink sink;

    public FakeCrashHook(Trace trace) {
        this.trace = trace;
    }

    @Override
    public void install(CrashSink s) {
        trace.add("crash", "install", "");
        this.sink = s;
    }

    /** An uncaught exception reaches the handler: the sink runs, then the process would end. */
    public void crash(Throwable t) {
        trace.add("crash", "uncaught", t.getClass().getName());
        if (sink != null) sink.crashed(t);
    }
}
