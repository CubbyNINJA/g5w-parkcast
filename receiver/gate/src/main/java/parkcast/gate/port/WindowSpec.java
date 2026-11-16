package parkcast.gate.port;

/** The layout of one notice window, as WindowManager.LayoutParams would carry it. */
public final class WindowSpec {
    public static final int TYPE_TOAST = 2005;
    public static final int FLAG_NOT_FOCUSABLE = 0x00000008;
    public static final int FLAG_NOT_TOUCHABLE = 0x00000010;
    public static final int FLAG_KEEP_SCREEN_ON = 0x00000080;
    public static final int GRAVITY_CENTER_HORIZONTAL = 0x01;
    public static final int GRAVITY_BOTTOM = 0x50;

    public final int type;
    public final int flags;
    public final int gravity;
    public final int x;
    public final int y;

    public WindowSpec(int type, int flags, int gravity, int x, int y) {
        this.type = type;
        this.flags = flags;
        this.gravity = gravity;
        this.x = x;
        this.y = y;
    }
}
