package parkcast.gate.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import parkcast.gate.harness.VirtualScheduler;
import parkcast.gate.World;
import parkcast.gate.harness.Fakes;

/**
 * Steps for the receiver's logs (11, AT-38 to AT-40): the regulation-state records, the byte budget of the
 * two log files, and what a logging failure may and may not change. Every Then step looks only at what the
 * doubles recorded.
 */
public class LogSteps {
    /** The reason the regulation double gives when its service is lost. */
    static final String LOST_REASON = "service lost";

    private final World w;
    private final CommonSteps common;

    public LogSteps(World w, CommonSteps common) {
        this.w = w;
        this.common = common;
    }

    // ------------------------------------------------------------ regulation state (AT-38)

    /** Times and expected record texts of the regulation states this scenario scripted, in order. */
    private final List<String> regulationExpected = new ArrayList<>();

    @Given("the stock regulation state is {string} when the receiver starts")
    public void regulationAtStart(String state) {
        w.regulation.initial = state;
        regulationExpected.add("regulation state=" + state);
    }

    @When("it changes to {string} and then becomes unavailable because the service is lost")
    public void regulationChangesThenLost(String state) {
        w.regulation.changeAt(w.scheduler, 300, state, null);
        w.regulation.changeAt(w.scheduler, 600, "unavailable", LOST_REASON);
        regulationExpected.add("regulation state=" + state);
        regulationExpected.add("regulation unavailable reason=" + LOST_REASON);
        w.runFor(1000);
    }

    @When("^the regulation state changes to (restricted|unrestricted|unavailable) at (\\d+) ms$")
    public void regulationChangesAt(String state, long t) {
        w.regulation.changeAt(w.scheduler, t, state, "unavailable".equals(state) ? LOST_REASON : null);
    }

    private List<Fakes.Line> regulationLines() {
        w.settle();
        List<Fakes.Line> out = new ArrayList<>();
        for (Fakes.Line l : w.lines()) if (l.text.contains("regulation ")) out.add(l);
        return out;
    }

    @Then("the log has the regulation state at start and at each change, in order")
    public void regulationInOrder() {
        List<Fakes.Line> got = regulationLines();
        List<String> texts = new ArrayList<>();
        for (Fakes.Line l : got) texts.add(l.text.substring(l.text.indexOf("regulation ")));
        if (!texts.equals(regulationExpected))
            throw new AssertionError("regulation records " + got + ", expected " + regulationExpected);
        long[] at = {0, 300, 600};
        for (int i = 0; i < got.size() && i < at.length; i++)
            if (got.get(i).t != at[i])
                throw new AssertionError("regulation record " + i + " written at " + got.get(i).t + " ms, not " + at[i]
                        + " ms; " + got);
    }

    @Then("the unavailable state is logged as {string} with the reason")
    public void unavailableLogged(String word) {
        for (Fakes.Line l : regulationLines())
            if (l.text.contains(word) && l.text.contains(LOST_REASON)) return;
        throw new AssertionError("no record names \"" + word + "\" with the reason \"" + LOST_REASON + "\"; "
                + regulationLines());
    }

    // ------------------------------------------------------------ the gate's decision

    @Then("^mirrored video stays (permitted|blocked) throughout$")
    public void videoStays(String video) {
        if ("permitted".equals(video)) common.neverBlocked();
        else common.blockedThroughout();
    }

    // ------------------------------------------------------------ the byte budget (AT-39)

    /** The file that was current when the scenario began, and the older file then. */
    private Fakes.LogFile beforeFile, olderFile;
    /** The appends a "record ... is logged" step caused, and the size it asked for. */
    private List<Fakes.Append> recordAppends = new ArrayList<>();
    private List<Fakes.Line> recordLines = new ArrayList<>();
    private long recordSize = -1;
    /** For the rotation loop: how many records were logged, and of what size. */
    private int loggedCount;

    @Given("the current log file holds {long} bytes and the older log file holds {long} bytes")
    public void seedFiles(long current, long older) {
        w.logs.seed(current, older);
        beforeFile = w.logs.cur;
        olderFile = w.logs.old;
    }

    @Given("the receiver's log folder is empty")
    public void emptyFolder() {
        w.logs.clear();
        beforeFile = null;
        olderFile = null;
    }

    @When("a record of {long} bytes is logged")
    public void logRecord(long n) {
        long mark = w.logs.lastAppendId();
        int lineMark = w.logs.lines.size();
        recordSize = n;
        w.logRecordOfSize(n);
        recordAppends = w.logs.appendsAfter(mark);
        recordLines = new ArrayList<>(w.logs.lines.subList(lineMark, w.logs.lines.size()));
    }

    @When("records of {long} bytes each are logged until the current file has rotated at least {int} times")
    public void logUntilRotated(long n, int times) {
        int r0 = w.logs.rotations;
        recordSize = n;
        while (w.logs.rotations - r0 < times) {
            w.logRecordOfSize(n);
            loggedCount++;
            if (loggedCount > 10000000) throw new AssertionError("no " + times + " rotations after " + loggedCount + " records");
        }
    }

    /** The one append the last "record ... is logged" step made. */
    private Fakes.Append theAppend() {
        if (recordAppends.size() != 1)
            throw new AssertionError(recordAppends.size() + " appends for one record; files " + w.logs.files);
        return recordAppends.get(0);
    }

    /** The append that holds the record itself, whole. */
    private Fakes.Append theRecord() {
        Fakes.Append a = theAppend();
        if (a.len != recordSize) throw new AssertionError("the append wrote " + a.len + " bytes, not " + recordSize);
        return a;
    }

    /** The record (or its replacement line) is whole, in {@code f} and in no other file. */
    private void wholeIn(Fakes.Append a, Fakes.LogFile f, String what) {
        List<Fakes.LogFile> in = w.logs.everHolding(a.id);
        if (in.size() != 1 || in.get(0) != f || f.bytesOf(a.id) != a.len)
            throw new AssertionError("the " + what + " (" + a.len + " bytes) is in " + in + ", expected whole in " + f
                    + "; files " + w.logs.files);
    }

    @Then("the whole record is in the file that held {long} bytes")
    public void wholeInBefore(long before) {
        if (beforeFile == null || beforeFile.origin.indexOf("seeded") < 0)
            throw new AssertionError("no file was seeded with " + before + " bytes");
        wholeIn(theRecord(), beforeFile, "record");
    }

    @Then("^that file holds (?:exactly )?(\\d+) bytes and was (not rotated|then rotated)$")
    public void thatFile(long size, String rotation) {
        Fakes.LogFile f = theRecord().file;
        if (f.size != size) throw new AssertionError(f + " holds " + f.size + " bytes, not " + size);
        boolean want = rotation.startsWith("then");
        if (f.rotated() != want) throw new AssertionError(f + (want ? " was not rotated" : " was rotated"));
    }

    @Then("^the older log file (is kept|was deleted as a whole file)$")
    public void olderFile(String what) {
        if (olderFile == null) throw new AssertionError("no older log file was seeded");
        if (what.startsWith("is kept")) {
            if (olderFile.deleted()) throw new AssertionError("the older log file was deleted: " + olderFile);
        } else if (!olderFile.deletedWhole) {
            throw new AssertionError("the older log file was not deleted as a whole file: " + olderFile);
        }
    }

    @Then("^the current log file was rotated before the (record|\"record too large\" line) was written$")
    public void rotatedBefore(String what) {
        Fakes.Append a = what.equals("record") ? theRecord() : theAppend();
        if (!beforeFile.rotated()) throw new AssertionError("the current log file was not rotated: " + beforeFile);
        if (a.file == beforeFile || a.file.serial != beforeFile.serial + 1)
            throw new AssertionError("the " + what + " went to " + a.file + ", not the file the rotation of " + beforeFile
                    + " started");
    }

    @Then("^the whole (record|\"record too large\" line) is in the new current log file$")
    public void wholeInNewCurrent(String what) {
        Fakes.Append a = what.equals("record") ? theRecord() : theAppend();
        if (a.file.serial != beforeFile.serial + 1 || a.file != w.logs.cur)
            throw new AssertionError("the " + what + " went to " + a.file + ", the current file is " + w.logs.cur);
        wholeIn(a, a.file, what);
    }

    @Then("the rotated file contains no part of the record")
    public void rotatedHasNone() {
        Fakes.Append a = theRecord();
        if (beforeFile.bytesOf(a.id) != 0) throw new AssertionError(beforeFile + " holds part of the record");
    }

    @Then("the older {long}-byte log file was deleted as a whole file at that rotation")
    public void olderDeletedAtRotation(long size) {
        if (olderFile == null || olderFile.size != size) throw new AssertionError("no older file of " + size + " bytes: " + olderFile);
        if (!olderFile.deletedWhole || olderFile.deletedSeq != beforeFile.rotatedSeq - 1)
            throw new AssertionError("the older file " + olderFile + " was not deleted whole right before " + beforeFile
                    + " was rotated");
    }

    @Then("the whole record is in one file, which holds exactly {long} bytes and was then rotated")
    public void wholeInOneRotated(long size) {
        Fakes.Append a = theRecord();
        wholeIn(a, a.file, "record");
        if (a.file.size != size || !a.file.rotated())
            throw new AssertionError(a.file + " does not hold exactly " + size + " bytes and was then rotated");
    }

    @Then("the file that was current before the record was deleted as a whole file at that second rotation")
    public void beforeDeletedAtSecond() {
        Fakes.LogFile f = theRecord().file;
        if (!beforeFile.deletedWhole || !f.rotated() || beforeFile.deletedSeq != f.rotatedSeq - 1)
            throw new AssertionError(beforeFile + " was not deleted whole right before " + f + " was rotated");
    }

    @Then("no rotated file contains part of the record")
    public void noRotatedHasPart() {
        Fakes.Append a = theRecord();
        for (Fakes.LogFile f : w.logs.files)
            if (f != a.file && f.bytesOf(a.id) > 0) throw new AssertionError(f + " holds part of the record");
        if (a.file.bytesOf(a.id) != a.len) throw new AssertionError(a.file + " does not hold the whole record");
    }

    @Then("at no point did the log files together hold more than {long} bytes")
    public void maxTotal(long n) {
        if (w.logs.maxTotal > n) throw new AssertionError("the log files together held " + w.logs.maxTotal + " bytes");
    }

    @Then("at no point did either log file hold more than {long} bytes")
    public void maxSingle(long n) {
        if (w.logs.maxSingle > n) throw new AssertionError("a log file held " + w.logs.maxSingle + " bytes");
    }

    @Then("no part of that record is in either log file")
    public void recordAbsent() {
        for (Fakes.Append a : recordAppends)
            if (a.len == recordSize) throw new AssertionError("the " + recordSize + "-byte record was written to " + a.file);
        for (Fakes.LogFile f : w.logs.files)
            for (long[] p : f.pieces)
                if (p[0] >= 0 && p[1] >= recordSize) throw new AssertionError(f + " holds a piece of " + p[1] + " bytes");
    }

    @Then("exactly one line was logged in its place")
    public void oneLineInstead() {
        theAppend();
        if (recordLines.size() != 1) throw new AssertionError(recordLines.size() + " lines logged: " + recordLines);
    }

    @Then("that line contains {string} and {string}")
    public void thatLineContains(String a, String b) {
        oneLineInstead();
        String text = recordLines.get(0).text;
        if (!text.contains(a) || !text.contains(b))
            throw new AssertionError("the line \"" + text + "\" does not contain \"" + a + "\" and \"" + b + "\"");
    }

    @Then("^the logs are in (\\S+)$")
    public void logsIn(String folder) {
        if (!folder.equals(World.logFolder()))
            throw new AssertionError("the log folder is " + World.logFolder() + ", not " + folder);
        for (Fakes.Line l : w.logs.lines)
            if (l.file.contains("/")) throw new AssertionError("a record went outside the log folder: " + l.file);
    }

    @Then("every record still present is whole and in exactly one file")
    public void presentWhole() {
        java.util.Map<Long, Long> bytes = new java.util.HashMap<>();
        java.util.Map<Long, java.util.Set<Integer>> where = new java.util.HashMap<>();
        for (Fakes.LogFile f : w.logs.files) {
            if (f.deleted()) continue;
            for (long[] p : f.pieces) {
                if (p[0] < 0) continue;
                Long n = bytes.get(p[0]);
                bytes.put(p[0], (n == null ? 0 : n) + p[1]);
                java.util.Set<Integer> in = where.get(p[0]);
                if (in == null) where.put(p[0], in = new java.util.HashSet<>());
                in.add(f.serial);
            }
        }
        if (bytes.isEmpty()) throw new AssertionError("no record is present; files " + w.logs.files);
        java.util.Map<Long, Fakes.Append> byId = new java.util.HashMap<>();
        for (Fakes.Append a : w.logs.appends) byId.put(a.id, a);
        for (java.util.Map.Entry<Long, Long> e : bytes.entrySet()) {
            Fakes.Append a = byId.get(e.getKey());
            if (where.get(e.getKey()).size() != 1 || e.getValue() != a.len)
                throw new AssertionError("record " + e.getKey() + " (" + a.len + " bytes) is " + e.getValue()
                        + " bytes in files " + where.get(e.getKey()));
        }
    }

    @Then("no record was trimmed")
    public void noneTrimmed() {
        if (w.logs.appends.size() != loggedCount)
            throw new AssertionError(loggedCount + " records logged, " + w.logs.appends.size() + " appended");
        for (Fakes.Append a : w.logs.appends)
            if (a.len != recordSize) throw new AssertionError("record " + a.id + " was written as " + a.len + " bytes");
    }


    // ------------------------------------------------------------ logging failures (AT-40)

    /** Next state the stimulus writes; it alternates so every stimulus is a fresh change. */
    private boolean stimulusRestricted = true;

    /**
     * Makes the receiver write one record at {@code t}: the regulation double reports a state change, and the
     * regulation log records it whatever the parking check. A scripted fault at {@code t} then hits that write.
     */
    private void stimulus(long t) {
        w.regulation.changeAt(w.scheduler, t, stimulusRestricted ? "restricted" : "unrestricted", null);
        stimulusRestricted = !stimulusRestricted;
    }

    /**
     * Scripts one logging fault at {@code t} in the current app start and the write it hits. "create": the
     * current file can't be created from {@code t} on. "write": the first append at {@code t} fails.
     * "rotation"/"delete": the current file is filled to one byte under the cap at {@code t}, so the write
     * rotates, and deleting the older file then fails.
     */
    private void faultAt(String fault, final long t) {
        String si = w.scheduler.startIndex + "|" + t;
        if (fault.contains("create")) {
            w.logs.down.add(new long[] {w.scheduler.startIndex, t, -1});
        } else if (fault.contains("rotation") || fault.contains("delete")) {
            w.scheduler.script(t, VirtualScheduler.BEFORE, () -> w.logs.padTo(World.logFileCap() - 1));
            w.logs.failDeleteAt.add(si);
        } else if (fault.contains("write")) {
            w.logs.failAppendAt.add(si);
        } else {
            throw new IllegalArgumentException("unsupported fault: " + fault);
        }
        scriptedFaults.add(new Object[] {w.scheduler.startIndex, fault, t});
        stimulus(t);
    }

    /** Logging works again from {@code t}: an open create fault ends and record filters are cleared, then a record is written. */
    private void recoverAt(final long t) {
        final int start = w.scheduler.startIndex;
        w.scheduler.script(t, VirtualScheduler.BEFORE, () -> {
            for (long[] d : w.logs.down)
                if (d[0] == start && d[2] < 0) d[2] = t;
            w.logs.failOn = null;
            w.logs.failOnceOn = null;
        });
        stimulus(t);
    }

    @When("^logging fails by (.+), recovers, and then fails again$")
    public void failsRecoversFails(String fault) {
        faultAt(fault, 1000);
        recoverAt(2000);
        faultAt(fault, 3000);
        w.runFor(12000);
    }

    @Then("^exactly one on-screen logging notice was shown(?: since the restart)?$")
    public void oneLoggingNotice() {
        w.settle();
        List<Fakes.Window> wins = w.notices.windows(Fakes.LOG, w.scheduler.startIndex);
        if (wins.size() != 1) {
            List<Long> at = new ArrayList<>();
            for (Fakes.Window win : wins) at.add(win.addedAt);
            throw new AssertionError(wins.size() + " logging-failure notice windows in this start, added at " + at);
        }
    }

    /** The decision the "parking check is X and mirrored video is Y" step set up held throughout. */
    @Then("the gate's decision didn't change")
    public void gateUnchanged() {
        if ("blocked".equals(w.gateVideo)) common.blockedThroughout();
        else common.neverBlocked();
    }


    @Given("one logging notice was shown during the previous app start")
    public void loggingNoticePreviousStart() {
        common.checkOnPermittedAtZero();
        w.session.active = true;
        faultAt("write", 1000);
        w.runFor(8000);
        if (w.notices.windows(Fakes.LOG, w.scheduler.startIndex).size() != 1)
            throw new AssertionError("the previous app start showed no single logging-failure notice");
    }

    @When("the receiver restarts and logging fails")
    public void restartsAndFails() {
        w.restart();
        faultAt("write", 1000);
        w.start();
        w.runFor(8000);
    }

    /** The two failures of l.149 (11), in the order the row gives them. */
    @Given("^(?:a log write fails at (\\d+) ms, and ACC registration throws an exception at (\\d+) ms"
            + "|ACC registration (throws an exception|returns no sticky state) at (\\d+) ms, and "
            + "(?:a log write fails at (\\d+) ms|writing the \"([^\"]*)\" record fails))$")
    public void compoundFailures(Long writeA, Long accA, String accHow, Long accB, Long writeB, String record) {
        if (writeA != null) {
            faultAt("write", writeA);
            w.acc.planThrow("exception", accA);
            return;
        }
        if (accHow.startsWith("throws")) w.acc.planThrow("exception", accB);
        else w.acc.planNoSticky(accB);
        if (writeB != null) faultAt("write", writeB);
        else w.logs.failOn = record;
    }

    @Given("^a log write fails(?: again)? at (\\d+) ms$")
    public void writeFailsAt(long t) {
        faultAt("write", t);
    }

    @Given("^(the log file fails to create|rotation fails to delete the older file) at (\\d+) ms$")
    public void otherFaultAt(String fault, long t) {
        faultAt(fault, t);
    }

    @Given("the first log write after {int} ms fails")
    public void firstWriteAfterFails(int t) {
        w.logs.failFirstAfter.add(w.scheduler.startIndex + "|" + t);
        stimulus(t + 1);
    }

    @When("^logging recovers at (\\d+) ms(?: and a log write fails again at (\\d+) ms)?$")
    public void recovers(long t, Long again) {
        recoverAt(t);
        if (again != null) faultAt("write", again);
    }


    /** One failure on its own, for "a notice on screen alone keeps its own slot" (l.371 of 11). */
    @Given("^the parking check is (on|off) and (?:a log write fails at (\\d+) ms|ACC registration throws an exception at (\\d+) ms)$")
    public void checkAndOneFailure(String state, Long writeAt, Long accAt) {
        if ("on".equals(state)) common.checkOnPermittedAtZero();
        else common.checkOffSupported();
        if (writeAt != null) faultAt("write", writeAt);
        else w.acc.planThrow("exception", accAt);
    }

    // ------------------------------------------------------------ the built package's manifest

    /** The receiver app's manifest text, once "the built package's manifest is scanned" read it. */
    private String manifest;

    /**
     * The package build is not wired yet, so this scans the manifest source the package is built from:
     * {@code receiver/app/src/main/AndroidManifest.xml}, found by walking up from the working directory.
     */
    @When("the built package's manifest is scanned")
    public void scanManifest() throws java.io.IOException {
        java.io.File dir = new java.io.File(System.getProperty("user.dir")).getAbsoluteFile();
        while (dir != null && !new java.io.File(dir, "specs").isDirectory()) dir = dir.getParentFile();
        if (dir == null) throw new IllegalStateException("no repository root above " + System.getProperty("user.dir"));
        java.io.File f = new java.io.File(dir, "receiver/app/src/main/AndroidManifest.xml");
        if (!f.isFile()) throw new AssertionError("no manifest at " + f);
        manifest = new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        if (!manifest.contains("<manifest")) throw new AssertionError(f + " is not an Android manifest");
    }

    @Then("it declares no SYSTEM_ALERT_WINDOW permission")
    public void noAlertWindowPermission() {
        noPermission("android.permission.SYSTEM_ALERT_WINDOW");
    }

    /** TYPE_TOAST windows need no permission; these are the ones a notice could tempt someone to add. */
    @Then("it declares no permission that only the notice code uses")
    public void noNoticePermission() {
        for (String p : new String[] {"SYSTEM_ALERT_WINDOW", "INTERNAL_SYSTEM_WINDOW", "POST_NOTIFICATIONS", "VIBRATE",
                "WAKE_LOCK"})
            noPermission("android.permission." + p);
    }

    private void noPermission(String name) {
        if (manifest == null) throw new IllegalStateException("no step scanned the manifest");
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<uses-permission(?:-sdk-23)?\\b[^>]*android:name\\s*=\\s*\"([^\"]+)\"").matcher(manifest);
        while (m.find())
            if (m.group(1).equals(name)) throw new AssertionError("the manifest declares " + name);
    }


    // ------------------------------------------------------------ the fault-free twin run (l.486 of 11)

    /** UI-double faults a twin run can leave out: they act on the notice double only, so dropping them is the whole difference. */
    private static final java.util.Set<String> TWIN_FAULTS = new java.util.HashSet<>(java.util.Arrays.asList(
            "the UI double throws on create for both logging-failure notice attempts",
            "the UI double throws on both removal calls for the logging-failure window",
            "the UI double throws on add for the first logging-failure notice attempt"));

    /**
     * Covering (l.901 of 11): the focus changes and broadcasts a scenario scripts through {@code CoverHarnessA}.
     * The twin never scripts them, so leaving them out is the whole difference.
     */
    private static final String COVERING = "covering";

    /** Every logging fault this scenario scripted through {@link #faultAt}, as {start, fault, t}, for the twin to replay. */
    private final List<Object[]> scriptedFaults = new ArrayList<>();

    /** Times of the "write" faults scripted for the current start, whichever step scripted them. */
    public List<Long> writeFaultTimes() {
        List<Long> out = new ArrayList<>();
        for (Object[] f : scriptedFaults)
            if ((Integer) f[0] == w.scheduler.startIndex && "write".equals(f[1])) out.add((Long) f[2]);
        return out;
    }

    /**
     * Replays the scenario's setup on a fresh World without {@code fault}, with the same logging faults at the
     * same times. The setup is the "parking check is X and mirrored video is Y" step when the scenario used it,
     * else the receiver.conf, build, first focus gain, Park replies and session state copied from this run. Both
     * runs end at the same time; then the five traces are compared.
     */
    @Then("^the gate decision, effective flag value, Park getter schedule, registration calls, and audio and connection traces match a run without (.+)$")
    public void matchesTwinWithout(String fault) {
        boolean covering = COVERING.equals(fault);
        if (!covering && !TWIN_FAULTS.contains(fault)) throw new IllegalArgumentException("unsupported fault: " + fault);
        if (!covering && w.gateCheck == null)
            throw new IllegalStateException("the twin run needs the \"parking check is X and mirrored video is Y\" setup");
        w.settle();
        World t = new World();
        if (t.scheduler.startIndex != w.scheduler.startIndex)
            throw new IllegalStateException("the twin run does not replay restarts");
        CommonSteps tc = new CommonSteps(t);
        LogSteps tl = new LogSteps(t, tc);
        if (w.gateCheck != null) new GateMiscSteps(t, tl, tc).checkAndPermitted(w.gateCheck, w.gateVideo);
        else copySetup(w, t);
        for (Object[] f : scriptedFaults) {
            if ((Integer) f[0] != w.scheduler.startIndex)
                throw new IllegalStateException("the twin run does not replay faults of an earlier start");
            tl.faultAt((String) f[1], (Long) f[2]);
        }
        t.runFor(w.ranUntil);
        if (covering) {
            if (coverTrace(w).equals(coverTrace(t)))
                throw new IllegalStateException("covering changed nothing in the cover records: " + coverTrace(w));
        } else if (noticeTrace(w).equals(noticeTrace(t)))
            throw new IllegalStateException("\"" + fault + "\" changed nothing in the notice double: " + noticeTrace(w));
        java.util.Map<String, String> a = traces(w), b = traces(t);
        for (String k : a.keySet())
            if (!a.get(k).equals(b.get(k)))
                throw new AssertionError(k + " differs from the run without " + fault + ":\n  with:    " + a.get(k)
                        + "\n  without: " + b.get(k));
    }

    /** The five traces the twin step compares, for the current start of {@code x}. */
    private static java.util.Map<String, String> traces(World x) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        StringBuilder g = new StringBuilder("shown");
        for (Fakes.Shown s : x.shows()) g.append(' ').append(s.t).append(':').append(s.id);
        g.append("; cleared ").append(x.clears());
        m.put("the gate decision", g.toString());
        List<String> flag = new ArrayList<>();
        for (Fakes.Line l : x.lines())
            if (l.text.contains("start check=") || l.text.contains("flag prev=")) flag.add(l.toString());
        m.put("the effective flag value", flag.toString());
        m.put("the Park getter schedule", x.calls().toString());
        StringBuilder r = new StringBuilder("ACC registered at");
        for (long[] u : x.acc.currentRegistrations()) r.append(' ').append(u[1]);
        r.append("; ACC unregistered at");
        for (long[] u : x.acc.unregisters) if (u[0] == x.scheduler.startIndex) r.append(' ').append(u[1]);
        r.append("; regulation registers=").append(x.regulation.registers).append(" unregisters=")
                .append(x.regulation.unregisters);
        m.put("the registration calls", r.toString());
        m.put("the audio and connection traces", x.session.counts + " reconnects=" + x.session.reconnects);
        return m;
    }

    /**
     * The inputs a scenario without the "parking check is X and mirrored video is Y" step sets before the start.
     * Park replies this copy does not carry are refused rather than silently dropped.
     */
    private static void copySetup(World from, World to) {
        Fakes.Park p = from.park;
        if (p.reread != null || !p.byIndex.isEmpty() || !p.chain.isEmpty() || !p.rereadNext.isEmpty()
                || !p.rankAt.isEmpty() || p.lostAt != -1)
            throw new IllegalStateException("the twin run replays only constant and per-start Park replies");
        to.config.text = from.config.text;
        to.config.missing = from.config.missing;
        to.config.unreadable = from.config.unreadable;
        to.build.product = from.build.product;
        to.build.missing = from.build.missing;
        to.build.unreadable = from.build.unreadable;
        to.focusAt = from.focusAt;
        to.focusRank = from.focusRank;
        to.displayWidth = from.displayWidth;
        to.settleMs = from.settleMs;
        to.park.gear = p.gear;
        to.park.immediate = p.immediate;
        to.park.missing = p.missing;
        to.park.constant = p.constant;
        to.park.byStart.putAll(p.byStart);
        to.session.active = from.session.active;
        to.session.audioPlaying = from.session.audioPlaying;
    }

    /** The cover and REVERSE_ON records of the current start, to show that covering did something. */
    private static String coverTrace(World x) {
        List<String> out = new ArrayList<>();
        for (Fakes.Line l : x.lines())
            if (l.text.contains("notice cover") || l.text.contains("reverse_on received")) out.add(l.toString());
        return out.toString();
    }

    /** The logging-failure notice calls and windows of the current start, to show a dropped fault did something. */
    private static String noticeTrace(World x) {
        StringBuilder b = new StringBuilder();
        for (Fakes.Op o : x.notices.ops)
            if (o.start == x.scheduler.startIndex && Fakes.LOG.equals(o.kind))
                b.append(o.op).append('#').append(o.attempt).append('@').append(o.t).append(' ');
        for (Fakes.Window win : x.notices.windows(Fakes.LOG, x.scheduler.startIndex))
            b.append("window@").append(win.addedAt).append(" removed@").append(win.removedAt).append(' ');
        return b.toString().trim();
    }
}
