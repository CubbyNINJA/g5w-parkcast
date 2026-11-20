package parkcast.core.steps;

import static parkcast.core.steps.Check.assertEquals;
import static parkcast.core.steps.Check.assertTrue;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import parkcast.core.ReceiverRuntime;
import parkcast.core.adapter.AccReceiver;
import parkcast.core.adapter.ReverseReceiver;
import parkcast.core.doubles.FakeBroadcasts;
import parkcast.core.doubles.FakeThreads;
import parkcast.core.doubles.Trace;
import parkcast.gate.harness.Fakes;
import parkcast.gate.harness.VirtualScheduler;

/**
 * Steps for the broadcast adapters (AIR-059, AIR-060): the ACC receiver registered on a worker thread with a
 * main-looper Handler, and the REVERSE_ON receiver registered in onCreate. Both adapters run over a fake
 * broadcast source in place of the gate's ACC and reverse doubles.
 */
public final class BroadcastSteps {
    private final S3World w;
    private FakeBroadcasts broadcasts;
    private FakeThreads threads;

    public BroadcastSteps(S3World w) {
        this.w = w;
    }

    /** In AIR-059 and AIR-060 scenarios the receiver registers through the Android adapters, not the gate doubles. */
    @Before("@AIR-059 or @AIR-060")
    public void useAdapters() {
        broadcasts = new FakeBroadcasts(w.trace);
        threads = new FakeThreads(w.scheduler);
        w.accOverride = new AccReceiver(broadcasts, threads);
        w.reverseOverride = new ReverseReceiver(broadcasts);
    }

    private FakeBroadcasts broadcasts() {
        if (broadcasts == null) throw new IllegalStateException("the broadcast adapters are not in place in this scenario");
        return broadcasts;
    }

    private FakeBroadcasts.Registration only(String action) {
        List<FakeBroadcasts.Registration> regs = broadcasts().of(action);
        assertEquals(1, regs.size(), "registerReceiver calls for " + action + ": " + regs.size());
        return regs.get(0);
    }

    // ------------------------------------------------------------------ AIR-059: ACC

    @Then("^the ACC registerReceiver call for (\\S+) ran on a thread other than the main thread$")
    public void accOffMain(String action) {
        w.settle();
        FakeBroadcasts.Registration reg = only(action);
        assertTrue(reg.thread != broadcasts.mainThread, "registered on the main thread: " + reg.entry);
        assertTrue(reg.thread != null && reg.thread != threads.mainThread, "registered on " + reg.thread);
    }

    @Then("it passed no permission and a Handler on the main looper")
    public void accNoPermissionMainHandler() {
        FakeBroadcasts.Registration reg = only(FakeBroadcasts.ACC);
        assertEquals(null, reg.permission, "permission");
        assertTrue(reg.handler == broadcasts.mainHandler, "handler: " + reg.handler);
    }

    @Given("ACC registration throws an exception at {int} ms")
    public void accThrows(int t) {
        atStart(t);
        broadcasts().throwOn.put(FakeBroadcasts.ACC, new SecurityException("synthetic ACC registration failure"));
    }

    @Given("ACC registration returns no sticky broadcast at {int} ms")
    public void accNoSticky(int t) {
        atStart(t);
        broadcasts().sticky.remove(FakeBroadcasts.ACC);
    }

    /** The ACC registration is made in the first onResume, at 0 ms; no other time is meaningful here. */
    private static void atStart(int t) {
        if (t != 0) throw new IllegalArgumentException("ACC registration is made at 0 ms, not " + t + " ms");
    }

    @Then("the registration-failure notice is shown at {int} ms")
    public void regNoticeAt(int t) {
        w.settle();
        List<Long> added = new ArrayList<>();
        for (Fakes.Window win : w.g.notices.windows(Fakes.REG, w.scheduler.startIndex)) added.add(win.addedAt);
        assertTrue(added.contains((long) t), "registration-failure notice added at " + added + ", not " + t + " ms");
    }

    // ------------------------------------------------------------------ AIR-060: REVERSE_ON

    @When("the lifecycle double drives onCreate, onStart and onResume at {int} ms")
    public void lifecycleUp(int t) {
        if (w.started) throw new IllegalStateException("the receiver has already started");
        w.started = true;
        final ReceiverRuntime r = w.build();
        w.runtime = r;
        final boolean focus = w.focusAt == 0;
        final Trace trace = w.trace;
        w.scheduler.script(t, VirtualScheduler.FIRST, () -> {
            trace.add("lifecycle", "onCreate called", "");
            r.onCreate();
            trace.add("lifecycle", "onCreate returned", "");
            r.onStart();
            r.onResume();
            if (focus) r.onWindowFocusChanged(true);
        });
        w.scheduler.runUntil(t);
        w.ranUntil = t;
    }

    @Then("^the registerReceiver call for (\\S+) returned before onCreate returned, on the main thread$")
    public void reverseInOnCreate(String action) {
        FakeBroadcasts.Registration reg = only(action);
        assertTrue(!reg.threw && reg.entry != null, "the registration did not return: " + reg.entry);
        List<Trace.Entry> called = w.trace.current("lifecycle", "onCreate called");
        List<Trace.Entry> returned = w.trace.current("lifecycle", "onCreate returned");
        assertEquals(1, returned.size(), "onCreate returns");
        assertTrue(reg.entry.seq > called.get(0).seq && reg.entry.seq < returned.get(0).seq,
                "registerReceiver returned outside onCreate: " + reg.entry + "\n" + w.trace);
        assertTrue(reg.thread == broadcasts.mainThread, "registered on " + reg.thread.getName());
    }

    @Then("^its IntentFilter has only the action (\\S+)$")
    public void onlyAction(String action) {
        FakeBroadcasts.Registration reg = only(action);
        assertEquals(Collections.singletonList(action), reg.actions, "IntentFilter actions");
    }
}
