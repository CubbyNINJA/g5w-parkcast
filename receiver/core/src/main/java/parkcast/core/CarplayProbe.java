package parkcast.core;

import java.nio.charset.Charset;
import parkcast.core.port.ProcNetTcp;

/**
 * The CarPlay listener check (AIR-079(b) to (d)): reads {@code /proc/net/tcp} once and logs whether a local TCP
 * 7000 listener exists. It never connects, never refuses to start on the result, and logs no line of the file.
 */
final class CarplayProbe {
    static final String PRESENT = "carplay listener present";
    static final String ABSENT = "carplay listener absent";
    static final String UNKNOWN = "carplay listener unknown";
    /** TCP 7000. */
    private static final int PORT = 0x1B58;
    /** TCP_LISTEN in the kernel's state column. */
    private static final int LISTEN = 0x0A;
    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");

    private final ProcNetTcp proc;
    private final RuntimeLog log;

    CarplayProbe(ProcNetTcp proc, RuntimeLog log) {
        this.proc = proc;
        this.log = log;
    }

    void run() {
        String result;
        try {
            result = listening(proc.read()) ? PRESENT : ABSENT;
        } catch (Exception e) {
            result = UNKNOWN + " " + e.getClass().getName();
        }
        log.record(result);
    }

    /** True if any row is a listener on local port 7000; a row that does not parse throws. */
    private static boolean listening(byte[] content) {
        String[] lines = new String(content, LATIN1).split("\n");
        boolean found = false;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] f = line.split("\\s+");
            if (f.length < 4) throw new NumberFormatException("short row");
            int colon = f[1].lastIndexOf(':');
            if (colon < 0) throw new NumberFormatException("no port");
            int port = Integer.parseInt(f[1].substring(colon + 1), 16);
            int state = Integer.parseInt(f[3], 16);
            if (port == PORT && state == LISTEN) found = true;
        }
        return found;
    }
}
