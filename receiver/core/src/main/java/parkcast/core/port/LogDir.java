package parkcast.core.port;

import java.io.IOException;

/**
 * The file operations the receiver's log folder needs, by absolute path (AIR-062). Every operation may fail.
 * There is deliberately no mkdir, chmod or chown: a missing or unwritable folder is a logging failure.
 */
public interface LogDir {
    /** The file's length; a missing file is 0. */
    long length(String path) throws IOException;

    /** Appends to the file, creating it inside an existing folder if it is missing. */
    void append(String path, byte[] b) throws IOException;

    /** Renames {@code from} to {@code to}, replacing {@code to}. */
    void rename(String from, String to) throws IOException;

    /** Deletes the file; a missing file is not an error. */
    void delete(String path) throws IOException;
}
