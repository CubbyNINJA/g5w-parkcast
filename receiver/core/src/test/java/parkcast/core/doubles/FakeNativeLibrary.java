package parkcast.core.doubles;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        connected = false;
        windowStart = -1;
        registered.clear();
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
                events = e;
                return connected ? register() : probe();
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
        if (stopThrows.contains(p)) throw new IllegalStateException(trace.add("native", "stopThrew", p.name()).toString());
        trace.add("native", "stop", p.name());
        if (p == Phase.ACCEPT) accepting = false;
        if (p == Phase.REGISTRATIONS) {
            for (String type : registered) trace.add("dnssd", "withdraw", type);
            registered.clear();
        }
        if (p == Phase.PORTS) closeAll();
    }

    @Override
    public Status status() {
        return new Status(accepting, generation);
    }

    /** The socket path the dns_sd client connects to. */
    public static final String MDNSD = "/dev/socket/mdnsd";
    /** The instance names the library registers, by service type, in registration order. */
    public static final String[][] REGISTRATIONS = {{"_airplay._tcp", "ParkCast"}, {"_raop._tcp", "0A1B2C3D4E5F@ParkCast"}};

    /** While non-null, each connect fails with this errno (ENOENT for a missing socket). */
    public String connectErrno;
    /** With {@link #connectErrno} set: the connect succeeds from this many ms into the connect window; -1 never. */
    public long connectOkAfterMs = -1;
    /** Service types whose TXT record cannot be built. */
    public final Set<String> txtFails = new HashSet<>();
    /** Service types whose registration returns this dns_sd error code. */
    public final Map<String, Integer> registerError = new HashMap<>();
    /** Service types whose registration is sent and then gets a name-conflict reply with this code. */
    public final Map<String, Integer> conflict = new HashMap<>();

    private boolean connected;
    /** When the first connect of this start was attempted; -1 before it. */
    private long windowStart = -1;
    /** Registrations in place, by service type. */
    private final List<String> registered = new ArrayList<>();

    /** A call before the connect succeeded: one connect probe. */
    private StartResult probe() {
        long now = trace.now();
        if (windowStart < 0) windowStart = now;
        if (connectErrno != null && (connectOkAfterMs < 0 || now - windowStart < connectOkAfterMs)) {
            trace.add("dnssd", "connect", MDNSD + " " + connectErrno);
            return StartResult.failed(connectErrno);
        }
        trace.add("dnssd", "connect", MDNSD + " ok");
        connected = true;
        return StartResult.ok(StartResult.MDNSD_UP);
    }

    /** A call once connected: both registrations, in order, each only once its TXT record is complete. */
    private StartResult register() {
        for (String[] r : REGISTRATIONS) {
            String type = r[0];
            if (txtFails.contains(type)) return StartResult.failed("txt-build");
            trace.add("dnssd", "txt", type + " complete");
            String reg = type + " name=" + r[1] + " txt=complete rename=off";
            Integer err = registerError.get(type);
            if (err != null) {
                trace.add("dnssd", "register", reg + " error=" + err);
                return StartResult.failed("register " + err);
            }
            trace.add("dnssd", "register", reg);
            registered.add(type);
            Integer c = conflict.get(type);
            if (c != null) {
                trace.add("dnssd", "conflict", type + " " + c);
                return StartResult.failed("name-conflict " + c);
            }
        }
        return StartResult.ok("registered");
    }

    /** The fake dns_sd closes its connection: the registrations are gone and Java is told. */
    public void closeConnection() {
        trace.add("dnssd", "close", MDNSD);
        connected = false;
        registered.clear();
        if (events != null) events.advertiseLost(generation);
    }

    /** Stop phases whose call throws java.lang.IllegalStateException before doing anything. */
    public final Set<Phase> stopThrows = new HashSet<>();

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
