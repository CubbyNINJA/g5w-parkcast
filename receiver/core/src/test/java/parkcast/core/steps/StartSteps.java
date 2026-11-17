package parkcast.core.steps;

import static parkcast.core.steps.Check.assertEquals;
import static parkcast.core.steps.Check.assertFalse;
import static parkcast.core.steps.Check.assertTrue;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import parkcast.core.doubles.FakeNativeLibrary;
import parkcast.core.doubles.Trace;

/** Steps for the start sequence: the hotspot check, the five ports, stdio, the build product and the marker. */
public final class StartSteps {
    static final String HOTSPOT = "192.168.0.1";

    private final S3World w;

    public StartSteps(S3World w) {
        this.w = w;
    }

    @Given("{word} port {int} is taken")
    public void portTaken(String proto, int port) {
        w.nativeLib.taken.add(proto.toLowerCase() + " " + port);
    }

    @Given("pointing descriptor 1 at \\/dev\\/null fails")
    public void dup2Fails() {
        w.nativeLib.dup2Fails = true;
    }

    @Given("the fake native library's start call throws java.lang.IllegalStateException")
    public void startThrows() {
        w.nativeLib.throwOnStart = true;
    }

    /** One step for every form, so "is the empty string" is never read as a product value. */
    @Given("ro.build.product {}")
    public void buildProduct(String read) {
        if (read.equals("is the empty string")) w.g.build.product = "";
        else if (read.equals("throws IllegalArgumentException when read")) w.g.build.unreadable = true;
        else if (read.startsWith("is ")) w.g.build.product = read.substring(3);
        else throw new IllegalArgumentException("no ro.build.product form: " + read);
    }

    @Given("Build.PRODUCT is {string}")
    public void buildProduct2(String p) {
        w.g.build.product2 = p;
    }

    @Then("the interface double was queried exactly once, and that query returned before any bind")
    public void queriedOnceBeforeBind() {
        queriedOnce();
        Trace.Entry q = w.trace.current("iface", "get").get(0);
        for (Trace.Entry b : w.trace.current("socket", "bind"))
            assertTrue(q.seq < b.seq, "bind before the interface query: " + b);
    }

    @Then("the interface double was queried exactly once")
    public void queriedOnce() {
        assertEquals(1, w.trace.current("iface", "get").size(), "queries: " + w.trace.current("iface", "get"));
    }

    @Then("the socket double recorded exactly five binds, one to each of the five ports, each to address 192.168.0.1")
    public void fiveBinds() {
        List<String> got = new ArrayList<>();
        for (Trace.Entry e : w.trace.current("socket", "bind")) got.add(e.details);
        List<String> want = new ArrayList<>();
        for (String[] p : FakeNativeLibrary.PORTS) want.add(p[0] + " " + HOTSPOT + " " + p[1]);
        assertEquals(new HashSet<>(want), new HashSet<>(got));
        assertEquals(5, got.size(), "binds: " + got);
    }

    @Then("no socket was bound to TCP 35002, to 0.0.0.0 or to any IPv6 address, and no IPv6 socket was created")
    public void noStrayBind() {
        for (Trace.Entry e : w.trace.current("socket", "bind")) {
            assertFalse(e.details.startsWith("tcp ") && e.details.contains(" 35002"), "TCP 35002: " + e);
            assertFalse(e.details.contains("0.0.0.0") || e.details.contains(":"), "wildcard or IPv6: " + e);
        }
        for (Trace.Entry e : w.trace.current("socket", "create"))
            assertTrue(e.details.startsWith("inet "), "non-IPv4 socket: " + e);
    }

    @Then("all five binds returned before the first dns_sd registration")
    public void bindsBeforeRegistration() {
        List<Trace.Entry> regs = w.trace.current("dnssd", "register");
        assertFalse(regs.isEmpty(), "no dns_sd registration");
        for (Trace.Entry b : w.trace.current("socket", "bind"))
            assertTrue(b.seq < regs.get(0).seq, "bind after registration: " + b);
    }

    @Then("no dns_sd registration was sent and no accept call was made")
    public void noRegistrationOrAccept() {
        assertTrue(w.trace.current("dnssd", "register").isEmpty(), "registrations: " + w.trace.current("dnssd", "register"));
        assertTrue(w.trace.current("native", "accept").isEmpty(), "accept: " + w.trace.current("native", "accept"));
    }

    @Then("no bind, no dns_sd registration and no accept call was made")
    public void noBindRegistrationOrAccept() {
        assertTrue(w.trace.current("socket", "bind").isEmpty(), "binds: " + w.trace.current("socket", "bind"));
        noRegistrationOrAccept();
    }

    @Then("no bind was attempted on any port outside the five ports")
    public void onlyFivePorts() {
        Set<String> five = new HashSet<>();
        for (String[] p : FakeNativeLibrary.PORTS) five.add(p[0] + " " + p[1]);
        for (Trace.Entry e : w.trace.current("socket", "bind")) {
            String[] f = e.details.split(" ");
            assertTrue(five.contains(f[0] + " " + f[2]), "bind outside the five ports: " + e);
        }
    }

    @Then("every one of the five ports that the receiver bound was closed by {int} ms")
    public void boundPortsClosed(int by) {
        for (Trace.Entry b : w.trace.current("socket", "bind")) {
            String sock = b.details.replace(" EADDRINUSE", "");
            boolean closed = false;
            for (Trace.Entry c : w.trace.current("socket", "close"))
                if (c.details.equals(sock) && c.seq > b.seq && c.t <= by) closed = true;
            assertTrue(closed, "not closed by " + by + " ms: " + b);
        }
    }

    @Then("the fake native library recorded no call")
    public void noNativeCall() {
        for (Trace.Entry e : w.trace.entries) {
            if (e.start != w.startIndex()) continue;
            assertFalse(e.cause.equals("native") || e.cause.equals("socket") || e.cause.equals("dnssd"),
                    "native call: " + e);
        }
    }

    @Then("the start marker has {string}")
    public void markerHas(String s) {
        String marker = null;
        for (String b : w.bodies()) if (b.startsWith("parkcast start ")) marker = b;
        assertTrue(marker != null, "no start marker in " + w.bodies());
        assertTrue((marker + " ").contains(s + " "), "marker lacks " + s + ": " + marker);
    }
}
