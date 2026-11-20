package parkcast.receiver;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import parkcast.gate.port.Scheduler;

/**
 * AIR-065(a): the gate's Scheduler on the main looper, timed by SystemClock.uptimeMillis(), which a wall-clock
 * change does not move. Each task posts with its own token, so cancelling it removes only that task.
 */
final class AndroidScheduler implements Scheduler {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public long now() {
        return SystemClock.uptimeMillis();
    }

    @Override
    public Task at(long ms, final Runnable r) {
        final Object token = new Object();
        handler.postAtTime(r, token, ms);
        return new Task() {
            @Override
            public void cancel() {
                handler.removeCallbacks(r, token);
            }
        };
    }
}
