package parkcast.gate;

import parkcast.gate.port.AccSource;
import parkcast.gate.port.Scheduler;

/** Tracks the car's power state from the ACC broadcast. Skeleton: no logic yet. */
public final class AccState {
    private final AccSource source;
    private final Scheduler scheduler;

    public AccState(AccSource source, Scheduler scheduler) {
        this.source = source;
        this.scheduler = scheduler;
    }

    public void register() {
        throw new UnsupportedOperationException("not implemented: AccState.register");
    }

    public boolean asleep() {
        throw new UnsupportedOperationException("not implemented: AccState.asleep");
    }

    public void unregister() {
        throw new UnsupportedOperationException("not implemented: AccState.unregister");
    }
}
