package parkcast.receiver;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import parkcast.core.port.PathReader;

/** {@link PathReader} over java.io: a missing file throws FileNotFoundException, other failures IOException. */
final class FilePathReader implements PathReader {
    @Override
    public byte[] read(String path) throws IOException {
        return Streams.readAll(new FileInputStream(path));
    }

    static final class Streams {
        private Streams() {}

        static byte[] readAll(FileInputStream in) throws IOException {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                return out.toByteArray();
            } finally {
                in.close();
            }
        }
    }
}
