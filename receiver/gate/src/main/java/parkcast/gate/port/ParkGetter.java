package parkcast.gate.port;

/** The stock Park getter, VehicleInfoManager.getGearPStatus(), called off the event thread. */
public interface ParkGetter {
    /** Starts one call; the answer comes back on the event thread through {@code cb}, or never. */
    void call(ParkReply cb);

    /** The answer to one call. */
    interface ParkReply {
        void value(int v);

        void failed(String why);
    }
}
