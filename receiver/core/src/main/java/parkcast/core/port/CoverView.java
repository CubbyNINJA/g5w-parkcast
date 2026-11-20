package parkcast.core.port;

/** The full-window black view above the video surface (AIR-057). */
public interface CoverView {
    void black(boolean on);

    /** Places the video surface in this box on the 1920x720 window; the rest stays black (AIR-051(b)). */
    void videoBox(int x, int y, int width, int height);

    /** The activity's own Surface, which the decoder renders to (AIR-056(b)). */
    Object surface();
}
