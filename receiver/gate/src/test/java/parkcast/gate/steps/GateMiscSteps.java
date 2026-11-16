package parkcast.gate.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import parkcast.gate.Receiver;
import parkcast.gate.World;
import parkcast.gate.harness.AT18Checker;
import parkcast.gate.harness.Fakes;
import parkcast.gate.harness.VirtualScheduler;
import parkcast.gate.port.AccStatus;

/**
 * Steps for the rest of the parking gate (09): ACC broadcasts with the check off, registration failures
 * across exit and restarts, the build allow-list, the AT-18 display timing and what the gate must never
 * touch (audio, the iPhone connection, the car). Every Then step looks only at what the doubles recorded,
 * or at the AT-18 checker and the static scan.
 */
public class GateMiscSteps {
    private final World w;
    /** Set once "runs for N ms and exits" returned from the receiver's exit. */
    private boolean exitCompleted;
    /** When the receiver was told to exit, once a step made it exit. */
    private long exitedAt = -1;

    /** When the receiver was told to exit; throws if no step made it exit. */
    public long exitedAt() {
        if (exitedAt < 0) throw new IllegalStateException("no step made the receiver exit");
        return exitedAt;
    }

    /** For the logging-failure steps of the previous app start (l.316, l.473 of 11). */
    private final LogSteps logSteps;
    private final CommonSteps common;

    public GateMiscSteps(World w, LogSteps logSteps, CommonSteps common) {
        this.w = w;
        this.logSteps = logSteps;
        this.common = common;
    }

    // ------------------------------------------------------------ ACC broadcasts with the check off

    @Then("no record was written from just before to just after each of those broadcasts")
    public void noRecordAroundBroadcasts() {
        w.settle();
        if (w.acc.deliveries.isEmpty()) throw new AssertionError("no ACC broadcast arrived");
        for (Fakes.Delivery d : w.acc.deliveries)
            if (d.after != d.before)
                throw new AssertionError("the broadcast at " + d.t + " ms wrote " + w.logs.lines.subList(d.before, d.after));
    }

    @Then("none of those broadcasts was a resume or started a re-read")
    public void noResumeNoReread() {
        w.settle();
        for (Fakes.Call c : w.calls())
            if (c.reread) throw new AssertionError("Park getter call at " + c.begin + " ms was a re-read; calls " + w.calls());
        for (Fakes.Delivery d : w.acc.deliveries)
            for (Fakes.Call c : w.calls())
                if (c.begin == d.t)
                    throw new AssertionError("Park getter call started with the broadcast at " + d.t + " ms; calls " + w.calls());
    }

    @Given("^ACC registration returns a sticky state with (?:the status extra (missing)|a non-integer extra \"[^\"]*\") at (\\d+) ms$")
    public void accRegStickyUnusable(String missing, long t) {
        w.acc.planSticky(missing != null ? AccStatus.MISSING : AccStatus.NON_INTEGER, t);
    }

    // ------------------------------------------------------------ registration failures, exit and restart

    @When("the receiver runs for {int} ms and exits")
    public void runsAndExits(int ms) {
        w.exitAt(ms);
        w.runFor(ms);
        exitedAt = ms;
        exitCompleted = true;
    }

    /** Exit at {@code t} runs before the gate's own timers then, so a retry due at {@code t} is dropped. */
    @When("the receiver starts exit at {int} ms")
    public void startsExit(int t) {
        w.start();
        final Receiver r = w.receiver;
        w.scheduler.script(t, VirtualScheduler.BEFORE, () -> {
            r.exit();
            exitCompleted = true;
        });
        exitedAt = t;
        w.runFor(t + 10000L);
    }

    @Then("^no (registration|logging)-failure notice window exists after exit$")
    public void noWindowAfterExit(String which) {
        if (exitedAt < 0) throw new IllegalStateException("no step made the receiver exit");
        String kind = "registration".equals(which) ? Fakes.REG : Fakes.LOG;
        for (Fakes.Window win : w.notices.windows(kind, w.scheduler.startIndex)) {
            if (win.addedAt > exitedAt) throw new AssertionError("a " + which + "-failure window was added at " + win.addedAt + " ms");
            if (win.removedAt < 0 || win.removedAt > exitedAt)
                throw new AssertionError("the " + which + "-failure window added at " + win.addedAt + " ms was still on screen after exit");
        }
    }

    /** The previous start's state, for "no removal call ... of the previous app start". */
    private int previousStart = -1;

    /** The next "the receiver starts again" makes the exit at 3000 ms: restart exits the old receiver then. */
    @Given("during the previous app start the first registration-failure notice attempt failed and exit came at 3000 ms")
    public void previousStartAttemptFailed() {
        previousStart = w.scheduler.startIndex;
        w.acc.planThrow("exception", 0);
        w.notices.throwKeys.add(previousStart + "|" + Fakes.REG + "|1|add");
        w.runFor(3000);
    }

    /**
     * The later app starts made no removal call for a window the previous start added. The previous start's own
     * exit may remove its windows (l.456 of 11 requires it), so its removal calls are not counted (l.473 of 11).
     */
    @Then("no removal call was made for any window of the previous app start")
    public void noRemovalOfPreviousStart() {
        if (previousStart < 0) throw new IllegalStateException("no step ran a previous app start");
        w.settle();
        for (Fakes.Op o : w.notices.ops("remove", null, null)) {
            if (o.start <= previousStart) continue;
            for (Fakes.Window win : w.notices.windows)
                if (win.start == previousStart && win.view == o.view)
                    throw new AssertionError("start " + o.start + " made a removal call at " + o.t
                            + " ms for the previous start's window added at " + win.addedAt + " ms");
        }
    }

    /** The next "the receiver starts again ..." makes the exit at {@code t}: restart exits the old receiver then. */
    @Given("during the previous app start the first logging-failure notice attempt failed and exit came at {int} ms")
    public void previousStartLogAttemptFailed(int t) {
        previousStart = w.scheduler.startIndex;
        common.checkOnSupported();
        logSteps.writeFailsAt(1000);
        w.notices.throwKeys.add(previousStart + "|" + Fakes.LOG + "|1|add");
        w.runFor(t);
    }

    /** The next "the receiver starts again" makes the exit at {@code t}; that exit removes the notice. */
    @Given("during the previous app start a logging-failure notice was shown and exit came at {int} ms")
    public void previousStartLogNoticeShown(int t) {
        previousStart = w.scheduler.startIndex;
        common.checkOnPermittedAtZero();
        logSteps.writeFailsAt(1000);
        w.runFor(t);
        if (w.notices.windows(Fakes.LOG, previousStart).size() != 1)
            throw new AssertionError("the previous app start showed no single logging-failure notice");
    }

    @When("the receiver starts again and a log write fails at {int} ms")
    public void startsAgainLogFails(int t) {
        w.restart();
        logSteps.writeFailsAt(t);
    }

    @When("the receiver starts again")
    public void startsAgainPlain() {
        w.restart();
    }

    @Then("no window of the previous app start is on screen")
    public void noPreviousWindowOnScreen() {
        if (previousStart < 0) throw new IllegalStateException("no step ran a previous app start");
        w.settle();
        for (Fakes.Window win : w.notices.windows)
            if (win.start == previousStart && win.removedAt < 0)
                throw new AssertionError("the previous start's window added at " + win.addedAt + " ms is still on screen");
    }

    @Then("^(?:the )?exit completes(?: without waiting for the retry)?$")
    public void exitCompletes() {
        if (exitedAt < 0) throw new IllegalStateException("no step made the receiver exit");
        if (!exitCompleted) throw new AssertionError("exit at " + exitedAt + " ms did not return");
    }

    @Then("^the ACC broadcast receiver was (never unregistered|unregistered once)$")
    public void accUnregistered(String how) {
        int want = how.startsWith("never") ? 0 : 1;
        List<Long> got = new ArrayList<>();
        for (long[] u : w.acc.currentUnregisters()) got.add(u[1]);
        if (got.size() != want)
            throw new AssertionError("ACC broadcast receiver unregistered at " + got + " ms, expected " + want + " time(s)");
    }

    @Given("^the previous run's ACC registration (threw an exception|returned no sticky state)$")
    public void previousRunRegistration(String how) {
        if (how.startsWith("threw")) w.acc.planThrow("exception", 0);
        else w.acc.planNoSticky(0);
        w.runFor(1000);
    }

    @When("^the receiver starts again and ACC registration (?:returns sticky status (\\d+)|(throws an exception)|(returns no sticky state)) at (\\d+) ms$")
    public void startsAgain(Integer sticky, String throwsIt, String noSticky, long t) {
        w.restart();
        if (sticky != null) w.acc.planSticky(AccStatus.of(sticky), t);
        else if (throwsIt != null) w.acc.planThrow("exception", t);
        else w.acc.planNoSticky(t);
        w.start();
    }

    @Then("the Park getter was first called at exactly {int} ms")
    public void firstCalledAt(int t) {
        w.settle();
        List<Fakes.Call> cs = w.calls();
        if (cs.isEmpty()) throw new AssertionError("the Park getter was never called");
        if (cs.get(0).begin != t) throw new AssertionError("the Park getter was first called at " + cs.get(0).begin + " ms");
    }

    @Given("writing the {string} record fails")
    public void writingFails(String x) {
        w.logs.failOn = x;
    }

    // ------------------------------------------------------------ AT-18: leaving Park removes the image

    /** Scheduler rank of the shift: BEFORE runs it ahead of a poll on the same millisecond, AFTER behind it. */
    private int shiftRank = VirtualScheduler.BEFORE;
    private long shiftAt = -1;
    /** End of the first reply of 0 after the shift, once a step found it. */
    private long zeroReplyAt = -1;

    /** receiver.conf set to the check on or off, on a supported build. */
    private void checkOnSupportedBuild(boolean on) {
        w.config.missing = false;
        w.config.unreadable = false;
        w.config.holds(on ? "video.require_park=true" : "video.require_park=false");
        w.build.missing = false;
        w.build.unreadable = false;
        w.build.product = CommonSteps.SUPPORTED_BUILD;
    }

    /** A frozen image: frames stop arriving at 100 ms, after some were shown, so the last one stays up. */
    @Given("^the parking check is on and video is showing a (moving|frozen) image after an on-time Park reading at 0 ms$")
    public void showingImage(String image) {
        checkOnSupportedBuild(true);
        w.park.byStart.put(0L, Fakes.Reply.valueAfter(1, 10));
        w.session.active = true;
        if ("frozen".equals(image)) w.scheduler.script(100, VirtualScheduler.BEFORE, () -> w.frames.frozen = true);
    }

    /**
     * The frame source has no queue of its own; frames the receiver holds back are its own queue. Every
     * frame stays delivered, and "queued or new" is judged on what reached the display.
     */
    @Given("frames are queued for display")
    public void framesQueued() {
    }

    @Given("the Park getter replies at once with the state at the moment it is called")
    public void parkRepliesAtOnce() {
        w.park.immediate = true;
    }

    /** A poll on that millisecond runs at GATE rank, so the shift goes just before or just after it. */
    @Given("^at (\\d+) ms the (shift|poll) is processed first$")
    public void shiftOrPollFirst(long t, String which) {
        shiftRank = "shift".equals(which) ? VirtualScheduler.BEFORE : VirtualScheduler.AFTER;
    }

    @When("the car shifts out of Park at {int} ms")
    public void shiftsOut(int t) {
        shiftAt = t;
        w.start();
        w.scheduler.script(t, shiftRank, () -> w.park.gear = 0);
        w.runFor(t + 1000L);
    }

    private long firstZeroReply() {
        if (shiftAt < 0) throw new IllegalStateException("no step shifted the car out of Park");
        w.settle();
        for (Fakes.Call c : w.calls())
            if (c.done && c.fail == null && c.value != null && c.value == 0 && c.end >= shiftAt) {
                zeroReplyAt = c.end;
                return c.end;
            }
        throw new AssertionError("no reply of 0 was received after the shift at " + shiftAt + " ms; calls " + w.calls());
    }

    @Then("the first reply of 0 is received at most 200 ms after the shift")
    public void zeroWithin200() {
        long r = firstZeroReply();
        if (r - shiftAt > 200)
            throw new AssertionError("first reply of 0 at " + r + " ms, " + (r - shiftAt) + " ms after the shift at " + shiftAt + " ms");
    }

    @Then("the first reply of 0 is received at {int} ms")
    public void zeroAt(int t) {
        long r = firstZeroReply();
        if (r != t) throw new AssertionError("first reply of 0 at " + r + " ms, expected " + t + " ms; calls " + w.calls());
    }

    /** The image is gone at the first Display.clear() at or after the shift. */
    private long imageGoneAt() {
        if (shiftAt < 0) throw new IllegalStateException("no step shifted the car out of Park");
        w.settle();
        for (long c : w.clears()) if (c >= shiftAt) return c;
        throw new AssertionError("the image was never removed after the shift at " + shiftAt + " ms; " + w.videoSummary());
    }

    @Then("the image is gone at most 250 ms after that reply is received")
    public void goneWithin250OfReply() {
        long r = zeroReplyAt >= 0 ? zeroReplyAt : firstZeroReply();
        long g = imageGoneAt();
        if (g - r > 250) throw new AssertionError("image gone at " + g + " ms, " + (g - r) + " ms after the reply at " + r + " ms");
    }

    @Then("the image is gone at most 450 ms after the shift")
    public void goneWithin450OfShift() {
        long g = imageGoneAt();
        if (g - shiftAt > 450)
            throw new AssertionError("image gone at " + g + " ms, " + (g - shiftAt) + " ms after the shift at " + shiftAt + " ms");
    }

    @Then("no frame, queued or new, is shown from the moment that reply is received")
    public void noFrameFromReply() {
        long r = zeroReplyAt >= 0 ? zeroReplyAt : firstZeroReply();
        for (Fakes.Shown s : w.shows())
            if (s.t >= r) throw new AssertionError("frame #" + s.id + " shown at " + s.t + " ms, at or after the reply at " + r
                    + " ms; " + w.videoSummary());
    }

    // ------------------------------------------------------------ AT-18 checker

    private long traceShift = -1, traceReply, traceGone;
    private String verdict;

    @Given("a display trace with the shift at {int} ms, the first reply of 0 at {int} ms and the image gone at {int} ms")
    public void displayTrace(int shift, int reply, int gone) {
        traceShift = shift;
        traceReply = reply;
        traceGone = gone;
    }

    @When("the AT-18 timing check judges it")
    public void at18Judges() {
        if (traceShift < 0) throw new IllegalStateException("no display trace was given");
        verdict = AT18Checker.broken(traceShift, traceReply, traceGone);
    }

    @Then("^it is (accepted|rejected)$")
    public void itIs(String want) {
        String got = "none".equals(verdict) ? "accepted" : "rejected";
        if (!got.equals(want)) throw new AssertionError("the trace was " + got + ", broken: " + verdict);
    }

    @Then("^the bounds it names as broken are (.*)$")
    public void boundsBroken(String want) {
        if (!want.equals(verdict)) throw new AssertionError("broken bounds named: " + verdict + ", expected: " + want);
    }

    // ------------------------------------------------------------ AT-20: build allow-list

    @Given("ro.build.product is {string}")
    public void buildProduct(String p) {
        w.build.missing = false;
        w.build.unreadable = false;
        w.build.product = p;
    }

    @Given("ro.build.product is missing")
    public void buildProductMissing() {
        w.build.missing = true;
        w.build.unreadable = false;
    }

    @Given("ro.build.product is unreadable")
    public void buildProductUnreadable() {
        w.build.missing = false;
        w.build.unreadable = true;
    }

    /** The port reads ro.build.product only; ro.build.product2 is recorded on the double and nowhere else. */
    @Given("ro.build.product is {string} and ro.build.product2 is {string}")
    public void buildProducts(String p, String p2) {
        buildProduct(p);
        w.build.product2 = p2;
    }

    // ------------------------------------------------------------ AT-21: the check off

    @Given("the Park getter call throws an exception")
    public void parkCallThrows() {
        w.park.constant = Fakes.Reply.failAfter("exception", 10);
    }

    @Given("the Park getter replies 0 at 10 ms and the call starting at 200 ms never replies, so the reading is stale from 2010 ms")
    public void staleFrom2010() {
        w.park.byStart.put(0L, Fakes.Reply.valueAt(0, 10));
        w.park.byStart.put(200L, Fakes.Reply.never());
        w.lastAnchor = 200;
    }

    @Given("the re-read replies {int} after {int} ms")
    public void rereadReplies(int v, int d) {
        w.park.reread = Fakes.Reply.valueAfter(v, d);
    }

    private static final String[] CAR_CHANGES = {"sendCommand", "writeCan", "changeSetting", "changeRegulation",
            "sendRegulationEvent", "changeDriverAssistanceDisplay"};

    private void assertNoCarChange() {
        w.settle();
        StringBuilder b = new StringBuilder();
        for (String m : CAR_CHANGES)
            if (w.vehicle.count(m) != 0) b.append(' ').append(m).append(" x").append(w.vehicle.count(m));
        if (b.length() != 0) throw new AssertionError("the receiver changed the car:" + b);
    }

    @Then("no stock restriction or setting was changed")
    public void noStockChange() {
        assertNoCarChange();
    }

    // ------------------------------------------------------------ AT-22 / AT-37: audio and the iPhone connection

    @Given("^the parking check is (on|off) and mirrored audio is playing$")
    public void checkAndAudio(String state) {
        checkOnSupportedBuild("on".equals(state));
        w.park.byStart.put(0L, Fakes.Reply.valueAfter(1, 10));
        mirroredAudioPlaying();
    }

    @Given("mirrored audio is playing")
    public void mirroredAudioPlaying() {
        w.session.active = true;
        w.session.audioPlaying = true;
    }

    /** Scripted only; the following "the receiver runs for N ms" runs it. */
    @When("the car leaves Park at {int} ms and returns to Park at {int} ms")
    public void leavesAndReturns(int out, int back) {
        w.start();
        w.scheduler.script(out, VirtualScheduler.BEFORE, () -> w.park.gear = 0);
        w.scheduler.script(back, VirtualScheduler.BEFORE, () -> w.park.gear = 1);
    }

    @Then("the gate never muted, stopped or disconnected the audio")
    public void audioNeverTouched() {
        w.settle();
        NoticeSteps.assertAudioUntouched(w);
    }

    @Then("the iPhone stayed connected throughout")
    public void iphoneStayedConnected() {
        w.settle();
        NoticeSteps.assertConnectionUntouched(w);
        if (w.session.reconnects != 0) throw new AssertionError("the iPhone reconnected " + w.session.reconnects + " times");
    }

    /** On-time reading of 1 at 0 ms, out of Park at 300 ms, run to 800 ms. */
    @Given("the parking check is on, on a supported build, and video is blocked because the car left Park")
    public void blockedAfterLeavingPark() {
        checkOnSupportedBuild(true);
        w.park.byStart.put(0L, Fakes.Reply.valueAfter(1, 10));
        w.session.active = true;
        w.start();
        w.scheduler.script(300, VirtualScheduler.BEFORE, () -> w.park.gear = 0);
        w.runFor(800);
    }

    /** End of the on-time reply of 1 after the car went back into Park. */
    private long backInParkReplyAt = -1;

    @When("an on-time Park reply of 1 is received")
    public void onTimeReplyOf1() {
        final long from = w.ranUntil + 1;
        w.scheduler.script(from, VirtualScheduler.BEFORE, () -> w.park.gear = 1);
        for (long t = from; t <= from + 2000; t += 50) {
            w.runFor(t);
            for (Fakes.Call c : w.calls())
                if (c.begin >= from && c.done && c.fail == null && c.value != null && c.value == 1 && c.end - c.begin < 200) {
                    backInParkReplyAt = c.end;
                    w.runFor(c.end + 500);
                    return;
                }
        }
        throw new AssertionError("no on-time Park reply of 1 within 2000 ms of " + from + " ms; calls " + w.calls());
    }

    @Then("the next frame is shown without the iPhone reconnecting")
    public void nextFrameNoReconnect() {
        if (backInParkReplyAt < 0) throw new IllegalStateException("no step received an on-time reply of 1");
        w.assertNextShownAfter(backInParkReplyAt);
        if (w.session.reconnects != 0) throw new AssertionError("the iPhone reconnected " + w.session.reconnects + " times");
        NoticeSteps.assertConnectionUntouched(w);
    }

    @Then("the audio was not interrupted")
    public void audioNotInterrupted() {
        w.settle();
        NoticeSteps.assertAudioUntouched(w);
    }

    // ------------------------------------------------------------ AT-24: read-only vehicle access

    /** The vehicle, regulation and session doubles record every call already: nothing to set up. */
    @Given("every vehicle interface the receiver can reach is recorded")
    public void vehicleRecorded() {
    }

    @When("the receiver starts, blocks, recovers and stops")
    public void startsBlocksRecoversStops() {
        checkOnSupportedBuild(true);
        w.park.byStart.put(0L, Fakes.Reply.valueAfter(1, 10));
        w.session.active = true;
        w.start();
        w.scheduler.script(500, VirtualScheduler.BEFORE, () -> w.park.gear = 0);
        w.scheduler.script(1500, VirtualScheduler.BEFORE, () -> w.park.gear = 1);
        w.exitAt(2500);
        w.runFor(2500);
        exitedAt = 2500;
        exitCompleted = true;
    }

    @Then("^Park was read only through VehicleInfoManager\\.getGearPStatus\\(\\)$")
    public void parkReadOnlyThroughGetter() {
        w.settle();
        if (w.calls().isEmpty()) throw new AssertionError("Park was never read through the Park getter");
        int l = w.vehicle.count("registerParkListener"), b = w.vehicle.count("requestBcmStatus");
        if (l + b != 0) throw new AssertionError("Park listener registered " + l + " times, BCM status requested " + b + " times");
    }

    @Then("no vehicle command, CAN write, setting change, regulation change or spoofed regulation event was sent")
    public void noVehicleWrite() {
        assertNoCarChange();
    }

    /** Registering for stock regulation notifications is allowed, so there is nothing to forbid here. */
    @Then("its registration for stock regulation change notifications was allowed")
    public void regulationRegistrationAllowed() {
    }

    // ------------------------------------------------------------ AT-27: never the HUD

    /** Blocked means out of Park from the start: the first reading at 0 ms says 0, and so does every later one. */
    @Given("^the parking check is (on|off) and mirrored video is (permitted|blocked)$")
    public void checkAndPermitted(String state, String video) {
        checkOnSupportedBuild("on".equals(state));
        boolean blocked = "blocked".equals(video);
        if (blocked) w.park.gear = 0;
        w.park.byStart.put(0L, Fakes.Reply.valueAfter(blocked ? 0 : 1, 10));
        w.session.active = true;
        w.gateCheck = state;
        w.gateVideo = video;
    }

    @When("the receiver shows frames for {int} ms")
    public void showsFramesFor(int ms) {
        w.runFor(ms);
    }

    @Then("no mirrored frame was routed to the HUD")
    public void noHud() {
        w.settle();
        int n = w.vehicle.count("routeFrameToHud");
        if (n != 0) throw new AssertionError(n + " frames were routed to the HUD");
    }

    @Then("no driver-assistance display setting was changed")
    public void noDriverAssistanceChange() {
        w.settle();
        int n = w.vehicle.count("changeDriverAssistanceDisplay");
        if (n != 0) throw new AssertionError("the driver-assistance display setting was changed " + n + " times");
    }
}
