package parkcast.core.doubles;

import java.util.LinkedHashMap;
import java.util.Map;
import parkcast.gate.harness.VirtualScheduler;
import parkcast.gate.port.ParkGetter;

/** Sits between the gate and a Park getter and records what the gate received for each call, by start time. */
public final class RecordingParkGetter implements ParkGetter {
    private final VirtualScheduler s;
    private final ParkGetter inner;
    /** Start time to "value N" or "unavailable"; absent while no answer came. */
    public final Map<Long, String> received = new LinkedHashMap<>();

    public RecordingParkGetter(VirtualScheduler s, ParkGetter inner) {
        this.s = s;
        this.inner = inner;
    }

    @Override
    public void call(final ParkReply cb) {
        final long begin = s.now();
        inner.call(new ParkReply() {
            @Override
            public void value(int v) {
                received.put(begin, "value " + v);
                cb.value(v);
            }

            @Override
            public void failed(String why) {
                received.put(begin, "unavailable");
                cb.failed(why);
            }
        });
    }
}
