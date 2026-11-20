package parkcast.core.doubles;

import parkcast.core.port.NsdWake;
import parkcast.gate.harness.VirtualScheduler;

/**
 * The fake NsdManager (file 13 preamble): {@code discoverServices} delivers {@code onDiscoveryStarted} 10 ms
 * later unless a scenario scripts {@code onStartDiscoveryFailed} instead. Records get, discover and stop as
 * trace cause "nsd".
 */
public final class FakeNsd implements NsdWake {
    public static final long STARTED_AFTER_MS = 10;

    private final Trace trace;
    private final VirtualScheduler scheduler;
    /** {@code getSystemService} returns null. */
    public boolean returnsNull;
    /** {@code getSystemService} throws SecurityException. */
    public boolean throwsSecurity;
    /** {@code discoverServices} throws SecurityException; the call is traced as "discoverThrew", not "discover". */
    public boolean discoverThrowsSecurity;
    /** When non-null, {@code onStartDiscoveryFailed} with this code replaces {@code onDiscoveryStarted}. */
    public Integer failCode;
    /** When failing, the absolute time the failure is delivered; -1 for 10 ms after discover. */
    public long failAt = -1;

    public FakeNsd(Trace trace, VirtualScheduler scheduler) {
        this.trace = trace;
        this.scheduler = scheduler;
    }

    @Override
    public NsdHandle get() {
        if (throwsSecurity) {
            trace.add("nsd", "get", "threw java.lang.SecurityException");
            throw new SecurityException("synthetic");
        }
        trace.add("nsd", "get", returnsNull ? "null" : "manager");
        if (returnsNull) return null;
        return new NsdHandle() {
            @Override
            public void discover(String type, final Listener l) {
                if (discoverThrowsSecurity) {
                    trace.add("nsd", "discoverThrew", "java.lang.SecurityException");
                    throw new SecurityException("synthetic");
                }
                trace.add("nsd", "discover", type);
                final int start = scheduler.startIndex;
                long at = scheduler.now() + STARTED_AFTER_MS;
                if (failCode != null) {
                    final int code = failCode;
                    scheduler.at(failAt >= 0 ? failAt : at, () -> {
                        if (scheduler.startIndex != start) return;
                        trace.add("nsd", "startFailed", String.valueOf(code));
                        l.startFailed(code);
                    });
                } else if (!startedNever) {
                    scheduler.at(startedAt >= 0 ? startedAt : at, () -> {
                        if (scheduler.startIndex != start) return;
                        trace.add("nsd", "started", "");
                        l.started();
                    });
                }
                if (foundName != null) {
                    scheduler.at(foundAt, () -> {
                        if (scheduler.startIndex == start) trace.add("nsd", "found", foundName);
                    });
                    scheduler.at(lostAt, () -> {
                        if (scheduler.startIndex == start) trace.add("nsd", "lost", foundName);
                    });
                }
            }

            @Override
            public void stop(Listener l) {
                if (throwOnStop) throw new IllegalStateException(trace.add("nsd", "stopThrew", "").toString());
                trace.add("nsd", "stop", "");
            }
        };
    }

    /** The absolute time {@code onDiscoveryStarted} is delivered; -1 for 10 ms after discover. */
    public long startedAt = -1;
    /** {@code onDiscoveryStarted} never arrives. */
    public boolean startedNever;
    /**
     * A service the manager reports found at {@link #foundAt} and lost at {@link #lostAt}. The adapter drops
     * discovery results before the listener (NsdWake), so they only reach the trace.
     */
    public String foundName;
    public long foundAt, lostAt;
    /** {@code stopServiceDiscovery} throws java.lang.IllegalStateException. */
    public boolean throwOnStop;
}
