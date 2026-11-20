package parkcast.core.steps;

import static parkcast.core.steps.Check.assertEquals;
import static parkcast.core.steps.Check.assertTrue;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.ArrayList;
import java.util.List;
import parkcast.core.doubles.Trace;
import parkcast.gate.harness.Fakes;
import parkcast.gate.harness.VirtualScheduler;
import parkcast.gate.port.Frame;

/**
 * Steps for notices held while covered (AIR-061 with AIR-069) and for a missing or unwritable log directory
 * (AIR-062). In the AIR-062 failure scenarios a frame source delivers one decoded frame every millisecond to
 * the runtime, so "mirrored video stays permitted" is read from what reached the screen, not from the log,
 * which cannot be written there.
 */
public final class NoticeLogSteps {
    /** The receiver's log directory (AIR-062). */
    static final String LOG_DIR = "/data/nemesis/logs/airplay-receiver/";

    private final S3World w;
    /** Frames delivered to the runtime, as {start, t, id}. */
    private final List<long[]> ticks = new ArrayList<>();
    private long nextId = 1;
    private boolean pumping;

    public NoticeLogSteps(S3World w) {
        this.w = w;
    }

    // ------------------------------------------------------------------ AIR-061 / AIR-069: held while covered

    @Given("the receiver's window does not gain focus until {int} ms")
    public void focusFrom(int t) {
        if (t <= 0) throw new IllegalArgumentException("focus is gained after 0 ms here, not at " + t + " ms");
        w.focusAt = t;
    }

    @Then("no notice create or add call was made before {int} ms")
    public void noNoticeBefore(int t) {
        w.settle();
        List<Fakes.Op> early = new ArrayList<>();
        for (Fakes.Op o : w.g.notices.ops(null, null, w.startIndex()))
            if ((o.op.equals("create") || o.op.equals("add")) && o.t < t) early.add(o);
        List<String> shown = new ArrayList<>();
        for (Fakes.Op o : early) shown.add(o.kind + " " + o.op + "@" + o.t);
        assertTrue(early.isEmpty(), "notice calls before " + t + " ms: " + shown);
    }

    // ------------------------------------------------------------------ AIR-062: the log directory

    /** In the AIR-062 failure scenarios, frames arrive every millisecond from 0 ms. */
    @Before("@AIR-062 and @failure")
    public void pumpFrames() {
        pumping = true;
        tick(0);
    }

    private void tick(final long t) {
        final int start = w.scheduler.startIndex;
        w.scheduler.script(t, VirtualScheduler.FRAME, () -> {
            if (w.scheduler.startIndex != start) return;
            tick(t + 1);
            if (w.runtime == null) return;
            Frame f = new Frame(nextId++, t);
            ticks.add(new long[] {start, t, f.id});
            w.runtime.onFrame(f);
        });
    }

    @Given("the log directory \\/data\\/nemesis\\/logs\\/airplay-receiver\\/ {}")
    public void logDir(String state) {
        switch (state) {
            case "does not exist":
                // No directory: the current file can never be created, so every append fails, for good.
                w.g.logs.down.add(new long[] {w.startIndex(), 0, -1});
                break;
            case "exists but every write throws":
                w.g.logs.failOn = "";
                break;
            default:
                throw new IllegalArgumentException("no log directory state \"" + state + "\"");
        }
    }

    @Then("exactly one logging-failure notice add call was made")
    public void oneLoggingNotice() {
        w.settle();
        List<Fakes.Op> adds = w.g.notices.ops("add", Fakes.LOG, w.startIndex());
        assertEquals(1, adds.size(), "logging-failure notice add calls: " + adds.size());
    }

    @Then("the fake file system recorded no mkdir, chmod or chown on any path outside \\/data\\/nemesis\\/logs\\/airplay-receiver\\/")
    public void nothingMadeOutside() {
        w.settle();
        List<String> bad = new ArrayList<>();
        for (Trace.Entry e : w.trace.entries) {
            if (!e.cause.equals("fs")) continue;
            if (!e.name.equals("mkdir") && !e.name.equals("chmod") && !e.name.equals("chown")) continue;
            String path = e.details.split(" ", 2)[0];
            if (!path.startsWith(LOG_DIR)) bad.add(e.toString());
        }
        assertTrue(bad.isEmpty(), "made or changed outside " + LOG_DIR + ": " + bad);
    }

    @Then("mirrored video stays permitted from {int} ms throughout")
    public void permittedThroughout(int from) {
        if (!pumping) throw new IllegalStateException("no frames are delivered in this scenario");
        w.settle();
        int start = w.startIndex();
        List<Long> early = new ArrayList<>(), missing = new ArrayList<>();
        int late = 0;
        for (long[] k : ticks) {
            if (k[0] != start) continue;
            boolean shown = w.g.screen.shownIds.contains(k[2]);
            if (k[1] < from && shown) early.add(k[1]);
            if (k[1] >= from) {
                late++;
                if (!shown) missing.add(k[1]);
            }
        }
        assertTrue(late > 0, "no frame was delivered from " + from + " ms");
        assertTrue(early.isEmpty(), "frames shown before " + from + " ms, at " + early);
        assertTrue(missing.isEmpty(), "frames not shown from " + from + " ms, at " + head(missing));
        List<Long> clears = new ArrayList<>();
        for (Long c : w.g.screen.clears) if (c >= from) clears.add(c);
        assertTrue(clears.isEmpty(), "the screen was cleared at " + head(clears));
    }

    private static List<Long> head(List<Long> l) {
        return l.size() <= 10 ? l : l.subList(0, 10);
    }
}
