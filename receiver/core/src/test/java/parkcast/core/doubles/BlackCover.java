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
        black = on;
        trace.add("cover", "black", on ? "on" : "off");
    }
}
