package parkcast.receiver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import parkcast.core.port.AppFiles;

/** {@link AppFiles} rooted at the app's private files directory. It only reads; it never writes or deletes. */
final class AndroidAppFiles implements AppFiles {
    private final File dir;

    AndroidAppFiles(File dir) {
        this.dir = dir;
    }

    @Override
    public String path(String name) {
        return new File(dir, name).getPath();
    }

    @Override
    public boolean exists(String name) throws IOException {
        return new File(dir, name).isFile();
    }

    @Override
    public long length(String name) throws IOException {
        File f = new File(dir, name);
        if (!f.isFile()) throw new java.io.FileNotFoundException(name);
        return f.length();
    }

    @Override
    public byte[] read(String name) throws IOException {
        return FilePathReader.Streams.readAll(new FileInputStream(new File(dir, name)));
    }
}
