package parkcast.receiver;

import android.os.Handler;
import android.os.HandlerThread;
import parkcast.core.port.Threads;

/** {@link Threads}: one worker HandlerThread for blocking reads, and the main looper for everything else. */
final class AndroidThreads implements Threads {
    private final Handler main;
    private final HandlerThread thread;
    private final Handler worker;

    AndroidThreads(Handler main) {
        this.main = main;
        thread = new HandlerThread("parkcast-worker");
        thread.start();
        worker = new Handler(thread.getLooper());
    }

    @Override
    public void background(Runnable r) {
        worker.post(r);
    }

    @Override
    public void main(Runnable r) {
        main.post(r);
    }

    /** Ends the worker; queued work is dropped. */
    void quit() {
        thread.quit();
    }
}
