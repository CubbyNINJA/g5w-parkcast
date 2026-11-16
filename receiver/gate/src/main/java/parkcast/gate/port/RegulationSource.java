package parkcast.gate.port;

/** Stock regulation change notifications. Logged only; never linked to the gate's decision. */
public interface RegulationSource {
    void register(RegulationListener l);

    void unregister();

    interface RegulationListener {
        void state(String s);

        void unavailable(String reason);
    }
}
