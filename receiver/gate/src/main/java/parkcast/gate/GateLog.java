package parkcast.gate;

import java.io.IOException;
import java.nio.charset.Charset;
import parkcast.gate.port.LogFiles;
import parkcast.gate.port.Scheduler;

/**
 * Writes the receiver's records (AIR-025, AIR-026, AIR-038, AIR-039). Each record is one line, prefixed with
 * the time it was written. The current file rotates before a write that would carry it past
 * {@link #FILE_CAP} bytes and right after a write that brings it to that size; at each rotation the older
 * file is deleted as a whole file first, so the two files together never exceed twice the cap. A record
 * larger than the cap, counting its terminator, is replaced by a {@code record too large <size>} record.
 * A failing write or retention operation is a logging failure: it is reported to {@link Notices}, the
 * record is not retried, later records are still attempted, and no exception text is ever written.
 */
public final class GateLog {
    /** The current log file in the receiver's log folder. */
    static final String FILE = "receiver.log";
    /** No log file may hold more than this many bytes (AIR-039). */
    static final long FILE_CAP = 2621440L;

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final LogFiles files;
    private final Scheduler scheduler;
    private Notices notices;

    public GateLog(LogFiles files, Scheduler scheduler) {
        this.files = files;
        this.scheduler = scheduler;
    }

    /** Writes one record. Never throws; a failure is reported as a logging failure. */
    public void record(String line) {
        byte[] b = encode(line);
        if (b.length > FILE_CAP) b = encode("record too large " + b.length);
        write(b);
    }

    public void setNotices(Notices notices) {
        this.notices = notices;
    }

    private byte[] encode(String line) {
        return ("t=" + scheduler.now() + " " + line + "\n").getBytes(UTF8);
    }

    private void write(byte[] b) {
        try {
            long size = files.size(FILE);
            if (size > 0 && size + b.length > FILE_CAP) rotate();
            files.append(FILE, b);
            if (files.size(FILE) >= FILE_CAP) rotate();
        } catch (IOException e) {
            failed();
        } catch (RuntimeException e) {
            failed();
        }
    }

    /** Deletes the older file whole, then makes the current file the older one and starts an empty one. */
    private void rotate() throws IOException {
        files.deleteOlder();
        files.rotate();
    }

    private void failed() {
        if (notices != null) notices.loggingFailed();
    }
}
