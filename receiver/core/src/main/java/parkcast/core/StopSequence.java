package parkcast.core;

import parkcast.core.port.CoverView;
import parkcast.core.port.NativeLibrary;
import parkcast.gate.Receiver;
import parkcast.gate.port.Display;

/**
 * The stop order (docs/design/S3_RECEIVER.md, AIR-071(c)): block video and the gate's exit, stop(ACCEPT),
 * stop(SESSION), stop(REGISTRATIONS), stop discovery, release the decoder, stop(PORTS), then the end marker.
 * It runs once. A step with nothing to undo is skipped silently; a step that throws is logged and the next
 * step still runs, so the end marker is always the last record.
 */
final class StopSequence {
    static final String OWNER_EXIT = "owner exit";
    static final String DESTROY = "destroy";

    private final Receiver gate;
    private final StartSequence start;
    private final Advertiser advertiser;
    private final DecoderControl decoder;
    private final NativeLibrary nativeLib;
    private final RuntimeLog log;
    private final Display display;
    private final CoverView cover;

    private boolean ran;

    StopSequence(Receiver gate, StartSequence start, Advertiser advertiser, DecoderControl decoder,
            NativeLibrary nativeLib, RuntimeLog log, Display display, CoverView cover) {
        this.display = display;
        this.cover = cover;
        this.gate = gate;
        this.start = start;
        this.advertiser = advertiser;
        this.decoder = decoder;
        this.nativeLib = nativeLib;
        this.log = log;
    }

    void run(String reason) {
        if (ran) return;
        ran = true;
        // Block video (AIR-070(a) step 1): the black cover up, the surface cleared, then the gate's exit, which
        // drops a held start-failure notice. Each part runs even if an earlier one threw.
        try {
            cover.black(true);
        } catch (RuntimeException e) {
            failed(BLOCK_VIDEO, e);
        }
        try {
            display.clear();
        } catch (RuntimeException e) {
            failed(BLOCK_VIDEO, e);
        }
        decoder.stop();
        start.stop();
        advertiser.cancel();
        try {
            gate.exit();
        } catch (RuntimeException e) {
            failed(BLOCK_VIDEO, e);
        }
        boolean nativeStarted = start.nativeStarted();
        if (nativeStarted) stopPhase(NativeLibrary.Phase.ACCEPT);
        if (nativeStarted) stopPhase(NativeLibrary.Phase.SESSION);
        if (nativeStarted) stopPhase(NativeLibrary.Phase.REGISTRATIONS);
        try {
            advertiser.stopDiscovery();
        } catch (RuntimeException e) {
            failed("stopServiceDiscovery", e);
        }
        try {
            decoder.releaseCodec();
        } catch (RuntimeException e) {
            failed("release the decoder", e);
        }
        if (nativeStarted) stopPhase(NativeLibrary.Phase.PORTS);
        log.record("parkcast stop reason=" + reason);
    }

    private void stopPhase(NativeLibrary.Phase phase) {
        try {
            nativeLib.stop(phase);
        } catch (RuntimeException e) {
            failed(stepName(phase), e);
        }
    }

    /** The step names a failed-step record uses: file 13's own names for the stop steps (AIR-070(b)). */
    static final String BLOCK_VIDEO = "block video";

    private static String stepName(NativeLibrary.Phase phase) {
        switch (phase) {
            case ACCEPT:
                return "stop accepting connections";
            case SESSION:
                return "end the session";
            case REGISTRATIONS:
                return "withdraw the mDNS registrations";
            default:
                return "close all five ports";
        }
    }

    private void failed(String step, RuntimeException e) {
        // TODO(phase B: stop records) settle the record text for a failed stop step with the design.
        log.record("stop step failed " + step + " " + e.getClass().getName());
    }
}
