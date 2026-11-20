package parkcast.core;

import parkcast.core.port.AppFiles;
import parkcast.core.port.CodecFactory;
import parkcast.core.port.CoverView;
import parkcast.core.port.CrashHook;
import parkcast.core.port.InterfaceQuery;
import parkcast.core.port.NativeEvents;
import parkcast.core.port.NativeLibrary;
import parkcast.core.port.NsdWake;
import parkcast.core.port.ProcNetTcp;
import parkcast.gate.Receiver;
import parkcast.gate.port.AccSource;
import parkcast.gate.port.BuildInfo;
import parkcast.gate.port.ConfigFile;
import parkcast.gate.port.Display;
import parkcast.gate.port.LogFiles;
import parkcast.gate.port.MirrorSession;
import parkcast.gate.port.NoticeWindows;
import parkcast.gate.port.ParkGetter;
import parkcast.gate.port.RegulationSource;
import parkcast.gate.port.ReverseSource;
import parkcast.gate.port.Scheduler;
import parkcast.gate.port.VehicleBus;

/**
 * The S3 receiver: the S2 gate plus the start and stop of the native receiver (docs/design/S3_RECEIVER.md).
 * Every collaborator comes in through the constructor; there is no static state. {@code onCreate} runs the
 * gate's start, whose hook writes the start marker first (AIR-071(a)), then {@link StartSequence}. Exit runs
 * {@link StopSequence} once.
 */
public final class ReceiverRuntime {
    static final String UNKNOWN_PRODUCT = "unknown";

    private final Receiver gate;
    private final BuildInfo build;
    private final CrashHook crash;
    private final String version;
    private final String commit;
    private final String uxplay;
    private final StartSequence start;
    private final StopSequence stop;
    private final RuntimeLog log;

    public ReceiverRuntime(Scheduler scheduler, ParkGetter park, AccSource acc, ReverseSource reverse,
            NoticeWindows windows, LogFiles logs, ConfigFile config, BuildInfo build, RegulationSource regulation,
            Display display, MirrorSession session, VehicleBus vehicle, int displayWidth, AppFiles files,
            InterfaceQuery ifaces, NativeLibrary nativeLib, NsdWake nsd, CodecFactory codecs, CoverView cover,
            CrashHook crash, String version, String commit, String uxplay) {
        final CodecDisplay codecDisplay = new CodecDisplay(display, cover);
        this.codecDisplay = codecDisplay;
        this.gate = new Receiver(scheduler, park, acc, reverse, windows, logs, config, build, regulation,
                codecDisplay, session, vehicle, displayWidth);
        this.build = build;
        this.crash = crash;
        this.version = version;
        this.commit = commit;
        this.uxplay = uxplay;
        final Receiver g = gate;
        this.log = new RuntimeLog() {
            @Override
            public void record(String line) {
                g.record(line);
            }

            @Override
            public void startFailed(String cause) {
                g.startFailed(cause);
            }
        };
        // The black cover follows the gate through CodecDisplay (AIR-057(e)).
        DecoderControl decoder = new DecoderControl(codecs, nativeLib, log);
        this.decoder = decoder;
        this.sessions = new SessionLog(log, new NativeLogFilter(files, StartSequence.KEY_FILE));
        decoder.useSessionLog(sessions);
        decoder.attach(scheduler, codecDisplay, cover, gate);
        Advertiser advertiser = new Advertiser(scheduler, nsd, nativeLib, log);
        this.start = new StartSequence(new HotspotCheck(ifaces), files, nativeLib, advertiser, decoder, log);
        this.stop = new StopSequence(gate, start, advertiser, decoder, nativeLib, log, display, cover);
        gate.useUptimeLog();
        gate.logNoticeAddFailures();
        gate.setStartHook(new Receiver.StartHook() {
            @Override
            public void beforeStartRecord(boolean requirePark) {
                log.record(marker(requirePark));
            }
        });
    }

    public void onCreate() {
        crash.install(new CrashHook.CrashSink() {
            @Override
            public void crashed(Throwable t) {
                // TODO(phase B: crash record) write the crash record and run the stop steps (AIR-071).
                new CrashRecorder(log).record(t);
            }
        });
        gate.onCreate();
        sessions.filterReady();
        try {
            start.run();
        } catch (RuntimeException e) {
            // AIR-069(d): a failure outside the five causes is logged with no notice.
            log.record("start failed " + e.getClass().getName());
        }
    }

    public void onStart() {
        gate.onStart();
    }

    public void onResume() {
        gate.onResume();
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        gate.onWindowFocusChanged(hasFocus);
    }

    /** The owner's exit: runs the stop steps once, then writes the end marker. */
    public void exit() {
        stop.run(StopSequence.OWNER_EXIT);
    }

    public void onPause() {
    }

    public void onStop() {
    }

    /** The system destroys the activity: the stop steps run once, with reason=destroy, unless exit ran them. */
    public void onDestroy() {
        stop.run(StopSequence.DESTROY);
    }

    private String marker(boolean requirePark) {
        return "parkcast start app=" + version + " commit=" + commit + " uxplay=" + uxplay + " product="
                + product() + " require_park=" + requirePark;
    }

    private String product() {
        String p;
        try {
            p = build.product();
        } catch (BuildInfo.Unreadable e) {
            return UNKNOWN_PRODUCT;
        } catch (RuntimeException e) {
            return UNKNOWN_PRODUCT;
        }
        return p == null ? UNKNOWN_PRODUCT : p;
    }

    /** Wires the CarPlay listener check over {@code /proc/net/tcp} (AIR-079(b) to (d)). */
    public void useProcNetTcp(ProcNetTcp p) {
        start.useCarplayProbe(new CarplayProbe(p, log));
    }

    private final DecoderControl decoder;
    private final SessionLog sessions;
    private final CodecDisplay codecDisplay;

    /** The library's event sink, as the native side calls it. */
    NativeEvents events() {
        return decoder;
    }

    SessionLog sessionLog() {
        return sessions;
    }

    /** Writes one record to the receiver's log, for the platform adapters (AIR-058). */
    public void record(String line) {
        gate.record(line);
    }

    /**
     * A decoded frame from input queued while video was permitted, delivered on the event thread (AIR-062). The
     * decoder offers its own frames through {@link CodecDisplay}; this entry stands in for it.
     */
    // Package-private on purpose: real frames come from DecoderControl; only the test seam (CoreSeams.frame)
    // calls this, so production code cannot mark a frame fresh without decoding it.
    void onFrame(parkcast.gate.port.Frame f) {
        codecDisplay.offer(true);
        gate.onFrame(f);
    }
}
