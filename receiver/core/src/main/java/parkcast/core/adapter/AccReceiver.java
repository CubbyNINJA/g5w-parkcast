package parkcast.core.adapter;

import parkcast.core.port.Broadcasts;
import parkcast.core.port.Threads;
import parkcast.gate.port.AccSource;
import parkcast.gate.port.AccStatus;

/**
 * AIR-059(a): the sticky ACC status broadcast, registered without a permission on the worker thread, with a
 * Handler on the main looper so broadcasts arrive on the gate's event thread. The registration's result (the
 * sticky state, none, or the exception) is posted to the main looper. The initial sticky delivery is skipped,
 * because registered() already carries it.
 */
public final class AccReceiver implements AccSource {
    public static final String ACTION = "com.hkmc.intent.action.ACTION_ACC_STATUS_CHANGED";
    public static final String EXTRA = "EXTRA_ACC_STATUS";

    private final Broadcasts broadcasts;
    private final Threads threads;
    private Broadcasts.Receiver receiver;
    /** Set on the worker once registerReceiver has returned; guarded by this. */
    private boolean live;
    private boolean closed;

    public AccReceiver(Broadcasts broadcasts, Threads threads) {
        this.broadcasts = broadcasts;
        this.threads = threads;
    }

    @Override
    public void register(final AccListener l) {
        final Broadcasts.Receiver r = new Broadcasts.Receiver() {
            @Override
            public void onReceive(Broadcasts.Message m) {
                if (!m.initialSticky()) l.changed(status(m));
            }
        };
        synchronized (this) {
            receiver = r;
        }
        threads.background(new Runnable() {
            @Override
            public void run() {
                final Broadcasts.Message sticky;
                try {
                    sticky = broadcasts.register(r, new String[] {ACTION}, null, broadcasts.mainHandler());
                } catch (final RuntimeException e) {
                    threads.main(new Runnable() {
                        @Override
                        public void run() {
                            l.registrationFailed(e.getClass().getName());
                        }
                    });
                    return;
                }
                boolean undo;
                synchronized (AccReceiver.this) {
                    live = true;
                    undo = closed;
                }
                if (undo) {
                    broadcasts.unregister(r);
                    return;
                }
                threads.main(new Runnable() {
                    @Override
                    public void run() {
                        l.registered(sticky == null ? null : status(sticky));
                    }
                });
            }
        });
    }

    @Override
    public void unregister() {
        Broadcasts.Receiver r;
        synchronized (this) {
            closed = true;
            r = live ? receiver : null;
            receiver = live ? null : receiver;
        }
        if (r != null) broadcasts.unregister(r);
    }

    static AccStatus status(Broadcasts.Message m) {
        if (!m.hasExtra(EXTRA)) return AccStatus.MISSING;
        Object v = m.extra(EXTRA);
        return v instanceof Integer ? AccStatus.of(((Integer) v).intValue()) : AccStatus.NON_INTEGER;
    }
}
