package parkcast.gate;

import parkcast.gate.port.ReverseSource;

/**
 * Tracks whether the receiver counts as covered (AIR-041(a), (f), (g), (j)).
 *
 * <p>The receiver counts as covered from process start until its window's first focus gain, from each focus
 * loss until the next gain, and from each {@code REVERSE_ON} receipt until the first focus gain delivered
 * after it. A registration that returns an intent counts as a receipt at that clock time. A registration
 * that throws leaves the receiver covered for the rest of the app start, with no retry and no unregister.
 * The reverse-off action and every other camera signal are never used: covering that {@code REVERSE_ON}
 * starts ends only at a focus gain.
 *
 * <p>Each change is applied to the notices before it is logged, so a failed write's logging-failure notice
 * is held while covered. The covered state at process start is not logged. Nothing here touches the gate,
 * the flag value, polling, ACC registration, audio or the iPhone connection.
 */
public final class Cover {
    private final ReverseSource reverse;
    private final GateLog log;
    private final Notices notices;

    private boolean hasFocus;
    /** A {@code REVERSE_ON} receipt that no later focus gain has ended. */
    private boolean reverseCover;
    /** The registration threw: covered for the rest of the app start. */
    private boolean registrationFailed;
    private boolean registerCalled;
    /** The registration returned: unregister once at exit. */
    private boolean registered;
    private boolean exited;

    public Cover(ReverseSource reverse, GateLog log, Notices notices) {
        this.reverse = reverse;
        this.log = log;
        this.notices = notices;
    }

    /** Registers the runtime {@code REVERSE_ON} receiver once per app start, before the first focus gain. */
    public void register() {
        if (registerCalled || exited) return;
        registerCalled = true;
        boolean intentReturned;
        try {
            intentReturned = reverse.register(new ReverseSource.ReverseListener() {
                @Override
                public void reverseOn() {
                    received();
                }
            });
        } catch (RuntimeException e) {
            registrationFailed = true;
            log.record("reverse registration failed");
            return;
        }
        registered = true;
        if (intentReturned) received();
    }

    /** The window's focus changed. A gain ends every {@code REVERSE_ON} cover received before it. */
    public void focusChanged(boolean gained) {
        if (exited) return;
        boolean was = covered();
        hasFocus = gained;
        if (gained) reverseCover = false;
        boolean now = covered();
        if (now == was) return;
        if (now) {
            notices.coverStart();
            log.record("notice cover covered=yes cause=focus");
        } else {
            notices.uncovered();
            log.record("notice cover covered=no cause=focus");
        }
    }

    /** True while any cover condition holds. */
    public boolean covered() {
        return registrationFailed || !hasFocus || reverseCover;
    }

    /** At exit: one unregister call for a registration that returned; no receipt counts after it. */
    public void exit() {
        if (exited) return;
        exited = true;
        if (!registered) return;
        try {
            reverse.unregister();
        } catch (RuntimeException e) {
            // Exit completes whether the call returns or throws.
        }
    }

    /** A {@code REVERSE_ON} receipt, whatever its extras, which are never read. */
    private void received() {
        if (exited) return;
        boolean was = covered();
        reverseCover = true;
        if (!was) notices.coverStart();
        log.record("reverse_on received");
        if (!was) log.record("notice cover covered=yes cause=reverse");
    }
}
