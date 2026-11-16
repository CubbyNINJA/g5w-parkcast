package parkcast.gate;

import parkcast.gate.port.AccSource;
import parkcast.gate.port.BuildInfo;
import parkcast.gate.port.ConfigFile;
import parkcast.gate.port.Display;
import parkcast.gate.port.Frame;
import parkcast.gate.port.LogFiles;
import parkcast.gate.port.MirrorSession;
import parkcast.gate.port.NoticeWindows;
import parkcast.gate.port.ParkGetter;
import parkcast.gate.port.RegulationSource;
import parkcast.gate.port.ReverseSource;
import parkcast.gate.port.Scheduler;
import parkcast.gate.port.VehicleBus;

/**
 * The receiver's lifecycle as the gate sees it: the activity callbacks, each frame, and exit.
 * The mirror session and the vehicle bus are handed in so the scenarios can prove nothing reaches them.
 * Skeleton: no logic yet.
 */
public final class Receiver {
    private final Scheduler scheduler;
    private final ParkGetter park;
    private final AccSource acc;
    private final ReverseSource reverse;
    private final NoticeWindows windows;
    private final LogFiles logs;
    private final ConfigFile config;
    private final BuildInfo build;
    private final RegulationSource regulation;
    private final Display display;
    private final MirrorSession session;
    private final VehicleBus vehicle;
    private final int displayWidth;

    public Receiver(Scheduler scheduler, ParkGetter park, AccSource acc, ReverseSource reverse,
            NoticeWindows windows, LogFiles logs, ConfigFile config, BuildInfo build, RegulationSource regulation,
            Display display, MirrorSession session, VehicleBus vehicle, int displayWidth) {
        this.scheduler = scheduler;
        this.park = park;
        this.acc = acc;
        this.reverse = reverse;
        this.windows = windows;
        this.logs = logs;
        this.config = config;
        this.build = build;
        this.regulation = regulation;
        this.display = display;
        this.session = session;
        this.vehicle = vehicle;
        this.displayWidth = displayWidth;
    }

    public void onCreate() {
        throw new UnsupportedOperationException("not implemented: Receiver.onCreate");
    }

    public void onStart() {
        throw new UnsupportedOperationException("not implemented: Receiver.onStart");
    }

    public void onResume() {
        throw new UnsupportedOperationException("not implemented: Receiver.onResume");
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        throw new UnsupportedOperationException("not implemented: Receiver.onWindowFocusChanged");
    }

    public void onFrame(Frame f) {
        throw new UnsupportedOperationException("not implemented: Receiver.onFrame");
    }

    public void exit() {
        throw new UnsupportedOperationException("not implemented: Receiver.exit");
    }
}
