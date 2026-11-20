package parkcast.core;

import parkcast.core.port.NativeEvents;
import parkcast.core.port.NativeLibrary;
import parkcast.core.port.NsdWake;
import parkcast.gate.port.Scheduler;

/**
 * Wakes mdnsd through NsdManager, then advertises through {@code start(ADVERTISE)} (AIR-050). Discovery
 * results are never logged. {@code onDiscoveryStarted} at exactly {@link #WINDOW_MS} or later is late.
 */
final class Advertiser {
    static final String TYPE = "_airplay._tcp";
    static final long WINDOW_MS = 5000L;
    static final long RETRY_MS = 250L;

    /** What the start sequence does next. */
    interface Outcome {
        void advertised();

        void failed();
    }

    private final Scheduler scheduler;
    private final NsdWake nsd;
    private final NativeLibrary nativeLib;
    private final RuntimeLog log;

    private NsdWake.NsdHandle handle;
    private NsdWake.Listener listener;
    private Scheduler.Task timeout;
    private long discoverAt;
    private boolean discoverReturned;
    private boolean discoveryStopped;
    private boolean advertiseCalled;
    private boolean done;
    /** The pending connect retry, if any. */
    private Scheduler.Task retry;
    /** When {@code onDiscoveryStarted} opened the connect window. */
    private long windowStart;

    Advertiser(Scheduler scheduler, NsdWake nsd, NativeLibrary nativeLib, RuntimeLog log) {
        this.scheduler = scheduler;
        this.nsd = nsd;
        this.nativeLib = nativeLib;
        this.log = log;
    }

    void start(final NativeLibrary.StartPlan plan, final NativeEvents events, final Outcome outcome) {
        try {
            handle = nsd.get();
        } catch (SecurityException e) {
            fail("security-exception", "none", outcome);
            return;
        }
        if (handle == null) {
            fail("null-manager", "none", outcome);
            return;
        }
        listener = new NsdWake.Listener() {
            @Override
            public void started() {
                onStarted(plan, events, outcome);
            }

            @Override
            public void startFailed(int errorCode) {
                if (done) return;
                fail("discovery-failed", String.valueOf(errorCode), outcome);
            }
        };
        discoverAt = scheduler.now();
        timeout = scheduler.at(discoverAt + WINDOW_MS, new Runnable() {
            @Override
            public void run() {
                if (done) return;
                fail("discovery-timeout", "none", outcome);
            }
        });
        try {
            handle.discover(TYPE, listener);
        } catch (SecurityException e) {
            fail("security-exception", "none", outcome);
            return;
        }
        discoverReturned = true;
    }

    private void onStarted(NativeLibrary.StartPlan plan, NativeEvents events, Outcome outcome) {
        if (done) return;
        if (scheduler.now() - discoverAt >= WINDOW_MS) {
            fail("discovery-timeout", "none", outcome);
            return;
        }
        cancelTimeout();
        windowStart = scheduler.now();
        attempt(plan, events, outcome);
    }

    /**
     * One call of {@code start(ADVERTISE)} (AIR-050(c)). While mdnsd is not connected the call is a connect probe,
     * retried every {@link #RETRY_MS} up to {@link #WINDOW_MS} after {@code onDiscoveryStarted}; once it connects,
     * "mdnsd up" is logged and a second call sends the registrations.
     */
    private void attempt(final NativeLibrary.StartPlan plan, final NativeEvents events, final Outcome outcome) {
        retry = null;
        if (done) return;
        NativeLibrary.StartResult r = nativeLib.start(NativeLibrary.Stage.ADVERTISE, plan, events);
        if (r == null || !r.ok) {
            long next = scheduler.now() + RETRY_MS;
            if (next - windowStart <= WINDOW_MS) {
                retry = scheduler.at(next, new Runnable() {
                    @Override
                    public void run() {
                        attempt(plan, events, outcome);
                    }
                });
                return;
            }
            fail(connectToken(r), "none", outcome);
            return;
        }
        log.record(NativeLibrary.StartResult.MDNSD_UP);
        advertiseCalled = true;
        r = nativeLib.start(NativeLibrary.Stage.ADVERTISE, plan, events);
        if (r == null || !r.ok) {
            String t = r == null || r.token == null ? "unreachable" : r.token;
            int sp = t.indexOf(' ');
            fail(sp < 0 ? t : t.substring(0, sp), sp < 0 ? "none" : t.substring(sp + 1), outcome);
            return;
        }
        done = true;
        log.record("advertise ok");
        outcome.advertised();
    }

    /** AIR-050(d): these errnos are logged as they are; any other connect failure is "unreachable". */
    private static String connectToken(NativeLibrary.StartResult r) {
        String t = r == null ? null : r.token;
        if ("ENOENT".equals(t) || "EACCES".equals(t) || "EPERM".equals(t) || "ECONNREFUSED".equals(t)) return t;
        return "unreachable";
    }

    private void fail(String token, String code, Outcome outcome) {
        done = true;
        cancelTimeout();
        log.record("advertise failed reason=" + token + " code=" + code);
        if (advertiseCalled) nativeLib.stop(NativeLibrary.Phase.REGISTRATIONS);
        stopDiscovery();
        outcome.failed();
    }

    /** Stops discovery once, and only if {@code discoverServices} had returned (AIR-050(d), (f)). */
    void stopDiscovery() {
        if (!discoverReturned || discoveryStopped) return;
        discoveryStopped = true;
        handle.stop(listener);
    }

    /** Exit: drop later callbacks and the timeout. */
    void cancel() {
        done = true;
        cancelTimeout();
    }

    boolean advertiseCalled() {
        return advertiseCalled;
    }

    private void cancelTimeout() {
        if (timeout != null) timeout.cancel();
        timeout = null;
        if (retry != null) retry.cancel();
        retry = null;
    }
}
