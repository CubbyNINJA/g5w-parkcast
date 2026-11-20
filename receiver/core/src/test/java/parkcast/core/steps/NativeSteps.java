package parkcast.core.steps;

import static parkcast.core.steps.Check.assertEquals;
import static parkcast.core.steps.Check.assertFalse;
import static parkcast.core.steps.Check.assertTrue;

import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.List;
import java.util.Locale;
import parkcast.core.doubles.FakeFileSystem;
import parkcast.core.doubles.FakeNativeLibrary;
import parkcast.core.doubles.Trace;
import parkcast.gate.harness.Fakes;
import parkcast.gate.harness.VirtualScheduler;

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

    // ---- URL, HLS and DRM requests (AIR-053(b)) ----

    @Given("^the library double delivers a (URL|HLS|DRM) request fixture for \"([^\"]*)\" at (\\d+) ms$")
    public void request(final String kind, final String url, int ms) {
        w.start();
        w.scheduler.script(ms, VirtualScheduler.AFTER, () -> w.nativeLib.request(kind, url));
    }

    @Then("the request was refused")
    public void refused() {
        w.settle();
        assertEquals(1, w.trace.current("native", "refuse").size(), "refusals " + w.trace.current("native", "refuse"));
    }

    @Then("no log record contains {string} or {string}")
    public void noLogRecordContains(String a, String b) {
        w.settle();
        noLogFileHas(a);
        noLogFileHas(b);
    }

    // ---- Audio (AIR-054(b), AIR-055(g)) ----

    @Given("the library double calls audio_process with the audio fixture bytes {string} at {int} ms")
    public void audioAt(String bytes, int ms) {
        final byte[] fixture = bytes.getBytes(StandardCharsets.US_ASCII);
        w.start();
        w.scheduler.script(ms, VirtualScheduler.AFTER, () -> w.nativeLib.audio(fixture));
    }

    @Then("audio_process returned")
    public void audioReturned() {
        w.settle();
        assertFalse(callbackReturns("audio_process").isEmpty(), "audio_process did not return: " + w.trace.current("native", null));
    }

    @Then("no log file, no file in the fake app-data directory and no file on the fake stick contains {string}")
    public void nowhere(String marker) {
        w.settle();
        noLogFileHas(marker);
        noAppFileHas(marker);
    }

    @Then("no audio event reached Java")
    public void noAudioEvent() {
        w.settle();
        for (Trace.Entry e : w.trace.current("java", "event"))
            assertFalse(e.details.toLowerCase(Locale.ROOT).contains("audio"), "audio event: " + e);
    }

    // ---- Callbacks return without waiting on Java (AIR-055(g)) ----

    /** A fixed fixture for audio_process; any other callback uses the library double's own fixture. */
    static final byte[] AUDIO_FIXTURE = "AUDIO-FIXTURE".getBytes(StandardCharsets.US_ASCII);

    private Scenario scenario;
    /** When the When step's callback call is scripted. */
    private long callAt;

    @Before
    public void keepScenario(Scenario s) {
        scenario = s;
    }

    @Given("the Java side is held busy from {int} ms to {int} ms")
    public void busy(int from, int until) {
        w.nativeLib.busyFrom = from;
        w.nativeLib.busyUntil = until;
    }

    @When("^the library double calls (\\w+) with its fixture at (\\d+) ms$")
    public void callbackAt(final String name, int ms) {
        callAt = ms;
        w.start();
        w.scheduler.script(ms, VirtualScheduler.AFTER, () -> {
            if ("audio_process".equals(name)) w.nativeLib.audio(AUDIO_FIXTURE);
            else w.nativeLib.callback(name);
        });
    }

    @Then("^(\\w+) returned before (\\d+) ms, without a crash$")
    public void returnedBefore(String name, int ms) {
        StreamSteps.ensureRun(w);
        Trace.Entry ret = null;
        for (Trace.Entry e : callbackReturns(name)) if (ret == null && e.t >= callAt) ret = e;
        assertTrue(ret != null, name + " did not return after " + callAt + " ms: " + w.trace.current("native", null));
        assertTrue(ret.t < ms, name + " called at " + callAt + " ms returned at " + ret.t);
        assertTrue(w.trace.current("crash", "uncaught").isEmpty(), "crash: " + w.trace.current("crash", "uncaught"));
    }

    @Then("the Examples' callbacks equal the list produced by the @host unguarded-callback check")
    public void examplesEqualList() throws IOException {
        TreeSet<String> examples = new TreeSet<>(examplesColumn());
        List<String> listed = new ArrayList<>();
        for (String l : Files.readAllLines(findUp("receiver/native/unguarded-callbacks.txt"), StandardCharsets.UTF_8)) {
            String t = l.trim();
            if (!t.isEmpty() && !t.startsWith("#")) listed.add(t);
        }
        assertEquals(listed.size(), examples.size(), "Examples " + examples + " against list " + listed);
        assertEquals(new TreeSet<>(listed), examples, "Examples against the committed list");
    }

    private List<Trace.Entry> callbackReturns(String name) {
        List<Trace.Entry> out = new ArrayList<>();
        for (Trace.Entry e : w.trace.current("native", "callback")) if (e.details.equals(name + " returned")) out.add(e);
        return out;
    }

    /** The first column of the Examples table this scenario's row belongs to. */
    private List<String> examplesColumn() throws IOException {
        assertTrue(scenario != null, "no scenario");
        String uri = scenario.getUri().toString();
        Path feature = uri.startsWith("file:") ? Paths.get(scenario.getUri())
                : findUp("specs/features/" + uri.substring(uri.lastIndexOf('/') + 1));
        List<String> lines = Files.readAllLines(feature, StandardCharsets.UTF_8);
        int row = scenario.getLine() - 1;
        int header = row;
        while (header > 0 && lines.get(header).trim().startsWith("|")) header--;
        assertTrue(lines.get(header).trim().startsWith("Examples:"), "no Examples above line " + scenario.getLine());
        List<String> out = new ArrayList<>();
        for (int i = header + 2; i < lines.size() && lines.get(i).trim().startsWith("|"); i++)
            out.add(lines.get(i).trim().split("\\|")[1].trim());
        return out;
    }

    /** Finds a repository file by walking up from the working directory. */
    static Path findUp(String relative) {
        for (Path d = Paths.get(System.getProperty("user.dir")).toAbsolutePath(); d != null; d = d.getParent()) {
            Path p = d.resolve(relative);
            if (Files.isRegularFile(p)) return p;
        }
        throw new AssertionError("not found above " + System.getProperty("user.dir") + ": " + relative);
    }
}
