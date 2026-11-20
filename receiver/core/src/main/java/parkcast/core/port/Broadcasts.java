package parkcast.core.port;

/**
 * Context.registerReceiver and unregisterReceiver, reduced to what the receiver uses: one IntentFilter of
 * actions, an optional permission, and the Handler that delivers. The Android side wraps a Context and a
 * Handler on the main looper.
 */
public interface Broadcasts {
    /** A Handler on the main looper, to deliver broadcasts on the gate's event thread. */
    Object mainHandler();

    /**
     * Registers {@code r} for an IntentFilter holding exactly {@code actions}. Returns the sticky intent, or
     * null when there is none. Throws whatever registerReceiver throws.
     */
    Message register(Receiver r, String[] actions, String permission, Object handler);

    void unregister(Receiver r);

    /** BroadcastReceiver.onReceive. */
    interface Receiver {
        void onReceive(Message m);
    }

    /** The parts of an Intent the receiver reads. */
    interface Message {
        boolean hasExtra(String key);

        /** The extra's value as stored, or null. */
        Object extra(String key);

        /** BroadcastReceiver.isInitialStickyBroadcast() while this message is being delivered. */
        boolean initialSticky();
    }
}
