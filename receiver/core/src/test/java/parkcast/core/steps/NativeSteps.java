package parkcast.core.steps;

import static parkcast.core.steps.Check.assertEquals;
import static parkcast.core.steps.Check.assertFalse;
import static parkcast.core.steps.Check.assertTrue;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.List;
import java.util.Locale;
import parkcast.core.doubles.FakeFileSystem;
import parkcast.core.doubles.FakeNativeLibrary;
import parkcast.core.doubles.Trace;
import parkcast.gate.harness.Fakes;

/** File 13 AIR-051 to AIR-055 steps on the library side: advertised display, HEVC, stdio, callbacks, requests. */
public final class NativeSteps {
    private final S3World w;

    public NativeSteps(S3World w) {
        this.w = w;
    }

    // ---- Advertised display and features (AIR-051(a), AIR-052(a)) ----

    @Then("the display fields the receiver advertises are width {int}, height {int} and refresh rate {int}")
    public void advertisedDisplay(int width, int height, int refresh) {
        String d = receiverInfo().details;
        assertTrue(d.contains("width=" + width + " ") && d.contains("height=" + height + " ")
                && d.contains("refresh=" + refresh + " "), "receiver_info: " + d);
    }

    @Then("the advertised features carry no HEVC bit")
    public void noHevcBit() {
        String d = receiverInfo().details;
        int i = d.indexOf("features=0x");
        assertTrue(i >= 0, "no features in receiver_info: " + d);
        long features = Long.parseLong(d.substring(i + "features=0x".length()).trim(), 16);
        assertEquals(0L, features & FakeNativeLibrary.HEVC_FEATURE, "HEVC bit in features 0x" + Long.toHexString(features));
    }

    @Then("the fake native library recorded no -h265 option or HEVC setting")
    public void noH265Option() {
        assertTrue(receiverInfo().details.contains("h265=false"), "receiver_info: " + receiverInfo().details);
        for (Trace.Entry e : w.trace.of("native", null, null)) {
            String d = e.details.toLowerCase(Locale.ROOT);
            assertFalse(d.contains("-h265") || d.contains("h265=true") || d.contains("hevc"), "HEVC setting: " + e);
        }
    }

    private Trace.Entry receiverInfo() {
        w.settle();
        List<Trace.Entry> info = w.trace.current("native", "receiver_info");
        assertEquals(1, info.size(), "receiver_info calls " + info);
        return info.get(0);
    }

    // ---- Standard output and error (AIR-055(f)) ----

    @Given("the fake native library prints {string} to stdout and to stderr on every call")
    public void prints(String marker) {
        w.nativeLib.printMarker = marker;
    }

    @Then("^at the receiver's first call into the fake native library, descriptors 1 and 2 pointed at /dev/null$")
    public void stdioBeforeFirstCall() {
        w.settle();
        List<Trace.Entry> calls = w.trace.current("native", null);
        assertFalse(calls.isEmpty(), "no library call was made");
        Trace.Entry dup = null;
        for (Trace.Entry e : w.trace.current("stdio", "dup2")) if (dup == null) dup = e;
        assertTrue(dup != null && dup.details.equals("fd 1,2 -> /dev/null"), "stdio redirect: " + dup);
        assertTrue(dup.seq < calls.get(0).seq, "first call " + calls.get(0) + " before the redirect " + dup);
    }

    @Then("no log file, no file on the fake stick and no logcat output contains {string}")
    public void nowhereWithLogcat(String marker) {
        w.settle();
        noLogFileHas(marker);
        noAppFileHas(marker);
        for (String l : w.nativeLib.logcat) assertFalse(l.contains(marker), "logcat: " + l);
    }

    // ---- Shared helpers ----

    void noLogFileHas(String marker) {
        for (Fakes.Line l : w.g.logs.lines) assertFalse(l.text.contains(marker), "log record: " + l.text);
    }

    /** The fake file system holds the app-data directory; the build has no stick double, so it holds no stick. */
    void noAppFileHas(String marker) {
        for (java.util.Map.Entry<String, FakeFileSystem.File> f : w.fs.files.entrySet()) {
            assertFalse(f.getKey().contains(marker), "file name: " + f.getKey());
            assertFalse(new String(f.getValue().bytes, java.nio.charset.StandardCharsets.ISO_8859_1).contains(marker),
                    "file contents: " + f.getKey());
        }
    }
}
