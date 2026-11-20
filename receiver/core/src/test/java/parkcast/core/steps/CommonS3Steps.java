package parkcast.core.steps;

import static parkcast.core.steps.Check.assertEquals;
import static parkcast.core.steps.Check.assertFalse;
import static parkcast.core.steps.Check.assertTrue;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import parkcast.core.doubles.Trace;
import parkcast.gate.harness.Fakes;
import parkcast.gate.harness.VirtualScheduler;

/** Steps every S3 runtime scenario shares: running the receiver, the log, video blocking and the process. */
public final class CommonS3Steps {
    /** The library error the native double reports for an unusable key (AIR-083(c)). */
    static final String LIBRARY_ERROR = "raop_init2 -1";
    /** Log records that say this app start failed; once one is written the gate may permit with no video. */
    static final String[] FAILURES = {"port busy", "advertise failed", "decoder unavailable",
            "hotspot address missing", "identity key unavailable", "stdio redirect failed", "start failed"};

    private final S3World w;

    public CommonS3Steps(S3World w) {
        this.w = w;
    }

    @When("the receiver runs for {int} ms")
    public void runsFor(int ms) {
        w.runFor(ms);
    }

    @When("exit\\() is called at {int} ms")
    public void exitAt(int t) {
        w.exitAt(t);
        w.runFor(t);
    }

    @Given("the receiver's window loses focus at {int} ms and gains focus at {int} ms")
    public void focusLostThenGained(int lost, int gained) {
        w.focusChanges.add(new long[] {lost, 0});
        w.focusChanges.add(new long[] {gained, 1});
    }

    @Given("the receiver's window does not gain focus")
    public void neverFocused() {
        w.focusAt = -1;
    }

    @Given("the interface double reports {}")
    public void interfacesReport(String phrase) {
        w.ifaces.report(phrase);
    }

    @Then("the log has {string}")
    public void logHas(String s) {
        if (!w.started) w.settle();
        assertTrue(record(s, null) != null, "no record starting \"" + s + "\" in " + w.bodies());
    }

    @Then("the log has {string}, naming {string}")
    public void logHasNamingComma(String s, String named) {
        logHasNaming(s, named);
    }

    @Then("the log has {string} naming {string}")
    public void logHasNaming(String s, String named) {
        assertTrue(record(s, named) != null, "no record \"" + s + "\" naming " + named + " in " + w.bodies());
    }

    @Then("the log has {string} naming the library error")
    public void logHasLibraryError(String s) {
        logHasNaming(s, LIBRARY_ERROR);
    }

    @Then("the log has a record that is exactly {string}")
    public void logHasExactly(String s) {
        assertTrue(w.bodies().contains(s), "no record exactly \"" + s + "\" in " + w.bodies());
    }

    @Then("the log has {string} and then {string}")
    public void logHasInOrder(String first, String second) {
        List<String> b = w.bodies();
        int i = indexStarting(b, first, 0);
        assertTrue(i >= 0, "no \"" + first + "\" in " + b);
        assertTrue(indexStarting(b, second, i + 1) >= 0, "no \"" + second + "\" after \"" + first + "\" in " + b);
    }

    @Then("mirrored video stays blocked throughout")
    public void blocked() {
        assertTrue(w.g.shows().isEmpty(), "frames were shown: " + w.g.shows().size());
        for (Trace.Entry e : w.trace.current("codec", "releaseOutput"))
            assertFalse(e.details.endsWith("render=true"), "rendered output: " + e);
        if (!failed()) {
            for (String b : w.bodies())
                assertFalse(b.startsWith("gate transition") && b.contains(" new=permitted"), "gate permitted: " + b);
        }
    }

    @Then("mirrored video stays blocked throughout and the screen is black throughout")
    public void blockedAndBlack() {
        blocked();
        black();
    }

    @Then("the screen is black throughout")
    public void black() {
        for (Trace.Entry e : w.trace.current("cover", "black"))
            assertEquals("on", e.details, "the black cover was lifted: " + e);
        for (Trace.Entry e : w.trace.current("codec", "releaseOutput"))
            assertFalse(e.details.endsWith("render=true"), "rendered output: " + e);
    }

    @Then("mirrored video is permitted from {int} ms")
    public void permittedFrom(int t) {
        Fakes.Line first = null;
        for (Fakes.Line l : w.lines()) {
            String b = S3World.body(l);
            if (b.startsWith("gate transition") && b.contains(" new=permitted")) {
                first = l;
                break;
            }
        }
        assertTrue(first != null, "the gate never permitted: " + w.bodies());
        assertEquals(t, first.t, "first permit: " + first.text);
    }

    @Then("the process stays alive and the activity is not finished")
    public void aliveAndNotFinished() {
        assertTrue(w.trace.current("crash", "uncaught").isEmpty(), "uncaught: " + w.trace.current("crash", "uncaught"));
        notFinished();
    }

    /** No finish port exists; a finished activity would have written the end marker (AIR-071(c)). */
    @Then("the activity is not finished")
    public void notFinished() {
        for (String b : w.bodies()) assertFalse(b.startsWith("parkcast stop"), "end marker written: " + b);
    }

    @Then("the log names that start failure")
    public void namesStartFailure() {
        assertTrue(record("start failed", "java.lang.IllegalStateException") != null, "bodies " + w.bodies());
    }

    private boolean failed() {
        for (String b : w.bodies())
            for (String f : FAILURES) if (b.startsWith(f)) return true;
        return false;
    }

    private String record(String start, String named) {
        for (String b : w.bodies()) if (b.startsWith(start) && (named == null || b.contains(named))) return b;
        return null;
    }

    private static int indexStarting(List<String> b, String s, int from) {
        for (int i = from; i < b.size(); i++) if (b.get(i).startsWith(s)) return i;
        return -1;
    }

    @Given("a session starts at {int} ms")
    public void sessionStartsAt(int t) {
        if (w.started) {
            w.scheduler.script(t, parkcast.gate.harness.VirtualScheduler.AFTER, new Runnable() {
                @Override
                public void run() {
                    w.nativeLib.session(w.nativeLib.sessionCodec, w.nativeLib.sessionW, w.nativeLib.sessionH);
                }
            });
        } else {
            w.sessionsAt.add((long) t);
        }
    }

    @When("the receiver runs for {int} ms and then exit\\() is called")
    public void runsForThenExit(int ms) {
        w.exitAt(ms);
        w.runFor(ms);
    }

    /** Park is gear 1 in the gate's park double; leaving Park sets it to 0 (as the gate's own step does). */
    @Given("the car leaves Park at {int} ms and returns to Park at {int} ms")
    public void leavesAndReturns(int out, int back) {
        w.scheduler.script(out, VirtualScheduler.BEFORE, () -> w.g.park.gear = 0);
        w.scheduler.script(back, VirtualScheduler.BEFORE, () -> w.g.park.gear = 1);
    }
}
