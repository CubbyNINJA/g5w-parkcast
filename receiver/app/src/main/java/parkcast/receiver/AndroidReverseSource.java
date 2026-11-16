package parkcast.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import parkcast.gate.port.ReverseSource;

/**
 * The runtime {@code REVERSE_ON} receiver behind {@link ReverseSource} (AIR-041; see docs/design/S2_GATE.md).
 * It registers for {@code REVERSE_ON} only, reads nothing from the intent, and lets registration failures
 * propagate so the gate can treat them as covering. There is no manifest receiver.
 */
public final class AndroidReverseSource implements ReverseSource {
    static final String ACTION = "com.hkmc.intent.action.REVERSE_ON";

    private final Context context;
    private BroadcastReceiver receiver;

    public AndroidReverseSource(Context context) {
        this.context = context;
    }

    /** Synchronous, so it returns inside {@code onCreate}. True when the registration returned an intent. */
    @Override
    public boolean register(final ReverseListener listener) {
        BroadcastReceiver r = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                // A sticky match is both returned by registerReceiver and delivered here; register()
                // already reported it, so the initial delivery is skipped (OD-136). No extras are read.
                if (isInitialStickyBroadcast()) return;
                listener.reverseOn();
            }
        };
        Intent returned = context.registerReceiver(r, new IntentFilter(ACTION));
        receiver = r;
        return returned != null;
    }

    /** Called at exit, only after a registration returned. */
    @Override
    public void unregister() {
        context.unregisterReceiver(receiver);
    }
}
