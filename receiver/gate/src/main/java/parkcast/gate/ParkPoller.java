package parkcast.gate;

import parkcast.gate.port.ParkGetter;
import parkcast.gate.port.Scheduler;

/**
 * Polls the Park getter every 200 ms, one call at a time (AIR-017(b), (e), (f); AIR-019(b) to (e), (g)).
 *
 * <p>The poller starts asleep and makes no call until {@link #resume()}. Each resume re-reads at once, or,
 * when a call started before the sleep is still pending, as soon as that call returns. Polls then follow
 * every 200 ms from each invocation; the next poll waits while a call is pending, even past its timeout.
 * A call with no reply strictly before 200 ms counts as unavailable at 200 ms; a reply at 200 ms or later
 * is late, never counts as a Park reading, and starts the next call at once, restarting the schedule.
 *
 * <p>A reply that arrives while asleep, or after a resume from a call started before the sleep, is
 * discarded: it is logged and never counts as a reading. Readings age from their receipt; one older than
 * 2,000 ms is unavailable.
 */
public final class ParkPoller {
    /** Interval between poll invocations, and the timeout of each call (AIR-017(b), AIR-019(e)). */
    static final long PERIOD_MS = 200L;
    /** A reading older than this is unavailable (AIR-019(b)). */
    static final long MAX_AGE_MS = 2000L;

    /*
     * Causes other than a returned Park reading name themselves and no Park value or receipt time
     * (AIR-026(d)).
     */
    static final String TIMEOUT_CAUSE = "the " + PERIOD_MS + " ms timeout";
    static final String AGE_CAUSE = "a reading older than " + MAX_AGE_MS + " ms";
    static final String SLEEP_CAUSE = "sleep entry";
    static final String FAILED_CAUSE = "a failed Park getter call";

    /** Told whenever what {@link #fresh()} returns may have changed, with the cause. */
    public interface Listener {
        /**
         * @param reading true when the cause is a returned Park reading
         * @param cause the cause: a returned reading's value and receipt time, or another cause by itself
         */
        void changed(boolean reading, String cause);
    }

    private final Scheduler scheduler;
    private final ParkGetter getter;
    private final GateLog log;
    private Listener listener;

    private boolean started;
    private boolean stopped;
    private boolean asleep = true;
    /** Incremented at each sleep entry, so a call knows whether it was started before the latest sleep. */
    private int epoch;
    /** The call still waiting for its reply, if any; at most one exists at a time. */
    private Call pending;
    /** A resume found a call from before the sleep still pending: re-read when it returns. */
    private boolean rereadOnReturn;

    /** The latest counted reading: present only while an on-time reply is the current observation. */
    private boolean available;
    private int value;
    private long receivedAt;
    private Scheduler.Task ageTask;

    public ParkPoller(Scheduler scheduler, ParkGetter getter, GateLog log) {
        this.scheduler = scheduler;
        this.getter = getter;
        this.log = log;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Starts the poller asleep: no call is made until the first resume. */
    public void start() {
        started = true;
    }

    /** Sleep entry: polling stops and no reading stays available (AIR-019(d)). */
    public void sleep() {
        if (!started || stopped || asleep) return;
        asleep = true;
        epoch++;
        rereadOnReturn = false;
        if (pending != null) pending.cancelTick();
        boolean had = available;
        dropReading();
        if (had) notifyChanged(false, SLEEP_CAUSE);
    }

    /** Resume: re-read at once, or as soon as a call from before the sleep returns (AIR-019(d)). */
    public void resume() {
        if (!started || stopped || !asleep) return;
        asleep = false;
        if (pending != null) {
            rereadOnReturn = true;
        } else {
            invoke();
        }
    }

    /** Stops polling for good; later replies are ignored. */
    public void stop() {
        if (stopped) return;
        stopped = true;
        if (pending != null) pending.cancelTick();
        pending = null;
        dropReading();
    }

    /** True when the latest counted reading is 1 and fresh by age. */
    public boolean fresh() {
        return !asleep && !stopped && available && value == 1 && isFreshAt(receivedAt, scheduler.now());
    }

    /** The age rule: a reading received at receivedAt is fresh by age at now. */
    public static boolean isFreshAt(long receivedAt, long now) {
        return now - receivedAt <= MAX_AGE_MS;
    }

    // ------------------------------------------------------------ calls

    private void invoke() {
        final Call c = new Call(scheduler.now(), epoch);
        pending = c;
        c.tick = scheduler.at(c.begin + PERIOD_MS, new Runnable() {
            @Override
            public void run() {
                tick(c);
            }
        });
        try {
            getter.call(new ParkGetter.ParkReply() {
                @Override
                public void value(int v) {
                    replied(c, true, v);
                }

                @Override
                public void failed(String why) {
                    replied(c, false, 0);
                }
            });
        } catch (RuntimeException e) {
            replied(c, false, 0);
        }
    }

    /** 200 ms after an invocation: a call still pending times out; otherwise the next poll starts. */
    private void tick(Call c) {
        c.tick = null;
        if (stopped || asleep) return;
        if (pending == c) {
            c.timedOut = true;
            boolean had = available;
            dropReading();
            if (had) notifyChanged(false, TIMEOUT_CAUSE);
        } else if (pending == null) {
            invoke();
        }
    }

    private void replied(Call c, boolean ok, int v) {
        if (c.done || stopped) return;
        c.done = true;
        if (pending == c) pending = null;
        long now = scheduler.now();
        if (asleep || c.epoch != epoch) {
            c.cancelTick();
            discard(c, ok, v, now);
            return;
        }
        boolean late = now - c.begin >= PERIOD_MS;
        if (late) {
            c.cancelTick();
            boolean had = available;
            dropReading();
            if (had) notifyChanged(false, TIMEOUT_CAUSE);
            invoke();
            return;
        }
        boolean park = ok && v == 1;
        boolean was = fresh();
        available = ok && (v == 0 || v == 1);
        value = ok ? v : -1;
        receivedAt = now;
        cancelAge();
        if (park) {
            ageTask = scheduler.at(now + MAX_AGE_MS + 1, new Runnable() {
                @Override
                public void run() {
                    ageTask = null;
                    if (!fresh() && available) {
                        available = false;
                        notifyChanged(false, AGE_CAUSE);
                    }
                }
            });
        }
        if (was != fresh() || !was) {
            if (ok) {
                notifyChanged(true, "the Park reading of " + v + " received at " + now + " ms");
            } else {
                notifyChanged(false, FAILED_CAUSE);
            }
        }
    }

    /** A reply that arrived asleep, or after resume from a call started before the sleep (AIR-019(g)). */
    private void discard(Call c, boolean ok, int v, long now) {
        String when = asleep ? "during sleep" : "after resume";
        log.record("park reply discarded " + when + " value=" + describe(ok, v) + " call=" + c.begin
                + " received=" + now);
        if (!asleep && rereadOnReturn) {
            rereadOnReturn = false;
            invoke();
        }
    }

    private static String describe(boolean ok, int v) {
        return ok ? Integer.toString(v) : "none";
    }

    private void dropReading() {
        available = false;
        cancelAge();
    }

    private void cancelAge() {
        if (ageTask != null) {
            ageTask.cancel();
            ageTask = null;
        }
    }

    private void notifyChanged(boolean reading, String cause) {
        if (listener != null) listener.changed(reading, cause);
    }

    /** One getter call: when it was invoked, and in which wake period. */
    private static final class Call {
        final long begin;
        final int epoch;
        Scheduler.Task tick;
        boolean done;
        boolean timedOut;

        Call(long begin, int epoch) {
            this.begin = begin;
            this.epoch = epoch;
        }

        void cancelTick() {
            if (tick != null) {
                tick.cancel();
                tick = null;
            }
        }
    }
}
