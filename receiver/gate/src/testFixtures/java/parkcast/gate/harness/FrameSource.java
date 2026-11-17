package parkcast.gate.harness;

import java.util.ArrayList;
import java.util.List;
import parkcast.gate.Receiver;
import parkcast.gate.port.Frame;

/**
 * Delivers decoded frames to {@link Receiver#onFrame(Frame)} at FRAME rank, one every {@link #period} ms
 * from {@link #from} ms. A frozen source delivers no new frames. Each tick schedules the next one, so a
 * run of any length costs one event per frame. Exceptions from the receiver propagate.
 */
public final class FrameSource {
    private final VirtualScheduler s;
    public long period = 16, from = 0;
    public boolean frozen;
    /** Every frame delivered, all starts. */
    public final List<Tick> ticks = new ArrayList<>();
    private long nextId = 1;
    private Receiver target;

    public static final class Tick {
        public final long t, id;
        public final int start;

        Tick(long t, long id, int start) {
            this.t = t;
            this.id = id;
            this.start = start;
        }

        @Override
        public String toString() {
            return "frame#" + id + "@" + t;
        }
    }

    public FrameSource(VirtualScheduler s) {
        this.s = s;
    }

    /** Starts delivering to {@code r} on the current start's clock. */
    public void startFor(Receiver r) {
        target = r;
        final Receiver mine = r;
        schedule(from, mine);
    }

    private void schedule(final long t, final Receiver mine) {
        final int start = s.startIndex;
        s.script(t, VirtualScheduler.FRAME, () -> {
            if (target != mine || s.startIndex != start) return;
            schedule(t + period, mine);
            if (frozen) return;
            Frame f = new Frame(nextId++, t);
            ticks.add(new Tick(t, f.id, start));
            mine.onFrame(f);
        });
    }

    /** Frames delivered in the current start. */
    public List<Tick> current() {
        List<Tick> out = new ArrayList<>();
        for (Tick k : ticks) if (k.start == s.startIndex) out.add(k);
        return out;
    }
}
