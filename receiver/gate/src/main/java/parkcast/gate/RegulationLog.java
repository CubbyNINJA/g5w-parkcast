package parkcast.gate;

import parkcast.gate.port.RegulationSource;

/**
 * Logs the stock regulation state at app start and on every change notification (AIR-038). An unavailable
 * state is logged as {@code unavailable} with its reason. The state is never linked to the gate's decision:
 * it permits or blocks nothing. A failure to register or unregister is caught and changes nothing else.
 */
public final class RegulationLog {
    private final RegulationSource source;
    private final GateLog log;

    private boolean started;
    private boolean registered;
    private boolean stopped;

    public RegulationLog(RegulationSource source, GateLog log) {
        this.source = source;
        this.log = log;
    }

    /** Registers for regulation changes once per app start; the source reports the current state first. */
    public void start() {
        if (started || stopped) return;
        started = true;
        try {
            source.register(new RegulationSource.RegulationListener() {
                @Override
                public void state(String s) {
                    if (!stopped) log.record("regulation state=" + s);
                }

                @Override
                public void unavailable(String reason) {
                    if (!stopped) log.record("regulation unavailable reason=" + reason);
                }
            });
            registered = true;
        } catch (RuntimeException e) {
            log.record("regulation unavailable reason=registration failed");
        }
    }

    /** At exit: unregisters once, and only a registration that returned. */
    public void stop() {
        if (stopped) return;
        stopped = true;
        if (!registered) return;
        try {
            source.unregister();
        } catch (RuntimeException e) {
            // Exit completes whether the call returns or throws.
        }
    }
}
