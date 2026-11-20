package parkcast.core;

/**
 * The {@code crash} record (AIR-069(a)): the exception class and the receiver's own {@code parkcast.*} stack
 * frames, never the message or any other frame. The process ends after the crash hook's sink returns.
 */
final class CrashRecorder {
    static final String OWN = "parkcast.";
    static final int MAX_FRAMES = 16;

    private final RuntimeLog log;

    CrashRecorder(RuntimeLog log) {
        this.log = log;
    }

    void record(Throwable t) {
        try {
            StringBuilder b = new StringBuilder("crash ").append(t.getClass().getName()).append(" at");
            int n = 0;
            StackTraceElement[] frames = t.getStackTrace();
            for (int i = 0; frames != null && i < frames.length && n < MAX_FRAMES; i++) {
                StackTraceElement e = frames[i];
                if (e == null || e.getClassName() == null || !e.getClassName().startsWith(OWN)) continue;
                b.append(' ').append(e.getClassName()).append('.').append(e.getMethodName()).append(':')
                        .append(e.getLineNumber());
                n++;
            }
            log.record(b.toString());
        } catch (RuntimeException e) {
            // The process ends whether or not the record was written.
        }
    }
}
