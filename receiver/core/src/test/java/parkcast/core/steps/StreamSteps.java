package parkcast.core.steps;

import static parkcast.core.steps.Check.assertEquals;
import static parkcast.core.steps.Check.assertFalse;
import static parkcast.core.steps.Check.assertTrue;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.List;
import java.util.regex.Pattern;
import parkcast.gate.harness.Fakes;
import parkcast.core.doubles.BlackCover;
import parkcast.core.doubles.Trace;

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

    @Given("a session starts at {int} ms")
    public void sessionAt(int ms) {
        w.sessionsAt.add((long) ms);
    }

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
}
