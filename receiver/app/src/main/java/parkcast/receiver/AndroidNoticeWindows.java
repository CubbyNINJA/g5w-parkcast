package parkcast.receiver;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;
import parkcast.gate.port.NoticeView;
import parkcast.gate.port.NoticeWindows;
import parkcast.gate.port.Placement;
import parkcast.gate.port.Win;
import parkcast.gate.port.WindowSpec;

/**
 * AIR-061: each notice is a toast's view, added by the receiver as a window of the spec's type. The toast is
 * never shown through the notification service; its gravity and offsets are only read, so notices sit where
 * this unit's system would put that toast.
 */
final class AndroidNoticeWindows implements NoticeWindows {
    private final Context context;
    private final WindowManager windows;

    AndroidNoticeWindows(Context context, WindowManager windows) {
        this.context = context;
        this.windows = windows;
    }

    @Override
    public NoticeView create(String text) {
        Toast toast = Toast.makeText(context, text, Toast.LENGTH_LONG);
        return new ToastNotice(toast, toast.getView());
    }

    @Override
    public int measure(NoticeView v, int displayWidth) {
        View view = ((ToastNotice) v).view;
        view.measure(View.MeasureSpec.makeMeasureSpec(displayWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return view.getMeasuredHeight();
    }

    @Override
    public Placement placementFrom(NoticeView regFailToast) {
        Toast t = ((ToastNotice) regFailToast).toast;
        return new Placement(t.getGravity(), t.getXOffset(), t.getYOffset());
    }

    @Override
    public Win add(NoticeView v, WindowSpec spec) {
        View view = ((ToastNotice) v).view;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, spec.type, spec.flags, PixelFormat.TRANSLUCENT);
        lp.gravity = spec.gravity;
        lp.x = spec.x;
        lp.y = spec.y;
        windows.addView(view, lp);
        return new Added(view);
    }

    @Override
    public void remove(Win w) {
        windows.removeView(((Added) w).view);
    }

    private static final class ToastNotice implements NoticeView {
        final Toast toast;
        final View view;

        ToastNotice(Toast toast, View view) {
            this.toast = toast;
            this.view = view;
        }
    }

    private static final class Added implements Win {
        final View view;

        Added(View view) {
            this.view = view;
        }
    }
}
