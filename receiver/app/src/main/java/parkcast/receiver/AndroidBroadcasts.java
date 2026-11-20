package parkcast.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import java.util.HashMap;
import java.util.Map;
import parkcast.core.port.Broadcasts;

/**
 * {@link Broadcasts} over a Context: one IntentFilter of the given actions, registered and unregistered in
 * code, never in the manifest (AIR-068). Registration may happen on a worker thread, so the map from core's
 * receivers to Android's is synchronized.
 */
final class AndroidBroadcasts implements Broadcasts {
    private final Context context;
    private final Handler main;
    private final Map<Receiver, BroadcastReceiver> live = new HashMap<Receiver, BroadcastReceiver>();

    AndroidBroadcasts(Context context, Handler main) {
        this.context = context;
        this.main = main;
    }

    @Override
    public Object mainHandler() {
        return main;
    }

    @Override
    public Message register(final Receiver r, String[] actions, String permission, Object handler) {
        IntentFilter filter = new IntentFilter();
        for (String a : actions) filter.addAction(a);
        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                r.onReceive(new IntentMessage(intent, isInitialStickyBroadcast()));
            }
        };
        synchronized (live) {
            live.put(r, br);
        }
        Intent sticky;
        try {
            sticky = context.registerReceiver(br, filter, permission, (Handler) handler);
        } catch (RuntimeException e) {
            synchronized (live) {
                live.remove(r);
            }
            throw e;
        }
        return sticky == null ? null : new IntentMessage(sticky, true);
    }

    @Override
    public void unregister(Receiver r) {
        BroadcastReceiver br;
        synchronized (live) {
            br = live.remove(r);
        }
        if (br != null) context.unregisterReceiver(br);
    }

    private static final class IntentMessage implements Message {
        private final Intent intent;
        private final boolean initialSticky;

        IntentMessage(Intent intent, boolean initialSticky) {
            this.intent = intent;
            this.initialSticky = initialSticky;
        }

        @Override
        public boolean hasExtra(String key) {
            return intent.hasExtra(key);
        }

        @Override
        public Object extra(String key) {
            Bundle b = intent.getExtras();
            return b == null ? null : b.get(key);
        }

        @Override
        public boolean initialSticky() {
            return initialSticky;
        }
    }
}
