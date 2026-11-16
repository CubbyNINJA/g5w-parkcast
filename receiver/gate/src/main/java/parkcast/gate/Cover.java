package parkcast.gate;

import parkcast.gate.port.ReverseSource;
import parkcast.gate.port.Scheduler;

/** Tracks whether the receiver's window is covered (REVERSE_ON, focus). Skeleton: no logic yet. */
public final class Cover {
    private final ReverseSource reverse;
    private final Scheduler scheduler;

    public Cover(ReverseSource reverse, Scheduler scheduler) {
        this.reverse = reverse;
        this.scheduler = scheduler;
    }

    public void register() {
        throw new UnsupportedOperationException("not implemented: Cover.register");
    }

    public void focusChanged(boolean hasFocus) {
        throw new UnsupportedOperationException("not implemented: Cover.focusChanged");
    }

    public boolean covered() {
        throw new UnsupportedOperationException("not implemented: Cover.covered");
    }
}
