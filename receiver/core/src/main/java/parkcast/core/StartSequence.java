package parkcast.core;

import parkcast.core.port.AppFiles;
import parkcast.core.port.NativeLibrary;

/**
 * The start order after the marker (AIR-069(f), docs/design/S3_RECEIVER.md): CarPlay probe slot, hotspot
 * check, KEYCHECK, BIND, advertising, then ACCEPT. The hotspot check and the key check always both run; each
 * failure is logged in order and only the first cause gets the notice. A later stage runs only if every
 * earlier one passed.
 */
final class StartSequence {
    static final String KEY_FILE = "parkcast.key";
    static final String STDIO_FAILED = "stdio redirect failed";
    static final String KEY_UNAVAILABLE = "identity key unavailable";
    static final String PORT_BUSY = "port busy";
    static final String ADVERTISE_FAILED = "advertise failed";

    private final HotspotCheck hotspot;
    private final AppFiles files;
    private final NativeLibrary nativeLib;
    private final Advertiser advertiser;
    private final DecoderControl decoder;
    private final RuntimeLog log;

    private boolean nativeStarted;
    private boolean stopped;

    StartSequence(HotspotCheck hotspot, AppFiles files, NativeLibrary nativeLib, Advertiser advertiser,
            DecoderControl decoder, RuntimeLog log) {
        this.hotspot = hotspot;
        this.files = files;
        this.nativeLib = nativeLib;
        this.advertiser = advertiser;
        this.decoder = decoder;
        this.log = log;
    }

    void run() {
        // TODO(phase B: carplay probe) log carplay listener present/absent/unknown; never refuse to start on it
        // (AIR-079(b) to (d)).
        if (carplay != null) carplay.run();
        String hotspotFailure = hotspot.run();
        if (hotspotFailure != null) log.record(hotspotFailure);

        final NativeLibrary.StartPlan plan = new NativeLibrary.StartPlan(files.path(KEY_FILE), HotspotCheck.ADDRESS);
        nativeStarted = true;
        NativeLibrary.StartResult key = nativeLib.start(NativeLibrary.Stage.KEYCHECK, plan, decoder);
        if (key != null && !key.ok && NativeLibrary.StartResult.STDIO_FAILED.equals(key.token)) {
            // AIR-055(f): no library call was made, and this failure takes AIR-069(d)'s path with no notice of
            // its own. A hotspot failure logged before it is still one of AIR-069(c)'s causes, so it keeps its
            // notice (AIR-069(f)).
            nativeStarted = false;
            log.record(STDIO_FAILED);
            if (hotspotFailure != null) log.startFailed(HotspotCheck.MISSING);
            return;
        }
        boolean keyFailed = key == null || !key.ok;
        if (keyFailed) log.record(KEY_UNAVAILABLE + " " + (key == null ? "unreachable" : key.token));
        if (hotspotFailure != null) {
            log.startFailed(HotspotCheck.MISSING);
            return;
        }
        if (keyFailed) {
            log.startFailed(KEY_UNAVAILABLE);
            return;
        }

        NativeLibrary.StartResult bind = nativeLib.start(NativeLibrary.Stage.BIND, plan, decoder);
        if (bind == null || !bind.ok) {
            log.record(PORT_BUSY + " " + (bind == null ? "unreachable" : bind.token));
            log.startFailed(PORT_BUSY);
            return;
        }

        advertiser.start(plan, decoder, new Advertiser.Outcome() {
            @Override
            public void advertised() {
                accept(plan);
            }

            @Override
            public void failed() {
                log.startFailed(ADVERTISE_FAILED);
            }
        });
    }

    private void accept(NativeLibrary.StartPlan plan) {
        if (stopped) return;
        NativeLibrary.StartResult r;
        try {
            r = nativeLib.start(NativeLibrary.Stage.ACCEPT, plan, decoder);
        } catch (RuntimeException e) {
            log.record("start failed " + e.getClass().getName());
            return;
        }
        // TODO(phase B: accept failure) map a failed ACCEPT to its record and cause.
        if (r == null || !r.ok) log.record("start failed accept " + (r == null ? "unreachable" : r.token));
    }

    /** Exit: no later stage runs. */
    void stop() {
        stopped = true;
    }

    /** True once the library has been called for this app start, so the stop steps have something to undo. */
    boolean nativeStarted() {
        return nativeStarted;
    }

    private CarplayProbe carplay;

    /** The CarPlay listener check, run first in {@link #run()} (AIR-079(b) to (d)). */
    void useCarplayProbe(CarplayProbe p) {
        carplay = p;
    }
}
