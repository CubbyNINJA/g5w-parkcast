package parkcast.gate;

import parkcast.gate.port.LogFiles;
import parkcast.gate.port.Scheduler;

/** Writes the receiver's records; a write failure raises the logging-failure notice. Skeleton: no logic yet. */
public final class GateLog {
    private final LogFiles files;
    private final Scheduler scheduler;

    public GateLog(LogFiles files, Scheduler scheduler) {
        this.files = files;
        this.scheduler = scheduler;
    }

    public void record(String line) {
        throw new UnsupportedOperationException("not implemented: GateLog.record");
    }

    public void setNotices(Notices notices) {
        throw new UnsupportedOperationException("not implemented: GateLog.setNotices");
    }
}
