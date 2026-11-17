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
                trace.add("dnssd", "connect", "ok");
                trace.add("dnssd", "register", "_airplay._tcp");
                trace.add("dnssd", "register", "_raop._tcp");
                return StartResult.ok(StartResult.MDNSD_UP);
            case ACCEPT:
                trace.add("native", "accept", "");
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
        trace.add("native", "raop_init", "");
        trace.add("native", "raop_set_log_callback", "");
        trace.add("native", "raop_set_log_level", plan.logLevel);
        trace.add("native", "raop_init2", "keyfile=" + plan.keyfile);
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
        events.sessionStarted(generation);
        events.connectionAccepted(generation, model);
        if (afterAccept != null) deliverLog(afterAccept);
        events.codecConfig(generation, codec, w, h);
    }

    @Override
    public void stop(Phase p) {
        trace.add("native", "stop", p.name());
        if (p == Phase.ACCEPT) accepting = false;
        if (p == Phase.REGISTRATIONS) trace.add("dnssd", "withdraw", "all");
        if (p == Phase.PORTS) closeAll();
    }

    @Override
    public Status status() {
        return new Status(accepting, generation);
    }

    /** One native log record from UxPlay's callback, for the open session; dropped before ACCEPT. */
    public void deliverLog(String record) {
        if (events == null) return;
        trace.add("native", "log", "gen=" + generation);
        events.log(generation, record);
    }

    /** "The iPhone ended the connection": the open session ends. */
    public void disconnect() {
        if (events == null || generation == 0) return;
        trace.add("native", "session end", "gen=" + generation);
        events.sessionEnded(generation);
    }

    /** The model identifier the double reports with an accepted connection (synthetic). */
    public String model = "iPhone15,2";
    /** A record delivered after the accepted connection and before video_set_codec_and_size; null for none. */
    public String afterAccept;
}
