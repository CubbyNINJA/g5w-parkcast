package parkcast.core.steps;

import static parkcast.core.steps.Check.assertEquals;
import static parkcast.core.steps.Check.assertFalse;
import static parkcast.core.steps.Check.assertTrue;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import parkcast.gate.harness.Fakes;
import parkcast.gate.harness.VirtualScheduler;

/** AIR-071's markers: file 13 lines 618-663 (session records, end reasons, stop reasons). */
public final class LogMarkerSteps {
    private final S3World w;
    /** The first app start's index, once a step started the receiver again; -1 before. */
    private int firstStart = -1;
    /** The first app start's records after the field check, for the field step. */
    private List<String> expected;

    public LogMarkerSteps(S3World w) {
        this.w = w;
    }

    // ---- causes (line 639's examples) ----

    @Given("a session starts at {int} ms, and the library double reports at {int} ms that the iPhone ended the connection")
    public void sessionThenDisconnect(int t, int end) {
        w.sessionsAt.add((long) t);
        disconnectAt(end);
    }

    @Given("the library double reports at {int} ms that the iPhone ended the connection")
    public void disconnectAt(int t) {
        w.scheduler.script(t, VirtualScheduler.AFTER, new Runnable() {
            @Override
            public void run() {
                w.nativeLib.disconnect();
            }
        });
    }

    @Given("a session starts at {int} ms, but video_set_codec_and_size reports HEVC with an HEVC codec-configuration fixture")
    public void hevcSession(final int t) {
        w.scheduler.script(t, VirtualScheduler.AFTER, new Runnable() {
            @Override
            public void run() {
                w.nativeLib.session("h265", 1920, 720);
            }
        });
    }

    @Given("the fake codec factory throws java.io.IOException on create, and a session starts at {int} ms")
    public void factoryThrowsIo(int t) {
        w.codecs.createError = new IOException("synthetic codec failure");
        w.sessionsAt.add((long) t);
    }

    @Given("a session starts at {int} ms and is still open when exit\\() is called")
    public void sessionStillOpen(int t) {
        w.sessionsAt.add((long) t);
    }

    @Given("the library double delivers the allowlisted record {string} after reporting the accepted connection and before calling video_set_codec_and_size")
    public void recordAfterAccept(String record) {
        w.nativeLib.afterAccept = record;
    }

    // ---- stops (line 654's examples) ----

    @When("the receiver runs for {int} ms and then the lifecycle double drives onPause, onStop and onDestroy")
    public void runsThenDestroyed(int ms) {
        w.start();
        w.scheduler.script(ms, VirtualScheduler.AFTER, new Runnable() {
            @Override
            public void run() {
                w.runtime.onPause();
                w.runtime.onStop();
                w.runtime.onDestroy();
            }
        });
        w.runFor(ms);
    }

    @When("the receiver is then started again with the car out of Park, a session starts at {int} ms of that app start, and it runs for {int} ms")
    public void startedAgain(int t, int ms) {
        firstStart = w.startIndex();
        w.restart();
        w.g.park.gear = 0;
        w.sessionsAt.add((long) t);
        w.runFor(ms);
    }

    // ---- the log ----

    @Then("the last record of the log is exactly {string}")
    public void lastRecordIs(String s) {
        List<String> b = w.bodies();
        assertTrue(!b.isEmpty(), "the log is empty");
        assertEquals(s, b.get(b.size() - 1), "bodies " + b);
    }

    @Then("the log has exactly one record that begins {string}")
    public void exactlyOneBeginning(String s) {
        int n = 0;
        for (String b : w.bodies()) if (b.startsWith(s)) n++;
        assertEquals(1, n, "records beginning \"" + s + "\" in " + w.bodies());
    }

    @Then("the first app start's log, with the logger's prefix taken off each record, has these records in this order, each byte for byte:")
    public void firstStartHas(DataTable table) {
        List<String> want = new ArrayList<>();
        for (String row : table.asList().subList(1, table.height())) want.add(fill(row));
        List<String> got = bodies(firstStart);
        int at = 0;
        for (String rec : want) {
            int i = got.subList(at, got.size()).indexOf(rec);
            assertTrue(i >= 0, "no \"" + rec + "\" after record " + at + " in " + got);
            at += i + 1;
        }
        expected = want;
    }

    @Then("each <field> in those records holds the build's or the library double's value, with no other text")
    public void fieldsHoldValues() {
        assertTrue(expected != null, "the ordered records were not checked");
        for (String rec : expected) assertFalse(rec.contains("<") || rec.contains(">"), "unfilled field: " + rec);
        assertTrue(w.nativeLib.model.matches("[A-Za-z0-9,]+"), "the double's model: " + w.nativeLib.model);
    }

    @Then("the second app start's log has {string} and {string}")
    public void secondStartHas(String a, String b) {
        for (String s : new String[] {a, b}) {
            boolean found = false;
            for (String r : w.bodies()) if (r.startsWith(s)) found = true;
            assertTrue(found, "no record starting \"" + s + "\" in " + w.bodies());
        }
    }

    @Then("the second app start's log has no {string}")
    public void secondStartHasNo(String s) {
        for (String r : w.bodies()) assertFalse(r.contains(s), "written: " + r);
    }

    /** The table's fields filled with the build's and the doubles' values. */
    private String fill(String row) {
        return row.replace("<version>", S3World.VERSION).replace("<git commit>", S3World.COMMIT)
                .replace("<UxPlay commit>", S3World.UXPLAY).replace("<iPhone model>", w.nativeLib.model);
    }

    private List<String> bodies(int start) {
        assertTrue(start >= 0, "the receiver was not started again");
        List<String> out = new ArrayList<>();
        for (Fakes.Line l : w.g.logs.lines) if (l.start == start) out.add(S3World.body(l));
        return out;
    }
}
