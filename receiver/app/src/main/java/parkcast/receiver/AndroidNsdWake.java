package parkcast.receiver;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import java.util.HashMap;
import java.util.Map;
import parkcast.core.port.NsdWake;

/**
 * {@link NsdWake}: a discovery through NsdManager, used only to wake the system mDNS daemon. Nothing is
 * registered or resolved, and found services are ignored. NsdManager calls back on its own thread, so the
 * start callbacks are posted to the main looper, where core runs.
 */
final class AndroidNsdWake implements NsdWake {
    private final Context context;
    private final Handler main;

    AndroidNsdWake(Context context, Handler main) {
        this.context = context;
        this.main = main;
    }

    @Override
    public NsdHandle get() {
        Object m = context.getSystemService(Context.NSD_SERVICE);
        return m instanceof NsdManager ? new Handle((NsdManager) m) : null;
    }

    private final class Handle implements NsdHandle {
        private final NsdManager manager;
        private final Map<Listener, NsdManager.DiscoveryListener> live =
                new HashMap<Listener, NsdManager.DiscoveryListener>();

        Handle(NsdManager manager) {
            this.manager = manager;
        }

        @Override
        public void discover(String type, final Listener l) {
            NsdManager.DiscoveryListener d = new NsdManager.DiscoveryListener() {
                @Override
                public void onDiscoveryStarted(String serviceType) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            l.started();
                        }
                    });
                }

                @Override
                public void onStartDiscoveryFailed(String serviceType, final int errorCode) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            l.startFailed(errorCode);
                        }
                    });
                }

                @Override
                public void onServiceFound(NsdServiceInfo info) {}

                @Override
                public void onServiceLost(NsdServiceInfo info) {}

                @Override
                public void onDiscoveryStopped(String serviceType) {}

                @Override
                public void onStopDiscoveryFailed(String serviceType, int errorCode) {}
            };
            synchronized (live) {
                live.put(l, d);
            }
            manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, d);
        }

        @Override
        public void stop(Listener l) {
            NsdManager.DiscoveryListener d;
            synchronized (live) {
                d = live.remove(l);
            }
            if (d != null) manager.stopServiceDiscovery(d);
        }
    }
}
