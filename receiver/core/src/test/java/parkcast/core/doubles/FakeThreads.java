package parkcast.core.doubles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import parkcast.core.port.Threads;
import parkcast.gate.harness.VirtualScheduler;

/**
 * A real worker thread on a virtual clock. {@code background} runs the job on the worker at once and waits
 * for it; the job reports how long it took in virtual time through {@link #took(long)} (default 0), and
 * whatever it posted to {@code main} runs on the scheduler's thread at its start plus that duration.
 */
public final class FakeThreads implements Threads {
    private final VirtualScheduler s;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fake-worker");
        t.setDaemon(true);
        return t;
    });
    /** The thread the scheduler runs on: the main looper. */
    public final Thread mainThread = Thread.currentThread();
    private volatile Thread workerThread;
    private final List<Runnable> posted = new ArrayList<>();
    private long duration;

    public FakeThreads(VirtualScheduler s) {
        this.s = s;
    }

    /** Called by the work on the worker: how long it took in virtual time. */
    public void took(long ms) {
        duration = ms;
    }

    public boolean onWorker() {
        return Thread.currentThread() == workerThread;
    }

    @Override
    public void background(Runnable r) {
        final long begin = s.now();
        duration = 0;
        posted.clear();
        Future<?> f = worker.submit(() -> {
            workerThread = Thread.currentThread();
            r.run();
        });
        try {
            f.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException) throw (RuntimeException) e.getCause();
            throw new IllegalStateException(e.getCause());
        }
        List<Runnable> out = new ArrayList<>(posted);
        posted.clear();
        for (Runnable m : out) s.at(begin + duration, m);
    }

    @Override
    public void main(Runnable r) {
        if (onWorker()) posted.add(r);
        else s.at(s.now(), r);
    }
}
