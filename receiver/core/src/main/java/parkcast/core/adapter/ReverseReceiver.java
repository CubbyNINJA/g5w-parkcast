package parkcast.core.adapter;

import parkcast.core.port.Broadcasts;
import parkcast.gate.port.ReverseSource;

/**
 * AIR-060: REVERSE_ON registered in code, synchronously on the calling (main) thread, so it returns inside
 * onCreate. Its filter holds REVERSE_ON only; it reads no extras and there is no reverse-off counterpart. The
 * broadcast is not sticky, so a returned intent is only reported, and an initial sticky delivery is ignored.
 */
public final class ReverseReceiver implements ReverseSource {
    public static final String ACTION = "com.hkmc.intent.action.REVERSE_ON";

    private final Broadcasts broadcasts;
    private Broadcasts.Receiver receiver;

    public ReverseReceiver(Broadcasts broadcasts) {
        this.broadcasts = broadcasts;
    }

    @Override
    public boolean register(final ReverseListener l) {
        Broadcasts.Receiver r = new Broadcasts.Receiver() {
            @Override
            public void onReceive(Broadcasts.Message m) {
                if (!m.initialSticky()) l.reverseOn();
            }
        };
        Broadcasts.Message m = broadcasts.register(r, new String[] {ACTION}, null, null);
        receiver = r;
        return m != null;
    }

    @Override
    public void unregister() {
        Broadcasts.Receiver r = receiver;
        receiver = null;
        if (r != null) broadcasts.unregister(r);
    }
}
