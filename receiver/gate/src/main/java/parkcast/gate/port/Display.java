package parkcast.gate.port;

/** The receiver's video surface. */
public interface Display {
    void show(Frame f);

    void clear();
}
