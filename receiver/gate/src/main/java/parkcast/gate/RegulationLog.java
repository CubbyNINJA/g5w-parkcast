package parkcast.gate;

import parkcast.gate.port.RegulationSource;

/** Logs stock regulation changes; never linked to the gate's decision. Skeleton: no logic yet. */
public final class RegulationLog {
    private final RegulationSource source;
    private final GateLog log;

    public RegulationLog(RegulationSource source, GateLog log) {
        this.source = source;
        this.log = log;
    }

    public void start() {
        throw new UnsupportedOperationException("not implemented: RegulationLog.start");
    }

    public void stop() {
        throw new UnsupportedOperationException("not implemented: RegulationLog.stop");
    }
}
