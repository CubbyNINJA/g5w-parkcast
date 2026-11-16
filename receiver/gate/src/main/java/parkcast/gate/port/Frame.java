package parkcast.gate.port;

/** One decoded video frame. */
public final class Frame {
    public final long id;
    public final long time;

    public Frame(long id, long time) {
        this.id = id;
        this.time = time;
    }
}
