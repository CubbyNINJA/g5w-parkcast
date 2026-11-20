package parkcast.core.steps;

import java.util.ArrayList;
import java.util.List;
import parkcast.core.ReceiverRuntime;
import parkcast.core.adapter.ConfigAdapter;
import parkcast.core.doubles.FakeConfigReader;
import parkcast.core.doubles.BlackCover;
import parkcast.core.doubles.FakeCodecFactory;
import parkcast.core.doubles.FakeCrashHook;
import parkcast.core.doubles.FakeFileSystem;
import parkcast.core.doubles.FakeInterfaces;
import parkcast.core.doubles.FakeNativeLibrary;
import parkcast.core.doubles.FakeNsd;
import parkcast.core.doubles.FakeProcNetTcp;
import parkcast.core.doubles.Trace;
import parkcast.gate.World;
import parkcast.gate.harness.Fakes;
import parkcast.gate.harness.VirtualScheduler;
import parkcast.gate.port.AccSource;
import parkcast.gate.port.ParkGetter;
import parkcast.gate.port.ReverseSource;

/**
 * One scenario's world: the gate's doubles (through the gate {@link World}) plus the S3 doubles, and the
 * {@link ReceiverRuntime} built over them. Steps assert only on what these doubles recorded.
 */
public final class S3World {
    /** The key file's name inside the app-data directory. */
    public static final String KEY_NAME = "parkcast.key";
    public static final String KEY_PATH = FakeFileSystem.ROOT + KEY_NAME;
    public static final String VERSION = "0.0.0-test", COMMIT = "c0ffee0", UXPLAY = "deadbee";

    public final World g = new World();
    public final VirtualScheduler scheduler = g.scheduler;
    public final Trace trace = new Trace(scheduler);
    public final FakeFileSystem fs = new FakeFileSystem(trace);
    public final FakeNativeLibrary nativeLib = new FakeNativeLibrary(trace, fs);
    public final FakeInterfaces ifaces = new FakeInterfaces(trace);
    public final FakeNsd nsd = new FakeNsd(trace, scheduler);
    public final FakeCodecFactory codecs = new FakeCodecFactory(trace, scheduler);
    public final BlackCover cover = new BlackCover(trace);
    public final FakeCrashHook crash = new FakeCrashHook(trace);
    public final FakeProcNetTcp procNetTcp = new FakeProcNetTcp(trace);

    /** When the window first gains focus; 0 = inside the start, -1 = never. */
    public long focusAt = 0;
    /** Later focus changes: {time, 1 for gain or 0 for loss}. */
    public final List<long[]> focusChanges = new ArrayList<>();
    /** Sessions to start, as times in ms. */
    public final List<Long> sessionsAt = new ArrayList<>();
    /** The key file as seeded, for "unchanged" checks; null if none was seeded. */
    public byte[] seededKey;
    public int seededMode;

    public ReceiverRuntime runtime;
    public boolean started;
    public long ranUntil = -1;

    public ReceiverRuntime build() {
        return new ReceiverRuntime(scheduler, parkOverride != null ? parkOverride : g.park, accOverride != null ? accOverride : g.acc, reverseOverride != null ? reverseOverride : g.reverse, g.notices, g.logs, new ConfigAdapter(configReader()), g.build,
                g.regulation, g.screen, g.session, g.vehicle, g.displayWidth, fs, ifaces, nativeLib, nsd, codecs,
                cover, crash, VERSION, COMMIT, UXPLAY);
    }

    public void start() {
        if (started) return;
        started = true;
        final ReceiverRuntime r = build();
        runtime = r;
        r.useProcNetTcp(procNetTcp);
        if (beforeCreate != null) beforeCreate.accept(r);
        final boolean inLifecycle = focusAt == 0;
        scheduler.script(0, VirtualScheduler.FIRST, () -> {
            r.onCreate();
            r.onStart();
            r.onResume();
            if (inLifecycle) r.onWindowFocusChanged(true);
        });
        if (focusAt > 0) scheduler.script(focusAt, VirtualScheduler.AFTER, () -> r.onWindowFocusChanged(true));
        for (long[] c : focusChanges) {
            final boolean gain = c[1] == 1;
            scheduler.script(c[0], VirtualScheduler.AFTER, () -> r.onWindowFocusChanged(gain));
        }
        for (Long at : sessionsAt) scheduler.script(at, VirtualScheduler.AFTER, () -> nativeLib.session("h264", 1920, 720));
        scheduler.runUntil(0);
        ranUntil = 0;
    }

    public void runFor(long ms) {
        start();
        scheduler.runUntil(ms);
        ranUntil = Math.max(ranUntil, ms);
    }

    public void settle() {
        if (!started || ranUntil <= 0) runFor(g.settleMs);
    }

    public void exitAt(long t) {
        start();
        final ReceiverRuntime r = runtime;
        scheduler.script(t, VirtualScheduler.AFTER, r::exit);
    }

    /** A new process: exit() on the old runtime now, then a fresh clock; files and logs are kept. */
    public void restart() {
        if (runtime != null) runtime.exit();
        g.restart();
        nativeLib.newStart();
        runtime = null;
        started = false;
        ranUntil = -1;
        focusChanges.clear();
        sessionsAt.clear();
        focusAt = 0;
    }

    public int startIndex() {
        return scheduler.startIndex;
    }

    /** The current start's log lines. */
    public List<Fakes.Line> lines() {
        return g.lines();
    }

    /** A log line's text without its time prefix. */
    public static String body(Fakes.Line l) {
        int sp = l.text.indexOf(' ');
        return sp < 0 ? "" : l.text.substring(sp + 1);
    }

    public List<String> bodies() {
        List<String> out = new ArrayList<>();
        for (Fakes.Line l : lines()) out.add(body(l));
        return out;
    }

    public void seedKey(byte[] bytes) {
        seededKey = bytes.clone();
        seededMode = FakeNativeLibrary.KEY_MODE;
        fs.seed(KEY_PATH, bytes, seededMode);
    }

    {
        trace.log = g.logs.lines;
    }
    /** Runs on the new runtime before onCreate, for steps that act before the filter is ready. */
    public java.util.function.Consumer<ReceiverRuntime> beforeCreate;

    /** A Park getter that replaces the gate's Park double when set (the AIR-058 adapter over its doubles). */
    public ParkGetter parkOverride;
    private FakeConfigReader configReader;

    /** The config file as the fake file system serves it at the one installed path (AIR-063). */
    public FakeConfigReader configReader() {
        if (configReader == null) configReader = new FakeConfigReader(g.config, trace);
        return configReader;
    }

    /** ACC and REVERSE_ON sources that replace the gate doubles when set (the AIR-059 and AIR-060 adapters). */
    public AccSource accOverride;
    public ReverseSource reverseOverride;
}
