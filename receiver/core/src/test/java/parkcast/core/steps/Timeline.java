package parkcast.core.steps;

import java.util.ArrayList;
import java.util.List;
import parkcast.core.doubles.Trace;
import parkcast.gate.harness.Fakes;

/**
 * The current start's trace entries and log records merged into one order, so a step can say "the log record
 * X came after this call". A log record written before a trace entry was added sits before it.
 */
final class Timeline {
    /** One trace entry or one log record. */
    static final class Item {
        final Trace.Entry entry;
        /** The log record's text without its time prefix; null for a trace entry. */
        final String record;
        final long t;
        final int index;

        Item(Trace.Entry entry, String record, long t, int index) {
            this.entry = entry;
            this.record = record;
            this.t = t;
            this.index = index;
        }

        boolean is(String cause, String name) {
            return entry != null && entry.cause.equals(cause) && entry.name.equals(name);
        }

        boolean recordStarts(String s) {
            return record != null && record.startsWith(s);
        }

        @Override
        public String toString() {
            return entry != null ? entry.toString() : t + "ms log " + record;
        }
    }

    final List<Item> items = new ArrayList<>();

    Timeline(S3World w) {
        List<Fakes.Line> log = w.g.logs.lines;
        int start = w.startIndex();
        int next = 0;
        for (Trace.Entry e : w.trace.entries) {
            int before = w.trace.logSizes.get(e.seq);
            for (; next < before && next < log.size(); next++) addLine(log.get(next), start);
            if (e.start == start) items.add(new Item(e, null, e.t, items.size()));
        }
        for (; next < log.size(); next++) addLine(log.get(next), start);
    }

    private void addLine(Fakes.Line l, int start) {
        if (l.start == start) items.add(new Item(null, S3World.body(l), l.t, items.size()));
    }

    List<Item> of(String cause, String name) {
        List<Item> out = new ArrayList<>();
        for (Item i : items) if (i.is(cause, name)) out.add(i);
        return out;
    }

    /** The first log record starting with {@code s}, or null. */
    Item record(String s) {
        for (Item i : items) if (i.recordStarts(s)) return i;
        return null;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        for (Item i : items) b.append('\n').append(i);
        return b.toString();
    }
}
