package parkcast.core.doubles;

import java.util.ArrayDeque;
import java.util.Deque;
import parkcast.core.port.Codec;
import parkcast.core.port.CodecFactory;
import parkcast.gate.harness.VirtualScheduler;

/**
 * The fake codec factory and decoder (file 13 preamble): each queued access unit yields one output 5 ms
 * later. Calls go to the trace as cause "codec"; a release records its index and render flag.
 */
public final class FakeCodecFactory implements CodecFactory {
    public static final long OUTPUT_AFTER_MS = 5;

    private final Trace trace;
    private final VirtualScheduler scheduler;
    public boolean throwOnCreate;
    public FakeCodec last;

    public FakeCodecFactory(Trace trace, VirtualScheduler scheduler) {
        this.trace = trace;
        this.scheduler = scheduler;
    }

    @Override
    public Codec create(String name) throws Exception {
        if (throwOnCreate) {
            trace.add("codec", "create", name + " threw java.lang.IllegalArgumentException");
            throw new IllegalArgumentException("synthetic codec failure");
        }
        trace.add("codec", "create", name);
        last = new FakeCodec();
        return last;
    }

    public final class FakeCodec implements Codec {
        private final Deque<Long> ready = new ArrayDeque<>();
        private int next;

        @Override
        public void configure(String mime, int width, int height, Object surface) {
            trace.add("codec", "configure", mime + " " + width + "x" + height);
        }

        @Override
        public void start() {
            trace.add("codec", "start", "");
        }

        @Override
        public boolean queue(byte[] au, final long ptsUs) {
            trace.add("codec", "queue", String.valueOf(ptsUs));
            final long due = scheduler.now() + OUTPUT_AFTER_MS;
            scheduler.at(due, () -> ready.add(due));
            return true;
        }

        @Override
        public int dequeue() {
            if (ready.isEmpty()) return -1;
            ready.poll();
            return next++;
        }

        @Override
        public void release(int index, boolean render) {
            trace.add("codec", "releaseOutput", index + " render=" + render);
        }

        @Override
        public void release() {
            if (throwOnRelease) throw new IllegalStateException(trace.add("codec", "releaseThrew", "").toString());
            trace.add("codec", "release", "");
        }
    }

    /** Releasing a codec throws java.lang.IllegalStateException. */
    public boolean throwOnRelease;
}
