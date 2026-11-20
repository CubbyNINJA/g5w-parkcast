package parkcast.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import parkcast.core.port.Broadcasts;

/**
 * The Android side of the runtime REVERSE_ON receiver (AIR-041, AIR-060), under core's
 * {@link parkcast.core.adapter.ReverseReceiver}. It registers for REVERSE_ON only, in code and synchronously on
 * the calling thread, never in the manifest. Its message carries no intent, so no extra can be read, and there
 * is no reverse-off counterpart. Any other action is refused.
 */
final class ReverseOnBroadcasts implements Broadcasts {
    static final String ACTION = "com.hkmc.intent.action.REVERSE_ON";

    private final Context context;
    private final Handler main;
    private Receiver registered;
    private BroadcastReceiver live;

    ReverseOnBroadcasts(Context context, Handler main) {
        this.context = context;
        this.main = main;
    }

    @Override
    public Object mainHandler() {
        return main;
    }

    @Override
    public Message register(final Receiver r, String[] actions, String permission, Object handler) {
        if (actions.length != 1 || !ACTION.equals(actions[0]))
            throw new IllegalArgumentException("REVERSE_ON is the only action registered here");
        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                r.onReceive(new NoExtras(isInitialStickyBroadcast()));
            }
        };
        Intent sticky = context.registerReceiver(br, new IntentFilter(ACTION), permission, (Handler) handler);
        registered = r;
        live = br;
        return sticky == null ? null : new NoExtras(true);
    }

    @Override
    public void unregister(Receiver r) {
        if (r != registered || live == null) return;
        BroadcastReceiver br = live;
        registered = null;
        live = null;
        context.unregisterReceiver(br);
    }

    /** A delivery with no extras: REVERSE_ON's extras are never read. */
    private static final class NoExtras implements Message {
        private final boolean initialSticky;

        NoExtras(boolean initialSticky) {
            this.initialSticky = initialSticky;
        }

        @Override
        public boolean hasExtra(String key) {
            return false;
        }

        @Override
        public Object extra(String key) {
            return null;
        }

        @Override
        public boolean initialSticky() {
            return initialSticky;
        }
    }
}
