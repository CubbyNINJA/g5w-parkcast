package parkcast.gate.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import parkcast.gate.World;
import parkcast.gate.harness.CoverHarnessA;
import parkcast.gate.harness.Fakes;

/**
 * Covering (11, AIR-041, AT-41, OD-117 to OD-130), scenarios l.496 to l.708: focus changes, REVERSE_ON and
 * REVERSE_OFF, and the removal calls and held attempts they cause. Steps assert only on the notice double's
 * ops and windows and on the log records of the current start.
 */
public class CoverStepsA {
    private static final String LIST = "(\\d+(?:, \\d+)* and \\d+|\\d+ ms and \\d+)";

    private final World w;
    private final LogSteps logSteps;
    private final GateMiscSteps misc;
    private final CoverHarnessA cover;

    /** The window kind "that window" and "it" refer to; the logging-failure window unless a step named another. */
    private String thatKind = Fakes.LOG;
    /** Set by "exactly one removal call per notice window ... each at N ms" for "no other removal call was made". */
    private long perWindowAt = -1;
    /** Set by "exactly one removal call was made at N ms" for "no further removal call was made". */
    private long namedRemovalAt = -1;
    /** Setup to replay in a second run, for the REVERSE_OFF comparison. */
    private final List<Consumer<Replay>> replay = new ArrayList<>();

    public CoverStepsA(World w, LogSteps logSteps, GateMiscSteps misc, CoverHarnessA cover) {
        this.w = w;
        this.logSteps = logSteps;
        this.misc = misc;
        this.cover = cover;
    }

    // ------------------------------------------------------------ focus and broadcast doubles

    @Given("the receiver's window never gains focus")
    public void neverGainsFocus() {
        w.focusAt = -1;
    }

    @Given("no focus gain is delivered before that registration call returns")
    public void noGainBeforeRegistration() {
        w.focusAt = -1;
    }

    @Given("^the receiver's window (loses|gains) focus at (\\d+) ms$")
    public void focusChange(String how, long t) {
        cover.focus(t, "gains".equals(how));
    }

    @When("^REVERSE_ON is delivered(?: again)? at (\\d+) ms(?: with (?:no )?extras)?$")
    public void reverseOn(long t) {
        cover.reverseOn(t);
        replay.add(r -> r.cover.reverseOn(t));
    }

    @Given("REVERSE_ON is delivered at {int} ms and a log write fails at {int} ms")
    public void reverseOnThenWriteFails(int on, int fail) {
        reverseOn(on);
        writeFails(fail);
    }

    @When("^REVERSE_OFF is delivered at (\\d+) ms with (?:no )?extras$")
    public void reverseOff(long t) {
        cover.reverseOff(t);
    }

    @Given("the REVERSE_ON registration returns an intent at {int} ms")
    public void registrationReturnsIntent(int t) {
        if (t != 0) throw new IllegalArgumentException("the REVERSE_ON registration is made at 0 ms, not " + t);
        w.reverse.returnsIntent = true;
    }

    // ------------------------------------------------------------ UI double faults

    @Given("^the UI double throws on every removal call for the (registration|logging)-failure window$")
    public void throwsOnEveryRemoval(String which) {
        thatKind = kind(which);
        w.notices.removeThrows.put(w.scheduler.startIndex + "|" + thatKind, 1_000_000);
    }

    @Given("the UI double throws on the removal calls at {int} ms and {int} ms and lets the one at {int} ms return")
    public void throwsOnRemovalsAt(int a, int b, int ok) {
        w.notices.removeThrowAt.add(w.scheduler.startIndex + "|*|" + a);
        w.notices.removeThrowAt.add(w.scheduler.startIndex + "|*|" + b);
        w.notices.removeThrowAt.remove(w.scheduler.startIndex + "|*|" + ok);
    }

    // ------------------------------------------------------------ add calls and views

    @Then("no notice add call was made")
    public void noAddCall() {
        w.settle();
        List<Fakes.Op> adds = w.notices.ops("add", null, w.scheduler.startIndex);
        if (!adds.isEmpty()) throw new AssertionError("notice add calls were made at " + times(adds));
    }

    @Then("^no (registration|logging)-failure notice add call was made after (\\d+) ms$")
    public void noAddAfter(String which, long after) {
        w.settle();
        for (Fakes.Op o : w.notices.ops("add", kind(which), w.scheduler.startIndex))
            if (o.t > after) throw new AssertionError("a " + which + "-failure notice add call was made at " + o.t + " ms");
    }

    @Then("no view was created, measured or added from {int} ms until {int} ms")
    public void noViewWork(int from, int until) {
        w.settle();
        for (Fakes.Op o : ops(null))
            if (o.t >= from && o.t < until && (o.op.equals("create") || o.op.equals("measure") || o.op.equals("add")))
                throw new AssertionError("a " + o.op + " call for the " + o.kind + " notice was made at " + o.t + " ms");
    }

    @Then("it was removed at exactly {int} ms")
    public void itWasRemovedAt(int t) {
        w.settle();
        Fakes.Window last = null;
        for (Fakes.Window x : w.notices.windows) if (x.start == w.scheduler.startIndex) last = x;
        if (last == null) throw new AssertionError("no notice window was added");
        if (last.removedAt != t)
            throw new AssertionError("the " + last.kind() + " window added at " + last.addedAt + " ms was removed at "
                    + last.removedAt + " ms (removal calls " + last.removeCalls + "), expected " + t + " ms");
    }

    @Then("exit makes no held attempt")
    public void exitMakesNoHeldAttempt() {
        w.settle();
        long exit = misc.exitedAt();
        for (Fakes.Op o : ops(null))
            if (o.t >= exit && !o.op.equals("remove"))
                throw new AssertionError("a " + o.op + " call for the " + o.kind + " notice was made at " + o.t
                        + " ms, at or after exit at " + exit + " ms");
    }

    // ------------------------------------------------------------ removal calls

    @Then("no removal call was made")
    public void noRemovalCall() {
        w.settle();
        List<Fakes.Op> rem = ops("remove");
        if (!rem.isEmpty()) throw new AssertionError("removal calls were made at " + times(rem));
    }

    @Then("^exactly one removal call for the (registration|logging)-failure window was made, at (\\d+) ms, inside the (focus|broadcast) callback$")
    public void oneRemovalInside(String which, long t, String callback) {
        w.settle();
        thatKind = kind(which);
        int found = 0, index = -1;
        for (int i = 0; i < w.notices.ops.size(); i++) {
            Fakes.Op o = w.notices.ops.get(i);
            if (o.start == w.scheduler.startIndex && o.op.equals("remove") && o.kind.equals(thatKind) && o.t == t) {
                found++;
                index = i;
            }
        }
        if (found != 1)
            throw new AssertionError(found + " removal calls for the " + which + "-failure window were made at " + t
                    + " ms; all: " + times(ops("remove", thatKind)));
        if (cover.enclosing(index, t, "focus".equals(callback)) == null)
            throw new AssertionError("the removal call at " + t + " ms was not made inside a " + callback
                    + " callback; deliveries " + cover.deliveries);
    }

    @Then("no other removal call for it was made")
    public void noOtherForIt() {
        w.settle();
        List<Fakes.Op> rem = ops("remove", thatKind);
        if (rem.size() != 1) throw new AssertionError("removal calls for that window were made at " + times(rem));
    }

    @Then("exactly one removal call per notice window was made, each at {int} ms")
    public void onePerWindow(int t) {
        w.settle();
        perWindowAt = t;
        List<Fakes.Window> wins = new ArrayList<>();
        for (Fakes.Window x : w.notices.windows) if (x.start == w.scheduler.startIndex) wins.add(x);
        if (wins.size() < 2) throw new AssertionError("expected both notice windows, found " + wins.size());
        for (Fakes.Window x : wins) {
            int n = 0;
            for (long c : x.removeCalls) if (c == t) n++;
            if (n != 1)
                throw new AssertionError("the " + x.kind() + " window got " + n + " removal calls at " + t
                        + " ms; its calls " + x.removeCalls);
        }
    }

    @Then("no other removal call was made")
    public void noOtherRemoval() {
        if (perWindowAt < 0) throw new IllegalStateException("no step named the removal calls that were made");
        w.settle();
        int windows = 0;
        for (Fakes.Window x : w.notices.windows) if (x.start == w.scheduler.startIndex) windows++;
        List<Fakes.Op> rem = ops("remove");
        for (Fakes.Op o : rem)
            if (o.t != perWindowAt) throw new AssertionError("a removal call was made at " + o.t + " ms; all " + times(rem));
        if (rem.size() != windows) throw new AssertionError("removal calls were made at " + times(rem));
    }

    @Then("^removal calls for that window were made only at " + LIST + " ms$")
    public void removalsOnlyAt(String list) {
        w.settle();
        List<Long> want = CommonSteps.numbers(list);
        List<Long> got = times(ops("remove", thatKind));
        if (!got.equals(want))
            throw new AssertionError("removal calls for the " + thatKind + " window were made at " + got + ", expected " + want);
    }

    @Then("^removal calls for that window after (\\d+) ms were made only at " + LIST + " ms$")
    public void removalsAfterOnlyAt(long after, String list) {
        w.settle();
        List<Long> want = CommonSteps.numbers(list);
        List<Long> got = new ArrayList<>();
        for (long t : times(ops("remove", thatKind))) if (t > after) got.add(t);
        if (!got.equals(want))
            throw new AssertionError("removal calls for the " + thatKind + " window after " + after + " ms were made at "
                    + got + ", expected " + want);
    }

    @Then("the last removal call while covered was made at {int} ms")
    public void lastWhileCovered(int t) {
        w.settle();
        CoverHarnessA.Delivery loss = cover.first("loss", 0);
        if (loss == null) throw new IllegalStateException("no step made the window lose focus");
        CoverHarnessA.Delivery gain = cover.first("gain", loss.t);
        int end = gain == null || gain.opsAfter < 0 ? w.notices.ops.size() : gain.opsAfter;
        long last = -1;
        for (int i = 0; i < end; i++) {
            Fakes.Op o = w.notices.ops.get(i);
            if (o.start == w.scheduler.startIndex && o.op.equals("remove") && o.t >= loss.t) last = o.t;
        }
        if (last != t)
            throw new AssertionError("the last removal call while covered was made at " + last + " ms, expected " + t
                    + " ms; all " + times(ops("remove")));
    }

    @Then("no removal call was made after {int} ms and before {int} ms")
    public void noRemovalBetween(int a, int b) {
        w.settle();
        for (Fakes.Op o : ops("remove"))
            if (o.t > a && o.t < b)
                throw new AssertionError("a removal call was made at " + o.t + " ms; all " + times(ops("remove")));
    }

    @Then("exactly one removal call was made at {int} ms")
    public void oneRemovalAt(int t) {
        w.settle();
        namedRemovalAt = t;
        int n = 0;
        for (Fakes.Op o : ops("remove")) if (o.t == t) n++;
        if (n != 1) throw new AssertionError(n + " removal calls were made at " + t + " ms; all " + times(ops("remove")));
    }

    @Then("no further removal call was made")
    public void noFurtherRemoval() {
        if (namedRemovalAt < 0) throw new IllegalStateException("no step named the last removal call");
        w.settle();
        for (Fakes.Op o : ops("remove"))
            if (o.t > namedRemovalAt)
                throw new AssertionError("a removal call was made at " + o.t + " ms, after " + namedRemovalAt + " ms (ran until "
                        + w.ranUntil + " ms)");
    }

    @Then("the only removal call was made at {int} ms")
    public void onlyRemovalAt(int t) {
        w.settle();
        List<Long> got = times(ops("remove"));
        if (got.size() != 1 || got.get(0) != t) throw new AssertionError("removal calls were made at " + got + ", expected only " + t);
    }

    // ------------------------------------------------------------ log records

    @Then("exactly one record containing {string} was written after {int} ms")
    public void oneRecordAfter(String text, int after) {
        w.settle();
        List<Fakes.Line> got = new ArrayList<>();
        for (Fakes.Line l : w.lines()) if (l.t > after && l.text.contains(text)) got.add(l);
        if (got.size() != 1) throw new AssertionError(got.size() + " records containing \"" + text + "\" after " + after + " ms: " + got);
    }

    @Then("exactly one record containing {string} was written, at {int} ms")
    public void oneRecordAt(String text, int t) {
        w.settle();
        List<Fakes.Line> got = new ArrayList<>();
        for (Fakes.Line l : w.lines()) if (l.text.contains(text)) got.add(l);
        if (got.size() != 1 || got.get(0).t != t)
            throw new AssertionError("records containing \"" + text + "\": " + got + ", expected exactly one at " + t + " ms");
    }

    @Then("no record containing {string} was written before {int} ms")
    public void noRecordBefore(String text, int before) {
        w.settle();
        for (Fakes.Line l : w.lines())
            if (l.t < before && l.text.contains(text)) throw new AssertionError("record written before " + before + " ms: " + l);
    }

    // ------------------------------------------------------------ REVERSE_OFF against a run without it

    @Then("the covered state, removal calls and held attempts match the same run without REVERSE_OFF")
    public void matchesRunWithoutReverseOff() {
        w.settle();
        World o = new World();
        o.config.text = w.config.text;
        o.config.missing = w.config.missing;
        o.config.unreadable = w.config.unreadable;
        o.build.product = w.build.product;
        o.build.missing = w.build.missing;
        o.build.unreadable = w.build.unreadable;
        o.focusAt = w.focusAt;
        o.focusRank = w.focusRank;
        Replay r = new Replay(o);
        for (long t : logSteps.writeFaultTimes()) r.log.writeFailsAt(t);
        for (Consumer<Replay> c : replay) c.accept(r);
        o.runFor(w.ranUntil);
        String with = trace(w), without = trace(o);
        if (!with.equals(without))
            throw new AssertionError("with REVERSE_OFF:\n" + with + "\nwithout it:\n" + without);
    }

    /** Notice ops (time, op, kind) and "notice cover" records (time, text) of the current start. */
    private static String trace(World x) {
        StringBuilder b = new StringBuilder();
        for (Fakes.Op op : x.notices.ops(null, null, x.scheduler.startIndex))
            b.append(op.t).append(' ').append(op.op).append(' ').append(op.kind).append('\n');
        for (Fakes.Line l : x.lines())
            if (l.text.contains("notice cover")) b.append(l.t).append(" log ").append(l.text).append('\n');
        return b.toString();
    }

    /** The doubles of the second run. */
    private static final class Replay {
        final World w;
        final LogSteps log;
        final CoverHarnessA cover;

        Replay(World w) {
            this.w = w;
            this.log = new LogSteps(w, new CommonSteps(w));
            this.cover = new CoverHarnessA(w);
        }
    }

    // ------------------------------------------------------------ helpers

    private void writeFails(long t) {
        logSteps.writeFailsAt(t);
    }

    private List<Fakes.Op> ops(String op) {
        return w.notices.ops(op, null, w.scheduler.startIndex);
    }

    private List<Fakes.Op> ops(String op, String kind) {
        return w.notices.ops(op, kind, w.scheduler.startIndex);
    }

    private static List<Long> times(List<Fakes.Op> ops) {
        List<Long> out = new ArrayList<>();
        for (Fakes.Op o : ops) out.add(o.t);
        return out;
    }

    private static String kind(String which) {
        return "registration".equals(which) ? Fakes.REG : Fakes.LOG;
    }
}
