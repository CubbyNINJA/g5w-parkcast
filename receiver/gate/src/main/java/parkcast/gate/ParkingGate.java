package parkcast.gate;

import parkcast.gate.port.BuildInfo;
import parkcast.gate.port.Display;
import parkcast.gate.port.Frame;

/** Decides, frame by frame, whether mirrored video may be shown. Skeleton: no logic yet. */
public final class ParkingGate {
    private final Display display;
    private final ParkPoller poller;
    private final AccState acc;
    private final Cover cover;
    private final ReceiverConfig config;
    private final BuildInfo build;
    private final GateLog log;

    public ParkingGate(Display display, ParkPoller poller, AccState acc, Cover cover, ReceiverConfig config,
            BuildInfo build, GateLog log) {
        this.display = display;
        this.poller = poller;
        this.acc = acc;
        this.cover = cover;
        this.config = config;
        this.build = build;
        this.log = log;
    }

    public void onFrame(Frame f) {
        throw new UnsupportedOperationException("not implemented: ParkingGate.onFrame");
    }

    public boolean permitted() {
        throw new UnsupportedOperationException("not implemented: ParkingGate.permitted");
    }
}
