package parkcast.core.port;

import java.io.IOException;

/** The receiver's app-data directory, read-only for core (AIR-083). Paths are relative to that directory. */
public interface AppFiles {
    /** The absolute path of {@code name} inside the app-data directory. */
    String path(String name);

    boolean exists(String name) throws IOException;

    long length(String name) throws IOException;

    byte[] read(String name) throws IOException;
}
