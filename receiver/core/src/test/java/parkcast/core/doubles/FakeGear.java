package parkcast.core.doubles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import parkcast.core.port.GearSource;
import parkcast.gate.harness.VirtualScheduler;

/**
 * The stock VehicleInfoManager.getGearPStatus() double. Each call is keyed by its virtual start time: it
 * takes {@link #defaultMs} unless a duration is set for that start, returns {@link #value} unless a fault is
 * set, and records its start, end and thread.
 */
public final class FakeGear implements GearSource {
    public static final class Call {
        public final long begin, end;
        public final Thread thread;

        Call(long begin, long end, Thread thread) {
            this.begin = begin;
            this.end = end;
            this.thread = thread;
        }

        @Override
        public String toString() {
            return begin + "-" + end + " on " + thread.getName();
        }
    }

    private final VirtualScheduler s;
    private final FakeThreads threads;
    private final Trace trace;
    public long defaultMs = 10;
    public int value = 1;
    public final Map<Long, Long> durationAt = new HashMap<>();
    public final Map<Long, Throwable> faultAt = new HashMap<>();
    public final List<Call> calls = new ArrayList<>();

    public FakeGear(VirtualScheduler s, FakeThreads threads, Trace trace) {
        this.s = s;
        this.threads = threads;
        this.trace = trace;
    }

    @Override
    public int getGearPStatus() throws Exception {
        long begin = s.now();
        Long d = durationAt.get(begin);
        long ms = d == null ? defaultMs : d;
        threads.took(ms);
        calls.add(new Call(begin, begin + ms, Thread.currentThread()));
        trace.add("gear", "getGearPStatus", "begin=" + begin + " end=" + (begin + ms));
        Throwable t = faultAt.get(begin);
        if (t instanceof Exception) throw (Exception) t;
        if (t instanceof Error) throw (Error) t;
        return value;
    }
}
