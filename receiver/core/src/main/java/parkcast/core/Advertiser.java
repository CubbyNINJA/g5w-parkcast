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
        // TODO(phase B: advertise probe) call start(ADVERTISE) every 250 ms for at most 5000 ms until the
        // mdnsd connect probe succeeds, and map its socket errors to AIR-050(d)'s tokens.
        advertiseCalled = true;
        NativeLibrary.StartResult r = nativeLib.start(NativeLibrary.Stage.ADVERTISE, plan, events);
        if (r == null || !r.ok) {
            fail(r == null ? "unreachable" : r.token, "none", outcome);
            return;
        }
        done = true;
        log.record(NativeLibrary.StartResult.MDNSD_UP);
        log.record("advertise ok");
        outcome.advertised();
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
    }
}
