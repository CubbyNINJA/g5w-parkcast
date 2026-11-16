package parkcast.gate.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import parkcast.gate.World;
import parkcast.gate.harness.Fakes;

/**
 * Steps of feature 10: the parking flag in receiver.conf, the install folder, and what each start records.
 *
 * <p>The install folder is modelled with the doubles that outlive a restart. The {@link Fakes.Config} double
 * is the installed {@code /data/nemesis/apps/<id>/config/receiver.conf}: an edit, an update, a reinstall or
 * a failed update changes only that double, because the gate reads the file only through the port and the
 * NEMESIS package that writes it comes in a later slice. The {@link Fakes.Logs} double is the log folder.
 * A new process is {@link World#restart()}, which keeps both.
 *
 * <p>Records are matched on their text without the {@code t=N } prefix GateLog writes. The start record is
 * the start's one record that begins with {@code start }; the startup flag record begins with {@code flag };
 * a gate-change record begins with {@code gate }. A token is a {@code key=value} pair in a record.
 */
public class ConfigSteps {
    private static final Pattern PREFIX = Pattern.compile("^t=\\d+ ");
    private static final String OFF = "parking check off";

    private final World w;
    private final CommonSteps common;

    /** The value written twice by "holds the line ... twice". */
    private String twiceLine;
    /** The text of the decoy receiver.conf outside the install folder (never reachable through the port). */
    private String decoy;
    /** The record a "that record" / "it" step refers to. */
    private Fakes.Line that;

    public ConfigSteps(World w) {
        this.w = w;
        this.common = new CommonSteps(w);
    }

    // ------------------------------------------------------------ record helpers

    /** The record's text without the time prefix. */
    static String body(Fakes.Line l) {
        return PREFIX.matcher(l.text).replaceFirst("");
    }

    /** True if the record holds {@code key=value} as a whole token. */
    static boolean hasToken(Fakes.Line l, String key, String value) {
        return Pattern.compile("(^|\\s)" + Pattern.quote(key + "=" + value) + "(\\s|$)").matcher(body(l)).find();
    }

    /** The records of the current start whose text, without the prefix, starts with {@code head}. */
    private List<Fakes.Line> recordsStarting(String head) {
        List<Fakes.Line> out = new ArrayList<>();
        for (Fakes.Line l : w.lines()) if (body(l).startsWith(head)) out.add(l);
        return out;
    }

    /** The current start's one start record. */
    private Fakes.Line startRecord() {
        w.settle();
        return startRecordOf(w.scheduler.startIndex);
    }

    private Fakes.Line startRecordOf(int start) {
        List<Fakes.Line> found = new ArrayList<>();
        for (Fakes.Line l : w.logs.lines) if (l.start == start && body(l).startsWith("start ")) found.add(l);
        if (found.size() != 1)
            throw new AssertionError("expected exactly one start record in start " + start + ", found " + found
                    + "; records: " + w.logs.lines);
        return found.get(0);
    }

    private static void requireToken(Fakes.Line l, String key, String value) {
        if (!hasToken(l, key, value))
            throw new AssertionError("record does not hold " + key + "=" + value + ": " + l);
    }

    private static void requireContains(Fakes.Line l, String x) {
        if (!l.text.contains(x)) throw new AssertionError("record does not contain \"" + x + "\": " + l);
    }

    // ------------------------------------------------------------ receiver.conf contents

    @Given("the installed receiver.conf holds the line {string} twice")
    public void holdsLineTwice(String line) {
        twiceLine = line;
        common.confHolds(line + " ; " + line);
    }

    @Given("^the (first|second) occurrence has two spaces before it and two spaces after it$")
    public void occurrencePadded(String which) {
        if (twiceLine == null) throw new IllegalStateException("no line was written twice");
        String padded = "  " + twiceLine + "  ";
        common.confHolds("first".equals(which) ? padded + " ; " + twiceLine : twiceLine + " ; " + padded);
    }

    @Given("the package is installed in \\/data\\/nemesis\\/apps\\/<id>\\/ with config\\/receiver.conf holding {string}, and a decoy package\\/config\\/receiver.conf holding {string}")
    public void installedWithDecoy(String installed, String decoyText) {
        common.confHolds(installed);
        decoy = decoyText;
    }

    // ------------------------------------------------------------ earlier runs, restarts and installs

    /** Runs one whole earlier process with the check in that state, then makes the next start a new process. */
    @Given("^the previous run had the parking check (on|off)$")
    public void previousRun(String state) {
        common.confHolds("on".equals(state) ? "video.require_park=true" : "video.require_park=false");
        w.runFor(w.settleMs);
        w.restart();
    }

    @Given("the previous run had the parking check on, and the installed receiver.conf now holds {string}")
    public void previousRunOnNowHolds(String text) {
        previousRun("on");
        common.confHolds(text);
    }

    @Given("the receiver is running")
    public void receiverIsRunning() {
        w.runFor(w.settleMs);
    }

    @When("the installed receiver.conf is changed to hold {string}")
    public void confChanged(String text) {
        common.confHolds(text);
    }

    @Then("the parking check stays on while the receiver keeps running")
    public void staysOnWhileRunning() {
        w.runFor(w.ranUntil + 3000);
        Fakes.Line start = startRecord();
        requireToken(start, "check", "on");
        for (Fakes.Line l : w.lines()) {
            if (l.text.contains(OFF)) throw new AssertionError("a record of the running start says \"" + OFF + "\": " + l);
            if (body(l).startsWith("flag ") && !hasToken(l, "new", "on"))
                throw new AssertionError("the flag changed while the receiver kept running: " + l);
        }
    }

    @Then("after the receiver restarts the parking check is off")
    public void afterRestartOff() {
        w.restart();
        w.start();
        common.parkingCheckIs("off");
    }

    @Given("the installed receiver.conf was edited to hold {string}")
    public void confEdited(String text) {
        common.confHolds(text);
    }

    /** An update and a reinstall both put the shipped file in place of the installed one (AIR-035). */
    @When("^the package is (updated|reinstalled) successfully, shipping \"([^\"]*)\"$")
    public void packageInstalled(String action, String shipped) {
        if (w.started) w.restart();
        common.confHolds(shipped);
        w.confIsOutcome = true;
    }

    /** The install stopped before the shipped file was in place, leaving the installed one in that state. */
    @When("^an update fails and leaves receiver.conf (missing|unreadable)$")
    public void updateFails(String state) {
        if (w.started) w.restart();
        if ("missing".equals(state)) common.confMissing();
        else common.confUnreadable();
        w.confIsOutcome = true;
    }

    @Then("^at the next start the parking check is (on|off)$")
    public void atNextStart(String state) {
        if (w.started) w.restart();
        w.start();
        common.parkingCheckIs(state);
    }

    // ------------------------------------------------------------ on screen

    @When("the receiver starts twice")
    public void startsTwice() {
        w.runFor(w.settleMs);
        w.restart();
        w.runFor(w.settleMs);
    }

    /** No notice of any start mentions Park or the check, and no registration-failure notice was attempted. */
    @Then("no on-screen message or indicator mentioned the parking check")
    public void nothingOnScreen() {
        w.settle();
        for (Fakes.Op o : w.notices.ops) {
            String t = o.text == null ? "" : o.text.toLowerCase();
            if (t.contains("park") || t.contains("check"))
                throw new AssertionError("a notice mentioned the parking check: " + o.op + " \"" + o.text + "\"");
            if ("add".equals(o.op) && Fakes.REG.equals(o.kind))
                throw new AssertionError("a registration-failure notice was attempted at " + o.t + " ms in start " + o.start);
        }
        for (Fakes.View v : w.notices.views) {
            String t = v.text == null ? "" : v.text.toLowerCase();
            if (t.contains("park") || t.contains("check"))
                throw new AssertionError("a notice view mentioned the parking check: \"" + v.text + "\"");
        }
    }

    @Then("each start's record gives the parking check as off, read from the file")
    public void eachStartOffFromFile() {
        w.settle();
        if (w.scheduler.startIndex < 2) throw new AssertionError("fewer than two starts ran");
        for (int i = 1; i <= w.scheduler.startIndex; i++) {
            Fakes.Line s = startRecordOf(i);
            requireToken(s, "check", "off");
            requireContains(s, "reason=read from the file");
        }
    }

    /** The AIR-025(a) fallback causes; a fallback start names one of them in its reason= field. */
    private static final String[] FALLBACK_REASONS = {"reason=the missing file", "reason=the unreadable file",
            "reason=the missing key", "reason=the conflicting duplicate key", "reason=the unrecognised value"};

    /**
     * Where the value came from, by the literal reason= phrase (AIR-025 rationale: tests match the reason=
     * phrases): "the file" is {@code reason=read from the file}, "the fallback" is one of the fallback causes.
     */
    private static void requireSource(Fakes.Line s, String source) {
        if ("the file".equals(source)) {
            requireContains(s, "reason=read from the file");
            return;
        }
        for (String r : FALLBACK_REASONS) if (s.text.contains(r)) return;
        throw new AssertionError("start record names no fallback cause: " + s);
    }

    // ------------------------------------------------------------ the start record

    @Then("^its start record gives the parking check as (on|off), from (the file|the fallback), with the initial gate state as (blocked|permitted)$")
    public void startRecordGives(String check, String source, String initial) {
        Fakes.Line s = startRecord();
        requireToken(s, "check", check);
        requireSource(s, source);
        requireToken(s, "gate", initial);
    }

    @Then("the start record contains {string}, whatever the order of its fields")
    public void startRecordContainsAnyOrder(String x) {
        startRecordContains(x);
    }

    @Then("the start record contains {string}")
    public void startRecordContains(String x) {
        requireContains(startRecord(), x);
    }

    @Then("the start record was written")
    public void startRecordWritten() {
        startRecord();
    }

    // ------------------------------------------------------------ "parking check off" and record order

    @Then("the first record of the start contains {string}")
    public void firstRecordContains(String x) {
        w.settle();
        List<Fakes.Line> ls = w.lines();
        if (ls.isEmpty()) throw new AssertionError("the start wrote no record");
        that = ls.get(0);
        requireContains(that, x);
    }

    @Then("that record is not the start record")
    public void thatIsNotStartRecord() {
        if (that == startRecord()) throw new AssertionError("it is the start record: " + that);
    }

    @Then("the start record comes after it and contains {string}")
    public void startRecordAfterAndContains(String x) {
        Fakes.Line s = startRecord();
        List<Fakes.Line> all = w.logs.lines;
        if (all.indexOf(s) <= all.indexOf(that))
            throw new AssertionError("the start record " + s + " does not come after " + that);
        requireContains(s, x);
    }

    /** Lines of an earlier start, written straight into the log file, then a new process. */
    @Given("the log file holds the records of an earlier synthetic start")
    public void earlierSyntheticStart() {
        try {
            for (int i = 1; i <= 3; i++)
                w.logs.append("receiver.log", ("t=" + (i * 10) + " earlier synthetic record " + i + " of 3\n").getBytes("UTF-8"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        earlier = new ArrayList<>();
        for (Fakes.Line l : w.logs.lines) earlier.add(l.toString());
        w.restart();
    }

    /** What the log held before the new start, as text. */
    private List<String> earlier;

    @Then("the earlier start's records come first, unchanged")
    public void earlierFirst() {
        w.settle();
        if (w.logs.lines.size() < earlier.size()) throw new AssertionError("records were lost: " + w.logs.lines);
        for (int i = 0; i < earlier.size(); i++)
            if (!earlier.get(i).equals(w.logs.lines.get(i).toString()))
                throw new AssertionError("record " + i + " changed: was " + earlier.get(i) + ", now " + w.logs.lines.get(i));
    }

    @Then("the next record contains {string}")
    public void nextRecordContains(String x) {
        if (w.logs.lines.size() <= earlier.size()) throw new AssertionError("no record follows the earlier start's");
        that = w.logs.lines.get(earlier.size());
        if (that.start != w.scheduler.startIndex) throw new AssertionError("the next record is not the new start's: " + that);
        requireContains(that, x);
    }

    @Then("it precedes the new start's start record, its startup flag record and any ACC registration record")
    public void precedesStartFlagAcc() {
        List<Fakes.Line> all = w.logs.lines;
        int at = all.indexOf(that);
        List<Fakes.Line> later = new ArrayList<>();
        later.add(startRecord());
        List<Fakes.Line> flags = recordsStarting("flag ");
        if (flags.isEmpty()) throw new AssertionError("the new start wrote no startup flag record: " + w.lines());
        later.add(flags.get(0));
        for (Fakes.Line l : w.lines()) if (l.text.contains("ACC registration")) later.add(l);
        for (Fakes.Line l : later)
            if (all.indexOf(l) <= at) throw new AssertionError(that + " does not precede " + l);
    }

    @Then("no record of the new start contains {string}")
    public void noRecordOfNewStart(String x) {
        w.settle();
        for (Fakes.Line l : w.lines()) if (l.text.contains(x)) throw new AssertionError("a record of the new start contains \"" + x + "\": " + l);
    }

    // ------------------------------------------------------------ a failed "parking check off" write

    @Given("writing the {string} record fails, and every later write succeeds")
    public void writeFailsOnce(String x) {
        w.logs.failOnceOn = x;
    }

    @Then("exactly one logging-failure notice was shown")
    public void oneLoggingNotice() {
        w.settle();
        List<Fakes.Window> ws = w.notices.windows(Fakes.LOG, w.scheduler.startIndex);
        if (ws.size() != 1) throw new AssertionError(ws.size() + " logging-failure notices shown; ops " + w.notices.ops);
    }

    @Then("no registration-failure notice was shown")
    public void noRegNotice() {
        w.settle();
        if (!w.notices.ops("add", Fakes.REG, w.scheduler.startIndex).isEmpty())
            throw new AssertionError("a registration-failure notice was added: " + w.notices.ops);
    }

    // ------------------------------------------------------------ gate-change records

    private List<Fakes.Line> gateRecords() {
        w.settle();
        return recordsStarting("gate ");
    }

    @Then("the log has exactly three gate-change records, in this order:")
    public void threeGateRecords(io.cucumber.datatable.DataTable table) {
        List<java.util.Map<String, String>> rows = table.asMaps();
        List<Fakes.Line> gates = gateRecords();
        if (gates.size() != rows.size())
            throw new AssertionError("expected " + rows.size() + " gate-change records, found " + gates.size() + ": " + gates);
        for (int i = 0; i < rows.size(); i++) {
            java.util.Map<String, String> r = rows.get(i);
            Fakes.Line g = gates.get(i);
            requireToken(g, "prev", r.get("previous"));
            requireToken(g, "new", r.get("new"));
            requireContains(g, r.get("reason"));
        }
    }

    @Then("the first of them, blocked to permitted after start, is logged as a transition")
    public void firstIsTransition() {
        List<Fakes.Line> gates = gateRecords();
        if (gates.isEmpty()) throw new AssertionError("no gate-change record");
        Fakes.Line g = gates.get(0);
        requireToken(g, "prev", "blocked");
        requireToken(g, "new", "permitted");
        requireContains(g, "transition");
    }

    /** Scripted only, like "the car leaves Park at N ms and returns to Park at M ms". */
    @Given("the car leaves Park at {int} ms, and the call starting at {int} ms replies {int} at {int} ms")
    public void leavesParkAndCallReplies(int out, int begin, int v, int t) {
        w.scheduler.script(out, parkcast.gate.harness.VirtualScheduler.BEFORE, () -> w.park.gear = 0);
        common.callStartingRepliesAt(begin, v, t);
    }

    @Then("^the gate-change record from (blocked|permitted) to (blocked|permitted) names a Park value of (\\d+) received at (\\d+) ms$")
    public void gateRecordNamesReading(String from, String to, int v, long t) {
        Fakes.Line g = gateRecordFromTo(from, to);
        requireContains(g, "of " + v + " ");
        requireContains(g, "received at " + t + " ms");
    }

    private Fakes.Line gateRecordFromTo(String from, String to) {
        for (Fakes.Line g : gateRecords()) if (hasToken(g, "prev", from) && hasToken(g, "new", to)) return g;
        throw new AssertionError("no gate-change record from " + from + " to " + to + ": " + w.lines());
    }

    @Given("the parking check is on and video is permitted after an on-time Park reading at 0 ms, and the Park getter call starting at 200 ms never replies")
    public void permittedThenCallNeverReplies() {
        common.checkOnPermittedAtZero();
        common.callStartingNeverReplies(200);
    }

    @Given("the parking check is on and video is permitted after an on-time Park reading at 0 ms, and ACC status changes to 6 at 300 ms")
    public void permittedThenSleep() {
        common.checkOnPermittedAtZero();
        common.accChanges(6, 300);
    }

    @Then("^(the gate-change record that blocks video|the startup flag record) names (.+) as its cause$")
    public void recordNamesCause(String record, String cause) {
        w.settle();
        if (record.startsWith("the gate-change")) {
            that = null;
            for (Fakes.Line g : gateRecords()) if (hasToken(g, "new", "blocked")) { that = g; break; }
            if (that == null) throw new AssertionError("no gate-change record blocks video: " + w.lines());
        } else {
            List<Fakes.Line> flags = recordsStarting("flag ");
            if (flags.size() != 1) throw new AssertionError("expected one startup flag record: " + w.lines());
            that = flags.get(0);
        }
        requireContains(that, cause);
    }

    @Then("that record gives no Park value or receipt time as its cause")
    public void noParkReadingInCause() {
        String b = body(that);
        String[] words = {"Park reading", "value=", "received"};
        for (String x : words) if (b.contains(x)) throw new AssertionError("the record names a Park reading (\"" + x + "\"): " + that);
    }

    // ------------------------------------------------------------ a flag changed across a restart

    @Given("the earlier run's log is still present")
    public void earlierLogPresent() {
        if (w.logs.lines.isEmpty()) throw new IllegalStateException("the earlier run wrote no log");
    }

    @Given("no log from the earlier run is present")
    public void noEarlierLog() {
        w.logs.lines.clear();
        w.logs.sizes.clear();
    }

    @Then("^the start record gives the parking check as (on|off), as the startup value$")
    public void startRecordStartupValue(String state) {
        Fakes.Line s = startRecord();
        requireToken(s, "check", state);
        requireContains(s, "startup value");
    }

    @Then("it gives the previous value as unknown")
    public void previousUnknown() {
        requireToken(startRecord(), "prev", "unknown");
    }

    /**
     * A fresh world with the same receiver.conf and build, and no earlier run or log, run as long as this start:
     * its records must be the same, so nothing this start did came from the earlier run.
     */
    @Then("the receiver read no state from the earlier run or its log")
    public void readNoEarlierState() {
        w.settle();
        World twin = new World();
        twin.config.text = w.config.text;
        twin.config.missing = w.config.missing;
        twin.config.unreadable = w.config.unreadable;
        twin.build.product = w.build.product;
        twin.build.missing = w.build.missing;
        twin.build.unreadable = w.build.unreadable;
        twin.runFor(w.ranUntil);
        List<String> mine = new ArrayList<>(), fresh = new ArrayList<>();
        for (Fakes.Line l : w.lines()) mine.add(l.text);
        for (Fakes.Line l : twin.lines()) fresh.add(l.text);
        if (!mine.equals(fresh))
            throw new AssertionError("this start's records differ from a first run's: " + mine + " vs " + fresh);
    }
}
