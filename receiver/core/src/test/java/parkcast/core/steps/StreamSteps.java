package parkcast.core.steps;

import static parkcast.core.steps.Check.assertEquals;
import static parkcast.core.steps.Check.assertTrue;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.List;
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
}
