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
 * AIR-061: each notice is the view of {@code Toast.makeText(...).getView()}, added by the receiver as a window of
 * the spec's type. No toast is ever shown through the notification service. The placement is read from a fresh
 * {@code Toast(Context)}, the same constructor makeText uses, so notices sit where this unit would put a toast.
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
        return new ToastNotice(Toast.makeText(context, text, Toast.LENGTH_LONG).getView());
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
        Toast t = new Toast(context);
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
        final View view;

        ToastNotice(View view) {
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
