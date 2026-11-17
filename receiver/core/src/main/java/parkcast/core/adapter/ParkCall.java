package parkcast.core.adapter;

import parkcast.core.port.GearSource;
import parkcast.core.port.Threads;
import parkcast.gate.port.ParkGetter;
import parkcast.gate.port.Scheduler;

/**
 * The gate's {@link ParkGetter} over the stock getGearPStatus() (AIR-058). Each call runs on the worker
 * thread and its answer is posted to the main looper. A call that throws, or fails to link, is unavailable
 * and logged with the throwable's class name. A call with no answer by 200 ms is logged as a timeout at
 * 200 ms; its late answer is then delivered as unavailable, never as a reading, so the gate starts no new
 * call while the worker is still busy with this one.
 */
public final class ParkCall implements ParkGetter {
    static final long TIMEOUT_MS = 200L;
    static final String TIMEOUT = "java.util.concurrent.TimeoutException";
    static final String RECORD = "park reading unavailable cause=";

    /** Where the adapter's log records go: the gate's log. */
    public interface Sink {
        void record(String line);
    }

    private final Scheduler scheduler;
    private final Threads threads;
    private final GearSource gear;
    private final Sink log;

    public ParkCall(Scheduler scheduler, Threads threads, GearSource gear, Sink log) {
        this.scheduler = scheduler;
        this.threads = threads;
        this.gear = gear;
        this.log = log;
    }

    @Override
    public void call(final ParkReply cb) {
        final Pending p = new Pending(cb);
        p.timer = scheduler.at(scheduler.now() + TIMEOUT_MS, new Runnable() {
            @Override
            public void run() {
                p.timer = null;
                if (p.done) return;
                p.timedOut = true;
                log.record(RECORD + TIMEOUT);
            }
        });
        threads.background(new Runnable() {
            @Override
            public void run() {
                int v = 0;
                String failure = null;
                try {
                    v = gear.getGearPStatus();
                } catch (Exception e) {
                    failure = e.getClass().getName();
                } catch (LinkageError e) {
                    failure = e.getClass().getName();
                }
                final int value = v;
                final String fault = failure;
                threads.main(new Runnable() {
                    @Override
                    public void run() {
                        deliver(p, value, fault);
                    }
                });
            }
        });
    }

    private void deliver(Pending p, int v, String fault) {
        if (p.done) return;
        p.done = true;
        if (p.timer != null) {
            p.timer.cancel();
            p.timer = null;
        }
        if (p.timedOut) {
            p.cb.failed(TIMEOUT);
        } else if (fault != null) {
            log.record(RECORD + fault);
            p.cb.failed(fault);
        } else {
            p.cb.value(v);
        }
    }

    /** One call's state, touched only on the main looper. */
    private static final class Pending {
        final ParkReply cb;
        Scheduler.Task timer;
        boolean done;
        boolean timedOut;

        Pending(ParkReply cb) {
            this.cb = cb;
        }
    }
}
