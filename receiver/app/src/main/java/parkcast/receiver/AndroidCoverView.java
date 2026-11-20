package parkcast.receiver;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import parkcast.core.port.CoverView;

/**
 * {@link CoverView}: the video SurfaceView, with a full-window black View above it. The cover starts up, so
 * nothing on the surface can be seen until the gate shows a frame.
 */
final class AndroidCoverView implements CoverView {
    private final FrameLayout root;
    private final SurfaceView video;
    private final View black;

    AndroidCoverView(Context context) {
        root = new FrameLayout(context);
        root.setBackgroundColor(Color.BLACK);
        video = new SurfaceView(context);
        black = new View(context);
        black.setBackgroundColor(Color.BLACK);
        root.addView(video, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(black, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        black.setVisibility(View.VISIBLE);
    }

    /** The view to set as the activity's content. */
    View root() {
        return root;
    }

    @Override
    public void black(boolean on) {
        black.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    @Override
    public void videoBox(int x, int y, int width, int height) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height, Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = x;
        lp.topMargin = y;
        video.setLayoutParams(lp);
    }

    @Override
    public Object surface() {
        return video.getHolder().getSurface();
    }
}
