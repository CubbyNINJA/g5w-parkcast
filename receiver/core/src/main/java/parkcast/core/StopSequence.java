package parkcast.core;

import parkcast.core.port.NativeLibrary;
import parkcast.gate.Receiver;

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

    private boolean ran;

    StopSequence(Receiver gate, StartSequence start, Advertiser advertiser, DecoderControl decoder,
            NativeLibrary nativeLib, RuntimeLog log) {
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
        // TODO(phase B: black cover) put the black cover up before the gate exit (AIR-071(c)).
        decoder.stop();
        start.stop();
        advertiser.cancel();
        try {
            gate.exit();
        } catch (RuntimeException e) {
            failed("gate exit", e);
        }
        boolean nativeStarted = start.nativeStarted();
        if (nativeStarted) stopPhase(NativeLibrary.Phase.ACCEPT);
        if (nativeStarted) stopPhase(NativeLibrary.Phase.SESSION);
        if (nativeStarted) stopPhase(NativeLibrary.Phase.REGISTRATIONS);
        try {
            advertiser.stopDiscovery();
        } catch (RuntimeException e) {
            failed("nsd stop", e);
        }
        try {
            decoder.releaseCodec();
        } catch (RuntimeException e) {
            failed("codec release", e);
        }
        if (nativeStarted) stopPhase(NativeLibrary.Phase.PORTS);
        log.record("parkcast stop reason=" + reason);
    }

    private void stopPhase(NativeLibrary.Phase phase) {
        try {
            nativeLib.stop(phase);
        } catch (RuntimeException e) {
            failed("stop " + phase.name(), e);
        }
    }

    private void failed(String step, RuntimeException e) {
        // TODO(phase B: stop records) settle the record text for a failed stop step with the design.
        log.record("stop step failed " + step + " " + e.getClass().getName());
    }
}
