package parkcast.gate.port;

/**
 * The toast-view factory and the window manager. There is deliberately no updateViewLayout,
 * Toast.show(), dialog, notification or activity method.
 */
public interface NoticeWindows {
    NoticeView create(String text);

    int measure(NoticeView v, int displayWidth);

    /** The placement a toast made by the factory would get; may throw. */
    Placement placementFrom(NoticeView regFailToast);

    Win add(NoticeView v, WindowSpec spec);

    void remove(Win w);
}
