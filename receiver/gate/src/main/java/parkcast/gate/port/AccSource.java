package parkcast.gate.port;

/** Runtime registration for the sticky ACC status broadcast. The result may come back later. */
public interface AccSource {
    void register(AccListener l);

    void unregister();

    /** Results and broadcasts, delivered on the event thread. */
    interface AccListener {
        /** Registration returned; {@code sticky} is null when there was no sticky state. */
        void registered(AccStatus sticky);

        void registrationFailed(String cause);

        void changed(AccStatus s);
    }
}
