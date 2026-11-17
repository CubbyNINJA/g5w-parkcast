package parkcast.core.port;

import java.io.IOException;

/** {@code /proc/net/tcp}, read-only (AIR-079). */
public interface ProcNetTcp {
    byte[] read() throws IOException;
}
