package parkcast.core.steps;

import static parkcast.core.steps.Check.assertEquals;
import static parkcast.core.steps.Check.assertFalse;
import static parkcast.core.steps.Check.assertTrue;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import parkcast.core.doubles.FakeFileSystem;
import parkcast.core.doubles.Trace;
import parkcast.gate.harness.Fakes;
import parkcast.gate.harness.VirtualScheduler;

/**
 * What a full run leaves alone (AIR-049, AIR-067, AIR-068, AIR-080), the crash path (AIR-069(a), (b)) and the
 * CarPlay listener check (AIR-079(b) to (d)). Every assertion reads only what the doubles recorded.
 */
public final class RunConditionSteps {
    /** The receiver's log directory on the unit (AIR-068). */
    static final String LOG_DIR = "/data/nemesis/logs/airplay-receiver/";
    /** Fake file system calls that change something (the feature header's list). */
    static final List<String> FS_CHANGES =
            Arrays.asList("create", "write", "mkdir", "chmod", "chown", "rename", "delete");
    /** Ports of CarPlay and DiPO the receiver must never bind explicitly, as "{@code <proto> <port>}". */
    static final List<String> FOREIGN_BINDS =
            Arrays.asList("tcp 5001", "tcp 6030", "udp 5020", "udp 6000", "udp 6011", "udp 6020");
    static final String HEADER =
            "  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode";
    static final String MESSAGE_MARKER = "CRASH-MSG-MARKER";

    private final S3World w;
    private final List<String> fixture = new ArrayList<>();
    private long crashAt = -1;

    public RunConditionSteps(S3World w) {
        this.w = w;
    }

    // ------------------------------------------------------------------ AIR-049: CarPlay and DiPO ports

    @Then("the socket double recorded no bind, connect or send to TCP {int}")
    public void noTcpTraffic(int port) {
        for (Trace.Entry e : w.trace.entries) {
            if (!e.cause.equals("socket")) continue;
            String[] p = e.details.split(" ");
            boolean tcpPort = p.length >= 3 && p[0].equals("tcp") && p[2].equals(String.valueOf(port));
            assertFalse(tcpPort, "socket call on TCP " + port + ": " + e);
        }
        noConnectTo(port);
    }

    @Then("no explicit bind to TCP 5001 or 6030, or to UDP 5020, 6000, 6011 or 6020")
    public void noForeignBinds() {
        for (Trace.Entry e : w.trace.of("socket", "bind", null)) {
            String[] p = e.details.split(" ");
            assertTrue(p.length >= 3, "unparsed bind: " + e);
            assertFalse(FOREIGN_BINDS.contains(p[0] + " " + p[2]), "bind to a CarPlay or DiPO port: " + e);
        }
    }

    @Then("the socket double recorded no connect to port {int}")
    public void noConnectTo(int port) {
        for (Trace.Entry e : w.trace.entries) {
            boolean outward = e.name.equals("connect") || e.name.startsWith("send");
            if (!outward) continue;
            for (String tok : e.details.split("[ :]"))
                assertFalse(tok.equals(String.valueOf(port)), "connect or send to port " + port + ": " + e);
        }
    }

    // ------------------------------------------------------------------ AIR-067: the gate's doubles

    @Then("the MirrorSession and VehicleBus doubles recorded zero calls")
    public void sessionAndVehicleUntouched() {
        assertEquals(0, w.g.session.total(), "MirrorSession calls: " + w.g.session.counts);
        assertEquals(0, w.g.vehicle.total(), "VehicleBus calls: " + w.g.vehicle.counts);
    }

    @Then("their audio and connection traces are unbroken")
    public void audioAndConnectionUnbroken() {
        Fakes.Session s = w.g.session;
        assertEquals(0, s.count("muteAudio") + s.count("stopAudio") + s.count("disconnectAudio"),
                "audio touched: " + s.counts);
        assertEquals(0, s.count("stopConnection") + s.count("disconnect"), "connection touched: " + s.counts);
        assertEquals(0, s.reconnects, "reconnects");
    }

    // ------------------------------------------------------------------ AIR-068: where files change

    /** The gate's log files go through {@code LogFiles}, whose names are relative to the log directory. */
    @Then("every create, write, rename and delete the fake file system recorded is inside \\/data\\/nemesis\\/logs\\/airplay-receiver\\/ or the fake app-data directory")
    public void fsChangesConfined() {
        for (Trace.Entry e : w.trace.entries) {
            if (!e.cause.equals("fs") || !FS_CHANGES.contains(e.name)) continue;
            boolean anyPath = false;
            for (String tok : e.details.split(" ")) {
                if (!tok.startsWith("/")) continue;
                anyPath = true;
                assertTrue(tok.startsWith(FakeFileSystem.ROOT) || tok.startsWith(LOG_DIR), "outside: " + e);
            }
            assertTrue(anyPath, "no path in " + e);
        }
    }

    // ------------------------------------------------------------------ AIR-069(a), (b): crash

    @When("a java.lang.IllegalStateException is thrown uncaught on the main thread at {int} ms")
    public void uncaughtAt(int t) {
        final IllegalStateException e = new IllegalStateException(MESSAGE_MARKER);
        e.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("parkcast.core.ReceiverRuntime", "onWindowFocusChanged", "ReceiverRuntime.java", 101),
                new StackTraceElement("android.os.Handler", "dispatchMessage", "Handler.java", 102),
                new StackTraceElement("java.lang.Thread", "run", "Thread.java", 841)});
        crashAt = t;
        w.start();
        w.scheduler.script(t, VirtualScheduler.AFTER, () -> w.crash.crash(e));
        w.runFor(t);
    }

    @Then("the log has a {string} record naming {string}, with the receiver's own stack frames")
    public void crashRecord(String start, String cls) {
        String r = null;
        for (String b : w.bodies()) if (b.startsWith(start + " ")) r = b;
        assertTrue(r != null, "no \"" + start + "\" record in " + w.bodies());
        assertTrue(r.contains(cls), "class missing: " + r);
        assertTrue(r.contains("parkcast.core.ReceiverRuntime.onWindowFocusChanged"), "own frame missing: " + r);
        assertFalse(r.contains("android.os.Handler") || r.contains("java.lang.Thread"), "foreign frame: " + r);
        assertFalse(r.contains(MESSAGE_MARKER), "exception message logged: " + r);
    }

    @Then("then the process ended")
    public void processEnded() {
        List<Trace.Entry> uncaught = w.trace.current("crash", "uncaught");
        List<Trace.Entry> end = w.trace.current("crash", "process end");
        assertEquals(1, uncaught.size(), "uncaught: " + uncaught);
        assertEquals(1, end.size(), "process end: " + end);
        assertTrue(end.get(0).seq > uncaught.get(0).seq, "ended before the handler: " + end);
        assertEquals(crashAt, end.get(0).t, "process end time");
        assertTrue(crashRecordSeqBeforeEnd(), "the crash record was not written before the process ended");
    }

    @Then("the lifecycle double recorded no new start of the receiver in the next {int} ms")
    public void noRestart(int ms) {
        int startIndex = w.startIndex();
        w.runFor(crashAt + ms);
        assertEquals(startIndex, w.startIndex(), "a new app start");
        assertEquals(1, w.trace.of("crash", "install", null).size(), "onCreate ran again");
        int markers = 0;
        for (String b : w.bodies()) if (b.startsWith("parkcast start ")) markers++;
        assertEquals(1, markers, "start markers: " + w.bodies());
    }

    /** The crash record is in the log; the fake records the end after the sink returns, so order holds. */
    private boolean crashRecordSeqBeforeEnd() {
        for (String b : w.bodies()) if (b.startsWith("crash ")) return w.crash.ended;
        return false;
    }

    // ------------------------------------------------------------------ AIR-079(b) to (d): /proc/net/tcp

    @Given("\\/proc\\/net\\/tcp is a fixture {}, with the marker {string} in another line")
    public void procFixture(String listener, String marker) {
        fixture.add(HEADER);
        if (listener.startsWith("with a local TCP 7000 listener")) {
            fixture.add("   0: 0100007F:1B58 00000000:0000 0A 00000000:00000000 00:00000000 00000000  1000        0 4101 1");
        } else if (listener.startsWith("with no local TCP 7000 listener")) {
            fixture.add("   0: 0100007F:1F90 00000000:0000 0A 00000000:00000000 00:00000000 00000000  1000        0 4102 1");
            fixture.add("   1: 0100007F:1B58 0200A8C0:D431 01 00000000:00000000 00:00000000 00000000  1000        0 4103 1");
        } else {
            throw new IllegalArgumentException("unknown fixture: " + listener);
        }
        fixture.add("   9: 0100A8C0:88B8 00000000:0000 0A 00000000:00000000 00:00000000 00000000     0        0 " + marker);
        setProc();
    }

    @Given("\\/proc\\/net\\/tcp throws java.io.IOException when read")
    public void procThrows() {
        w.procNetTcp.throwsOnRead = true;
    }

    @Given("\\/proc\\/net\\/tcp holds a malformed fixture")
    public void procMalformed() {
        fixture.add(HEADER);
        fixture.add("   0: 0100007F:ZZZZ 00000000:0000 0A");
        setProc();
    }

    @Then("no log record contains {string} or any other line of the fixture")
    public void nothingFromFixture(String marker) {
        for (String b : w.bodies()) {
            assertFalse(b.contains(marker), "marker logged: " + b);
            for (String line : fixture) assertFalse(b.contains(line.trim()), "fixture line logged: " + b);
            assertFalse(b.contains("0100007F") || b.contains("local_address"), "fixture text logged: " + b);
        }
    }

    @Then("the log has {string} naming the exception class {string}")
    public void logHasClass(String start, String cls) {
        String found = null;
        for (String b : w.bodies()) if (b.startsWith(start)) found = b;
        assertTrue(found != null, "no \"" + start + "\" in " + w.bodies());
        assertEquals(start + " " + cls, found, "record");
    }

    private void setProc() {
        StringBuilder b = new StringBuilder();
        for (String l : fixture) b.append(l).append('\n');
        w.procNetTcp.content = b.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    // ------------------------------------------------------------------ AIR-080: staying behind

    @Given("the receiver's window loses focus at {int} ms, after the default focus gain, and never regains it")
    public void losesFocusForGood(int t) {
        w.focusChanges.add(new long[] {t, 0});
    }

    @Then("no moveTaskToFront, startActivity or requestFocus call was recorded")
    public void noTakeBack() {
        for (Trace.Entry e : w.trace.entries) {
            boolean takeBack = e.name.equals("moveTaskToFront") || e.name.equals("startActivity")
                    || e.name.equals("requestFocus");
            assertFalse(takeBack, "screen taken back: " + e);
        }
    }
}
