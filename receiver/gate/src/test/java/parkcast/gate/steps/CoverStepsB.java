package parkcast.gate.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import parkcast.gate.World;
import parkcast.gate.harness.CoverHarnessA;
import parkcast.gate.harness.CoverHarnessB;
import parkcast.gate.harness.Fakes;
import parkcast.gate.harness.VirtualScheduler;

/** AIR-041 covering: REVERSE_ON registration, focus-held notices, cover records and the wiring scan. */
public class CoverStepsB {
    private final World w;
    private final CommonSteps common;
    private final LogSteps logSteps;
    private final GateMiscSteps misc;
    private final CoverHarnessB cover;
    /** Part A's focus and broadcast doubles, shared so every scripted delivery is recorded in one place. */
    private final CoverHarnessA deliveries;
    /** Registration calls made before "the receiver starts again". */
    private int registersBefore = -1;
    private boolean scanned;

    public CoverStepsB(World w, CommonSteps common, LogSteps logSteps, GateMiscSteps misc, CoverHarnessA deliveries) {
        this.w = w;
        this.common = common;
        this.logSteps = logSteps;
        this.misc = misc;
        this.cover = new CoverHarnessB(w);
        this.deliveries = deliveries;
    }

    // ------------------------------------------------------------ REVERSE_ON registration and lifecycle

    @Given("the lifecycle double runs onCreate, onStart and onResume at {int} ms and delivers the first focus gain as soon as onResume returns")
    public void lifecycleDouble(int t) {
        if (t != 0) throw new IllegalArgumentException("the lifecycle double runs at 0 ms only, not " + t + " ms");
        w.focusAt = 0;
        w.focusRank = VirtualScheduler.FIRST;
        cover.install();
    }

    @Then("the recorder shows the REVERSE_ON registration call returned before onResume returned")
    public void registrationBeforeOnResume() {
        registrationBefore("onResume returned", "onResume returned");
    }

    @Then("the recorder shows it returned before the first onWindowFocusChanged\\(true) was delivered")
    public void registrationBeforeFocus() {
        registrationBefore("focus gain delivered", "the first onWindowFocusChanged(true) was delivered");
    }

    private void registrationBefore(String event, String name) {
        w.settle();
        if (cover.current("onCreate called").isEmpty())
            throw new IllegalStateException("the recorder was not installed by the lifecycle double step");
        List<CoverHarnessB.Event> ret = cover.current("register returned");
        if (ret.isEmpty())
            throw new AssertionError("no REVERSE_ON registration call returned; it threw "
                    + cover.current("register threw").size() + " time(s)");
        List<CoverHarnessB.Event> then = cover.current(event);
        if (then.isEmpty()) throw new IllegalStateException("the recorder saw no \"" + event + "\"");
        if (ret.get(0).seq > then.get(0).seq)
            throw new AssertionError("the REVERSE_ON registration returned after " + name);
    }

    @Then("exactly one REVERSE_ON registration call was made")
    public void oneRegistration() {
        w.settle();
        if (w.reverse.registers != 1) throw new AssertionError(w.reverse.registers + " REVERSE_ON registration calls were made");
    }

    @Given("the REVERSE_ON registration throws at {int} ms")
    public void registrationThrows(int t) {
        if (t != 0) throw new IllegalArgumentException("the registration is made in onCreate at 0 ms, not " + t + " ms");
        w.reverse.throwsOnRegister = true;
    }

    @Then("the REVERSE_ON registration was attempted only once")
    public void attemptedOnce() {
        oneRegistration();
    }

    @Then("no unregisterReceiver call was made for it at exit")
    public void noUnregister() {
        w.settle();
        if (w.reverse.unregisters != 0)
            throw new AssertionError(w.reverse.unregisters + " REVERSE_ON unregisterReceiver call(s) were made");
    }

    @Given("^unregistering the REVERSE_ON receiver (returns|throws)$")
    public void unregistering(String how) {
        cover.unregisterThrows = "throws".equals(how);
        cover.install();
    }

    @Then("exactly one unregisterReceiver call for the REVERSE_ON receiver was made, at {int} ms")
    public void oneUnregisterAt(int t) {
        w.settle();
        if (cover.current("onCreate called").isEmpty())
            throw new IllegalStateException("the recorder was not installed by the unregistering step");
        List<CoverHarnessB.Event> got = cover.current("unregister");
        if (got.size() != 1 || got.get(0).t != t)
            throw new AssertionError("REVERSE_ON unregisterReceiver calls were made at " + got);
    }

    @Given("during the previous app start the REVERSE_ON receiver was registered and exit came at {int} ms")
    public void previousStartRegistered(int t) {
        common.checkOnSupported();
        w.exitAt(t);
        w.runFor(t);
        if (w.reverse.registers != 1)
            throw new AssertionError("the previous app start made " + w.reverse.registers + " REVERSE_ON registration calls");
        registersBefore = w.reverse.registers;
    }

    @Then("the REVERSE_ON registration was attempted once since the restart")
    public void attemptedOnceSinceRestart() {
        if (registersBefore < 0) throw new IllegalStateException("no step ran a previous app start");
        w.settle();
        int n = w.reverse.registers - registersBefore;
        if (n != 1) throw new AssertionError(n + " REVERSE_ON registration calls since the restart");
    }

    // ------------------------------------------------------------ scripted focus and REVERSE_ON

    @When("REVERSE_ON is delivered at {int} ms and REVERSE_OFF at {int} ms")
    public void reverseOnAndOff(int on, int off) {
        deliveries.reverseOn(on);
        deliveries.reverseOff(off);
    }

    @When("the receiver's window loses focus at {int} ms and gains it at {int} ms")
    public void losesAndGains(int a, int b) {
        deliveries.focus(a, false);
        deliveries.focus(b, true);
    }

    @When("the receiver runs for {int} ms and then exits")
    public void runsThenExits(int ms) {
        misc.runsAndExits(ms);
    }

    @Then("exit completes without waiting")
    public void exitCompletesWithoutWaiting() {
        misc.exitCompletes();
    }

    @Given("the Park getter replies Park within {int} ms")
    public void parkRepliesPark(int ms) {
        w.park.constant = CommonSteps.replyAfter("1", ms);
    }

    /** Fails the first append containing {@code text} made at exactly {@code t} ms. */
    @Given("writing the {string} record at {int} ms fails")
    public void recordFailsAt(String text, int t) {
        w.scheduler.script(t, VirtualScheduler.FIRST, () -> w.logs.failOnceOn = text);
        w.scheduler.script(t + 1L, VirtualScheduler.FIRST, () -> {
            if (text.equals(w.logs.failOnceOn)) w.logs.failOnceOn = null;
        });
    }

    // ------------------------------------------------------------ notice calls

    @Then("no view was created, measured or added at any time")
    public void noViewEver() {
        w.settle();
        for (Fakes.Op o : w.notices.ops(null, null, w.scheduler.startIndex))
            if ("create".equals(o.op) || "measure".equals(o.op) || "add".equals(o.op))
                throw new AssertionError("a " + o.kind + " view call \"" + o.op + "\" was made at " + o.t + " ms");
    }

    @Then("no removal call was made, before or at exit")
    public void noRemovalEver() {
        w.settle();
        List<Fakes.Op> rs = w.notices.ops("remove", null, w.scheduler.startIndex);
        if (!rs.isEmpty()) throw new AssertionError("removal calls were made at " + times(rs) + " ms");
    }

    @Then("both notices were added at exactly {int} ms, in either order")
    public void bothAddedAt(int t) {
        w.settle();
        for (String kind : new String[] {Fakes.REG, Fakes.LOG}) {
            List<Fakes.Op> as = w.notices.ops("add", kind, w.scheduler.startIndex);
            if (as.size() != 1 || as.get(0).t != t)
                throw new AssertionError(kind + " notice add calls were made at " + times(as) + " ms");
        }
    }

    @Then("logging-failure notice attempts were made only at {int} ms")
    public void logAttemptsOnlyAt(int t) {
        w.settle();
        List<Long> got = times(w.notices.ops("create", Fakes.LOG, w.scheduler.startIndex));
        if (got.size() != 1 || got.get(0) != t)
            throw new AssertionError("logging-failure notice attempts were made at " + got + " ms");
    }

    @Then("no logging-failure notice view was created before {int} ms")
    public void noLogViewBefore(int t) {
        w.settle();
        for (Fakes.Op o : w.notices.ops("create", Fakes.LOG, w.scheduler.startIndex))
            if (o.t < t) throw new AssertionError("a logging-failure notice view was created at " + o.t + " ms");
    }

    @Then("the next app start's first logging failure makes its own first attempt")
    public void nextStartAttempts() {
        w.restart();
        logSteps.writeFailsAt(1000);
        w.runFor(8000);
        List<Fakes.Op> cs = w.notices.ops("create", Fakes.LOG, w.scheduler.startIndex);
        if (cs.isEmpty()) throw new AssertionError("the next app start made no logging-failure notice attempt");
        Fakes.Op first = cs.get(0);
        if (first.t != 1000 || first.attempt != 1)
            throw new AssertionError("the next app start's first logging-failure notice attempt was attempt " + first.attempt
                    + " at " + first.t + " ms");
    }

    // ------------------------------------------------------------ records

    private List<Fakes.Line> containing(String... parts) {
        w.settle();
        List<Fakes.Line> out = new ArrayList<>();
        for (Fakes.Line l : w.lines()) {
            boolean all = true;
            for (String p : parts) all &= l.text.contains(p);
            if (all) out.add(l);
        }
        return out;
    }

    @Then("exactly one record containing {string}, {string} and {string} was written, at {int} ms")
    public void oneRecordOfThreeAt(String a, String b, String c, int t) {
        List<Fakes.Line> ls = containing(a, b, c);
        if (ls.size() != 1 || ls.get(0).t != t)
            throw new AssertionError("records containing \"" + a + "\", \"" + b + "\" and \"" + c + "\": " + ls);
    }

    @Then("exactly one record containing {string} was written")
    public void oneRecord(String a) {
        List<Fakes.Line> ls = containing(a);
        if (ls.size() != 1) throw new AssertionError("records containing \"" + a + "\": " + ls);
    }

    @Then("no record containing {string} was written")
    public void noRecord(String a) {
        List<Fakes.Line> ls = containing(a);
        if (!ls.isEmpty()) throw new AssertionError("records containing \"" + a + "\": " + ls);
    }

    @Then("no record containing {string} was written at {int} ms")
    public void noRecordAt(String a, int t) {
        for (Fakes.Line l : containing(a))
            if (l.t == t) throw new AssertionError("a record containing \"" + a + "\" was written at " + t + " ms: " + l);
    }

    @Then("a record containing {string} was written at {int} ms and at {int} ms")
    public void recordAtBoth(String a, int t1, int t2) {
        List<Fakes.Line> ls = containing(a);
        boolean first = false, second = false;
        for (Fakes.Line l : ls) {
            first |= l.t == t1;
            second |= l.t == t2;
        }
        if (!first || !second) throw new AssertionError("records containing \"" + a + "\": " + ls);
    }

    // ------------------------------------------------------------ wiring scan

    private static final String ACTION = "com.hkmc.intent.action.REVERSE_ON";

    @When("the receiver's sources and source AndroidManifest.xml are scanned")
    public void scan() {
        cover.scan();
        scanned = true;
    }

    private void requireScan() {
        if (!scanned) throw new IllegalStateException("no step scanned the sources");
    }

    @Then("the sources register a runtime receiver for com.hkmc.intent.action.REVERSE_ON and unregister it at exit")
    public void registersRuntimeReceiver() {
        requireScan();
        List<String> action = cover.sourcesContaining("\"" + ACTION + "\"");
        if (action.isEmpty()) throw new AssertionError("no source names the action \"" + ACTION + "\"");
        List<String> reg = cover.sourcesContaining("registerReceiver(");
        reg.retainAll(action);
        if (reg.isEmpty()) throw new AssertionError("no source naming " + ACTION + " calls registerReceiver; " + action);
        List<String> unreg = cover.sourcesContaining("unregisterReceiver(");
        unreg.retainAll(action);
        if (unreg.isEmpty()) throw new AssertionError("no source naming " + ACTION + " calls unregisterReceiver; " + action);
    }

    @Then("the manifest declares no receiver for REVERSE_ON")
    public void manifestHasNoReceiver() {
        requireScan();
        if (cover.manifest == null) throw new IllegalStateException("no manifest at " + cover.manifestPath);
        Matcher m = Pattern.compile("(?s)<receiver\\b.*?(?:</receiver>|/>)").matcher(cover.manifest);
        while (m.find())
            if (m.group().contains("REVERSE_ON")) throw new AssertionError("the manifest declares " + m.group());
    }

    @Then("the sources contain no REVERSE_OFF or isRearViewOn reference")
    public void noReverseOffOrRearView() {
        requireScan();
        for (String s : new String[] {"REVERSE_OFF", "isRearViewOn"}) {
            List<String> hits = cover.sourcesContaining(s);
            if (!hits.isEmpty()) throw new AssertionError(s + " is referenced in " + hits);
            if (cover.manifest != null && cover.manifest.contains(s))
                throw new AssertionError(s + " is referenced in " + cover.manifestPath);
        }
    }

    @Then("the sources read none of REVERSE_ON's extras")
    public void readsNoExtras() {
        requireScan();
        List<String> handling = cover.sourcesContaining("REVERSE_ON");
        if (handling.isEmpty())
            throw new AssertionError("no source code references REVERSE_ON, so its handling could not be checked");
        Pattern extras = Pattern.compile("\\.(?:getExtras|get\\w*Extra|hasExtra)\\s*\\(");
        for (int i = 0; i < cover.sourceFiles.size(); i++) {
            String name = cover.sourceFiles.get(i).getFileName().toString();
            if (!handling.contains(name)) continue;
            Matcher m = extras.matcher(cover.sourceCode.get(i));
            if (m.find()) throw new AssertionError(name + " handles REVERSE_ON and reads an extra: " + m.group());
        }
    }

    private static List<Long> times(List<Fakes.Op> os) {
        List<Long> out = new ArrayList<>();
        for (Fakes.Op o : os) out.add(o.t);
        return out;
    }
}
