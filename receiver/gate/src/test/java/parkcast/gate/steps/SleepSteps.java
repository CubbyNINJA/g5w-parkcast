package parkcast.gate.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import parkcast.gate.ParkPoller;
import parkcast.gate.World;
import parkcast.gate.harness.Fakes;
import parkcast.gate.harness.FrameSource;
import parkcast.gate.harness.StaticScan;
import parkcast.gate.harness.VirtualScheduler;
import parkcast.gate.port.AccStatus;

/**
 * Steps for ACC sleep and resume (09): sticky and later ACC broadcasts with any status extra, the
 * re-read after a resume, discarded replies, ACC registration failures and exit. Every Then step looks
 * only at what the doubles recorded, except the age verdict, which asks {@link ParkPoller#isFreshAt}.
 */
public class SleepSteps {
    /** The status extra of an ACC broadcast as the scenarios name it. */
    private static final String EXTRA = "(with status \\d+|with the status extra missing|with a non-integer status extra \"[^\"]*\")";
    /** A line of a Java stack trace. */
    private static final Pattern STACK_LINE =
            Pattern.compile("^\\s*(at [\\w$.<>]+\\(.*\\)|Caused by: .*|\\.\\.\\. \\d+ more)\\s*$");

    private final World w;
    /** The record found by "exactly one record contains", for "that record". */
    private Fakes.Line thatRecord;
    /** When the receiver exited, once a step made it exit. */
    private long exitedAt = -1;
    /** The static scan, once a step ran it. */
    private StaticScan scan;
    /** The reading the age steps talk about: when it was received. */
    private long countedReceivedAt = -1;

    public SleepSteps(World w) {
        this.w = w;
    }

    /** "with status 6", "with the status extra missing" or "with a non-integer status extra "on"". */
    static AccStatus extra(String text) {
        if (text.startsWith("with status ")) return AccStatus.of(Integer.parseInt(text.substring("with status ".length())));
        if ("with the status extra missing".equals(text)) return AccStatus.MISSING;
        if (text.startsWith("with a non-integer status extra")) return AccStatus.NON_INTEGER;
        throw new IllegalArgumentException("unknown ACC status extra: " + text);
    }

    // ------------------------------------------------------------ ACC scripting

    @Given("^ACC registration returns a sticky broadcast " + EXTRA + " at (\\d+) ms$")
    public void accRegStickyBroadcast(String text, long t) {
        w.acc.planSticky(extra(text), t);
    }

    @When("^an ACC broadcast " + EXTRA + " arrives at (\\d+) ms$")
    public void accBroadcastArrives(String text, long t) {
        final AccStatus st = extra(text);
        if (st.isInteger() && st.value() == 0) w.acc.changeToZeroScripted = true;
        w.scheduler.script(t, VirtualScheduler.BEFORE, () -> w.acc.deliver(st));
    }

    @Given("video is permitted after an on-time Park reading at 0 ms")
    public void permittedAtZero() {
        w.park.byStart.put(0L, Fakes.Reply.valueAfter(1, 10));
    }

    // ------------------------------------------------------------ Park getter checks

    @Then("^the Park getter was called every (\\d+) ms from (\\d+) to (\\d+) ms and at no other time$")
    public void calledEvery(long period, long from, long to) {
        w.settle();
        List<Long> want = new ArrayList<>();
        for (long t = from; t <= to; t += period) want.add(t);
        List<Long> got = new ArrayList<>();
        for (Fakes.Call c : w.calls()) got.add(c.begin);
        if (!got.equals(want)) throw new AssertionError("Park getter calls started at " + got + ", expected " + want);
    }

    // ------------------------------------------------------------ video checks

    /** Every frame (after {@code u} ms, if given) was shown, and the display was never cleared then. */
    @Then("^the parking gate never blocked video(?: after (\\d+) ms)?$")
    public void gateNeverBlocked(Long u) {
        w.settle();
        long after = u == null ? Long.MIN_VALUE : u;
        boolean any = false;
        for (FrameSource.Tick k : w.ticks()) {
            if (k.t <= after) continue;
            any = true;
            if (!w.shown(k)) throw new AssertionError("frame at " + k.t + " ms was not shown; " + w.videoSummary());
        }
        if (!any) throw new AssertionError("no frame was delivered" + (u == null ? "" : " after " + u + " ms"));
        for (long c : w.clears())
            if (c > after) throw new AssertionError("display cleared at " + c + " ms; " + w.videoSummary());
    }

    // ------------------------------------------------------------ static scan

    /** The scan reads src/main/java and build/classes/java/main of the gate module. */
    @Given("the receiver's sources and its compiled classes")
    public void sourcesAndClasses() {
    }

    @When("a static scan searches them")
    public void staticScan() {
        scan = StaticScan.run();
        if (scan.files.isEmpty()) throw new AssertionError("the static scan found no sources or classes to search");
    }

    @Then("it finds no reference to registerSystemOffResponder")
    public void noSystemOffResponder() {
        if (!scan.systemOffHits.isEmpty())
            throw new AssertionError("registerSystemOffResponder referenced in " + scan.systemOffHits);
    }

    @Then("it finds no micom power-service transaction 14")
    public void noMicomTransaction() {
        if (!scan.micomHits.isEmpty())
            throw new AssertionError("micom power-service transaction found in " + scan.micomHits);
    }

    // ------------------------------------------------------------ exit

    @When("the receiver starts, runs for {int} ms and exits")
    public void startsRunsAndExits(int ms) {
        w.exitAt(ms);
        w.runFor(ms);
        exitedAt = ms;
    }

    @Then("the ACC broadcast receiver was unregistered at exit")
    public void unregisteredAtExit() {
        if (exitedAt < 0) throw new IllegalStateException("no step made the receiver exit");
        List<long[]> regs = w.acc.currentRegistrations();
        List<long[]> uns = w.acc.currentUnregisters();
        if (regs.isEmpty()) throw new AssertionError("the ACC broadcast receiver was never registered");
        if (uns.size() != 1 || uns.get(0)[1] != exitedAt)
            throw new AssertionError("expected one unregister at " + exitedAt + " ms, got " + times(uns));
    }

    private static List<Long> times(List<long[]> xs) {
        List<Long> out = new ArrayList<>();
        for (long[] x : xs) out.add(x[1]);
        return out;
    }

    // ------------------------------------------------------------ the re-read after a resume

    @Given("^the re-read after the resume replies (\\d+) after (\\d+) ms, and the call after it replies (\\d+) within 10 ms$")
    public void rereadThenNext(int v, long d, int v2) {
        w.park.reread = Fakes.Reply.valueAfter(v, d);
        w.park.rereadNext.clear();
        w.park.rereadNext.add(Fakes.Reply.valueAfter(v2, 10));
    }

    @Given("the re-read after the resume never replies")
    public void rereadNever() {
        w.park.reread = Fakes.Reply.never();
    }

    @Then("no Park getter call started after {int} ms and before {int} ms")
    public void noCallBetween(int from, int to) {
        w.settle();
        for (Fakes.Call c : w.calls())
            if (c.begin > from && c.begin < to)
                throw new AssertionError("Park getter call started at " + c.begin + " ms, between " + from + " and " + to + " ms: " + w.calls());
    }

    @Then("the re-read was called at exactly {int} ms")
    public void rereadAt(int t) {
        w.settle();
        List<Fakes.Call> rereads = new ArrayList<>();
        for (Fakes.Call c : w.calls()) if (c.reread) rereads.add(c);
        if (rereads.isEmpty()) throw new AssertionError("no re-read was called: " + w.calls());
        if (rereads.get(0).begin != t)
            throw new AssertionError("the re-read was called at " + rereads.get(0).begin + " ms, expected " + t + " ms: " + w.calls());
    }

    // ------------------------------------------------------------ age of the counted reading

    /** The latest value reply that no "discarded" record names (a record at the reply's own time). */
    @Then("the latest counted Park reading is the one received at {int} ms")
    public void latestCounted(int t) {
        w.settle();
        long latest = -1;
        for (Fakes.Call c : w.calls()) {
            if (!c.done || c.fail != null || c.value == null) continue;
            if (discardedAt(c.end)) continue;
            if (c.end > latest) latest = c.end;
        }
        if (latest != t)
            throw new AssertionError("latest counted reading was received at " + latest + " ms, expected " + t + " ms: "
                    + w.calls() + " records " + texts(w.lines()));
        countedReceivedAt = latest;
    }

    @Then("that reading is fresh by age at {int} ms and unavailable by age at {int} ms")
    public void freshThenUnavailable(int fresh, int stale) {
        if (countedReceivedAt < 0) throw new IllegalStateException("no step named the counted reading");
        if (!ParkPoller.isFreshAt(countedReceivedAt, fresh))
            throw new AssertionError("the reading received at " + countedReceivedAt + " ms is not fresh at " + fresh + " ms");
        if (ParkPoller.isFreshAt(countedReceivedAt, stale))
            throw new AssertionError("the reading received at " + countedReceivedAt + " ms is still fresh at " + stale + " ms");
    }

    private boolean discardedAt(long t) {
        for (Fakes.Line l : w.lines()) if (l.t == t && l.text.contains("discarded")) return true;
        return false;
    }

    // ------------------------------------------------------------ records

    @Then("exactly one record contains {string}")
    public void exactlyOneRecord(String x) {
        w.settle();
        List<Fakes.Line> hits = containing(x);
        if (hits.size() != 1)
            throw new AssertionError(hits.size() + " records contain \"" + x + "\": " + texts(hits) + "; all: " + texts(w.lines()));
        thatRecord = hits.get(0);
    }

    @Then("that record contains {string} and does not contain {string}")
    public void thatRecordContains(String yes, String no) {
        if (thatRecord == null) throw new IllegalStateException("no step picked a record");
        if (!thatRecord.text.contains(yes)) throw new AssertionError("record lacks \"" + yes + "\": " + thatRecord.text);
        if (thatRecord.text.contains(no)) throw new AssertionError("record contains \"" + no + "\": " + thatRecord.text);
    }

    @Then("exactly one record contains {string}, and it names {string}")
    public void exactlyOneRecordNaming(String x, String cause) {
        exactlyOneRecord(x);
        if (!thatRecord.text.contains(cause)) throw new AssertionError("record does not name \"" + cause + "\": " + thatRecord.text);
    }

    @Then("exactly one record contains {string}, and it contains {string}")
    public void exactlyOneRecordContaining(String x, String token) {
        exactlyOneRecord(x);
        if (!thatRecord.text.contains(token)) throw new AssertionError("record lacks \"" + token + "\": " + thatRecord.text);
    }

    @Then("the record that contains {string} contains neither {string} nor {string} in any letter case")
    public void recordHasNeither(String x, String a, String b) {
        w.settle();
        List<Fakes.Line> hits = containing(x);
        if (hits.isEmpty()) throw new AssertionError("no record contains \"" + x + "\": " + texts(w.lines()));
        for (Fakes.Line l : hits) {
            String t = lower(l.text);
            if (t.contains(lower(a)) || t.contains(lower(b)))
                throw new AssertionError("record contains \"" + a + "\" or \"" + b + "\": " + l.text);
        }
    }

    @Then("no record contains {string} or {string}")
    public void noRecordContainsEither(String a, String b) {
        w.settle();
        for (Fakes.Line l : w.lines())
            if (l.text.contains(a) || l.text.contains(b))
                throw new AssertionError("record contains \"" + a + "\" or \"" + b + "\": " + l.text);
    }

    @Then("no record contains a stack trace")
    public void noStackTrace() {
        w.settle();
        for (Fakes.Line l : w.lines())
            for (String part : l.text.split("\\r?\\n"))
                if (isStackLine(part)) throw new AssertionError("record contains a stack trace line: " + l.text);
    }

    private List<Fakes.Line> containing(String x) {
        List<Fakes.Line> out = new ArrayList<>();
        for (Fakes.Line l : w.lines()) if (l.text.contains(x)) out.add(l);
        return out;
    }

    private static List<String> texts(List<Fakes.Line> ls) {
        List<String> out = new ArrayList<>();
        for (Fakes.Line l : ls) out.add(l.t + ":" + l.text);
        return out;
    }

    // ------------------------------------------------------------ ACC registration

    @Given("ACC registration throws a SyntheticFailureError with the message {string} at {int} ms")
    public void accRegThrowsSynthetic(String message, int t) {
        w.acc.planThrow("SyntheticFailureError: " + message, t);
    }

    @Then("ACC registration was attempted only once")
    public void accRegisteredOnce() {
        w.settle();
        List<long[]> regs = w.acc.currentRegistrations();
        if (regs.size() != 1) throw new AssertionError("ACC registration attempted " + regs.size() + " times at " + times(regs));
    }

    // ------------------------------------------------------------ notices

    @Then("no registration-failure notice was shown at any time")
    public void noRegNotice() {
        w.settle();
        for (Fakes.Op o : w.notices.ops)
            if ("add".equals(o.op) && Fakes.REG.equals(o.kind))
                throw new AssertionError("registration-failure notice added at " + o.t + " ms");
        for (Fakes.Window win : w.notices.windows)
            if (Fakes.REG.equals(win.kind())) throw new AssertionError("registration-failure notice window was created");
    }

    @Then("the synthetic notice output is empty")
    public void noticeOutputEmpty() {
        w.settle();
        if (!w.notices.ops.isEmpty() || !w.notices.views.isEmpty() || !w.notices.windows.isEmpty())
            throw new AssertionError("notice output: ops " + w.notices.ops.size() + ", views " + w.notices.views.size()
                    + ", windows " + w.notices.windows.size());
    }

    // ------------------------------------------------------------ helpers kept for later steps

    static boolean isStackLine(String text) {
        return STACK_LINE.matcher(text).matches();
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
