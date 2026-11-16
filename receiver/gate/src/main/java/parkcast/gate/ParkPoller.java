package parkcast.gate;

import parkcast.gate.port.ParkGetter;
import parkcast.gate.port.Scheduler;

/** Polls the Park getter every 200 ms, one call at a time. Skeleton: no logic yet. */
public final class ParkPoller {
    private final Scheduler scheduler;
    private final ParkGetter getter;

    public ParkPoller(Scheduler scheduler, ParkGetter getter) {
        this.scheduler = scheduler;
        this.getter = getter;
    }

    public void start() {
        throw new UnsupportedOperationException("not implemented: ParkPoller.start");
    }

    public void sleep() {
        throw new UnsupportedOperationException("not implemented: ParkPoller.sleep");
    }

    public void resume() {
        throw new UnsupportedOperationException("not implemented: ParkPoller.resume");
    }

    public void stop() {
        throw new UnsupportedOperationException("not implemented: ParkPoller.stop");
    }

    /** True when the latest counted reading is 1 and fresh by age. */
    public boolean fresh() {
        throw new UnsupportedOperationException("not implemented: ParkPoller.fresh");
    }

    /** The age rule: a reading received at receivedAt is fresh by age at now. */
    public static boolean isFreshAt(long receivedAt, long now) {
        throw new UnsupportedOperationException("not implemented: ParkPoller.isFreshAt");
    }
}
