package parkcast.core;

/**
 * The session records of AIR-071(b) and the withheld count (AIR-073(c)). Sessions number from 1 at each app
 * start (AIR-071(e)). Native records go through {@link NativeLogFilter}; a record that arrives before the
 * filter is ready, that the filter drops, or that makes the filter throw is counted, never written
 * (AIR-072(b)). Drops outside a session go into the next session's count, or are discarded if none follows
 * (OD-190).
 */
final class SessionLog {
    private final RuntimeLog log;
    private NativeLogFilter filter;
    private boolean ready;
    private int number;
    private boolean open;
    private int withheld;

    SessionLog(RuntimeLog log, NativeLogFilter filter) {
        this.log = log;
        this.filter = filter;
    }

    /** The allowlist is loaded: native records may be written from now on (OD-190). */
    void filterReady() {
        ready = true;
    }

    /** Replaces the filter; for tests that need a filter that fails. */
    void useFilter(NativeLogFilter f) {
        filter = f;
    }

    /** A session started: it takes the next number. */
    void started() {
        number++;
        open = true;
        firstFrame = false;
    }

    /** One record from the library's log callback. */
    void nativeRecord(String record) {
        String out;
        if (!ready || filter == null) {
            out = null;
        } else {
            try {
                out = filter.pass(record);
            } catch (RuntimeException e) {
                out = null;
            }
        }
        if (out == null) {
            withheld++;
            return;
        }
        log.record(out);
    }

    /** One drop counted outside the filter, such as a log record the ring had no room for (OD-190). */
    void dropped() {
        withheld++;
    }

    /** The open session ended for {@code reason}; nothing if none is open. */
    void end(String reason) {
        if (!open) return;
        open = false;
        log.record("session " + number + " end reason=" + reason + " withheld=" + withheld);
        withheld = 0;
    }

    boolean isOpen() {
        return open;
    }

    int number() {
        return number;
    }

    private static final java.util.regex.Pattern MODEL = java.util.regex.Pattern.compile("[A-Za-z0-9,.]{1,32}");
    private boolean firstFrame;

    /** AIR-071(b): the accepted connection, naming the model only (AIR-073(f)); anything else is "unknown". */
    void accepted(String model) {
        String m = model != null && MODEL.matcher(model).matches() ? model : "unknown";
        log.record("session " + number + " connection accepted model=" + m);
    }

    /** AIR-071(b): the stream's codec and size, from video_set_codec_and_size. */
    void codec(String name, int width, int height) {
        String c = "h264".equals(name) || "h265".equals(name) ? name : "unknown";
        log.record("session " + number + " codec " + c + " " + width + "x" + height);
    }

    /** AIR-071(b): the decoder was configured; for the frame pipeline to call. */
    void decoderConfigured() {
        log.record("decoder " + DecoderControl.DECODER + " configured");
    }

    /** AIR-071(b): the session's first frame, rendered or withheld by the gate; written once per session. */
    void firstFrame(boolean rendered) {
        if (!open || firstFrame) return;
        firstFrame = true;
        log.record("session " + number + (rendered ? " first frame rendered" : " first frame withheld by gate"));
    }
}
