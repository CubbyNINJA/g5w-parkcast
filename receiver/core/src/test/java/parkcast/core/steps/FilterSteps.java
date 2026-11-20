package parkcast.core.steps;

import static parkcast.core.steps.Check.assertEquals;
import static parkcast.core.steps.Check.assertTrue;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import parkcast.core.CoreSeams;
import parkcast.core.ReceiverRuntime;
import parkcast.core.doubles.FakeFileSystem;
import parkcast.core.doubles.Trace;
import parkcast.gate.harness.Fakes;
import parkcast.gate.harness.VirtualScheduler;

/** Native logging and AIR-073's filter (AIR-072, AIR-073, AIR-083(d)): file 13 lines 851-934 and 1036. */
public final class FilterSteps {
    /** A record on the allowlist (UxPlay's mirror accept line); the scenarios add to it. */
    static final String BASE = "raop_rtp_mirror accepting client";
    static final String HEX = "0123456789abcdef";
    static final String B64 = "Zm9vYmFy";

    private final S3World w;
    /** The records this scenario delivered, in order. */
    private final List<String> delivered = new ArrayList<>();
    /** The parts of the delivered record that must never reach a log. */
    private final List<String> parts = new ArrayList<>();
    private long endAt = -1;

    public FilterSteps(S3World w) {
        this.w = w;
    }

    // ---- AIR-072(a): callback and level ----

    @Then("the fake native library recorded the log callback call and the log level call, with level debug, before raop_init2")
    public void callbackAndLevelBeforeInit2() {
        int cb = seq("raop_set_log_callback", null), lvl = seq("raop_set_log_level", "debug"), init2 = seq("raop_init2", null);
        assertTrue(cb >= 0 && lvl >= 0 && init2 >= 0, "trace:\n" + w.trace);
        assertTrue(cb < init2 && lvl < init2, "not before raop_init2:\n" + w.trace);
    }

    @Then("it recorded no other log level call")
    public void noOtherLevelCall() {
        assertEquals(1, w.trace.of("native", "raop_set_log_level", null).size(), "trace:\n" + w.trace);
    }

    private int seq(String name, String details) {
        for (Trace.Entry e : w.trace.of("native", name, null))
            if (details == null || e.details.equals(details)) return e.seq;
        return -1;
    }

    // ---- AIR-072(b): not ready, or throwing ----

    @Given("the library double delivers the allowlisted record {string} before the filter is ready")
    public void deliversBeforeReady(String marker) {
        final String rec = BASE + " " + marker;
        delivered.add(rec);
        parts.add(marker);
        w.beforeCreate = new Consumer<ReceiverRuntime>() {
            @Override
            public void accept(ReceiverRuntime r) {
                CoreSeams.deliver(r, rec);
            }
        };
        w.sessionsAt.add(1000L);
        endAt = 3000;
    }

    @Given("the library double delivers the allowlisted record {string} while the filter throws on every record")
    public void deliversWhileThrowing(String marker) {
        w.beforeCreate = new Consumer<ReceiverRuntime>() {
            @Override
            public void accept(ReceiverRuntime r) {
                CoreSeams.throwingFilter(r);
            }
        };
        w.sessionsAt.add(1000L);
        deliverAt(2000, BASE + " " + marker);
        parts.add(marker);
        endAt = 3000;
    }

    @When("the receiver runs until the session ends")
    public void runsUntilSessionEnds() {
        sessionEndsAt((int) endAt);
    }

    @Then("no log file contains {string}")
    public void noLogFileContains(String s) {
        for (Fakes.Line l : w.g.logs.lines) assertTrue(!l.text.contains(s), "written: " + l.text);
    }

    @Then("the withheld count includes that record")
    public void withheldIncludesIt() {
        assertTrue(withheld() >= 1, "bodies " + w.bodies());
    }

    // ---- AIR-073: allowlist, redaction, drop whole ----

    @Given("the library double delivers these native records at {int} ms:")
    public void deliversTheseRecords(int t, DataTable table) {
        for (String row : table.asList().subList(1, table.height())) deliverAt(t, describedRecord(row));
    }

    @Given("every read of the key file throws IOException from {int} ms")
    public void keyReadsThrowFrom(int t) {
        w.scheduler.script(t, VirtualScheduler.AFTER, new Runnable() {
            @Override
            public void run() {
                w.fs.readThrows.add(S3World.KEY_PATH);
            }
        });
    }

    @Given("the session ends at {int} ms")
    public void sessionEndsAt(int t) {
        w.start();
        w.scheduler.script(t, VirtualScheduler.AFTER, new Runnable() {
            @Override
            public void run() {
                w.nativeLib.disconnect();
            }
        });
        w.runFor(t);
    }

    /** A regular expression, not a cucumber expression: Gherkin turns a cell's {@code \n} into a newline. */
    @Given("^the library double delivers at (\\d+) ms an allowlisted record that also carries ((?s).*)$")
    public void deliversCarrying(int t, String carried) {
        String c = carried(carried);
        for (String p : c.split("[\r\n]")) parts.add(p);
        deliverAt(t, BASE + " " + c);
    }

    @Given("the library double delivers at {int} ms an allowlisted record, changed so that {} and otherwise still on the allowlist")
    public void deliversChanged(int t, String change) {
        Matcher prefix = Pattern.compile("it starts with \"(.*)\"").matcher(change);
        Matcher run = Pattern.compile("it holds a (\\d+)-character run of (hex digits|base64 characters)").matcher(change);
        String rec;
        if (prefix.matches()) {
            rec = prefix.group(1) + BASE;
            parts.add(prefix.group(1).trim());
        } else if (change.equals("it holds the byte 0x01")) {
            rec = BASE + " a\u0001b";
            parts.add("\u0001");
        } else if (run.matches()) {
            String r = run(run.group(2).startsWith("hex") ? HEX : B64, Integer.parseInt(run.group(1)));
            rec = BASE + " " + r;
            parts.add(r);
        } else {
            throw new IllegalArgumentException("unknown change: " + change);
        }
        deliverAt(t, rec);
    }

    @Given("the library double delivers at {int} ms an allowlisted record holding a 31-character run of hex digits, and at {int} ms one holding a 43-character run of base64 characters")
    public void deliversShortRuns(int t1, int t2) {
        deliverAt(t1, BASE + " " + run(HEX, 31));
        deliverAt(t2, BASE + " " + run(B64, 43));
    }

    @Then("the four allowlisted records are written with each address replaced by {string}")
    public void fourRedacted(String with) {
        int n = 0;
        for (String b : w.bodies()) if (b.equals(BASE + " " + with)) n++;
        assertEquals(4, n, "bodies " + w.bodies());
    }

    @Then("no log record contains {string}, {string}, {string}, {string}, {string} or {string}")
    public void noRecordContains(String a, String b, String c, String d, String e, String f) {
        for (String s : new String[] {a, b, c, d, e, f}) noLogFileContains(s);
    }

    @Then("the withheld count logged at session end is {int}")
    public void withheldIs(int n) {
        assertEquals(n, withheld(), "bodies " + w.bodies());
    }

    @Then("no part of that record was written")
    public void noPartWritten() {
        noLogFileContains(BASE);
        for (String p : parts) if (!p.isEmpty()) noLogFileContains(p);
    }

    @Then("both records are written unchanged")
    public void bothWrittenUnchanged() {
        List<String> bodies = w.bodies();
        for (String r : delivered) assertTrue(bodies.contains(r), "missing " + r + " in " + bodies);
    }

    // ---- helpers ----

    private void deliverAt(long t, final String rec) {
        delivered.add(rec);
        w.scheduler.script(t, VirtualScheduler.AFTER, new Runnable() {
            @Override
            public void run() {
                w.nativeLib.deliverLog(rec);
            }
        });
    }

    /** The withheld count from the first session's end record; -1 when there is none. */
    private int withheld() {
        for (String b : w.bodies())
            if (b.startsWith("session 1 end ")) {
                Matcher m = Pattern.compile(" withheld=(\\d+)$").matcher(b);
                return m.find() ? Integer.parseInt(m.group(1)) : -1;
            }
        return -1;
    }

    /** A record from file 13's table wording (line 873). */
    static String describedRecord(String row) {
        Matcher on = Pattern.compile("an allowlisted record carrying (?:IPv4|IPv6|MAC) (\\S+)").matcher(row);
        if (on.matches()) return BASE + " " + on.group(1);
        Matcher name = Pattern.compile("a record off the allowlist carrying the name \"(.*)\"").matcher(row);
        if (name.matches()) return "name=" + name.group(1);
        Matcher carry = Pattern.compile("a record off the allowlist carrying \"(.*)\"").matcher(row);
        if (carry.matches()) return "connection from " + carry.group(1);
        Matcher text = Pattern.compile("a record off the allowlist reading \"(.*)\"").matcher(row);
        if (text.matches()) return text.group(1);
        throw new IllegalArgumentException("unknown record: " + row);
    }

    /** The carried item: a quoted string with its escapes, or one of the key phrases. */
    static String carried(String s) {
        if (s.equals("the key file's name")) return S3World.KEY_NAME;
        if (s.equals("the key file's path")) return S3World.KEY_PATH;
        if (s.equals("the fake key fixture's bytes")) return new String(FakeFileSystem.VALID_KEY, StandardCharsets.ISO_8859_1);
        if (s.equals("a 16-byte slice of the fake key fixture's bytes")) {
            return new String(java.util.Arrays.copyOfRange(FakeFileSystem.VALID_KEY, 8, 24), StandardCharsets.ISO_8859_1);
        }
        if (s.length() < 2 || !s.startsWith("\"") || !s.endsWith("\"")) throw new IllegalArgumentException(s);
        String in = s.substring(1, s.length() - 1);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < in.length(); i++) {
            char ch = in.charAt(i);
            if (ch == '\\' && i + 1 < in.length()) {
                char n = in.charAt(++i);
                b.append(n == 'n' ? '\n' : n == 'r' ? '\r' : n);
            } else {
                b.append(ch);
            }
        }
        return b.toString();
    }

    static String run(String unit, int n) {
        StringBuilder b = new StringBuilder();
        while (b.length() < n) b.append(unit);
        return b.substring(0, n);
    }
}
