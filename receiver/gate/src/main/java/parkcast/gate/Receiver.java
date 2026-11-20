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
 *
 * <p>{@code onCreate} reads the flag, identifies the build, logs the start, starts regulation logging and
 * registers the {@code REVERSE_ON} receiver, so that registration returns before the window can gain focus.
 * {@code onResume} registers for the ACC broadcast; the Park poller makes no call until that registration
 * reports the car awake. Frames pass through the gate. Exit is synchronous and makes every unregister and
 * notice removal call at the clock time it starts.
 *
 * <p>The mirror session and the vehicle bus are handed in so the scenarios can prove nothing reaches them:
 * the gate never mutes, stops or disconnects audio or the iPhone connection, and writes nothing to the car.
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

    private final GateLog log;
    private final Notices notices;
    private final ReceiverConfig flag;
    private final ParkPoller poller;
    private final ParkingGate gate;
    private final AccState accState;
    private final Cover cover;
    private final RegulationLog regulationLog;

    private StartHook startHook;

    private boolean created;
    private boolean resumed;
    private boolean exited;

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

        this.log = new GateLog(logs, scheduler);
        this.notices = new Notices(windows, scheduler, displayWidth);
        this.log.setNotices(notices);
        this.notices.setLog(log);
        this.flag = new ReceiverConfig(config);
        this.poller = new ParkPoller(scheduler, park, log);
        this.gate = new ParkingGate(display, poller, flag, build, log);
        this.poller.setListener(gate);
        this.accState = new AccState(acc, flag, log, notices, poller);
        this.cover = new Cover(reverse, log, notices);
        this.regulationLog = new RegulationLog(regulation, log);
    }

    /**
     * Reads the flag, sets and logs the initial gate state, and registers the {@code REVERSE_ON} receiver.
     * The start record gives the flag as the startup value with the previous value unknown (AIR-026(b)): no
     * state is read from an earlier run or its log. The {@code reason=} field goes last because it holds spaces.
     */
    public void onCreate() {
        if (created || exited) return;
        created = true;
        flag.load();
        boolean on = flag.requirePark();
        if (startHook != null) startHook.beforeStartRecord(on);
        if (!on) log.record("parking check off");
        gate.start();
        log.record("start check=" + onOff(on) + " prev=unknown as=the startup value gate="
                + ParkingGate.state(gate.permitted()) + " reason=" + flag.reason());
        log.record("flag prev=unknown new=" + onOff(on) + " cause=the startup value");
        regulationLog.start();
        cover.register();
        poller.start();
    }

    public void onStart() {
        // Nothing to do: registration for the ACC broadcast waits for onResume.
    }

    /** Registers for the ACC broadcast once per app start. */
    public void onResume() {
        if (!created || resumed || exited) return;
        resumed = true;
        accState.register();
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        if (exited) return;
        cover.focusChanged(hasFocus);
    }

    /** Shows the frame only while the gate permits video. */
    public void onFrame(Frame f) {
        if (exited) return;
        gate.onFrame(f);
    }

    /** Synchronous exit: unregisters, removes notice windows and stops polling, all at this clock time. */
    public void exit() {
        if (exited) return;
        exited = true;
        cover.exit();
        accState.unregister();
        notices.exit();
        poller.stop();
        regulationLog.stop();
    }

    /**
     * Called by {@link #onCreate} once the flag is read and before the gate's first record, so the S3 runtime
     * can write its start marker first (AIR-071(a), OD-189).
     */
    public interface StartHook {
        /** @param requirePark the effective {@code require_park} value for this app start */
        void beforeStartRecord(boolean requirePark);
    }

    /** Sets the hook {@link #onCreate} calls before its first record. Set before {@code onCreate}. */
    public void setStartHook(StartHook hook) {
        this.startHook = hook;
    }

    /** Records start with the bare uptime in milliseconds instead of {@code t=} (AIR-071(g)). */
    public void useUptimeLog() {
        log.useUptimePrefix();
    }

    /** Records failed registration- and logging-failure notice attempts as well (AIR-061(b)); S3 only. */
    public void logNoticeAddFailures() {
        notices.logAddFailures();
    }

    /** Writes one record through the receiver's log, with its rotation and logging-failure handling. */
    public void record(String line) {
        log.record(line);
    }

    /** The receiver could not start: show the one start-failure notice for {@code cause} (AIR-069). */
    public void startFailed(String cause) {
        if (exited) return;
        notices.startFailed(cause);
    }

    private static String onOff(boolean on) {
        return on ? "on" : "off";
    }
}
