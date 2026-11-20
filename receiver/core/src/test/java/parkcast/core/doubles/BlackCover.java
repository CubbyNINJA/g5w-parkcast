package parkcast.core.doubles;

import parkcast.core.port.CoverView;

/** The black cover view above the video surface; records each change as trace cause "cover". */
public final class BlackCover implements CoverView {
    private final Trace trace;
    public boolean black = true;

    public BlackCover(Trace trace) {
        this.trace = trace;
    }

    @Override
    public void black(boolean on) {
        if (on && throwOnBlack) throw new IllegalStateException(trace.add("cover", "blackThrew", "on").toString());
        black = on;
        trace.add("cover", "black", on ? "on" : "off");
    }

    /** Raising the cover throws java.lang.IllegalStateException (a failing "block video" stop step). */
    public boolean throwOnBlack;
    /** Stands for the receiver activity's own Surface (AIR-056(b)). */
    public static final Object SURFACE = new Object() {
        @Override
        public String toString() {
            return "activity-surface";
        }
    };

    @Override
    public void videoBox(int x, int y, int width, int height) {
        trace.add("cover", "videoBox", width + "x" + height + " at " + x + "," + y);
    }

    @Override
    public Object surface() {
        return SURFACE;
    }
}
