package parkcast.gate;

import parkcast.gate.port.AccSource;
import parkcast.gate.port.AccStatus;

/**
 * Tracks the car's power state from the ACC broadcast (AIR-019(f), (h), (i); AIR-021(b)).
 *
 * <p>The receiver counts as asleep until registration returns or throws. A returned sticky state is the
 * initial state: an awake one counts as the resume. Status 0, 2 and 5 are awake; 6, 7, 255, 1, any other
 * value, a missing extra and a non-integer extra are asleep. A change within the same class does nothing.
 *
 * <p>A registration that throws or returns no sticky state leaves the receiver asleep for the rest of the
 * run, with no retry. With the parking check on it is logged as {@code ACC registration failed} and reported
 * by the registration-failure notice, and each later broadcast is logged as ignored. With the check off it
 * is logged once as {@code ACC registration unavailable}, and later broadcasts write nothing.
 */
public final class AccState {
    static final String CAUSE_EXCEPTION = "exception";
    static final String CAUSE_NO_STICKY = "no sticky state";

    private final AccSource source;
    private final ReceiverConfig config;
    private final GateLog log;
    private final Notices notices;
    private final ParkPoller poller;

    private boolean registerCalled;
    /** Registration returned, with or without a sticky state: unregister at exit. */
    private boolean returned;
    private boolean threw;
    /** Registration failed: broadcasts are ignored for the rest of the run. */
    private boolean ignoring;
    private boolean awake;
    private boolean exited;

    public AccState(AccSource source, ReceiverConfig config, GateLog log, Notices notices, ParkPoller poller) {
        this.source = source;
        this.config = config;
        this.log = log;
        this.notices = notices;
        this.poller = poller;
    }

    /** Registers for the ACC broadcast once per app start; the result may arrive later. */
    public void register() {
        if (registerCalled || exited) return;
        registerCalled = true;
        try {
            source.register(new AccSource.AccListener() {
                @Override
                public void registered(AccStatus sticky) {
                    onRegistered(sticky);
                }

                @Override
                public void registrationFailed(String cause) {
                    onThrew();
                }

                @Override
                public void changed(AccStatus s) {
                    onChanged(s);
                }
            });
        } catch (RuntimeException e) {
            onThrew();
        }
    }

    /** True until registration returns an awake state, and whenever the latest state is asleep. */
    public boolean asleep() {
        return !awake;
    }

    /** At exit: unregisters a registration that returned; never one that threw. */
    public void unregister() {
        if (exited) return;
        exited = true;
        if (returned && !threw) {
            try {
                source.unregister();
            } catch (RuntimeException e) {
                // Exit completes whether the call returns or throws.
            }
        }
    }

    static boolean isAwake(AccStatus s) {
        if (s == null || !s.isInteger()) return false;
        int v = s.value();
        return v == 0 || v == 2 || v == 5;
    }

    private void onRegistered(AccStatus sticky) {
        if (exited || returned || threw) return;
        returned = true;
        if (sticky == null) {
            failed(CAUSE_NO_STICKY);
            return;
        }
        if (isAwake(sticky)) {
            awake = true;
            poller.resume();
        }
    }

    private void onThrew() {
        if (exited || returned || threw) return;
        threw = true;
        failed(CAUSE_EXCEPTION);
    }

    /** Registration threw or returned no sticky state: asleep for the rest of the run, no retry. */
    private void failed(String cause) {
        ignoring = true;
        awake = false;
        if (config.requirePark()) {
            log.record("ACC registration failed cause=" + cause);
            notices.registrationFailed();
        } else {
            log.record("ACC registration unavailable cause=" + cause);
        }
    }

    private void onChanged(AccStatus s) {
        if (exited || !returned || threw) return;
        if (ignoring) {
            if (config.requirePark()) log.record("ACC broadcast ignored status=" + s);
            return;
        }
        boolean now = isAwake(s);
        if (now == awake) return;
        awake = now;
        if (now) {
            poller.resume();
        } else {
            poller.sleep();
        }
    }
}
