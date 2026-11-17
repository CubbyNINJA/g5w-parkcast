package parkcast.core.steps;

import static parkcast.core.steps.Check.assertEquals;
import static parkcast.core.steps.Check.assertFalse;
import static parkcast.core.steps.Check.assertTrue;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.ArrayList;
import java.util.List;
import parkcast.core.doubles.FakeNativeLibrary;
import parkcast.core.doubles.Trace;
import parkcast.gate.harness.VirtualScheduler;

/** Steps for advertising (AIR-050): the NsdManager wake, the dns_sd connect window and the registrations. */
public final class AdvertiseSteps {
    /** NsdManager calls the fake records; anything else under "nsd" would be a call this list does not allow. */
    private static final String[] NSD_ALLOWED = {"get", "discover", "started", "startFailed", "stop", "found", "lost"};

    private final S3World w;
    /** When "discoverServices returns at N ms" was given, N; checked once the scenario has run. */
    private long discoverReturnsAt = -1;

    public AdvertiseSteps(S3World w) {
        this.w = w;
    }

    // ---- Givens -------------------------------------------------------------------------------------------

    @Given("the fake NsdManager delivers onServiceFound for {string} at {int} ms and onServiceLost for it at {int} ms")
    public void foundThenLost(String name, int found, int lost) {
        w.nsd.foundName = name;
        w.nsd.foundAt = found;
        w.nsd.lostAt = lost;
    }

    @Given("discoverServices returns at {int} ms")
    public void discoverReturnsAt(int t) {
        discoverReturnsAt = t;
    }

    @After
    public void checkDiscoverReturned() {
        if (discoverReturnsAt < 0 || !w.started) return;
        List<Trace.Entry> d = w.trace.current("nsd", "discover");
        assertEquals(1, d.size(), "discoverServices calls: " + d);
        assertEquals(discoverReturnsAt, d.get(0).t, "discoverServices returned at");
    }

    @Given("the fake NsdManager delivers onDiscoveryStarted {}")
    public void startedWhen(String when) {
        if (when.equals("never")) {
            w.nsd.startedNever = true;
            return;
        }
        assertTrue(when.startsWith("at ") && when.endsWith(" ms"), "unknown time: " + when);
        w.nsd.startedAt = Long.parseLong(when.substring(3, when.length() - 3));
    }

    @Given("onDiscoveryStarted arrives at {int} ms")
    public void startedAt(int t) {
        w.nsd.startedAt = t;
    }

    @Given("\\/dev\\/socket\\/mdnsd is missing until {int} ms of the connect window")
    public void missingUntil(int ms) {
        w.nativeLib.connectErrno = "ENOENT";
        w.nativeLib.connectOkAfterMs = ms;
    }

    @Given("\\/dev\\/socket\\/mdnsd is missing for the whole run")
    public void missingForRun() {
        w.nativeLib.connectErrno = "ENOENT";
    }

    @Given("getSystemService\\(Context.NSD_SERVICE) returns null")
    public void nsdNull() {
        w.nsd.returnsNull = true;
    }

    @Given("getSystemService\\(Context.NSD_SERVICE) throws SecurityException")
    public void nsdThrows() {
        w.nsd.throwsSecurity = true;
    }

    @Given("onStartDiscoveryFailed is delivered with error code {int}")
    public void discoveryFails(int code) {
        w.nsd.failCode = code;
    }

    @Given("onDiscoveryStarted never arrives")
    public void startedNever() {
        w.nsd.startedNever = true;
    }

    @Given("\\/dev\\/socket\\/mdnsd is missing for the whole connect window")
    public void missingForWindow() {
        w.nativeLib.connectErrno = "ENOENT";
    }

    @Given("\\/dev\\/socket\\/mdnsd refuses with {word} for the whole connect window")
    public void refusesForWindow(String errno) {
        w.nativeLib.connectErrno = errno;
    }

    /** The fake reports the failure as its errno; any errno outside the four logged ones is "unreachable". */
    @Given("every dns_sd connect throws {word} for the whole connect window")
    public void connectThrows(String errno) {
        w.nativeLib.connectErrno = errno;
    }

    @Given("the {word} TXT record cannot be built")
    public void txtFails(String type) {
        w.nativeLib.txtFails.add(type);
    }

    @Given("the {word} registration returns dns_sd error code {int}")
    public void registerError(String type, int code) {
        w.nativeLib.registerError.put(type, code);
    }

    @Given("the {word} registration gets a name-conflict reply with dns_sd error code {int}")
    public void nameConflict(String type, int code) {
        w.nativeLib.conflict.put(type, code);
    }

    @Given("the fake dns_sd closes its connection at {int} ms")
    public void closesAt(int t) {
        w.scheduler.script(t, VirtualScheduler.AFTER, w.nativeLib::closeConnection);
    }

    // ---- Thens --------------------------------------------------------------------------------------------

    @Then("these were recorded in this order: the five binds, getSystemService\\(Context.NSD_SERVICE), "
            + "discoverServices\\(\"_airplay._tcp\", NsdManager.PROTOCOL_DNS_SD, listener), onDiscoveryStarted, "
            + "a successful dns_sd connect to \\/dev\\/socket\\/mdnsd, the log record \"mdnsd up\", "
            + "the two dns_sd registrations, the log record \"advertise ok\"")
    public void fullOrder() {
        Timeline tl = new Timeline(w);
        List<Timeline.Item> binds = tl.of("socket", "bind");
        assertEquals(5, binds.size(), "binds" + tl);
        List<Timeline.Item> regs = tl.of("dnssd", "register");
        assertEquals(2, regs.size(), "registrations" + tl);
        List<Timeline.Item> seq = new ArrayList<>();
        seq.addAll(binds);
        seq.add(one(tl, "nsd", "get"));
        Timeline.Item discover = one(tl, "nsd", "discover");
        assertEquals("_airplay._tcp", discover.entry.details, "discover type");
        seq.add(discover);
        seq.add(one(tl, "nsd", "started"));
        List<Timeline.Item> connects = tl.of("dnssd", "connect");
        assertEquals(1, connects.size(), "connects" + tl);
        assertEquals(FakeNativeLibrary.MDNSD + " ok", connects.get(0).entry.details, "connect");
        seq.add(connects.get(0));
        seq.add(record(tl, "mdnsd up"));
        seq.addAll(regs);
        seq.add(record(tl, "advertise ok"));
        for (int i = 1; i < seq.size(); i++)
            assertTrue(seq.get(i - 1).index < seq.get(i).index, seq.get(i - 1) + " not before " + seq.get(i) + tl);
    }

    @Then("one registration is of type {string} with instance name exactly {string}")
    public void oneRegistration(String type, String name) {
        registrationNamed(type, name);
    }

    @Then("the other is of type {string} with instance name exactly {string}")
    public void otherRegistration(String type, String name) {
        registrationNamed(type, name);
    }

    @Then("each registration carried its complete TXT record when it was sent, with automatic renaming off")
    public void txtAndRenameOff() {
        List<Trace.Entry> regs = w.trace.current("dnssd", "register");
        assertTrue(!regs.isEmpty(), "no registration: " + w.trace);
        for (Trace.Entry e : regs) {
            assertTrue(e.details.contains(" txt=complete"), "TXT not complete: " + e);
            assertTrue(e.details.contains(" rename=off"), "renaming not off: " + e);
        }
    }

    @Then("no registration was sent before its TXT record was complete")
    public void txtBeforeRegistration() {
        for (Trace.Entry r : w.trace.current("dnssd", "register")) {
            String type = r.details.substring(0, r.details.indexOf(' '));
            boolean txt = false;
            for (Trace.Entry t : w.trace.current("dnssd", "txt"))
                if (t.seq < r.seq && t.details.equals(type + " complete")) txt = true;
            assertTrue(txt, "no complete TXT before " + r);
        }
    }

    @Then("the fake native library recorded no call that starts its built-in mDNS responder")
    public void noResponder() {
        for (Trace.Entry e : w.trace.current(null, null)) {
            String n = e.name.toLowerCase();
            assertFalse(n.contains("responder") || n.contains("mdns"), "responder call: " + e);
        }
    }

    @Then("registerService and resolveService were never called on the fake NsdManager")
    public void nsdOnlyWakes() {
        for (Trace.Entry e : w.trace.current("nsd", null)) {
            boolean ok = false;
            for (String a : NSD_ALLOWED) if (e.name.equals(a)) ok = true;
            assertTrue(ok, "unexpected NsdManager call: " + e);
        }
    }

    @Then("no log record contains {string}")
    public void noRecordContains(String s) {
        for (String b : w.bodies()) assertFalse(b.contains(s), "record contains \"" + s + "\": " + b);
    }

    @Then("the receiver made no call on the fake NsdManager after onDiscoveryStarted")
    public void noNsdCallAfterStarted() {
        List<Trace.Entry> s = w.trace.current("nsd", "started");
        assertEquals(1, s.size(), "onDiscoveryStarted deliveries");
        for (Trace.Entry e : w.trace.current("nsd", null)) {
            if (e.seq <= s.get(0).seq) continue;
            assertFalse(e.name.equals("get") || e.name.equals("discover") || e.name.equals("stop"), "call after start: " + e);
        }
    }

    @Then("a dns_sd connect attempt was made at {int} ms and the log has {string}")
    public void connectAtAndLog(int t, String s) {
        boolean at = false;
        for (Trace.Entry e : w.trace.current("dnssd", "connect")) if (e.t == t) at = true;
        assertTrue(at, "no connect at " + t + " ms: " + w.trace.current("dnssd", "connect"));
        new CommonS3Steps(w).logHas(s);
    }

    @Then("the log has a record that is exactly {string}, and no dns_sd connect was made")
    public void exactlyAndNoConnect(String s) {
        new CommonS3Steps(w).logHasExactly(s);
        assertTrue(w.trace.current("dnssd", "connect").isEmpty(), "connects: " + w.trace.current("dnssd", "connect"));
    }

    @Then("dns_sd connect attempts were made only at {} ms of the connect window")
    public void connectTimes(String list) {
        assertEquals(parseTimes(list), windowTimes(), "connect times");
    }

    @Then("dns_sd connect attempts were made only at {} ms of the connect window, {int} attempts in all")
    public void connectTimesCount(String list, int n) {
        List<Long> want = parseTimes(list);
        assertEquals(n, want.size(), "the scenario's own list");
        assertEquals(want, windowTimes(), "connect times");
    }

    @Then("only the attempt at {int} ms succeeded")
    public void onlyAttemptSucceeded(int t) {
        long base = windowStart();
        List<Long> ok = new ArrayList<>();
        for (Trace.Entry e : w.trace.current("dnssd", "connect"))
            if (e.details.endsWith(" ok")) ok.add(e.t - base);
        List<Long> want = new ArrayList<>();
        want.add((long) t);
        assertEquals(want, ok, "successful connects");
    }

    @Then("the log has no {string}")
    public void logHasNo(String s) {
        for (String b : w.bodies()) assertFalse(b.startsWith(s), "record \"" + b + "\"");
    }

    @Then("no registration was made")
    public void noRegistration() {
        assertTrue(w.trace.current("dnssd", "register").isEmpty(), "registrations: " + w.trace.current("dnssd", "register"));
    }

    @Then("no registration is left in place")
    public void noneLeft() {
        for (Trace.Entry r : w.trace.current("dnssd", "register")) {
            if (r.details.contains(" error=")) continue;
            String type = r.details.substring(0, r.details.indexOf(' '));
            assertTrue(withdrawnAfter(type, r.seq) != null, type + " registered and never withdrawn:\n" + w.trace);
        }
    }

    @Then("the {word} registration was withdrawn")
    public void withdrawn(String type) {
        Trace.Entry r = null;
        for (Trace.Entry e : w.trace.current("dnssd", "register"))
            if (e.details.startsWith(type + " ") && !e.details.contains(" error=")) r = e;
        assertTrue(r != null, "no " + type + " registration:\n" + w.trace);
        assertTrue(withdrawnAfter(type, r.seq) != null, type + " not withdrawn:\n" + w.trace);
    }

    @Then("discovery was stopped with stopServiceDiscovery if discoverServices had returned")
    public void discoveryStoppedIfReturned() {
        int discovers = w.trace.current("nsd", "discover").size();
        int stops = w.trace.current("nsd", "stop").size();
        assertEquals(discovers == 0 ? 0 : 1, stops, "stopServiceDiscovery calls after " + discovers + " discoverServices calls");
    }

    @Then("no accept call was made, and no registration, discoverServices call or dns_sd connect was made after the log record {string}")
    public void nothingAfterFailure(String s) {
        Timeline tl = new Timeline(w);
        assertTrue(tl.of("native", "accept").isEmpty(), "accept calls" + tl);
        Timeline.Item f = record(tl, s);
        for (Timeline.Item i : tl.items) {
            if (i.index <= f.index) continue;
            assertFalse(i.is("dnssd", "register") || i.is("nsd", "discover") || i.is("dnssd", "connect"),
                    i + " after \"" + s + "\"" + tl);
        }
    }

    @Then("no dns_sd connect attempt or registration was made after {int} ms")
    public void nothingAfter(int t) {
        for (Trace.Entry e : w.trace.current("dnssd", null))
            assertFalse(e.t > t && (e.name.equals("connect") || e.name.equals("register")), "after " + t + " ms: " + e);
    }

    @Then("the {word} and {word} registrations were both withdrawn")
    public void bothWithdrawn(String a, String b) {
        withdrawn(a);
        withdrawn(b);
    }

    @Then("stopServiceDiscovery was called after both withdrawals")
    public void stopAfterWithdrawals() {
        List<Trace.Entry> stops = w.trace.current("nsd", "stop");
        assertEquals(1, stops.size(), "stopServiceDiscovery calls");
        List<Trace.Entry> wd = w.trace.current("dnssd", "withdraw");
        assertEquals(2, wd.size(), "withdrawals");
        for (Trace.Entry e : wd) assertTrue(e.seq < stops.get(0).seq, e + " not before " + stops.get(0));
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    /** The connect window opens when onDiscoveryStarted is delivered. */
    private long windowStart() {
        List<Trace.Entry> s = w.trace.current("nsd", "started");
        assertEquals(1, s.size(), "onDiscoveryStarted deliveries");
        return s.get(0).t;
    }

    private List<Long> windowTimes() {
        long base = windowStart();
        List<Long> out = new ArrayList<>();
        for (Trace.Entry e : w.trace.current("dnssd", "connect")) out.add(e.t - base);
        return out;
    }

    private static List<Long> parseTimes(String list) {
        List<Long> out = new ArrayList<>();
        for (String p : list.replace(" and ", ", ").split(", ")) out.add(Long.parseLong(p.trim()));
        return out;
    }

    private Trace.Entry withdrawnAfter(String type, int seq) {
        for (Trace.Entry e : w.trace.current("dnssd", "withdraw"))
            if (e.seq > seq && e.details.equals(type)) return e;
        return null;
    }

    private void registrationNamed(String type, String name) {
        int n = 0;
        for (Trace.Entry e : w.trace.current("dnssd", "register"))
            if (e.details.startsWith(type + " ")) {
                n++;
                assertTrue(e.details.contains(" name=" + name + " "), "instance name: " + e);
            }
        assertEquals(1, n, "registrations of " + type);
    }

    private static Timeline.Item one(Timeline tl, String cause, String name) {
        List<Timeline.Item> l = tl.of(cause, name);
        assertEquals(1, l.size(), cause + "." + name + tl);
        return l.get(0);
    }

    private static Timeline.Item record(Timeline tl, String s) {
        Timeline.Item i = tl.record(s);
        assertTrue(i != null, "no record \"" + s + "\"" + tl);
        return i;
    }
}
