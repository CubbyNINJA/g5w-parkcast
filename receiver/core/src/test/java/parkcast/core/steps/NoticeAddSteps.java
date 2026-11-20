package parkcast.core.steps;

import io.cucumber.java.en.Given;
import parkcast.gate.harness.Fakes;
import parkcast.gate.steps.CommonSteps;

/**
 * Steps for a notice add that throws while logging works (AIR-061(b), AT-61). The gate's own steps are not on
 * the S3 glue path, so the few Given steps the scenario shares with S2 are restated here over the same doubles.
 */
public final class NoticeAddSteps {
    private final S3World w;

    public NoticeAddSteps(S3World w) {
        this.w = w;
    }

    @Given("the parking check is on, on a supported build")
    public void checkOnSupported() {
        w.g.config.missing = false;
        w.g.config.unreadable = false;
        w.g.config.holds("video.require_park=true");
        w.g.build.missing = false;
        w.g.build.unreadable = false;
        w.g.build.product = CommonSteps.SUPPORTED_BUILD;
    }

    @Given("the Park getter replies {int} within {int} ms")
    public void parkReplies(int v, int ms) {
        w.g.park.constant = Fakes.Reply.valueAfter(v, ms);
    }

    @Given("the UI double throws on add for the first logging-failure notice attempt")
    public void loggingAddThrows() {
        w.g.notices.throwKeys.add(w.startIndex() + "|" + Fakes.LOG + "|1|add");
    }

    /** The one append at {@code t} fails; a regulation change at {@code t} makes sure a record is written then. */
    @Given("one log write fails at {int} ms, and later writes succeed")
    public void oneWriteFails(int t) {
        w.g.logs.failAppendAt.add(w.startIndex() + "|" + t);
        w.g.regulation.changeAt(w.scheduler, t, "restricted", null);
    }
}
