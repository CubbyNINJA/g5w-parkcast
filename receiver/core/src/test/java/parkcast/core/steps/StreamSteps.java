package parkcast.core.steps;

import static parkcast.core.steps.Check.assertEquals;
import static parkcast.core.steps.Check.assertFalse;
import static parkcast.core.steps.Check.assertTrue;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import parkcast.gate.harness.Fakes;
import parkcast.core.doubles.BlackCover;
import parkcast.core.doubles.Trace;
import parkcast.gate.harness.VirtualScheduler;

/** File 13 AIR-051 and AIR-055 to AIR-057 steps on the Java side: sessions, the decoder, the cover and the gate. */
public final class StreamSteps {
    /** How long a Then step runs the receiver past the last session when no run step came first. */
    static final long RUN_PAST_SESSION_MS = 2000;

    private final S3World w;

    public StreamSteps(S3World w) {
        this.w = w;
    }

    /** Runs the receiver past its last session when the scenario has no run step ("When a session starts"). */
    static void ensureRun(S3World w) {
        if (w.started && w.ranUntil > 0) return;
        long last = w.g.settleMs;
        for (Long t : w.sessionsAt) last = Math.max(last, t + RUN_PAST_SESSION_MS);
        w.runFor(last);
    }

    // ---- Sessions ----

    // ---- video_set_codec_and_size (AIR-055) ----

    @Then("video_set_codec_and_size returned a value of 0 or more")
    public void codecConfigReturned() {
        ensureRun(w);
        Trace.Entry ret = null;
        for (Trace.Entry e : w.trace.current("native", "callback"))
            if (ret == null && e.details.startsWith("video_set_codec_and_size return=")) ret = e;
        assertTrue(ret != null, "video_set_codec_and_size never returned: " + w.trace);
        int v = Integer.parseInt(ret.details.substring("video_set_codec_and_size return=".length()));
        assertTrue(v >= 0, "returned " + v);
    }

    @Then("^Java received the stream geometry (\\d+)x(\\d+)$")
    public void javaGotGeometry(int width, int height) {
        ensureRun(w);
        boolean got = false;
        for (Trace.Entry e : w.trace.current("java", "event"))
            if (e.details.startsWith("codecConfig ") && e.details.endsWith(" " + width + "x" + height)) got = true;
        assertTrue(got, "no codecConfig " + width + "x" + height + " reached Java: " + w.trace.current("java", "event"));
    }

    // ---- The decoder (AIR-056) ----

    @Then("^the fake codec factory recorded exactly one call, createByCodecName\\(\"OMX\\.TCC\\.avcdec\"\\)$")
    public void oneCreateByName() {
        ensureRun(w);
        List<Trace.Entry> creates = w.trace.current("codec", "create");
        assertEquals(1, creates.size(), "create calls " + creates);
        assertEquals("OMX.TCC.avcdec", creates.get(0).details, "created");
    }

    @Then("createByCodecName was not called before video_set_codec_and_size reported the H.264 configuration")
    public void createAfterConfig() {
        ensureRun(w);
        Trace.Entry config = null;
        for (Trace.Entry e : w.trace.current("java", "event"))
            if (config == null && e.details.startsWith("codecConfig h264 ")) config = e;
        assertTrue(config != null, "no H.264 configuration reached Java");
        for (Trace.Entry c : w.trace.current("codec", "create"))
            assertTrue(c.seq > config.seq, "create " + c + " before " + config);
    }

    @Then("it recorded no codec-list lookup")
    public void noLookup() {
        assertEquals(0, w.codecs.lookups, "codec-list lookups");
    }

    @Then("the fake codec was configured with the receiver activity's own Surface")
    public void configuredOnActivitySurface() {
        ensureRun(w);
        assertTrue(w.codecs.last != null, "no codec was created");
        assertTrue(w.codecs.last.surface == BlackCover.SURFACE, "configured with " + w.codecs.last.surface);
        assertEquals(1, w.trace.current("codec", "configure").size(), "configure calls");
        assertEquals(1, w.trace.current("codec", "start").size(), "start calls");
    }

    // ---- Stream geometry and the letterbox (AIR-051(b), (c)) ----

    @Given("^a session starts at (\\d+) ms with stream geometry (\\d+)x(\\d+)$")
    public void sessionWithGeometry(int ms, int width, int height) {
        w.nativeLib.sessionW = width;
        w.nativeLib.sessionH = height;
        w.sessionsAt.add((long) ms);
    }

    @Then("^the video is drawn in a box of (\\d+)x(\\d+) on the 1920x720 screen, at x (\\d+)(?: or (\\d+))? and y (\\d+), within 1 px$")
    public void videoBox(int width, int height, int x, String xAlt, int y) {
        ensureRun(w);
        int[] box = lastBox();
        int x2 = xAlt == null ? x : Integer.parseInt(xAlt);
        assertTrue(Math.abs(box[0] - width) <= 1 && Math.abs(box[1] - height) <= 1, "box " + box[0] + "x" + box[1]);
        assertTrue(box[2] >= Math.min(x, x2) - 1 && box[2] <= Math.max(x, x2) + 1, "x " + box[2]);
        assertTrue(Math.abs(box[3] - y) <= 1, "y " + box[3]);
    }

    @Then("the rest of the screen is black")
    public void restIsBlack() {
        ensureRun(w);
        List<Trace.Entry> boxes = w.trace.current("cover", "videoBox");
        int[] box = lastBox();
        for (Trace.Entry e : boxes) assertEquals(boxes.get(0).details, e.details, "the video box moved");
        assertTrue(box[2] >= 0 && box[3] >= 0 && box[2] + box[0] <= 1920 && box[3] + box[1] <= 720,
                "box outside the screen: " + boxes.get(0).details);
        Trace.Entry firstRender = null;
        for (Trace.Entry e : w.trace.current("codec", "releaseOutput"))
            if (firstRender == null && e.details.endsWith("render=true")) firstRender = e;
        assertTrue(firstRender != null, "nothing was rendered");
        assertTrue(boxes.get(0).seq < firstRender.seq, "the box was set after the first render");
    }

    @Then("^the log records the stream geometry (\\d+)x(\\d+)$")
    public void logGeometry(int width, int height) {
        ensureRun(w);
        Pattern p = Pattern.compile("(^|\\s)" + width + "x" + height + "(\\s|$)");
        boolean found = false;
        for (Fakes.Line l : w.lines()) if (p.matcher(S3World.body(l)).find()) found = true;
        assertTrue(found, "no log record names " + width + "x" + height + ": " + w.bodies());
    }

    /** The latest box: {width, height, x, y}. */
    private int[] lastBox() {
        List<Trace.Entry> boxes = w.trace.current("cover", "videoBox");
        assertFalse(boxes.isEmpty(), "the video box was never set");
        String d = boxes.get(boxes.size() - 1).details;
        String[] wh = d.substring(0, d.indexOf(" at ")).split("x");
        String[] xy = d.substring(d.indexOf(" at ") + 4).split(",");
        return new int[] {Integer.parseInt(wh[0]), Integer.parseInt(wh[1]), Integer.parseInt(xy[0]), Integer.parseInt(xy[1])};
    }

    // ---- HEVC (AIR-052(b)) ----

    @Given("a session starts at {int} ms, but video_set_codec_and_size reports HEVC with an HEVC codec-configuration fixture")
    public void hevcSession(int ms) {
        w.nativeLib.sessionCodec = "h265";
        w.sessionsAt.add((long) ms);
    }

    @Then("no byte of the HEVC fixture was queued to the fake codec")
    public void noHevcQueued() {
        ensureRun(w);
        assertTrue(w.trace.current("codec", "queue").isEmpty(), "queued: " + w.trace.current("codec", "queue"));
        assertTrue(w.trace.current("codec", "create").isEmpty(), "a codec was created: " + w.trace.current("codec", "create"));
    }

    @Then("the session is ended")
    public void sessionEnded() {
        ensureRun(w);
        boolean ended = false;
        for (Trace.Entry e : w.trace.current("native", "session")) if (e.details.startsWith("ended ")) ended = true;
        assertTrue(ended, "the session was not ended: " + w.trace.current("native", null));
        assertFalse(w.nativeLib.sessionOpen(), "a session is still open");
    }

    // ---- Callbacks after stop (AIR-055) ----

    /** The seq of the last library stop call; later trace entries came after stop returned. */
    private int stopSeq = -1;

    @When("the library double calls video_process with an access unit {int} ms after stop returns")
    public void callbackAfterStop(int after) {
        if (!w.started || w.ranUntil <= 0) w.runFor(w.g.settleMs);
        List<Trace.Entry> stops = w.trace.current("native", "stop");
        assertFalse(stops.isEmpty(), "stop was never called");
        Trace.Entry last = stops.get(stops.size() - 1);
        stopSeq = last.seq;
        long at = Math.max(last.t, w.ranUntil) + after;
        w.scheduler.script(at, VirtualScheduler.AFTER, () -> w.nativeLib.callback("video_process"));
        w.runFor(at + 1000);
    }

    @Then("no native event reached Java after stop returned")
    public void noEventAfterStop() {
        for (Trace.Entry e : w.trace.current("java", "event")) assertTrue(e.seq < stopSeq, "event after stop: " + e);
        boolean dropped = false;
        for (Trace.Entry e : w.trace.current("native", "callback"))
            if (e.seq > stopSeq && e.details.equals("video_process dropped")) dropped = true;
        assertTrue(dropped, "the late callback was never made: " + w.trace.current("native", "callback"));
    }

    @Then("no input was queued to the fake codec after stop returned")
    public void noQueueAfterStop() {
        for (Trace.Entry e : w.trace.current("codec", "queue")) assertTrue(e.seq < stopSeq, "queued after stop: " + e);
    }

    // ---- Decoder faults (AIR-056) ----

    @Given("^the fake codec (factory throws java\\.io\\.IOException on create|throws IllegalStateException on configure)$")
    public void codecFault(String fault) {
        if (fault.startsWith("factory")) w.codecs.createThrowsIoe = true;
        else w.codecs.throwOnConfigure = true;
    }

    @Then("the fake codec factory recorded no other create call")
    public void noOtherCreate() {
        ensureRun(w);
        assertEquals(1, w.trace.current("codec", "create").size(), "create calls " + w.trace.current("codec", "create"));
    }

    @Then("no output buffer was released with render true, and the screen is black throughout")
    public void noRenderAndBlack() {
        ensureRun(w);
        for (Trace.Entry e : w.trace.current("codec", "releaseOutput"))
            assertFalse(e.details.endsWith("render=true"), "rendered: " + e);
        for (Trace.Entry e : w.trace.current("cover", "black")) assertEquals("on", e.details, "cover " + e);
    }

    @Given("the fake codec throws MediaCodec.CodecException at {int} ms")
    public void codecErrorAt(int ms) {
        w.codecs.errorAt = ms;
    }

    /** AIR-056(d): a codec configuration re-delivered for the dead session creates no decoder. */
    @Given("the library double delivers the H.264 codec configuration again at {int} ms")
    public void configAgainAt(int ms) {
        w.scheduler.script(ms, VirtualScheduler.AFTER, () -> w.nativeLib.callback("video_set_codec_and_size"));
    }

    @Then("the fake codec was released")
    public void codecReleased() {
        ensureRun(w);
        assertFalse(w.trace.current("codec", "release").isEmpty(), "the codec was not released");
    }

    @Then("the fake codec factory recorded no create call after {int} ms")
    public void noCreateAfter(int ms) {
        ensureRun(w);
        for (Trace.Entry e : w.trace.current("codec", "create")) assertTrue(e.t <= ms, "create at " + e.t);
    }

    @Then("the fake codec released an output buffer with render true after {int} ms")
    public void renderedAfter(int ms) {
        ensureRun(w);
        for (Trace.Entry e : w.trace.current("codec", "releaseOutput"))
            if (e.t > ms && e.details.endsWith("render=true")) return;
        throw new AssertionError("nothing was rendered after " + ms + " ms: " + w.trace.current("codec", "releaseOutput").size()
                + " releases; creates " + w.trace.current("codec", "create"));
    }

    @Then("no notice add call was made and no notice is held")
    public void noNoticeHeld() {
        ensureRun(w);
        List<Fakes.Op> adds = w.g.notices.ops("add", null, w.startIndex());
        assertTrue(adds.isEmpty(), "notice adds: " + adds.size());
        for (Fakes.Window win : w.g.notices.windows)
            assertFalse(win.start == w.startIndex() && win.removedAt < 0, "notice held: " + win.kind());
    }

    // ---- The gate drives rendering (AIR-057) ----

    /** The Park getter call whose return the "released after it returned" step refers to. */
    private long anchorBegin = -1;

    /** Scripted only; the next run or Then step runs it. The gate glue is not loaded in core. */
    @Given("the car leaves Park at {int} ms")
    public void leavesPark(int out) {
        w.start();
        w.scheduler.script(out, VirtualScheduler.BEFORE, () -> w.g.park.gear = 0);
    }

    @Then("every output buffer released before the Park getter call started at {int} ms returned was released with render true")
    public void renderedBeforeReturn(int begin) {
        long end = returnOf(begin);
        int n = 0;
        for (Trace.Entry e : w.trace.current("codec", "releaseOutput"))
            if (e.t < end) {
                n++;
                assertTrue(e.details.endsWith("render=true"), "released at " + e.t + ": " + e.details);
            }
        assertTrue(n > 0, "nothing was released before " + end + " ms");
        anchorBegin = begin;
    }

    @Then("every output buffer released after it returned was released with render false")
    public void notRenderedAfterReturn() {
        long end = returnOf(anchorBegin);
        int n = 0;
        for (Trace.Entry e : w.trace.current("codec", "releaseOutput"))
            if (e.t > end) {
                n++;
                assertTrue(e.details.endsWith("render=false"), "released at " + e.t + ": " + e.details);
            }
        assertTrue(n > 0, "nothing was released after " + end + " ms");
    }

    @Then("the black cover was showing from no later than {int} ms")
    public void coverBy(int ms) {
        ensureRun(w);
        assertTrue(coverOnAt(ms), "the cover was down at " + ms + " ms: " + w.trace.current("cover", "black"));
    }

    // ---- A wall-clock jump (AIR-065) ----

    /**
     * Every runtime and gate timer reads the Scheduler port, which is uptime (AIR-065(a)); no port carries a wall clock,
     * and the static scan (AT-65) keeps it that way. The jump is therefore recorded at its time and leaves the virtual
     * uptime clock alone; the Then steps read uptime positions only.
     */
    @Given("the wall clock jumps forward by {int} ms at {int} ms, and the uptime clock does not")
    public void wallClockJumps(final int by, int at) {
        w.start();
        w.scheduler.script(at, VirtualScheduler.BEFORE, () -> {
            long before = w.scheduler.now();
            w.trace.add("clock", "wall", "+" + by);
            assertEquals(before, w.scheduler.now(), "the uptime clock moved with the wall clock");
        });
    }

    @Then("mirrored video stays permitted from {int} ms until the Park getter call started at {int} ms returned")
    public void permittedUntilReturn(int from, int begin) {
        long end = returnOf(begin);
        Fakes.Line first = null;
        for (Fakes.Line l : w.lines()) {
            String b = S3World.body(l);
            if (!b.startsWith("gate transition")) continue;
            if (first == null && b.contains(" new=permitted")) first = l;
            else if (first != null && b.contains(" new=blocked")) assertFalse(l.t < end, "blocked before " + end + " ms: " + l.text);
        }
        assertTrue(first != null, "the gate never permitted: " + w.bodies());
        assertEquals((long) from, first.t, "first permit: " + first.text);
        int n = 0;
        for (Trace.Entry e : w.trace.current("codec", "releaseOutput"))
            if (e.t >= from && e.t < end) {
                n++;
                assertTrue(e.details.endsWith("render=true"), "released at " + e.t + ": " + e.details);
            }
        assertTrue(n > 0, "nothing was released from " + from + " to " + end + " ms");
        assertFalse(w.trace.current("clock", "wall").isEmpty(), "no wall-clock jump was scripted");
    }

    /** Trace times are VirtualScheduler times, which stand for SystemClock.uptimeMillis() (AIR-065(a)). */
    @Then("the black cover was showing from no later than {int} ms of uptime")
    public void coverByUptime(int ms) {
        coverBy(ms);
    }

    @Then("the screen is black from {int} ms to {int} ms")
    public void blackBetween(int from, int to) {
        ensureRun(w);
        assertTrue(coverOnAt(from), "the cover was down at " + from + " ms");
        for (Trace.Entry e : w.trace.current("cover", "black"))
            assertFalse(e.t > from && e.t <= to && e.details.equals("off"), "cover lowered at " + e.t);
        for (Trace.Entry e : w.trace.current("codec", "releaseOutput"))
            assertFalse(e.t >= from && e.t <= to && e.details.endsWith("render=true"), "rendered at " + e.t);
    }

    @Then("the fake codec released an output buffer for every access unit queued from {int} ms to {int} ms, each with render false")
    public void drainedWithoutRender(int from, int to) {
        ensureRun(w);
        List<Trace.Entry> queued = queued();
        List<Trace.Entry> released = w.trace.current("codec", "releaseOutput");
        int n = 0;
        for (int i = 0; i < queued.size(); i++) {
            Trace.Entry q = queued.get(i);
            if (q.t < from || q.t > to) continue;
            n++;
            Trace.Entry r = releaseOf(released, i);
            assertTrue(r != null, "no output released for the unit queued at " + q.t + " ms");
            assertTrue(r.details.endsWith("render=false"), "unit queued at " + q.t + ": " + r.details);
        }
        assertTrue(n > 0, "nothing was queued from " + from + " to " + to + " ms");
    }

    @Then("the first output buffer released with render true after the gate next permits video came from an access unit queued after the gate permitted video")
    public void firstRenderIsFresh() {
        ensureRun(w);
        long blockedAt = -1, permittedAt = -1;
        for (Fakes.Line l : w.lines()) {
            String b = S3World.body(l);
            if (blockedAt < 0 && b.startsWith("gate transition prev=permitted new=blocked")) blockedAt = l.t;
            else if (blockedAt >= 0 && permittedAt < 0 && b.startsWith("gate transition prev=blocked new=permitted"))
                permittedAt = l.t;
        }
        assertTrue(permittedAt >= 0, "the gate never permitted video again: " + w.bodies());
        List<Trace.Entry> queued = queued();
        Trace.Entry first = null;
        for (Trace.Entry r : w.trace.current("codec", "releaseOutput"))
            if (first == null && r.t >= permittedAt && r.details.endsWith("render=true")) first = r;
        assertTrue(first != null, "nothing was rendered after " + permittedAt + " ms");
        int index = Integer.parseInt(first.details.substring(0, first.details.indexOf(' ')));
        assertTrue(index < queued.size(), "no unit for output " + index);
        assertTrue(queued.get(index).t >= permittedAt, "rendered a unit queued at " + queued.get(index).t
                + " ms, before video was permitted at " + permittedAt + " ms");
    }

    /** AIR-057(a)(b)(d): a unit still being decoded when video is permitted again was blocked when queued. */
    @Then("the access unit still being decoded when the gate next permitted video was released with render false")
    public void inFlightUnitNotRendered() {
        ensureRun(w);
        long blockedAt = -1, permittedAt = -1;
        for (Fakes.Line l : w.lines()) {
            String b = S3World.body(l);
            if (blockedAt < 0 && b.startsWith("gate transition prev=permitted new=blocked")) blockedAt = l.t;
            else if (blockedAt >= 0 && permittedAt < 0 && b.startsWith("gate transition prev=blocked new=permitted"))
                permittedAt = l.t;
        }
        assertTrue(permittedAt >= 0, "the gate never permitted video again: " + w.bodies());
        List<Trace.Entry> queued = queued();
        int index = -1;
        for (int i = 0; i < queued.size(); i++) {
            long t = queued.get(i).t;
            if (t < permittedAt && permittedAt < t + 5) index = i;
        }
        assertTrue(index >= 0, "no unit was being decoded at " + permittedAt + " ms");
        Trace.Entry r = releaseOf(w.trace.current("codec", "releaseOutput"), index);
        assertTrue(r != null, "no output released for the unit queued at " + queued.get(index).t + " ms");
        assertTrue(r.details.endsWith("render=false"), "unit queued at " + queued.get(index).t + ": " + r.details);
    }

    @Then("the library double saw the connection kept, with no close, from {int} ms to {int} ms")
    public void connectionKept(int from, int to) {
        ensureRun(w);
        for (Trace.Entry e : w.trace.current("socket", "close")) assertFalse(e.t >= from && e.t <= to, "closed: " + e);
        for (Trace.Entry e : w.trace.current("native", "stop")) assertFalse(e.t >= from && e.t <= to, "stopped: " + e);
        for (Trace.Entry e : w.trace.current("native", "session"))
            assertFalse(e.t >= from && e.t <= to && e.details.startsWith("ended "), "session ended: " + e);
    }

    private long returnOf(long begin) {
        ensureRun(w);
        Fakes.Call c = w.g.callAt(begin);
        assertTrue(c.done, "the call started at " + begin + " ms never returned");
        return c.end;
    }

    /** Whether the cover was up at {@code ms}; it starts up before any change. */
    private boolean coverOnAt(long ms) {
        String state = "on";
        for (Trace.Entry e : w.trace.current("cover", "black")) if (e.t <= ms) state = e.details;
        return state.equals("on");
    }

    /** Units the fake codec accepted, in queue order (output index i belongs to the i-th). */
    private List<Trace.Entry> queued() {
        List<Trace.Entry> out = new ArrayList<>();
        for (Trace.Entry e : w.trace.current("codec", "queue")) if (!e.details.startsWith("threw")) out.add(e);
        return out;
    }

    private static Trace.Entry releaseOf(List<Trace.Entry> released, int index) {
        for (Trace.Entry r : released) if (r.details.startsWith(index + " ")) return r;
        return null;
    }
}
