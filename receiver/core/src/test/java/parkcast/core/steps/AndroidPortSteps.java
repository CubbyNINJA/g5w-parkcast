package parkcast.core.steps;

import static parkcast.core.steps.Check.assertEquals;
import static parkcast.core.steps.Check.assertFalse;
import static parkcast.core.steps.Check.assertTrue;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import parkcast.core.adapter.ConfigAdapter;
import parkcast.core.adapter.ParkCall;
import parkcast.core.adapter.RegulationAdapter;
import parkcast.core.doubles.FakeGear;
import parkcast.core.doubles.FakeThreads;
import parkcast.core.doubles.RecordingParkGetter;
import parkcast.core.doubles.Trace;
import parkcast.core.port.RegulationBinder;
import parkcast.gate.port.RegulationSource;
import parkcast.gate.harness.Fakes;

/**
 * Steps for the Android port adapters (AIR-058, AIR-063): the Park getter over getGearPStatus() on a worker
 * thread, and the config file read from its one path. Each step asserts on what the doubles recorded.
 */
public final class AndroidPortSteps {
    private final S3World w;
    private FakeThreads threads;
    private FakeGear gear;
    private RecordingParkGetter recorder;
    /** The call the scenario is about, by its start time. */
    private long faultAt = -1;

    public AndroidPortSteps(S3World w) {
        this.w = w;
    }

    /** Puts the Park getter adapter, over the gear double, in place of the gate's Park double. */
    private FakeGear gear() {
        if (gear == null) {
            threads = new FakeThreads(w.scheduler);
            gear = new FakeGear(w.scheduler, threads, w.trace);
            final S3World world = w;
            ParkCall call = new ParkCall(w.scheduler, threads, gear, line -> world.runtime.record(line));
            recorder = new RecordingParkGetter(w.scheduler, call);
            w.parkOverride = recorder;
        }
        return gear;
    }

    @Given("each getGearPStatus\\() call takes {int} ms to reply, except the one started at {int} ms, which takes {int} ms")
    public void callsTake(int ms, int at, int slow) {
        gear().defaultMs = ms;
        gear.durationAt.put((long) at, (long) slow);
    }

    @Given("the getGearPStatus\\() call started at {int} ms {}")
    public void callFails(int at, String fault) {
        gear();
        faultAt = at;
        long t = at;
        switch (fault) {
            case "does not reply within 200 ms":
                gear.durationAt.put(t, 300L);
                break;
            case "replies exactly 200 ms after the call started":
                gear.durationAt.put(t, 200L);
                break;
            case "throws SecurityException":
                gear.faultAt.put(t, new SecurityException("denied"));
                break;
            case "throws android.os.DeadObjectException":
                gear.faultAt.put(t, new android.os.DeadObjectException());
                break;
            case "fails because VehicleInfoManager's class is missing":
                gear.faultAt.put(t, new NoClassDefFoundError("VehicleInfoManager"));
                break;
            default:
                throw new IllegalArgumentException("no getGearPStatus() fault: " + fault);
        }
    }

    @Then("VehicleInfoManager.getGearPStatus\\() was called only at {}")
    public void calledOnlyAt(String times) {
        List<Long> want = new ArrayList<>();
        Matcher m = Pattern.compile("\\d+").matcher(times);
        while (m.find()) want.add(Long.parseLong(m.group()));
        List<Long> got = new ArrayList<>();
        for (FakeGear.Call c : gear().calls) got.add(c.begin);
        assertEquals(want, got, "getGearPStatus() start times");
    }

    @Then("every call ran on a thread other than the main thread")
    public void offMainThread() {
        assertFalse(gear().calls.isEmpty(), "no getGearPStatus() call was made");
        for (FakeGear.Call c : gear.calls)
            assertTrue(c.thread != threads.mainThread, "a call ran on the main thread: " + c);
    }

    @Then("no call started while another was in flight")
    public void oneAtATime() {
        List<FakeGear.Call> calls = gear().calls;
        for (int i = 1; i < calls.size(); i++)
            assertTrue(calls.get(i).begin >= calls.get(i - 1).end,
                    "call " + calls.get(i) + " started while " + calls.get(i - 1) + " was in flight");
    }

    @Then("the gate received {string} for that call")
    public void gateReceived(String what) {
        gear();
        assertEquals(what, recorder.received.get(faultAt), "what the gate received for the call at " + faultAt
                + " ms, of " + recorder.received);
    }

    @Then("the log has a Park reading {string} naming {string}")
    public void logHasReading(String what, String named) {
        String start = "park reading " + what;
        for (String b : w.bodies())
            if (b.startsWith(start) && b.contains(named)) return;
        throw new AssertionError("no record starting \"" + start + "\" naming \"" + named + "\" in " + w.bodies());
    }

    @Then("mirrored video is blocked from no later than {int} ms")
    public void blockedBy(int t) {
        boolean permitted = false;
        for (Fakes.Line l : w.lines()) {
            String b = S3World.body(l);
            if (!b.startsWith("gate transition")) continue;
            if (b.contains(" new=permitted")) permitted = true;
            else if (permitted && b.contains(" new=blocked")) {
                assertTrue(l.t <= t, "first block after the permit came at " + l.t + " ms: " + l.text);
                return;
            }
        }
        throw new AssertionError("mirrored video was never permitted and then blocked: " + w.bodies());
    }

    /** The status the fake regulation service gives at registration, and what the adapter reported. */
    private int regulationStatus;
    private final List<String> regulationReports = new ArrayList<>();

    @Given("the fake com.hkmc.regulation service reports status {int} when asked")
    public void regulationStatus(int status) {
        regulationStatus = status;
    }

    @When("the Android regulation adapter registers")
    public void regulationRegisters() {
        final int status = regulationStatus;
        RegulationBinder service = new RegulationBinder() {
            @Override
            public int getStatus() {
                return status;
            }

            @Override
            public void addListener(Listener l) {
            }

            @Override
            public void removeListener(Listener l) {
            }
        };
        new RegulationAdapter(service).register(new RegulationSource.RegulationListener() {
            @Override
            public void state(String s) {
                regulationReports.add("state \"" + s + "\"");
            }

            @Override
            public void unavailable(String reason) {
                regulationReports.add("unavailable \"" + reason + "\"");
            }
        });
    }

    @Then("the regulation adapter reported {}")
    public void regulationReported(String what) {
        List<String> want = new ArrayList<>();
        want.add(what);
        assertEquals(want, regulationReports, "what the regulation adapter reported");
    }

    @Then("the fake file system recorded config reads only of {}")
    public void configReadsOnlyOf(String path) {
        List<String> reads = w.configReader().reads;
        assertFalse(reads.isEmpty(), "no config read was recorded");
        for (String p : reads) assertEquals(path, p, "config read");
    }

    @Then("no create or write of that path")
    public void noCreateOrWrite() {
        noCreate();
        for (Trace.Entry e : w.trace.of("fs", null, null))
            assertFalse(!e.name.equals("read") && e.details.startsWith(ConfigAdapter.PATH), "config path touched: " + e);
    }

    @Given("^(/.*)/receiver\\.conf does not exist$")
    public void configMissing(String dir) {
        assertEquals(ConfigAdapter.PATH, dir + "/receiver.conf", "config path");
        w.g.config.missing = true;
    }

    @Then("the gate's start record has the effective value true with {string}")
    public void startRecordOn(String with) {
        for (String b : w.bodies())
            if (b.startsWith("start check=")) {
                assertTrue(b.startsWith("start check=on ") && b.contains(with), "start record: " + b);
                return;
            }
        throw new AssertionError("no start record in " + w.bodies());
    }

    @Then("the fake file system recorded no create of that path")
    public void noCreate() {
        for (Trace.Entry e : w.trace.of("fs", "create", null))
            assertFalse(e.details.startsWith(ConfigAdapter.PATH), "config path created: " + e);
    }
}
