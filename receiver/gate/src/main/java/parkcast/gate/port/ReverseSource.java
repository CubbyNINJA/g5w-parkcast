package parkcast.gate.port;

/** Runtime registration for REVERSE_ON only. Synchronous, so it returns inside onCreate. */
public interface ReverseSource {
    /** Returns true when the registration returned an intent; throws on failure. */
    boolean register(ReverseListener l);

    void unregister();

    interface ReverseListener {
        void reverseOn();
    }
}
