package parkcast.core.port;

/** One {@code MediaCodec} decoder in synchronous mode on the activity Surface (AIR-056). */
public interface Codec {
    /** Configures the decoder; a non-empty {@code csd} is passed as the format's codec-specific data. */
    void configure(String mime, int width, int height, byte[] csd, Object surface) throws Exception;

    void start() throws Exception;

    /** Queues one access unit; false when no input buffer was free. */
    boolean queue(byte[] au, long ptsUs) throws Exception;

    /** The next output buffer's index, or -1 when none is ready. */
    int dequeue() throws Exception;

    void release(int index, boolean render) throws Exception;

    void release();
}
