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
        if (createError != null) {
            trace.add("codec", "create", name + " threw " + createError.getClass().getName());
            throw createError;
        }
        if (createThrowsIoe) {
            trace.add("codec", "create", name + " threw java.io.IOException");
            throw new java.io.IOException("synthetic codec failure");
        }
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
        /** The surface passed to configure. */
        public Object surface;
        /** The codec-specific data passed to configure. */
        public byte[] csd;
        /** Bytes of the last queued access unit. */
        public int lastQueuedBytes = -1;

        @Override
        public void configure(String mime, int width, int height, byte[] csd, Object surface) {
            this.surface = surface;
            this.csd = csd;
            trace.add("codec", "configure", mime + " " + width + "x" + height + " surface=" + surface);
            if (throwOnConfigure) throw new IllegalStateException("synthetic configure failure");
        }

        @Override
        public void start() {
            trace.add("codec", "start", "");
        }

        @Override
        public boolean queue(byte[] au, final long ptsUs) {
            failIfDue("queue");
            lastQueuedBytes = au.length;
            trace.add("codec", "queue", String.valueOf(ptsUs));
            final long due = scheduler.now() + OUTPUT_AFTER_MS;
            scheduler.at(due, () -> ready.add(due));
            return true;
        }

        @Override
        public int dequeue() {
            failIfDue("dequeue");
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

        private void failIfDue(String call) {
            if (errorAt < 0 || scheduler.now() < errorAt) return;
            trace.add("codec", call, "threw CodecException");
            throw new CodecException("synthetic decoder error");
        }
    }

    /** create throws java.io.IOException. */
    public boolean createThrowsIoe;
    /** configure throws java.lang.IllegalStateException. */
    public boolean throwOnConfigure;
    /** From this time on, queue and dequeue throw {@link CodecException}; -1 = never. */
    public long errorAt = -1;
    /** Codec-list lookups (MediaCodecList, createDecoderByType); the port offers none, so it stays 0. */
    public int lookups;

    /** Stands for MediaCodec.CodecException, which extends IllegalStateException. */
    public static final class CodecException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        public CodecException(String m) {
            super(m);
        }
    }

    /** Releasing a codec throws java.lang.IllegalStateException. */
    public boolean throwOnRelease;
    /** When set, create throws this (file 13 line 639: java.io.IOException). */
    public Exception createError;
}
