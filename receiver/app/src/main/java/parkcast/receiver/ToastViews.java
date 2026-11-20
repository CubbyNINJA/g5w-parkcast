package parkcast.receiver;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

/**
 * AIR-061(a): a notice's content is a toast's view, added by the receiver as a toast window. The toast itself
 * is never shown through the notification service, and no overlay permission is needed.
 */
final class ToastViews {
    private ToastViews() {}

    static View view(Context context, CharSequence text) {
        return Toast.makeText(context, text, Toast.LENGTH_LONG).getView();
    }
}
