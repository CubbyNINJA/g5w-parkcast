package parkcast.gate;

import parkcast.gate.port.BuildInfo;
import parkcast.gate.port.Display;
import parkcast.gate.port.Frame;

/**
 * Decides, frame by frame, whether mirrored video may be shown (AIR-017(a), AIR-018, AIR-020, AIR-026).
 *
 * <p>With the parking check on, the gate starts blocked and permits video only while the poller holds a
 * current, available reading of {@code 1} on a supported build. A build is supported when
 * {@code ro.build.product} starts with {@code CVEV.}; a missing or unreadable property, or any other value,
 * leaves the gate blocked even on a {@code 1}. With the check off, video is never blocked.
 *
 * <p>Each change of state is logged with the previous and resulting states and its cause. A change to
 * blocked clears the display at once, and no frame is shown while blocked, so a queued frame never appears.
 * The stock regulation state and the ACC broadcast never decide the gate directly: sleep reaches the gate
 * only through the poller, which holds no reading while asleep.
 */
public final class ParkingGate implements ParkPoller.Listener {
    /** The supported firmware family, matched on {@code ro.build.product} (AIR-020(a)). */
    static final String SUPPORTED_PREFIX = "CVEV.";

    private final Display display;
    private final ParkPoller poller;
    private final ReceiverConfig config;
    private final BuildInfo build;
    private final GateLog log;

    private boolean started;
    private boolean supported;
    private boolean permitted;

    public ParkingGate(Display display, ParkPoller poller, ReceiverConfig config, BuildInfo build, GateLog log) {
        this.display = display;
        this.poller = poller;
        this.config = config;
        this.build = build;
        this.log = log;
    }

    /**
     * Identifies the build and sets the initial state: blocked with the check on, permitted with it off.
     * Writes nothing; the caller logs the initial state in the start record.
     */
    public void start() {
        if (started) return;
        started = true;
        supported = identify();
        permitted = !config.requirePark();
    }

    /** True while video may be shown. */
    public boolean permitted() {
        return permitted;
    }

    /** Shows the frame only while permitted; a frame that arrives while blocked is dropped. */
    public void onFrame(Frame f) {
        if (!started) return;
        evaluate("reading older than " + ParkPoller.MAX_AGE_MS + " ms");
        if (permitted) display.show(f);
    }

    @Override
    public void changed(boolean reading, String cause) {
        evaluate(cause);
    }

    /** Recomputes the state; on a change, logs the transition and clears the display when now blocked. */
    private void evaluate(String cause) {
        if (!started) return;
        boolean now = !config.requirePark() || (supported && poller.fresh());
        if (now == permitted) return;
        permitted = now;
        if (!now) display.clear();
        log.record("gate prev=" + state(!now) + " new=" + state(now) + " cause=" + cause);
    }

    static String state(boolean permitted) {
        return permitted ? "permitted" : "blocked";
    }

    private boolean identify() {
        String product;
        try {
            product = build.product();
        } catch (BuildInfo.Unreadable e) {
            return false;
        } catch (RuntimeException e) {
            return false;
        }
        return product != null && product.startsWith(SUPPORTED_PREFIX);
    }
}
