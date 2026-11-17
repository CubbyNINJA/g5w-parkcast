package parkcast.core.port;

import java.io.IOException;

/** Reads a whole file by absolute path. A missing file throws {@link java.io.FileNotFoundException}. */
public interface PathReader {
    byte[] read(String path) throws IOException;
}
