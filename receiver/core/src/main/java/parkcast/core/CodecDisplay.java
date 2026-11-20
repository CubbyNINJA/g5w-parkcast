package parkcast.core;

import parkcast.core.port.CoverView;
import parkcast.gate.port.Display;
import parkcast.gate.port.Frame;

/**
 * The display the gate draws on: the activity's video surface under the black cover (AIR-057(e)). A frame the
 * gate shows lowers the cover, and a clear raises it before the surface is cleared. Only a frame the decoder
 * marked fresh is shown; the decoder releases a frame the gate did not show with render false.
 */
final class CodecDisplay implements Display {
    private final Display inner;
    private final CoverView cover;
    private boolean coverUp = true;
    private boolean fresh;
    private boolean shown;

    CodecDisplay(Display inner, CoverView cover) {
        this.inner = inner;
        this.cover = cover;
    }

    /** Marks the next frame as decoded from input queued while video was permitted. */
    void offer(boolean fresh) {
        this.fresh = fresh;
        this.shown = false;
    }

    /** Whether the gate showed the frame offered last. */
    boolean shown() {
        return shown;
    }

    @Override
    public void show(Frame f) {
        if (!fresh) return;
        shown = true;
        inner.show(f);
        if (coverUp) {
            coverUp = false;
            cover.black(false);
        }
    }

    @Override
    public void clear() {
        raise();
        inner.clear();
    }

    /** Puts the black cover up if it is down. */
    void raise() {
        if (coverUp) return;
        coverUp = true;
        cover.black(true);
    }
}
