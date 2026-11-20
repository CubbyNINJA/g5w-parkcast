package parkcast.core.adapter;

import parkcast.core.port.RegulationBinder;
import parkcast.gate.port.RegulationSource;

/**
 * The receiver's regulation state over the stock regulation service (OD-195). At registration it reads the
 * status once, then listens for changes: 0 is reported as "off" and 1 as "on". Any other value, a missing
 * service, a call that throws, or the service dying is reported as unavailable with a reason. Unregistering
 * removes the listener. It only reads: the port it uses has no way to change the state.
 */
public final class RegulationAdapter implements RegulationSource {
    private final RegulationBinder service;
    private volatile RegulationBinder.Listener live;

    /** {@code service} may be null when the platform has no regulation service. */
    public RegulationAdapter(RegulationBinder service) {
        this.service = service;
    }

    @Override
    public void register(final RegulationListener l) {
        if (service == null) {
            l.unavailable("service missing");
            return;
        }
        int status;
        try {
            status = service.getStatus();
        } catch (Exception e) {
            l.unavailable(e.getClass().getSimpleName());
            return;
        }
        report(l, status);
        RegulationBinder.Listener b = new RegulationBinder.Listener() {
            @Override
            public void onStatusChanged(int s) {
                if (live == this) report(l, s);
            }

            @Override
            public void onServiceLost() {
                if (live != this) return;
                live = null;
                l.unavailable("service lost");
            }
        };
        live = b;
        try {
            service.addListener(b);
        } catch (Exception e) {
            live = null;
            l.unavailable(e.getClass().getSimpleName());
        }
    }

    @Override
    public void unregister() {
        RegulationBinder.Listener b = live;
        live = null;
        if (b == null || service == null) return;
        try {
            service.removeListener(b);
        } catch (Exception e) {
            // the service is gone or refused; nothing is left listening on our side
        }
    }

    private static void report(RegulationListener l, int status) {
        if (status == 0) l.state("off");
        else if (status == 1) l.state("on");
        else l.unavailable("unknown value " + status);
    }
}
