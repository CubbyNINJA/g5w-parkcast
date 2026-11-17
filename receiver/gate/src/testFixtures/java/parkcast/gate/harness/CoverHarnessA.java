package parkcast.gate.harness;

import java.util.ArrayList;
import java.util.List;
import parkcast.gate.World;

/**
 * The focus and broadcast doubles of 11's preamble for one scenario (injected by cucumber-picocontainer).
 * Focus changes and REVERSE_ON are scripted on the scenario's clock at rank FIRST, so they run before the
 * gate's own timers of the same millisecond; each delivery records the notice-op indexes around its
 * callback, so a step can tell whether a removal call was made inside it.
 *
 * The receiver's reverse port has no REVERSE_OFF channel and carries no extras, so a REVERSE_OFF delivery
 * is recorded but reaches nothing, and "with extras" is the same delivery as "with no extras".
 */
public final class CoverHarnessA {

    /** One delivered focus change or broadcast. */
    public static final class Delivery {
        /** "gain", "loss", "reverse_on" or "reverse_off". */
        public final String type;
        public final long t;
        /** Indexes into {@code notices.ops}: ops in [before, after) were made inside the callback. */
        public int opsBefore = -1, opsAfter = -1;
        /** False when there was no receiver or listener to deliver to. */
        public boolean delivered;

        Delivery(String type, long t) {
            this.type = type;
            this.t = t;
        }

        public boolean isFocus() {
            return "gain".equals(type) || "loss".equals(type);
        }

        @Override
        public String toString() {
            return type + "@" + t + (delivered ? "" : "(undelivered)");
        }
    }

    private final World w;
    public final List<Delivery> deliveries = new ArrayList<>();

    public CoverHarnessA(World w) {
        this.w = w;
    }

    /** A focus gain or loss at {@code t}; 0 ms runs right after the lifecycle. */
    public void focus(long t, final boolean gain) {
        final Delivery d = new Delivery(gain ? "gain" : "loss", t);
        deliveries.add(d);
        w.scheduler.script(t, rank(t), () -> {
            if (w.receiver == null) return;
            d.opsBefore = w.notices.ops.size();
            d.delivered = true;
            try {
                w.receiver.onWindowFocusChanged(gain);
            } finally {
                d.opsAfter = w.notices.ops.size();
            }
        });
    }

    /** REVERSE_ON delivered to the runtime receiver, if one is registered at {@code t}. */
    public void reverseOn(long t) {
        final Delivery d = new Delivery("reverse_on", t);
        deliveries.add(d);
        w.scheduler.script(t, rank(t), () -> {
            if (w.reverse.listener == null) return;
            d.opsBefore = w.notices.ops.size();
            d.delivered = true;
            try {
                w.reverse.listener.reverseOn();
            } finally {
                d.opsAfter = w.notices.ops.size();
            }
        });
    }

    /** REVERSE_OFF: the port has no channel for it, so the delivery reaches nothing. */
    public void reverseOff(long t) {
        final Delivery d = new Delivery("reverse_off", t);
        deliveries.add(d);
        w.scheduler.script(t, rank(t), () -> {
            d.opsBefore = w.notices.ops.size();
            d.opsAfter = d.opsBefore;
        });
    }

    /** The delivery of {@code type} at {@code t} whose callback made op {@code index}, or null. */
    public Delivery enclosing(int index, long t, boolean focus) {
        for (Delivery d : deliveries)
            if (d.t == t && d.isFocus() == focus && !"reverse_off".equals(d.type)
                    && d.opsBefore >= 0 && index >= d.opsBefore && index < d.opsAfter)
                return d;
        return null;
    }

    /** The first delivered delivery of {@code type} at or after {@code from}, or null. */
    public Delivery first(String type, long from) {
        Delivery best = null;
        for (Delivery d : deliveries)
            if (d.type.equals(type) && d.t >= from && (best == null || d.t < best.t)) best = d;
        return best;
    }

    private static int rank(long t) {
        return t == 0 ? VirtualScheduler.BEFORE : VirtualScheduler.FIRST;
    }
}
