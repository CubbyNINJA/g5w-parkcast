package parkcast.gate.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import parkcast.gate.ParkPoller;
import parkcast.gate.World;
import parkcast.gate.harness.Fakes;
import parkcast.gate.harness.VirtualScheduler;

/** Steps for the Park polling cadence, timeouts, late replies, reading age and resume re-reads (09). */
public class GateTimingSteps {
    private final World w;
    /** End of "that next reply" (the call after the named call), once a step found it. */
    private long nextReplyAt = -1;
    /** The reading of the age scenario: when it was received, and the verdict on it. */
    private long readingReceivedAt = -1;
    private boolean freshByAge;

    public GateTimingSteps(World w) {
        this.w = w;
    }

    // ------------------------------------------------------------ signals no port carries

    /** No port carries a stationary signal, so the receiver cannot see it: nothing to set up. */
    @Given("the car reports that it is stationary")
    public void carStationary() {
    }

    /** No port carries the parking brake, so the receiver cannot see it: nothing to set up. */
    @Given("the parking brake reads applied")
    public void brakeApplied() {
    }

    @Then("no Park listener was registered and no BCM status request was made")
    public void noListenerNoBcm() {
        w.settle();
        int l = w.vehicle.count("registerParkListener"), b = w.vehicle.count("requestBcmStatus");
        if (l != 0 || b != 0)
            throw new AssertionError("Park listener registered " + l + " times, BCM status requested " + b + " times");
    }

    // ------------------------------------------------------------ scripting

    @Given("the Park getter call starting at {int} ms replies {int} at exactly {int} ms, and the call starting at {int} ms replies {int} at {int} ms")
    public void twoCallsReply(int b1, int v1, int t1, int b2, int v2, int t2) {
        w.park.byStart.put((long) b1, Fakes.Reply.valueAt(v1, t1));
        w.park.byStart.put((long) b2, Fakes.Reply.valueAt(v2, t2));
        w.lastAnchor = b1;
    }

    /** Which of two events landing on the same millisecond runs first: the reply, or the gate's timeout. */
    @Given("^at (\\d+) ms the (reply|timeout) is processed first$")
    public void processedFirst(long t, String which) {
        w.park.rankAt.put(t, "reply".equals(which) ? VirtualScheduler.BEFORE : VirtualScheduler.AFTER);
    }

    @Given("^the call after it replies (\\d+|with no value|with a failed call) after 10 ms, and the call after that replies 1 within 10 ms$")
    public void callAfterItReplies(String what) {
        if (w.lastAnchor < 0) throw new IllegalStateException("no Park getter call was named before \"the call after it\"");
        w.park.chainAnchor = w.lastAnchor;
        w.park.chain.clear();
        w.park.chain.add(CommonSteps.replyAfter(what, 10));
        w.park.chain.add(Fakes.Reply.valueAfter(1, 10));
    }

    @Given("^the re-read after the resume (?:replies (\\d+) after (\\d+) ms|stays pending to the end)$")
    public void rereadAfterResume(Integer v, Integer d) {
        w.park.reread = v == null ? Fakes.Reply.never() : Fakes.Reply.valueAfter(v, d);
    }

    // ------------------------------------------------------------ reading age (ParkPoller.isFreshAt)

    @Given("a Park reading of {int} from a call started at {int} ms and received at {int} ms")
    public void aReading(int v, int begin, int received) {
        readingReceivedAt = received;
    }

    @When("its age is judged at {int} ms")
    public void judgeAge(int now) {
        freshByAge = ParkPoller.isFreshAt(readingReceivedAt, now);
    }

    @Then("^the reading is (fresh|unavailable) by age$")
    public void readingIs(String state) {
        boolean want = "fresh".equals(state);
        if (freshByAge != want)
            throw new AssertionError("reading received at " + readingReceivedAt + " ms judged "
                    + (freshByAge ? "fresh" : "unavailable"));
    }

    @Then("the reading received at {int} ms is fresh by age at {int} ms")
    public void receivedReadingFresh(int received, int now) {
        w.settle();
        boolean found = false;
        for (Fakes.Call c : w.calls()) if (c.done && c.fail == null && c.end == received) found = true;
        if (!found) throw new AssertionError("no Park reading was received at " + received + " ms; calls " + w.calls());
        if (!ParkPoller.isFreshAt(received, now))
            throw new AssertionError("reading received at " + received + " ms is not fresh by age at " + now + " ms");
    }

    // ------------------------------------------------------------ Park getter calls

    @Then("no other Park getter call started after {int} ms")
    public void noCallAfter(int t) {
        w.settle();
        for (Fakes.Call c : w.calls())
            if (c.begin > t) throw new AssertionError("Park getter call started at " + c.begin + " ms; calls " + w.calls());
    }

    @Then("consecutive Park replies were received at most 200 ms apart")
    public void repliesAtMost200Apart() {
        w.settle();
        long prev = -1;
        int n = 0;
        for (Fakes.Call c : w.calls()) {
            if (!c.done) continue;
            if (prev >= 0 && c.end - prev > 200)
                throw new AssertionError("replies at " + prev + " and " + c.end + " ms are more than 200 ms apart; calls "
                        + w.calls());
            prev = c.end;
            n++;
        }
        if (n < 2) throw new AssertionError("fewer than two Park replies were received; calls " + w.calls());
        if (w.ranUntil - prev > 200)
            throw new AssertionError("no reply after " + prev + " ms up to " + w.ranUntil + " ms; calls " + w.calls());
    }

    // ------------------------------------------------------------ video

    /** The reply landing at {@code t} shows no frame from then until the next call's reply. */
    @Then("the reply at {int} ms never permits video")
    public void replyNeverPermits(int t) {
        w.settle();
        Fakes.Call c = null;
        for (Fakes.Call x : w.calls()) if (x.done && x.end == t) c = x;
        if (c == null) throw new AssertionError("no Park getter reply at " + t + " ms; calls " + w.calls());
        w.assertNoneShownIn(t, nextCompletedAfter(c));
    }

    /** The first reply that came 200 ms or more after its call shows no frame until the next call's reply. */
    @Then("the late reply never permits video")
    public void lateReplyNeverPermits() {
        w.settle();
        for (Fakes.Call c : w.calls())
            if (c.done && c.end - c.begin >= 200) {
                w.assertNoneShownIn(c.end, nextCompletedAfter(c));
                return;
            }
        throw new AssertionError("no Park getter reply was late; calls " + w.calls());
    }

    /** When the first call made after {@code c} completed, or "never" if none did. */
    private long nextCompletedAfter(Fakes.Call c) {
        for (Fakes.Call x : w.calls()) if (x.index > c.index && x.done) return x.end;
        return Long.MAX_VALUE;
    }

    @Then("mirrored video is blocked from {int} ms until that next reply")
    public void blockedUntilNextReply(int a) {
        w.settle();
        Fakes.Call next = w.callAfter(w.callAt(w.lastAnchor));
        if (next == null || !next.done)
            throw new AssertionError("the call after the one at " + w.lastAnchor + " ms did not reply; calls " + w.calls());
        nextReplyAt = next.end;
        w.assertNoneShownIn(a, nextReplyAt);
        w.assertClearedAtIfShownBefore(a);
    }

    @Then("after that next reply mirrored video is shown again on the next frame")
    public void shownAfterNextReply() {
        w.assertNextShownAfter(requireNextReply());
    }

    @Then("^after that next reply mirrored video is still blocked until (\\d+) ms, then shown on the next frame$")
    public void stillBlockedAfterNextReply(long u) {
        w.assertNoneShownIn(requireNextReply(), u);
        w.assertNextShownAfter(u);
    }

    private long requireNextReply() {
        if (nextReplyAt < 0) throw new IllegalStateException("\"until that next reply\" must be checked first");
        return nextReplyAt;
    }

    @Then("mirrored video was never blocked after the first reply")
    public void neverBlockedAfterFirstReply() {
        w.settle();
        long first = -1;
        for (Fakes.Call c : w.calls())
            if (c.done) {
                first = c.end;
                break;
            }
        if (first < 0) throw new AssertionError("no Park reply was received; calls " + w.calls());
        w.assertAllShownAfter(first);
    }
}
