package parkcast.core.steps;

import static parkcast.core.steps.Check.assertEquals;
import static parkcast.core.steps.Check.assertFalse;
import static parkcast.core.steps.Check.assertTrue;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import parkcast.gate.harness.Fakes;

/** Steps for the start-failure notice (AIR-069) and the faults that lead to it. */
public final class StartFailureSteps {
    private final S3World w;

    public StartFailureSteps(S3World w) {
        this.w = w;
    }

    static String noticeText(String cause) {
        return "ParkCast can't start: " + cause + ". Exit and try again.";
    }

    private List<Fakes.Op> adds() {
        return w.g.notices.ops("add", Fakes.START, w.startIndex());
    }

    private Fakes.Window window() {
        List<Fakes.Window> ws = w.g.notices.windows(Fakes.START, w.startIndex());
        assertEquals(1, ws.size(), "start-failure windows: " + ws.size());
        return ws.get(0);
    }

    @Given("onStartDiscoveryFailed is delivered with error code {int} at {int} ms")
    public void discoveryFails(int code, int at) {
        w.nsd.failCode = code;
        w.nsd.failAt = at;
    }

    @Given("the fake codec factory throws on create and a session starts at {int} ms")
    public void codecThrows(int at) {
        w.codecs.throwOnCreate = true;
        w.sessionsAt.add((long) at);
    }

    @Given("the UI double throws on add for the start-failure notice")
    public void addThrows() {
        w.g.notices.throwKeys.add(w.startIndex() + "|" + Fakes.START + "|1|add");
    }

    @When("the receiver is then started again with wlan0 up holding 192.168.0.1, and runs for {int} ms")
    public void restartHealthy(int ms) {
        w.restart();
        w.ifaces.report("wlan0 up holding 192.168.0.1");
        w.runFor(ms);
    }

    @Then("exactly one start-failure notice add call was made, at exactly {int} ms, for {string}")
    public void oneAddAtFor(int t, String cause) {
        oneAddAt(t);
        oneAddFor(cause);
    }

    @Then("exactly one start-failure notice add call was made, for {string}")
    public void oneAddFor(String cause) {
        List<Fakes.Op> a = adds();
        assertEquals(1, a.size(), "start-failure adds: " + a.size());
        assertEquals(noticeText(cause), a.get(0).text);
    }

    @Then("exactly one start-failure notice add call was made, at exactly {int} ms")
    public void oneAddAt(int t) {
        List<Fakes.Op> a = adds();
        assertEquals(1, a.size(), "start-failure adds: " + a.size());
        assertEquals(t, a.get(0).t, "add time");
    }

    @Then("exactly one start-failure notice add attempt was made, at exactly {int} ms")
    public void oneAttemptAt(int t) {
        oneAddAt(t);
    }

    @Then("it is on screen at {int} ms, and exactly one removal call for it was made, at exactly {int} ms")
    public void onScreenThenRemoved(int last, int removed) {
        assertTrue(window().onScreenAt(last), "not on screen at " + last);
        oneRemoval(removed);
    }

    @Then("exactly one removal call for it was made, at exactly {int} ms")
    public void oneRemoval(int t) {
        assertEquals(Arrays.asList((long) t), window().removeCalls);
    }

    @Then("no notice add call was made after {int} ms")
    public void noAddAfter(int t) {
        for (Fakes.Op o : w.g.notices.ops("add", null, w.startIndex()))
            assertFalse(o.t > t, "add at " + o.t + ": " + o.text);
    }

    @Then("no notice add call was made")
    public void noAdd() {
        List<Fakes.Op> a = w.g.notices.ops("add", null, w.startIndex());
        assertTrue(a.isEmpty(), "adds: " + texts(a));
    }

    @Then("no notice create or add call was made in either app start")
    public void noCreateOrAddEither() {
        for (int s = 1; s <= 2; s++) {
            List<Fakes.Op> c = w.g.notices.ops("create", null, s);
            List<Fakes.Op> a = w.g.notices.ops("add", null, s);
            assertTrue(c.isEmpty() && a.isEmpty(), "start " + s + ": creates " + texts(c) + " adds " + texts(a));
        }
    }

    @Then("the first app start wrote its end marker at {int} ms")
    public void firstEndMarker(int t) {
        Fakes.Line end = null;
        for (Fakes.Line l : w.g.logs.lines)
            if (l.start == 1 && S3World.body(l).startsWith("parkcast stop reason=")) end = l;
        assertTrue(end != null, "no end marker in the first start");
        assertEquals(t, end.t, "end marker: " + end.text);
    }

    @Then("no Toast.show\\() call, other window type, dialog, notification or activity was recorded")
    public void noOtherUi() {
        // The port has no show call: a "toast" op is the placement toast the gate makes and never shows
        // (AIR-040(g), OD-121). It must never become a window of its own.
        for (Fakes.Window win : w.g.notices.windows)
            assertFalse(win.view != null && win.view.toast, "placement toast added as a window at " + win.addedAt);
        for (Fakes.Op o : w.g.notices.ops("add", null, null))
            assertEquals(Fakes.START, o.kind, "other notice added: " + o.text);
    }

    @Then("its view's text contains neither {string} nor {string} nor {string}")
    public void textLacks(String a, String b, String c) {
        String text = window().view.text;
        for (String s : new String[] {a, b, c}) assertFalse(text.contains(s), "notice text has " + s + ": " + text);
    }

    @Then("its view's text contains neither the key file's path nor its name nor {string}")
    public void textLacksKey(String s) {
        textLacks(S3World.KEY_PATH, S3World.KEY_NAME, s);
    }

    @Then("its view's text contains neither the key file's path nor its name nor the library error")
    public void textLacksKeyAndLibraryError() {
        textLacks(S3World.KEY_PATH, S3World.KEY_NAME, CommonS3Steps.LIBRARY_ERROR);
    }

    @Then("the failed add is logged")
    public void failedAddLogged() {
        boolean found = false;
        for (String b : w.bodies()) if (b.startsWith("start-failure notice add failed")) found = true;
        assertTrue(found, "no failed-add record in " + w.bodies());
    }

    private static List<String> texts(List<Fakes.Op> ops) {
        List<String> out = new ArrayList<>();
        for (Fakes.Op o : ops) out.add(o.t + ":" + o.kind + ":" + o.text);
        return out;
    }
}
