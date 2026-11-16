package parkcast.gate.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import parkcast.gate.World;
import parkcast.gate.harness.Fakes;
import parkcast.gate.harness.FrameSource;
import parkcast.gate.harness.VirtualScheduler;
import parkcast.gate.port.AccStatus;

/**
 * Steps shared by features 09 to 11: receiver.conf and build set-up, starting and running the receiver,
 * Park getter and ACC scripting, and the generic video, Park-call and log-record checks. Every Then step
 * looks only at what the doubles recorded.
 */
public class CommonSteps {
    /** The supported build every scenario uses unless a step says otherwise. */
    public static final String SUPPORTED_BUILD = "CVEV.CAN.0000.V015.011.260206";
    /** The package's shipped receiver.conf. */
    public static final String SHIPPED_CONF = "video.require_park=true";

    private final World w;

    public CommonSteps(World w) {
        this.w = w;
    }

    /** "1", "0", "7", "with no value" or "with a failed call", replying {@code d} ms after the call starts. */
    public static Fakes.Reply replyAfter(String what, long d) {
        if ("with no value".equals(what)) return Fakes.Reply.failAfter("no value", d);
        if ("with a failed call".equals(what)) return Fakes.Reply.failAfter("failed call", d);
        return Fakes.Reply.valueAfter(Integer.parseInt(what), d);
    }

    /** Every number in a list such as "0, 200, 400 and 600". */
    public static List<Long> numbers(String list) {
        List<Long> out = new ArrayList<>();
        Matcher m = Pattern.compile("\\d+").matcher(list);
        while (m.find()) out.add(Long.parseLong(m.group()));
        return out;
    }

    // ------------------------------------------------------------ receiver.conf and build

    @Given("the installed receiver.conf holds {string}")
    public void confHolds(String text) {
        w.config.missing = false;
        w.config.unreadable = false;
        w.config.holds(text);
    }

    @Given("the installed receiver.conf is a file holding {string}")
    public void confIsFileHolding(String text) {
        confHolds(text);
    }

    @Given("the installed receiver.conf is the package's shipped file")
    public void confIsShipped() {
        confHolds(SHIPPED_CONF);
    }

    @Given("the installed receiver.conf is missing")
    public void confMissing() {
        w.config.missing = true;
        w.config.unreadable = false;
    }

    @Given("the installed receiver.conf is unreadable")
    public void confUnreadable() {
        w.config.missing = false;
        w.config.unreadable = true;
    }

    @Given("the installed receiver.conf is unreadable, and its bytes are {string}")
    public void confUnreadableWithBytes(String text) {
        w.config.holds(text);
        confUnreadable();
    }

    @Given("the parking check is on, on a supported build")
    public void checkOnSupported() {
        confHolds("video.require_park=true");
        supportedBuild();
    }

    @Given("the parking check is off, on a supported build")
    public void checkOffSupported() {
        confHolds("video.require_park=false");
        supportedBuild();
    }

    @Given("the parking check is on and video is permitted after an on-time Park reading at 0 ms")
    public void checkOnPermittedAtZero() {
        checkOnSupported();
        w.park.byStart.put(0L, Fakes.Reply.valueAfter(1, 10));
    }

    private void supportedBuild() {
        w.build.missing = false;
        w.build.unreadable = false;
        w.build.product = SUPPORTED_BUILD;
    }

    /**
     * Before the start this sets receiver.conf; after it, it checks the start's records: off means a record
     * contains "parking check off", on means none does and the start record gives a reason.
     */
    @Given("^the parking check is (on|off)$")
    public void parkingCheckIs(String state) {
        boolean on = "on".equals(state);
        if (!w.started) {
            confHolds(on ? "video.require_park=true" : "video.require_park=false");
            return;
        }
        w.settle();
        boolean offRecord = false, reason = false;
        for (Fakes.Line l : w.lines()) {
            if (l.text.contains("parking check off")) offRecord = true;
            if (l.text.contains("reason=")) reason = true;
        }
        if (on && offRecord) throw new AssertionError("a record says \"parking check off\": " + w.lines());
        if (!on && !offRecord) throw new AssertionError("no record says \"parking check off\": " + w.lines());
        if (!reason) throw new AssertionError("no start record with \"reason=\": " + w.lines());
    }

    // ------------------------------------------------------------ starting and running

    @When("the receiver starts")
    public void receiverStarts() {
        w.start();
    }

    @When("the receiver runs for {int} ms")
    public void receiverRunsFor(int ms) {
        w.runFor(ms);
    }

    @When("the receiver runs for {int} ms with mirroring active")
    public void receiverRunsForMirroring(int ms) {
        w.session.active = true;
        w.runFor(ms);
    }

    // ------------------------------------------------------------ Park getter scripting

    @Given("^the Park getter replies (\\d+|with no value|with a failed call) within 10 ms$")
    public void parkReplies(String what) {
        w.park.constant = replyAfter(what, 10);
    }

    @Given("^the first Park getter call replies (\\d+) at (?:exactly )?(\\d+) ms and every later call within 10 ms$")
    public void firstCallRepliesAt(int v, long t) {
        w.park.byIndex.put(0, Fakes.Reply.valueAt(v, t));
    }

    @Given("^the first two Park getter calls each reply (\\d+) after (\\d+) ms and every later call within 10 ms$")
    public void firstTwoReplyAfter(int v, long d) {
        w.park.byIndex.put(0, Fakes.Reply.valueAfter(v, d));
        w.park.byIndex.put(1, Fakes.Reply.valueAfter(v, d));
    }

    @Given("^the (?:Park getter )?call starting at (\\d+) ms replies (\\d+) at (?:exactly )?(\\d+) ms(?: and every later call within 10 ms)?$")
    public void callStartingRepliesAt(long begin, int v, long t) {
        w.park.byStart.put(begin, Fakes.Reply.valueAt(v, t));
        w.lastAnchor = begin;
    }

    @Given("^the (?:Park getter )?call starting at (\\d+) ms never replies$")
    public void callStartingNeverReplies(long begin) {
        w.park.byStart.put(begin, Fakes.Reply.never());
        w.lastAnchor = begin;
    }

    @Given("^the (?:Park getter )?call starting at (\\d+) ms throws an exception at (\\d+) ms$")
    public void callStartingThrows(long begin, long t) {
        w.park.byStart.put(begin, Fakes.Reply.failAt("exception", t));
        w.lastAnchor = begin;
    }

    @Given("the first Park getter call never replies")
    public void firstCallNeverReplies() {
        w.park.byIndex.put(0, Fakes.Reply.never());
    }

    @Given("the first Park getter call throws an exception at {int} ms")
    public void firstCallThrows(int t) {
        w.park.byIndex.put(0, Fakes.Reply.failAt("exception", t));
    }

    @Given("the Park getter service is missing")
    public void parkServiceMissing() {
        w.park.missing = true;
    }

    @Given("the Park getter service is lost at {int} ms")
    public void parkServiceLost(int t) {
        w.park.lose(t);
    }

    // ------------------------------------------------------------ ACC scripting

    @Given("ACC status changes to {int} at {int} ms")
    public void accChanges(int st, int t) {
        scriptAcc(st, t);
    }

    @Given("ACC status changes to {int} at {int} ms and to {int} at {int} ms")
    public void accChangesTwice(int st1, int t1, int st2, int t2) {
        scriptAcc(st1, t1);
        scriptAcc(st2, t2);
    }

    private void scriptAcc(final int st, long t) {
        if (st == 0) w.acc.changeToZeroScripted = true;
        w.scheduler.script(t, VirtualScheduler.BEFORE, () -> w.acc.deliver(AccStatus.of(st)));
    }

    @Given("ACC registration throws an exception at {int} ms")
    public void accRegThrows(int t) {
        w.acc.planThrow("exception", t);
    }

    @Given("ACC registration returns no sticky state at {int} ms")
    public void accRegNoSticky(int t) {
        w.acc.planNoSticky(t);
    }

    @Given("ACC registration returns sticky status {int} at {int} ms")
    public void accRegSticky(int st, int t) {
        w.acc.planSticky(AccStatus.of(st), t);
    }

    // ------------------------------------------------------------ Park getter checks

    @Then("^the Park getter was called only at ([\\d, and]+) ms$")
    public void calledOnlyAt(String list) {
        w.settle();
        List<Long> want = numbers(list);
        List<Long> got = new ArrayList<>();
        for (Fakes.Call c : w.calls()) got.add(c.begin);
        if (!got.equals(want)) throw new AssertionError("Park getter calls started at " + got + ", expected " + want);
    }

    @Then("the Park getter was never called")
    public void neverCalled() {
        w.settle();
        if (!w.calls().isEmpty()) throw new AssertionError("Park getter was called: " + w.calls());
    }

    @Then("at most one Park getter call was active at any time")
    public void atMostOneActive() {
        w.settle();
        List<Fakes.Call> cs = w.calls();
        for (int i = 1; i < cs.size(); i++) {
            Fakes.Call prev = cs.get(i - 1), next = cs.get(i);
            if (!prev.done || prev.end > next.begin)
                throw new AssertionError(prev + " was still active when " + next + " started");
        }
    }

    // ------------------------------------------------------------ video checks

    @Then("^mirrored video is (permitted|blocked)$")
    public void videoIs(String state) {
        w.settle();
        List<FrameSource.Tick> ticks = w.ticks();
        if (ticks.isEmpty()) throw new AssertionError("no frame was delivered");
        FrameSource.Tick last = ticks.get(ticks.size() - 1);
        if ("permitted".equals(state)) {
            if (!w.shown(last)) throw new AssertionError("last frame (" + last.t + " ms) not shown; " + w.videoSummary());
        } else {
            if (w.shown(last)) throw new AssertionError("last frame (" + last.t + " ms) was shown; " + w.videoSummary());
            List<Fakes.Shown> shows = w.shows();
            if (!shows.isEmpty()) {
                long lastShow = shows.get(shows.size() - 1).t;
                boolean cleared = false;
                for (long c : w.clears()) if (c >= lastShow) cleared = true;
                if (!cleared) throw new AssertionError("display not cleared after the last show; " + w.videoSummary());
            }
        }
    }

    @Then("mirrored video is blocked throughout")
    public void blockedThroughout() {
        w.settle();
        if (w.ticks().isEmpty()) throw new AssertionError("no frame was delivered");
        if (!w.shows().isEmpty()) throw new AssertionError("video was shown; " + w.videoSummary());
    }

    @Then("mirrored video was never blocked")
    public void neverBlocked() {
        w.settle();
        List<Fakes.Shown> shows = w.shows();
        if (shows.isEmpty()) throw new AssertionError("video was never shown; " + w.videoSummary());
        long first = shows.get(0).t;
        for (FrameSource.Tick k : w.ticks())
            if (k.t > first && !w.shown(k))
                throw new AssertionError("frame at " + k.t + " ms was not shown; " + w.videoSummary());
        for (long c : w.clears())
            if (c >= first) throw new AssertionError("display cleared at " + c + " ms; " + w.videoSummary());
    }

    @Then("mirrored video is permitted at {int} ms")
    public void permittedAt(int t) {
        w.settle();
        FrameSource.Tick last = null;
        for (FrameSource.Tick k : w.ticks()) if (k.t <= t) last = k;
        if (last == null) throw new AssertionError("no frame was delivered by " + t + " ms");
        if (!w.shown(last)) throw new AssertionError("frame at " + last.t + " ms not shown; " + w.videoSummary());
        for (long c : w.clears())
            if (c > last.t && c <= t) throw new AssertionError("display cleared at " + c + " ms; " + w.videoSummary());
    }

    @Then("mirrored video is blocked from {int} ms to the end")
    public void blockedFromToEnd(int a) {
        w.settle();
        w.assertNoneShownIn(a, Long.MAX_VALUE);
        w.assertClearedAtIfShownBefore(a);
    }

    /** "From A until U": nothing in [A, U) shown, cleared at A; a frame at exactly U is not constrained. */
    @Then("^mirrored video is blocked from (\\d+) ms until (\\d+) ms(, then shown on the next frame| and shown from the next frame after \\2 ms)?$")
    public void blockedFromUntil(long a, long u, String after) {
        w.settle();
        w.assertNoneShownIn(a, u);
        w.assertClearedAtIfShownBefore(a);
        showAfter(u, after);
    }

    @Then("^mirrored video is blocked until (\\d+) ms(, then shown on the next frame| and shown from the next frame after \\1 ms)$")
    public void blockedUntil(long u, String after) {
        w.settle();
        w.assertNoneShownIn(0, u);
        showAfter(u, after);
    }

    private void showAfter(long u, String after) {
        if (after == null) return;
        if (after.startsWith(",")) w.assertNextShownAfter(u);
        else w.assertAllShownAfter(u);
    }

    // ------------------------------------------------------------ log records

    @Then("no record contains {string}")
    public void noRecordContains(String x) {
        w.settle();
        List<Fakes.Line> hits = w.logs.containing(x);
        if (!hits.isEmpty()) throw new AssertionError("records contain \"" + x + "\": " + hits);
    }
}
