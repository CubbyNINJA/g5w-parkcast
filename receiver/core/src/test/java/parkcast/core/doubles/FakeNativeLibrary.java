package parkcast.core.doubles;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import parkcast.core.port.NativeEvents;
import parkcast.core.port.NativeLibrary;

/**
 * The fake native library (file 13 preamble) with the socket double and the fake dns_sd folded in, since in
 * the real receiver all three sit behind the one JNI library. Library calls go to the trace as "native",
 * sockets as "socket" and dns_sd as "dnssd".
 *
 * <p>Socket details are "{@code <proto> <address> <port>}", with " EADDRINUSE" appended to a failed bind.
 */
public final class FakeNativeLibrary implements NativeLibrary {
    /** The five ports, in the order the library binds them. */
    public static final String[][] PORTS = {
            {"tcp", "35000"}, {"tcp", "35001"}, {"udp", "35000"}, {"udp", "35001"}, {"udp", "35002"}};
    public static final int KEY_MODE = 0600;

    private final Trace trace;
    private final FakeFileSystem fs;
    /** Pointing descriptor 1 at /dev/null fails: step 0 of KEYCHECK, before any library call. */
    public boolean dup2Fails;
    /** The start call throws java.lang.IllegalStateException. */
    public boolean throwOnStart;
    /** Taken ports, as lowercase "{@code <proto> <port>}". */
    public final Set<String> taken = new HashSet<>();

    private final List<String> bound = new ArrayList<>();
    private NativeEvents events;
    private boolean accepting;
    private int generation;

    public FakeNativeLibrary(Trace trace, FakeFileSystem fs) {
        this.trace = trace;
        this.fs = fs;
    }

    /** A new app start: a new process, so no socket or listener carries over. */
    public void newStart() {
        bound.clear();
        events = null;
        accepting = false;
        generation = 0;
        stdioNull = false;
        sessionOpen = false;
        dropping = false;
    }

    @Override
    public StartResult start(Stage stage, StartPlan plan, NativeEvents e) {
        if (throwOnStart) throw new IllegalStateException("synthetic start failure");
        switch (stage) {
            case KEYCHECK:
                return keycheck(plan);
            case BIND:
                return bind(plan);
            case ADVERTISE:
                trace.add("native", "advertise", "");
                print();
                trace.add("dnssd", "connect", "ok");
                trace.add("dnssd", "register", "_airplay._tcp");
                trace.add("dnssd", "register", "_raop._tcp");
                return StartResult.ok(StartResult.MDNSD_UP);
            case ACCEPT:
                trace.add("native", "accept", "");
                print();
                events = e;
                accepting = true;
                return StartResult.ok("accepting");
            default:
                throw new IllegalArgumentException(String.valueOf(stage));
        }
    }

    private StartResult keycheck(StartPlan plan) {
        if (dup2Fails) {
            trace.add("stdio", "dup2", "fd 1 -> /dev/null failed");
            return StartResult.failed(StartResult.STDIO_FAILED);
        }
        trace.add("stdio", "dup2", "fd 1,2 -> /dev/null");
        stdioNull = true;
        trace.add("native", "raop_init", "");
        print();
        trace.add("native", "raop_init2", "keyfile=" + plan.keyfile);
        print();
        try {
            if (!fs.files.containsKey(plan.keyfile)) {
                fs.create(plan.keyfile, FakeFileSystem.VALID_KEY, KEY_MODE);
                return StartResult.ok("key created");
            }
            byte[] key = fs.readPath(plan.keyfile);
            if (key.length != FakeFileSystem.VALID_KEY.length) return StartResult.failed("raop_init2 -1");
            return StartResult.ok("key loaded");
        } catch (IOException x) {
            return StartResult.failed("java.io.IOException");
        }
    }

    private StartResult bind(StartPlan plan) {
        for (String[] p : PORTS) {
            String sock = p[0] + " " + plan.address + " " + p[1];
            trace.add("socket", "create", "inet " + p[0]);
            if (taken.contains(p[0] + " " + p[1])) {
                trace.add("socket", "bind", sock + " EADDRINUSE");
                trace.add("socket", "close", sock);
                closeAll();
                return StartResult.failed(p[0] + " " + p[1]);
            }
            trace.add("socket", "bind", sock);
            bound.add(sock);
        }
        long features = BASE_FEATURES | (plan.h265 ? HEVC_FEATURE : 0L);
        trace.add("native", "receiver_info", "width=" + plan.width + " height=" + plan.height + " refresh="
                + plan.refreshHz + " h265=" + plan.h265 + " features=0x" + Long.toHexString(features));
        print();
        return StartResult.ok("bound");
    }

    private void closeAll() {
        for (String s : bound) trace.add("socket", "close", s);
        bound.clear();
    }

    /** "A session starts": reports an accepted connection and its codec configuration to Java. */
    public void session(String codec, int w, int h) {
        if (!accepting || events == null) return;
        generation++;
        trace.add("native", "session", "gen=" + generation);
        sessionOpen = true;
        dropping = false;
        deliverStarted(generation);
        trace.add("native", "callback", "video_set_codec_and_size return=0");
        deliverConfig(generation, codec, w, h);
        streamFrom(generation);
    }

    @Override
    public void stop(Phase p) {
        trace.add("native", "stop", p.name());
        print();
        if (p == Phase.ACCEPT) {
            accepting = false;
            dropping = true;
        }
        if (p == Phase.SESSION && sessionOpen) {
            sessionOpen = false;
            trace.add("native", "session", "ended gen=" + generation);
        }
        if (p == Phase.REGISTRATIONS) trace.add("dnssd", "withdraw", "all");
        if (p == Phase.PORTS) closeAll();
    }

    @Override
    public Status status() {
        return new Status(accepting, generation);
    }

    // ---- Stream, callback and stdio fixtures (file 13, AIR-051 to AIR-055) ----

    /** The feature bits the fake advertises without HEVC, and the HEVC bit it would add; both fake values. */
    public static final long BASE_FEATURES = 0x5A7FFFF7L, HEVC_FEATURE = 1L << 42;
    /** A made-up HEVC codec-configuration fixture (not a real stream). */
    public static final byte[] HEVC_FIXTURE = {0x00, 0x00, 0x00, 0x01, 0x40, 0x01, 0x0C, 0x01, 0x7E, 0x48};
    /** Access units arrive every 16 ms once a session's codec is configured. */
    public static final long AU_PERIOD_MS = 16;

    /** Set by the world: the clock the stream and the busy window run on. */
    public parkcast.gate.harness.VirtualScheduler scheduler;
    /** What the next session's video_set_codec_and_size reports. */
    public String sessionCodec = "h264";
    public int sessionW = 1920, sessionH = 720;
    /** Print this to stdout and stderr on every library call; null = print nothing. */
    public String printMarker;
    /** What reached logcat: the marker of each call made while descriptors 1 and 2 were not on /dev/null. */
    public final List<String> logcat = new ArrayList<>();
    /** Java is held busy in [busyFrom, busyUntil): deliveries wait until busyUntil. */
    public long busyFrom = -1, busyUntil = -1;

    private boolean stdioNull, sessionOpen, dropping;

    private void print() {
        if (printMarker != null && !stdioNull) {
            logcat.add("stdout " + printMarker);
            logcat.add("stderr " + printMarker);
        }
    }

    private boolean busy() {
        long now = scheduler.now();
        return now >= busyFrom && now < busyUntil;
    }

    /** Hands an event to Java on the Java side's own time: later if Java is busy, never once stopped. */
    private void deliver(final String name, final Runnable r) {
        if (dropping || events == null) {
            trace.add("native", "callback", name + " dropped");
            return;
        }
        if (scheduler != null && busy()) {
            scheduler.at(busyUntil, () -> deliver(name, r));
            return;
        }
        trace.add("java", "event", name);
        r.run();
    }

    private void deliverStarted(final int gen) {
        final NativeEvents e = events;
        deliver("sessionStarted", () -> e.sessionStarted(gen));
    }

    private void deliverConfig(final int gen, final String codec, final int w, final int h) {
        final NativeEvents e = events;
        deliver("codecConfig " + codec + " " + w + "x" + h, () -> e.codecConfig(gen, codec, w, h));
    }

    private void deliverUnit(final int gen, final long pts) {
        final NativeEvents e = events;
        deliver("accessUnit " + pts, () -> e.accessUnit(gen, pts));
    }

    private void streamFrom(final int gen) {
        if (scheduler == null) return;
        final long first = scheduler.now();
        scheduler.at(first, new Runnable() {
            @Override
            public void run() {
                if (!sessionOpen || dropping || generation != gen) return;
                long now = scheduler.now();
                trace.add("native", "callback", "video_process returned");
                deliverUnit(gen, now);
                scheduler.at(now + AU_PERIOD_MS, this);
            }
        });
    }

    /** The library double calls a callback with its fixture; the callback itself returns at once. */
    public void callback(String name) {
        int gen = generation;
        if ("video_process".equals(name)) {
            if (dropping) trace.add("native", "callback", name + " dropped");
            else deliverUnit(gen, scheduler.now());
        } else if ("video_set_codec_and_size".equals(name)) {
            if (dropping) trace.add("native", "callback", name + " dropped");
            else deliverConfig(gen, sessionCodec, sessionW, sessionH);
        }
        trace.add("native", "callback", name + " returned");
    }

    /** A URL, HLS or DRM request: the library refuses it and reports only its kind, never the URL. */
    public void request(String kind, String url) {
        String k = kind.toLowerCase(java.util.Locale.ROOT);
        trace.add("native", "refuse", k);
        final NativeEvents e = events;
        final int gen = generation;
        final String rec = "refused " + k;
        deliver("log " + rec, () -> e.log(gen, rec));
    }

    /** audio_process with fixture bytes: the library discards them; nothing reaches Java. */
    public void audio(byte[] bytes) {
        trace.add("native", "callback", "audio_process returned");
    }

    /** Whether a session is open. */
    public boolean sessionOpen() {
        return sessionOpen;
    }
}
