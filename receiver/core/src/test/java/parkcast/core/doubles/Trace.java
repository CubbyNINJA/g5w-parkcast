package parkcast.core.doubles;

import java.util.ArrayList;
import java.util.List;
import parkcast.gate.harness.VirtualScheduler;

/**
 * One ordered record of every call the receiver makes on a core double, shared by all of them so steps can
 * compare order across doubles ("all five binds returned before the first dns_sd registration"). Each entry
 * carries the app start it happened in and the virtual time it returned.
 */
public final class Trace {
    public static final class Entry {
        public final int seq, start;
        public final long t;
        /** The double: iface, socket, native, nsd, dnssd, fs, cover, codec, crash. */
        public final String cause;
        public final String name, details;

        Entry(int seq, int start, long t, String cause, String name, String details) {
            this.seq = seq;
            this.start = start;
            this.t = t;
            this.cause = cause;
            this.name = name;
            this.details = details;
        }

        @Override
        public String toString() {
            return "#" + seq + " s" + start + " " + t + "ms " + cause + "." + name + (details.isEmpty() ? "" : " " + details);
        }
    }

    private final VirtualScheduler s;
    public final List<Entry> entries = new ArrayList<>();

    public Trace(VirtualScheduler s) {
        this.s = s;
    }

    public Entry add(String cause, String name, String details) {
        Entry e = new Entry(entries.size(), s.startIndex, s.now(), cause, name, details == null ? "" : details);
        entries.add(e);
        return e;
    }

    /** Entries of {@code cause} (null for any) named {@code name} (null for any) in {@code start} (null for any). */
    public List<Entry> of(String cause, String name, Integer start) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries)
            if ((cause == null || e.cause.equals(cause)) && (name == null || e.name.equals(name))
                    && (start == null || e.start == start))
                out.add(e);
        return out;
    }

    /** Entries of the current start. */
    public List<Entry> current(String cause, String name) {
        return of(cause, name, s.startIndex);
    }

    public int startIndex() {
        return s.startIndex;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        for (Entry e : entries) b.append(e).append('\n');
        return b.toString();
    }
}
