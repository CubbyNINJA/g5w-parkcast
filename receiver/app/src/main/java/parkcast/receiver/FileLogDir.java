package parkcast.receiver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import parkcast.core.port.LogDir;

/**
 * {@link LogDir} over java.io. Appending never creates the directory, so a missing log directory is a
 * logging failure (AIR-040), not a silent mkdir.
 */
final class FileLogDir implements LogDir {
    @Override
    public long length(String path) throws IOException {
        return new File(path).length();
    }

    @Override
    public void append(String path, byte[] data) throws IOException {
        FileOutputStream out = new FileOutputStream(path, true);
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }

    @Override
    public void rename(String from, String to) throws IOException {
        if (!new File(from).renameTo(new File(to))) throw new IOException("rename failed: " + from);
    }

    @Override
    public void delete(String path) throws IOException {
        File f = new File(path);
        if (!f.delete() && f.exists()) throw new IOException("delete failed: " + path);
    }
}
