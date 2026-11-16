package parkcast.gate;

import java.util.ArrayList;
import java.util.List;
import parkcast.gate.harness.Fakes;
import parkcast.gate.harness.FrameSource;
import parkcast.gate.harness.VirtualScheduler;

/**
 * State shared by every step class of one scenario (injected by cucumber-picocontainer): the doubles, the
 * clock, the frame source, start options and the current {@link Receiver}. Given steps set up doubles and
 * queue scripted events before the start; "the receiver starts" builds the receiver and runs the lifecycle
 * at 0 ms; "runs for N ms" advances the clock to N inclusive. Steps assert on the doubles only.
 */
public final class World {
    public final VirtualScheduler scheduler = new VirtualScheduler();
    public final Fakes.Logs logs = new Fakes.Logs(scheduler);
    public final Fakes.Acc acc = new Fakes.Acc(scheduler, logs);
    public final Fakes.Park park = new Fakes.Park(scheduler, acc);
    public final Fakes.Notices notices = new Fakes.Notices(scheduler);
    public final Fakes.Config config = new Fakes.Config();
    public final Fakes.Build build = new Fakes.Build();
    public final Fakes.Screen screen = new Fakes.Screen(scheduler);
    public final Fakes.Session session = new Fakes.Session();
    public final Fakes.Vehicle vehicle = new Fakes.Vehicle();
    public final Fakes.Regulation regulation = new Fakes.Regulation();
    public final Fakes.Reverse reverse = new Fakes.Reverse();
    public final FrameSource frames = new FrameSource(scheduler);

    /** Display width handed to the receiver (the head unit's 1280 px wide screen). */
    public int displayWidth = 1280;
    /** When the activity window first gains focus; 0 = inside the start, -1 = no activity window. */
    public long focusAt = 0;
    /**
     * The scheduler rank of the first focus gain. FIRST puts a focus at 0 ms inside the lifecycle; a later rank
     * delivers it after the scripted events of that same millisecond (the activity is still covered then).
     */
    public int focusRank = VirtualScheduler.FIRST;
    /** What "the parking check is X and mirrored video is Y" set up ("on"/"off", "permitted"/"blocked"), or null. */
    public String gateCheck, gateVideo;
    /** How long a Then step runs a receiver that was started but never run. */
    public long settleMs = 1000;

    public Receiver receiver;
    public boolean started;
    /** The time the current start has been run to, or -1 if it has not run past its start. */
    public long ranUntil = -1;
    /** The last Park getter call a step named by its start time (for "the call after it"). */
    public long lastAnchor = -1;
    /** Set by a step that stands for an install: "the installed receiver.conf holds X" then checks, not sets. */
    public boolean confIsOutcome;
    /** Index into {@code screen.clears} where the current start's clears begin. */
    private int clearsMark;

    /** Builds the receiver and runs its lifecycle at 0 ms: onCreate, onStart, onResume, first focus gain. */
    public void start() {
        if (started) return;
        started = true;
        final Receiver r = new Receiver(scheduler, park, acc, reverse, notices, logs, config, build, regulation,
                screen, session, vehicle, displayWidth);
        receiver = r;
        final long focus = focusAt;
        final boolean inLifecycle = focus == 0 && focusRank == VirtualScheduler.FIRST;
        scheduler.script(0, VirtualScheduler.FIRST, () -> {
            r.onCreate();
            r.onStart();
            r.onResume();
            if (inLifecycle) r.onWindowFocusChanged(true);
        });
        if (focus >= 0 && !inLifecycle) scheduler.script(focus, focusRank, () -> r.onWindowFocusChanged(true));
        frames.startFor(r);
        scheduler.runUntil(0);
        ranUntil = 0;
    }

    /** Starts if needed, then advances the clock to {@code ms} inclusive. */
    public void runFor(long ms) {
        start();
        scheduler.runUntil(ms);
        ranUntil = Math.max(ranUntil, ms);
    }

    /** Used by Then steps: a receiver that was never run is started and run for {@link #settleMs}. */
    public void settle() {
        if (!started || ranUntil <= 0) runFor(settleMs);
    }

    /** Scripts {@code exit()} on the current receiver at {@code t}, after the gate's own tasks then. */
    public void exitAt(long t) {
        start();
        final Receiver r = receiver;
        scheduler.script(t, VirtualScheduler.AFTER, r::exit);
    }

    /**
     * A new process: exit() on the old receiver now, its tasks cancelled, the clock back at 0, ACC and the
     * Park getter reset; logs and receiver.conf are kept. The new receiver is built by the next start.
     */
    public void restart() {
        if (receiver != null) receiver.exit();
        scheduler.newStart();
        park.newStart();
        acc.newStart();
        clearsMark = screen.clears.size();
        receiver = null;
        started = false;
        ranUntil = -1;
        lastAnchor = -1;
    }

    // ------------------------------------------------------------ observations of the current start

    public List<FrameSource.Tick> ticks() {
        return frames.current();
    }

    public boolean shown(FrameSource.Tick k) {
        return screen.shownIds.contains(k.id);
    }

    /** Times of Display.clear() in the current start. */
    public List<Long> clears() {
        return new ArrayList<>(screen.clears.subList(clearsMark, screen.clears.size()));
    }

    public List<Fakes.Shown> shows() {
        List<Fakes.Shown> out = new ArrayList<>();
        for (Fakes.Shown s : screen.shows) if (s.start == scheduler.startIndex) out.add(s);
        return out;
    }

    public List<Fakes.Line> lines() {
        List<Fakes.Line> out = new ArrayList<>();
        for (Fakes.Line l : logs.lines) if (l.start == scheduler.startIndex) out.add(l);
        return out;
    }

    public List<Fakes.Call> calls() {
        return park.current();
    }

    /** The current start's Park getter call that began at {@code begin}. */
    public Fakes.Call callAt(long begin) {
        for (Fakes.Call c : calls()) if (c.begin == begin) return c;
        throw new AssertionError("no Park getter call started at " + begin + " ms; calls: " + calls());
    }

    /** The call made right after {@code c}, or null. */
    public Fakes.Call callAfter(Fakes.Call c) {
        for (Fakes.Call x : calls()) if (x.index == c.index + 1) return x;
        return null;
    }

    // ------------------------------------------------------------ video checks on the recordings

    /** No frame delivered at a time in [from, to) was shown. */
    public void assertNoneShownIn(long from, long to) {
        for (FrameSource.Tick k : ticks())
            if (k.t >= from && k.t < to && shown(k))
                throw new AssertionError("frame at " + k.t + " ms was shown inside [" + from + ", " + to + ") ms; "
                        + videoSummary());
    }

    /** The first frame delivered after {@code u} was shown. */
    public void assertNextShownAfter(long u) {
        for (FrameSource.Tick k : ticks())
            if (k.t > u) {
                if (!shown(k))
                    throw new AssertionError("the first frame after " + u + " ms (" + k.t + " ms) was not shown; "
                            + videoSummary());
                return;
            }
        throw new AssertionError("no frame was delivered after " + u + " ms; ran until " + ranUntil + " ms");
    }

    /** Every frame delivered after {@code u} was shown, and the display was not cleared after {@code u}. */
    public void assertAllShownAfter(long u) {
        assertNextShownAfter(u);
        for (FrameSource.Tick k : ticks())
            if (k.t > u && !shown(k))
                throw new AssertionError("frame at " + k.t + " ms (after " + u + " ms) was not shown; " + videoSummary());
        for (long c : clears())
            if (c > u) throw new AssertionError("display cleared at " + c + " ms (after " + u + " ms); " + videoSummary());
    }

    /** If anything was shown before {@code a}, the display was cleared at exactly {@code a}. */
    public void assertClearedAtIfShownBefore(long a) {
        boolean before = false;
        for (Fakes.Shown s : shows()) if (s.t < a) before = true;
        if (before && !clears().contains(a))
            throw new AssertionError("video shown before " + a + " ms was not cleared at " + a + " ms; " + videoSummary());
    }

    public String videoSummary() {
        List<Long> shownAt = new ArrayList<>();
        for (Fakes.Shown s : shows()) shownAt.add(s.t);
        return "shown at " + shownAt + ", cleared at " + clears() + ", calls " + calls();
    }

    // ------------------------------------------------------------ the log writer on its own (11, AT-39)

    private GateLog sizeLog;

    /** The folder the LogFiles port maps the receiver's log files to on the unit. */
    public static String logFolder() {
        return GateLog.FOLDER;
    }

    /** The most bytes one log file may hold (AIR-039). */
    public static long logFileCap() {
        return GateLog.FILE_CAP;
    }

    /**
     * Logs one record of exactly {@code n} bytes, its newline terminator included, through a log writer on
     * this scenario's log files and clock. The writer's time prefix would make records under a few bytes
     * impossible, so the size scenarios use this seam instead of a receiver.
     */
    public void logRecordOfSize(long n) {
        if (n < 1 || n > Integer.MAX_VALUE) throw new IllegalArgumentException("record size " + n);
        byte[] b = new byte[(int) n];
        java.util.Arrays.fill(b, (byte) 'x');
        b[b.length - 1] = (byte) '\n';
        if (sizeLog == null) sizeLog = new GateLog(logs, scheduler);
        sizeLog.put(b);
    }
}
