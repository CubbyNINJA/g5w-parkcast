package parkcast.core;

import parkcast.core.port.Codec;
import parkcast.core.port.CodecFactory;
import parkcast.core.port.NativeEvents;
import parkcast.core.port.NativeLibrary;

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
        if (stopped || failed || codec != null) return;
        try {
            codec = codecs.create(DECODER);
        } catch (Exception e) {
            failed = true;
            log.record(UNAVAILABLE + " " + e.getClass().getName());
            nativeLib.stop(NativeLibrary.Phase.SESSION);
            log.startFailed(UNAVAILABLE);
            return;
        }
        // TODO(phase B: frame pipeline) configure the codec on the cover surface and start it.
    }

    @Override
    public void accessUnit(int generation, long ptsMs) {
        if (stopped || codec == null) return;
        // TODO(phase B: frame pipeline) queue the access unit, dequeue and release through the gate.
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
}
