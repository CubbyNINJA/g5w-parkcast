package parkcast.gate.port;

/** Where a toast window sits: gravity and offsets. */
public final class Placement {
    public final int gravity;
    public final int x;
    public final int y;

    public Placement(int gravity, int x, int y) {
        this.gravity = gravity;
        this.x = x;
        this.y = y;
    }
}
