package parkcast.core;

import java.util.ArrayDeque;
import parkcast.core.port.Codec;
import parkcast.core.port.CodecFactory;
import parkcast.core.port.CoverView;
import parkcast.core.port.NativeEvents;
import parkcast.core.port.NativeLibrary;
import parkcast.gate.Receiver;
import parkcast.gate.port.Frame;
import parkcast.gate.port.Scheduler;

/**
 * Receives the library's events on the Java side and owns the decoder. A decoder that cannot be created is
 * a start failure (AIR-069(c)): the session is stopped and the notice names "decoder unavailable". Events
 * that arrive after exit are dropped.
 */
final class DecoderControl implements NativeEvents {
    static final String DECODER = "OMX.TCC.avcdec";
    static final String UNAVAILABLE = "decoder unavailable";

    private final CodecFactory codecs;
    private final NativeLibrary nativeLib;
    private final RuntimeLog log;

    private Codec codec;
    private boolean stopped;
    private boolean failed;

    DecoderControl(CodecFactory codecs, NativeLibrary nativeLib, RuntimeLog log) {
        this.codecs = codecs;
        this.nativeLib = nativeLib;
        this.log = log;
    }

    @Override
    public void sessionStarted(int generation) {
        if (stopped) return;
        // TODO(phase B: session records) log the session start with its generation (AIR-071).
    }

    @Override
    public void codecConfig(int generation, String codecName, int width, int height) {
        if (stopped || failed || codec != null || generation == deadGen) return;
        if (HEVC.equals(codecName)) {
            log.record("session " + generation + " codec h265 " + width + "x" + height);
            log.record(HEVC_REFUSED);
            nativeLib.stop(NativeLibrary.Phase.SESSION);
            return;
        }
        try {
            codec = codecs.create(DECODER);
        } catch (Exception e) {
            failed = true;
            log.record(UNAVAILABLE + " " + e.getClass().getName());
            nativeLib.stop(NativeLibrary.Phase.SESSION);
            log.startFailed(UNAVAILABLE);
            return;
        }
        try {
            codec.configure(MIME, width, height, cover.surface());
            codec.start();
        } catch (Exception e) {
            Codec c = codec;
            codec = null;
            failed = true;
            try {
                c.release();
            } catch (Exception ignored) {
                // The decoder is already unusable; the start failure below is what counts.
            }
            log.record(UNAVAILABLE + " " + e.getClass().getName());
            nativeLib.stop(NativeLibrary.Phase.SESSION);
            log.startFailed(UNAVAILABLE);
            return;
        }
        codecGen = generation;
        Letterbox box = Letterbox.fit(width, height);
        cover.videoBox(box.x, box.y, box.width, box.height);
        log.record("session " + generation + " codec h264 " + width + "x" + height);
        log.record("decoder " + DECODER + " configured");
    }

    @Override
    public void accessUnit(int generation, long ptsMs) {
        if (stopped || codec == null || generation != codecGen) return;
        try {
            pending.add(gate.videoPermitted());
            codec.queue(new byte[0], ptsMs * 1000L);
        } catch (Exception e) {
            decoderError(e);
            return;
        }
        scheduleDrain();
    }

    @Override
    public void sessionEnded(int generation) {
        if (stopped) return;
        // TODO(phase B: session records) log the session end and the withheld count.
    }

    @Override
    public void advertiseLost(int generation) {
        if (stopped) return;
        // TODO(phase B: advertise lost) handle the lost registration.
    }

    @Override
    public void log(int generation, String record) {
        if (stopped) return;
        // TODO(phase B: native records) filter against the allowlist and withhold key material (AIR-073, AIR-083).
        if (record.startsWith("refused ")) log.record(record);
    }

    /** Exit: drop every later event. */
    void stop() {
        stopped = true;
    }

    /** Releases the decoder if one was created. Returns false when there was nothing to release. */
    boolean releaseCodec() {
        if (codec == null) return false;
        Codec c = codec;
        codec = null;
        c.release();
        return true;
    }

    // ---- The frame pipeline (AIR-051(b), AIR-056, AIR-057) ----

    static final String HEVC = "h265";
    static final String HEVC_REFUSED = "hevc refused";
    static final String DECODER_ERROR = "decoder error";
    static final String MIME = "video/avc";
    /** How long after a queue the decoder is drained. */
    static final long DRAIN_MS = 5;

    private Scheduler scheduler;
    private CodecDisplay display;
    private CoverView cover;
    private Receiver gate;
    private int codecGen = -1;
    private int deadGen = -1;
    private long frameId;
    private boolean drainDue;
    /** Per queued unit, whether video was permitted when it was queued; oldest first. */
    private final ArrayDeque<Boolean> pending = new ArrayDeque<Boolean>();

    /** Connects the pipeline to the clock, the display the gate draws on, the cover and the gate. */
    void attach(Scheduler scheduler, CodecDisplay display, CoverView cover, Receiver gate) {
        this.scheduler = scheduler;
        this.display = display;
        this.cover = cover;
        this.gate = gate;
    }

    private void scheduleDrain() {
        if (drainDue) return;
        drainDue = true;
        scheduler.at(scheduler.now() + DRAIN_MS, new Runnable() {
            @Override
            public void run() {
                drainDue = false;
                drain();
            }
        });
    }

    /** Releases every ready output buffer: rendered only when the gate shows it (AIR-057(a), (b), (d)). */
    private void drain() {
        if (stopped || codec == null) return;
        try {
            int index;
            while ((index = codec.dequeue()) >= 0) {
                Boolean fresh = pending.poll();
                display.offer(fresh != null && fresh.booleanValue());
                gate.onFrame(new Frame(frameId++, scheduler.now()));
                codec.release(index, display.shown());
            }
        } catch (Exception e) {
            decoderError(e);
            return;
        }
        if (!pending.isEmpty()) scheduleDrain();
    }

    /** A mid-session decoder error: release, log, end the session, no retry and no notice (AIR-056(d), (e)). */
    private void decoderError(Exception e) {
        Codec c = codec;
        codec = null;
        deadGen = codecGen;
        pending.clear();
        display.raise();
        try {
            if (c != null) c.release();
        } catch (Exception ignored) {
            // Already failed; the session ends either way.
        }
        log.record(DECODER_ERROR + " " + e.getClass().getName());
        nativeLib.stop(NativeLibrary.Phase.SESSION);
    }
}
