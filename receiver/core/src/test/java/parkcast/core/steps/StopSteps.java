package parkcast.core.steps;

import static parkcast.core.steps.Check.assertEquals;
import static parkcast.core.steps.Check.assertFalse;
import static parkcast.core.steps.Check.assertTrue;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Arrays;
import java.util.List;
import parkcast.core.doubles.FakeNativeLibrary;
import parkcast.core.port.NativeLibrary;
import parkcast.gate.harness.Fakes;
import parkcast.gate.harness.VirtualScheduler;

/**
 * File 13's stop scenarios (AIR-070): the eight stop steps in order, a failing step that does not stop the
 * later ones, and steps with nothing to undo skipped quietly.
 *
 * <p>Each step is matched by what the doubles recorded: video blocked is the black cover raised, then the
 * native stop phases, the dns_sd withdrawals, the NSD stop, the codec release, the socket closes, and the end
 * marker in the log. A failing step is named in its record by the feature's own step names (AIR-070(b),
 * OD-194).
 */
public final class StopSteps {
    /** The feature's stop step names, in AIR-070(a) order; the end marker is the eighth step. */
    static final List<String> STEPS = Arrays.asList("block video", "stop accepting connections",
            "end the session", "withdraw the mDNS registrations", "stopServiceDiscovery", "release the decoder",
            "close all five ports");
    static final String END_MARKER = "parkcast stop reason=";
    static final String FAILED = "stop step failed ";
    /** How long to keep running after the stop to show nothing is logged after the end marker. */
    static final long AFTER_STOP_MS = 5000;

    private final S3World w;
    /** The index into {@link #STEPS} of the step made to throw; -1 for none. */
    private int failing = -1;

    public StopSteps(S3World w) {
        this.w = w;
    }

    @When("the lifecycle double delivers onDestroy at {int} ms")
    public void onDestroyAt(int t) {
        w.start();
        final parkcast.core.ReceiverRuntime r = w.runtime;
        w.scheduler.script(t, VirtualScheduler.AFTER, () -> r.onDestroy());
        w.runFor(t);
    }

    @Given("the stop step {string} throws")
    public void stepThrows(String step) {
        failing = STEPS.indexOf(step);
        assertTrue(failing >= 0, "unknown stop step " + step);
        switch (failing) {
            case 0:
                w.cover.throwOnBlack = true;
                break;
            case 1:
                w.nativeLib.stopThrows.add(NativeLibrary.Phase.ACCEPT);
                break;
            case 2:
                w.nativeLib.stopThrows.add(NativeLibrary.Phase.SESSION);
                break;
            case 3:
                w.nativeLib.stopThrows.add(NativeLibrary.Phase.REGISTRATIONS);
                break;
            case 4:
                w.nsd.throwOnStop = true;
                break;
            case 5:
                w.codecs.throwOnRelease = true;
                break;
            default:
                w.nativeLib.stopThrows.add(NativeLibrary.Phase.PORTS);
        }
    }

    @Then("the doubles recorded these steps in this order: video blocked, accepting stopped, the session ended, "
            + "both dns_sd registrations withdrawn, stopServiceDiscovery called, the fake codec released, all five "
            + "ports closed, the end marker written")
    public void eightStepsInOrder() {
        Timeline tl = new Timeline(w);
        Fakes.Line marker = marker();
        assertTrue(marker != null, "no end marker in" + tl);
        int from = -1;
        for (Timeline.Item i : tl.items) if (i.t < marker.t) from = i.index;
        assertTrue(stepsInOrder(tl, 0, from) >= 0, "steps out of order or missing in" + tl);
        assertTrue(w.g.screen.clears.contains(marker.t),
                "Display.clear() not called at the stop, clears " + w.g.screen.clears + " marker " + marker);
        for (String b : w.bodies()) assertFalse(b.startsWith(FAILED), "stop step failed: " + b);
    }

    @And("the end marker is the log's last record")
    public void markerIsLast() {
        w.runFor(w.ranUntil + AFTER_STOP_MS);
        List<String> b = w.bodies();
        assertFalse(b.isEmpty(), "no log records");
        assertTrue(b.get(b.size() - 1).startsWith(END_MARKER), "last record is not the end marker: " + b);
        int markers = 0;
        for (String s : b) if (s.startsWith(END_MARKER)) markers++;
        assertEquals(1, markers, "end markers in " + b);
    }

    @Then("the log has a record of that step failing")
    public void stepFailingLogged() {
        String want = FAILED + STEPS.get(failing) + " java.lang.IllegalStateException";
        assertTrue(w.bodies().contains(want), "no record \"" + want + "\" in " + w.bodies());
        int failures = 0;
        for (String b : w.bodies()) if (b.startsWith(FAILED)) failures++;
        assertEquals(1, failures, "stop step failure records in " + w.bodies());
    }

    @And("every stop step after it was recorded, in order, ending with the end marker")
    public void laterStepsRan() {
        Timeline tl = new Timeline(w);
        Timeline.Item threw = null;
        for (Timeline.Item i : tl.items) if (i.recordStarts(FAILED)) threw = i;
        assertTrue(threw != null, "no failure record in" + tl);
        assertTrue(stepsInOrder(tl, failing + 1, threw.index) >= 0, "later steps out of order or missing in" + tl);
        List<String> b = w.bodies();
        assertTrue(b.get(b.size() - 1).startsWith(END_MARKER), "last record is not the end marker: " + b);
    }

    @Then("stopServiceDiscovery was not called and the log has no error record for the discovery step")
    public void discoveryNotStopped() {
        assertTrue(w.trace.current("nsd", "stop").isEmpty(), "nsd.stop recorded:\n" + w.trace);
        assertTrue(w.trace.current("nsd", "discover").isEmpty(), "discoverServices was called:\n" + w.trace);
        noFailureFor("stopServiceDiscovery");
    }

    @And("the log has no error record for the registration, session or decoder steps")
    public void noQuietStepErrors() {
        noFailureFor("withdraw the mDNS registrations");
        noFailureFor("end the session");
        noFailureFor("release the decoder");
        assertTrue(w.trace.current("codec", "release").isEmpty(), "a codec was released:\n" + w.trace);
    }

    @And("all five ports were closed, and then the end marker was written")
    public void portsThenMarker() {
        Timeline tl = new Timeline(w);
        Timeline.Item stopPorts = last(tl.of("native", "stop"));
        assertTrue(stopPorts != null && stopPorts.entry.details.equals("PORTS"), "stop(PORTS) not last in" + tl);
        assertTrue(stepsInOrder(tl, 6, stopPorts.index) >= 0, "ports or end marker missing in" + tl);
        assertEquals(END_MARKER + "owner exit", w.bodies().get(w.bodies().size() - 1), "last record");
    }

    private void noFailureFor(String step) {
        for (String b : w.bodies()) assertFalse(b.startsWith(FAILED + step), "error record for " + step + ": " + b);
    }

    /**
     * Finds steps {@code first} to 8 in order after item {@code from}. Returns the index of the end marker, or
     * -1 when a step is missing or out of order.
     */
    private int stepsInOrder(Timeline tl, int first, int from) {
        int at = from;
        for (int step = first; step <= STEPS.size(); step++) {
            at = stepEnd(tl, step, at);
            if (at < 0) return -1;
        }
        return at;
    }

    /** The index of the last item that shows {@code step} done, all found after {@code from}; -1 if not. */
    private int stepEnd(Timeline tl, int step, int from) {
        switch (step) {
            case 0:
                return next(tl, from, "cover", "black", "on", 1);
            case 1:
                return next(tl, from, "native", "stop", "ACCEPT", 1);
            case 2:
                return next(tl, from, "native", "stop", "SESSION", 1);
            case 3:
                return next(tl, from, "dnssd", "withdraw", null, 2);
            case 4:
                return next(tl, from, "nsd", "stop", null, 1);
            case 5:
                return next(tl, from, "codec", "release", null, 1);
            case 6:
                return next(tl, from, "socket", "close", null, FakeNativeLibrary.PORTS.length);
            default:
                for (Timeline.Item i : tl.items) if (i.index > from && i.recordStarts(END_MARKER)) return i.index;
                return -1;
        }
    }

    /** Finds {@code n} matching entries after {@code from}; returns the last one's index, or -1. */
    private static int next(Timeline tl, int from, String cause, String name, String details, int n) {
        int found = 0;
        for (Timeline.Item i : tl.items) {
            if (i.index <= from || !i.is(cause, name)) continue;
            if (details != null && !i.entry.details.equals(details)) continue;
            if (++found == n) return i.index;
        }
        return -1;
    }

    private static Timeline.Item last(List<Timeline.Item> items) {
        return items.isEmpty() ? null : items.get(items.size() - 1);
    }

    private Fakes.Line marker() {
        Fakes.Line found = null;
        for (Fakes.Line l : w.lines()) if (S3World.body(l).startsWith(END_MARKER)) found = l;
        return found;
    }
}
