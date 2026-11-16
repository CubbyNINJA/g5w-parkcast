package parkcast.gate;

import parkcast.gate.port.ConfigFile;

/** Reads video.require_park from the installed receiver.conf. Skeleton: no logic yet. */
public final class ReceiverConfig {
    private final ConfigFile file;

    public ReceiverConfig(ConfigFile file) {
        this.file = file;
    }

    /** Reads the file once. */
    public void load() {
        throw new UnsupportedOperationException("not implemented: ReceiverConfig.load");
    }

    /** True when the parking check is on. */
    public boolean requirePark() {
        throw new UnsupportedOperationException("not implemented: ReceiverConfig.requirePark");
    }

    /** Why the check fell back to on, or null when the file was read as written. */
    public String fallbackCause() {
        throw new UnsupportedOperationException("not implemented: ReceiverConfig.fallbackCause");
    }
}
