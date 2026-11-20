package parkcast.gate;

import parkcast.gate.port.NoticeView;
import parkcast.gate.port.NoticeWindows;
import parkcast.gate.port.Placement;
import parkcast.gate.port.Scheduler;
import parkcast.gate.port.Win;
import parkcast.gate.port.WindowSpec;

/**
 * The registration-failure and logging-failure notices (AIR-019(i), AIR-040, AIR-041(b) to (e)).
 *
 * <p>Each notice has its own once-per-app-start allowance of at most two attempts: a first attempt when its
 * failure is first reported, and one retry 5,000 ms after a first attempt that fails to show. An attempt is a
 * toast-style window of type {@code TYPE_TOAST}, not focusable and not touchable, removed with one removal
 * call exactly 5,000 ms after its add returns. Both windows take their gravity and x from the placement of a
 * toast for the registration-failure text; the logging-failure window sits one measured toast height above.
 *
 * <p>While the receiver counts as covered no view is created, measured or added: due attempts are held until
 * it stops counting as covered, and windows on screen are removed at once when it becomes covered. Exit makes
 * one removal call per window still on screen and drops every held attempt and waiting retry.
 *
 * <p>Nothing here changes the gate, the flag value, polling, registration, audio or the iPhone connection,
 * and nothing here writes a record: a notice that fails to show or to be removed is not a logging failure. The
 * S3 runtime opts in to one record per failed attempt ({@link #logAddFailures}, AIR-061(b)); that record is not
 * a logging failure either.
 */
public final class Notices {
    /** The registration-failure notice's text (AIR-019(i)). */
    static final String REGISTRATION_TEXT =
            "Mirroring is off: the car's power state couldn't be read. Restart the receiver from NEMESIS Manager.";
    /** The logging-failure notice's text (AIR-040(e)). */
    static final String LOGGING_TEXT = "Receiver logging stopped working. Mirroring is unaffected.";
    /** How long a shown notice stays on screen (AIR-040(e)). */
    static final long ON_SCREEN_MS = 5000L;
    /** The wait before a failed first attempt's retry, and before a failed removal's second call. */
    static final long RETRY_MS = 5000L;
    /** The interval between removal calls while covered (AIR-041(b)). */
    static final long COVERED_RETRY_MS = 1000L;

    private static final int VERTICAL_GRAVITY_MASK = 0x70;

    private final NoticeWindows windows;
    private final Scheduler scheduler;
    private final int displayWidth;
    private final Notice registration;
    private final Notice logging;
    private final StartFailureNotice startFailure = new StartFailureNotice();
    /** Where a failed start-failure notice add is recorded; absent for the S2 gate on its own. */
    private GateLog log;
    /** Failed registration- and logging-failure notice attempts are recorded too; off for the S2 gate. */
    private boolean logAddFailures;

    /** Covered from process start until the first focus gain (AIR-041(a)). */
    private boolean covered = true;
    private boolean exited;

    public Notices(NoticeWindows windows, Scheduler scheduler, int displayWidth) {
        this.windows = windows;
        this.scheduler = scheduler;
        this.displayWidth = displayWidth;
        this.registration = new Notice(REGISTRATION_TEXT, false);
        this.logging = new Notice(LOGGING_TEXT, true);
    }

    /** The ACC registration failed with the parking check on: show the registration-failure notice. */
    public void registrationFailed() {
        registration.trigger();
    }

    /** A log create, write or retention operation failed: show the logging-failure notice. */
    public void loggingFailed() {
        logging.trigger();
    }

    /**
     * The receiver could not start: show the start-failure notice for {@code cause} (AIR-069(c) to (f)). At
     * most one per app start; later calls do nothing.
     */
    public void startFailed(String cause) {
        startFailure.trigger(cause);
    }

    /** Gives the start-failure notice a log for its failed-add record (AIR-061(b)). */
    public void setLog(GateLog log) {
        this.log = log;
    }

    /**
     * Records each failed registration-failure or logging-failure notice attempt through the log given to
     * {@link #setLog} (AIR-061(b)). The S2 gate on its own keeps these silent; the S3 runtime turns this on.
     */
    public void logAddFailures() {
        this.logAddFailures = true;
    }

    /** The receiver became covered: hold attempts and remove windows on screen at once (AIR-041(b), (c)). */
    public void coverStart() {
        if (exited || covered) return;
        covered = true;
        registration.onCoverStart();
        logging.onCoverStart();
        startFailure.onCoverStart();
    }

    /** The receiver stopped counting as covered: finish cover removals, then make held attempts. */
    public void uncovered() {
        if (exited || !covered) return;
        covered = false;
        registration.onUncover();
        logging.onUncover();
        registration.makeHeld();
        logging.makeHeld();
        startFailure.makeHeld();
    }

    /** Receiver exit: one removal call per window on screen; held attempts and retries are dropped. */
    public void exit() {
        if (exited) return;
        exited = true;
        registration.onExit();
        logging.onExit();
        startFailure.onExit();
    }

    /** Opens a placed registration-failure toast's gravity and x to the bottom centre when it is not at the bottom. */
    private WindowSpec placedSpec(Placement p) {
        int gravity = p.gravity;
        int x = p.x;
        if ((gravity & VERTICAL_GRAVITY_MASK) != WindowSpec.GRAVITY_BOTTOM) {
            gravity = WindowSpec.GRAVITY_BOTTOM | WindowSpec.GRAVITY_CENTER_HORIZONTAL;
            x = 0;
        }
        return new WindowSpec(WindowSpec.TYPE_TOAST, WindowSpec.FLAG_NOT_FOCUSABLE | WindowSpec.FLAG_NOT_TOUCHABLE,
                gravity, x, p.y);
    }

    /**
     * The start-failure notice (AIR-069(c) to (f), AIR-061(b), (c)): one per app start, held while covered and
     * added at uncover, on screen for 5,000 ms. A cover start removes it, and it is not shown again in that app
     * start. A failed add is logged once and not retried. Exit drops a held notice and removes a shown one.
     */
    private final class StartFailureNotice {
        private boolean triggered;
        private String text;
        private boolean held;
        /** Shown and removed by a cover start, or its one add failed: no further add in this app start. */
        private boolean done;
        private Win win;
        private Scheduler.Task removalTask;

        void trigger(String cause) {
            if (exited || triggered) return;
            triggered = true;
            text = "ParkCast can't start: " + cause + ". Exit and try again.";
            if (covered) {
                held = true;
                return;
            }
            attempt();
        }

        void makeHeld() {
            if (!held) return;
            held = false;
            if (!done) attempt();
        }

        private void attempt() {
            done = true;
            Win added;
            try {
                NoticeView view = windows.create(text);
                NoticeView toast = windows.create(REGISTRATION_TEXT);
                Placement p = windows.placementFrom(toast);
                if (p == null) {
                    addFailed("placement missing");
                    return;
                }
                added = windows.add(view, placedSpec(p));
            } catch (RuntimeException e) {
                addFailed(e.getClass().getName());
                return;
            }
            if (added == null) return;
            win = added;
            removalTask = scheduler.at(scheduler.now() + ON_SCREEN_MS, new Runnable() {
                @Override
                public void run() {
                    removalTask = null;
                    remove();
                }
            });
        }

        private void addFailed(String why) {
            if (log != null) log.record("start-failure notice add failed " + why);
        }

        private void remove() {
            if (removalTask != null) {
                removalTask.cancel();
                removalTask = null;
            }
            if (win == null) return;
            Win w = win;
            win = null;
            try {
                windows.remove(w);
            } catch (RuntimeException e) {
                // One removal call; the notice is not shown again in this app start either way.
            }
        }

        void onCoverStart() {
            held = false;
            if (win != null) {
                done = true;
                remove();
            }
        }

        void onExit() {
            held = false;
            remove();
        }
    }

    private Scheduler.Task after(long delay, Runnable r) {
        return scheduler.at(scheduler.now() + delay, r);
    }

    /** One notice: its allowance, its attempts and its window. */
    private final class Notice {
        private static final int FIRST_REMOVAL = 1;
        private static final int SECOND_REMOVAL = 2;
        private static final int COVERED_REMOVAL = 3;
        private static final int UNCOVER_REMOVAL = 4;

        private final String text;
        /** The logging-failure notice sits one registration-failure toast height above the registration-failure slot. */
        private final boolean raised;

        /** The allowance is used: further failures of this kind make no attempt. */
        private boolean triggered;
        /** The number of the attempt held while covered, or 0. */
        private int held;
        private Scheduler.Task retryTask;

        /** The window on screen: from its add returning until a removal call returns normally. */
        private Win win;
        private Scheduler.Task removalTask;
        private long removalDue;
        /** The window went through a cover start and has not yet been through the matching uncover. */
        private boolean coverRemoval;
        private long lastRemovalCallAt = -1L;

        Notice(String text, boolean raised) {
            this.text = text;
            this.raised = raised;
        }

        void trigger() {
            if (exited || triggered) return;
            triggered = true;
            attemptOrHold(1);
        }

        private void attemptOrHold(int n) {
            if (exited) return;
            if (covered) {
                held = n;
                return;
            }
            attempt(n);
        }

        void makeHeld() {
            if (held == 0) return;
            int n = held;
            held = 0;
            attempt(n);
        }

        /**
         * Creates this notice's view, places it from a registration-failure toast that is never shown, and adds
         * its window (AIR-040(g), OD-121). Both notices take gravity and x from that toast, falling back to the
         * bottom centre; the registration-failure window takes its y, and the logging-failure window that y plus
         * the toast view's height, measured just before the add. Any throw, or a height of 0 or less, fails the
         * attempt of this notice.
         */
        private void attempt(int n) {
            Win added;
            try {
                NoticeView view = windows.create(text);
                NoticeView toast = windows.create(REGISTRATION_TEXT);
                Placement p = windows.placementFrom(toast);
                if (p == null) {
                    failed(n, "placement missing");
                    return;
                }
                int gravity = p.gravity;
                int x = p.x;
                if ((gravity & VERTICAL_GRAVITY_MASK) != WindowSpec.GRAVITY_BOTTOM) {
                    gravity = WindowSpec.GRAVITY_BOTTOM | WindowSpec.GRAVITY_CENTER_HORIZONTAL;
                    x = 0;
                }
                int y = p.y;
                if (raised) {
                    int h = windows.measure(toast, displayWidth);
                    if (h <= 0) {
                        failed(n, "height " + h);
                        return;
                    }
                    y += h;
                }
                added = windows.add(view, new WindowSpec(WindowSpec.TYPE_TOAST,
                        WindowSpec.FLAG_NOT_FOCUSABLE | WindowSpec.FLAG_NOT_TOUCHABLE, gravity, x, y));
            } catch (RuntimeException e) {
                failed(n, e.getClass().getName());
                return;
            }
            shown(added);
        }

        /** A first attempt that fails is retried once, 5,000 ms later; a failed retry gives the notice up. */
        private void failed(int n, String why) {
            if (logAddFailures && log != null) {
                log.record((raised ? "logging-failure" : "registration-failure") + " notice add failed " + why);
            }
            if (n != 1) return;
            retryTask = after(RETRY_MS, new Runnable() {
                @Override
                public void run() {
                    retryTask = null;
                    attemptOrHold(2);
                }
            });
        }

        private void shown(Win added) {
            if (added == null) return;
            win = added;
            scheduleRemoval(FIRST_REMOVAL, ON_SCREEN_MS);
        }

        // -------------------------------------------------------- removal

        private void scheduleRemoval(final int phase, long delay) {
            cancelRemoval();
            removalDue = scheduler.now() + delay;
            removalTask = scheduler.at(removalDue, new Runnable() {
                @Override
                public void run() {
                    removalTask = null;
                    removalDue(phase);
                }
            });
        }

        private void cancelRemoval() {
            if (removalTask != null) {
                removalTask.cancel();
                removalTask = null;
            }
        }

        private void removalDue(int phase) {
            if (exited || win == null) return;
            if (callRemove()) return;
            if (phase == FIRST_REMOVAL) {
                scheduleRemoval(SECOND_REMOVAL, RETRY_MS);
            } else if (phase == COVERED_REMOVAL && covered) {
                scheduleRemoval(COVERED_REMOVAL, COVERED_RETRY_MS);
            }
            // After a failed second call, or the one call after an uncover: none until exit or a cover start.
        }

        /** One removal call; true when it returned normally and the window is off screen. */
        private boolean callRemove() {
            lastRemovalCallAt = scheduler.now();
            try {
                windows.remove(win);
            } catch (RuntimeException e) {
                return false;
            }
            win = null;
            cancelRemoval();
            return true;
        }

        /** Removes the window at once; a call already made at this clock time is the one removal call. */
        void onCoverStart() {
            if (win == null) return;
            cancelRemoval();
            coverRemoval = true;
            if (lastRemovalCallAt != scheduler.now() && callRemove()) return;
            scheduleRemoval(COVERED_REMOVAL, COVERED_RETRY_MS);
        }

        /** Makes a covered removal call due now, then one more call 5,000 ms later if still on screen. */
        void onUncover() {
            if (!coverRemoval) return;
            coverRemoval = false;
            if (win == null) return;
            long now = scheduler.now();
            boolean dueNow = removalTask != null && removalDue == now;
            cancelRemoval();
            if (dueNow && lastRemovalCallAt != now && callRemove()) return;
            scheduleRemoval(UNCOVER_REMOVAL, RETRY_MS);
        }

        void onExit() {
            held = 0;
            if (retryTask != null) {
                retryTask.cancel();
                retryTask = null;
            }
            cancelRemoval();
            if (win != null && lastRemovalCallAt != scheduler.now()) {
                try {
                    windows.remove(win);
                } catch (RuntimeException e) {
                    // Exit completes whether the call returns or throws.
                }
            }
            win = null;
        }
    }
}
