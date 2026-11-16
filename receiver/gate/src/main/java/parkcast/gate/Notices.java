package parkcast.gate;

import parkcast.gate.port.NoticeWindows;
import parkcast.gate.port.Scheduler;

/** The registration-failure and logging-failure notices. Skeleton: no logic yet. */
public final class Notices {
    private final NoticeWindows windows;
    private final Scheduler scheduler;
    private final int displayWidth;

    public Notices(NoticeWindows windows, Scheduler scheduler, int displayWidth) {
        this.windows = windows;
        this.scheduler = scheduler;
        this.displayWidth = displayWidth;
    }

    public void registrationFailed() {
        throw new UnsupportedOperationException("not implemented: Notices.registrationFailed");
    }

    public void loggingFailed() {
        throw new UnsupportedOperationException("not implemented: Notices.loggingFailed");
    }

    public void exit() {
        throw new UnsupportedOperationException("not implemented: Notices.exit");
    }
}
