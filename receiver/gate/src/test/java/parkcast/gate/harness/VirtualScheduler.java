package parkcast.gate.harness;

import java.util.PriorityQueue;
import parkcast.gate.port.Scheduler;

/**
 * A controlled clock in whole milliseconds. Events at the same millisecond run in rank order:
 * FIRST (activity callbacks), BEFORE (scripted car and ACC changes), GATE (the gate's own timers),
 * AFTER (Park replies by default, exit), FRAME (the next mirrored frame).
 */
public final class VirtualScheduler implements Scheduler {
    public static final int FIRST = -1, BEFORE = 0, GATE = 1, AFTER = 2, FRAME = 3;
    private static final int MAX_EVENTS = 2_000_000;

    private static final class Ev implements Comparable<Ev> {
        final long t;
        final int rank;
        final long seq;
        final Runnable r;
        boolean cancelled;

        Ev(long t, int rank, long seq, Runnable r) {
            this.t = t;
            this.rank = rank;
            this.seq = seq;
            this.r = r;
        }

        @Override
        public int compareTo(Ev o) {
            if (t != o.t) return Long.compare(t, o.t);
            if (rank != o.rank) return Integer.compare(rank, o.rank);
            return Long.compare(seq, o.seq);
        }
    }

    private final PriorityQueue<Ev> queue = new PriorityQueue<>();
    private long now;
    private long seq;
    private int events;
    /** Which process start is running, from 1. */
    public int startIndex = 1;

    @Override
    public long now() {
        return now;
    }

    @Override
    public Task at(long ms, Runnable r) {
        final Ev e = add(ms, GATE, r);
        return () -> e.cancelled = true;
    }

    /** Schedules a harness event at {@code ms}, or now if that has passed. */
    public void script(long ms, int rank, Runnable r) {
        add(ms, rank, r);
    }

    private Ev add(long ms, int rank, Runnable r) {
        Ev e = new Ev(Math.max(ms, now), rank, seq++, r);
        queue.add(e);
        return e;
    }

    /** Runs every event up to and including {@code t}, then leaves the clock at {@code t}. */
    public void runUntil(long t) {
        while (!queue.isEmpty() && queue.peek().t <= t) {
            Ev e = queue.poll();
            if (e.cancelled) continue;
            if (++events > MAX_EVENTS) throw new IllegalStateException("scheduler ran more than " + MAX_EVENTS + " events");
            now = Math.max(now, e.t);
            e.r.run();
        }
        now = Math.max(now, t);
    }

    /** A new process: nothing pending, the clock back at 0. */
    public void newStart() {
        queue.clear();
        now = 0;
        startIndex++;
    }
}
