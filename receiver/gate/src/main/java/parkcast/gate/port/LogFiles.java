package parkcast.gate.port;

import java.io.IOException;

/** The receiver's log folder. Every operation may fail. */
public interface LogFiles {
    long size(String name) throws IOException;

    void append(String name, byte[] b) throws IOException;

    void rotate() throws IOException;

    void deleteOlder() throws IOException;
}
