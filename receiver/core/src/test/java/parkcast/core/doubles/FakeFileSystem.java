package parkcast.core.doubles;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import parkcast.core.port.AppFiles;

/**
 * The fake file system (file 13 preamble), cut down to the fake app-data directory the receiver's key file
 * lives in. It records every create, write, chmod, rename and delete with its path and mode under the
 * trace cause "fs", can make reads or creates throw, and keeps its files across an app restart.
 */
public final class FakeFileSystem implements AppFiles {
    public static final String ROOT = "/fake/app-data/files/";
    /** A fixture key: 32 bytes the fake library accepts. Synthetic, identifies nothing. */
    public static final byte[] VALID_KEY = fill(32, 0x5a);
    /** A fixture the fake library rejects as corrupt. */
    public static final byte[] CORRUPT_KEY = "not a key".getBytes();

    public static final class File {
        public byte[] bytes;
        public int mode;

        File(byte[] bytes, int mode) {
            this.bytes = bytes;
            this.mode = mode;
        }
    }

    private final Trace trace;
    public final Map<String, File> files = new HashMap<>();
    /** Paths whose every read throws IOException. */
    public final Set<String> readThrows = new HashSet<>();
    /** Paths whose create throws IOException. */
    public final Set<String> createThrows = new HashSet<>();

    public FakeFileSystem(Trace trace) {
        this.trace = trace;
    }

    private static byte[] fill(int n, int v) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = (byte) (v + i);
        return b;
    }

    /** Seeds a file without recording it: the state before the app starts. */
    public void seed(String path, byte[] bytes, int mode) {
        files.put(path, new File(bytes.clone(), mode));
    }

    @Override
    public String path(String name) {
        return ROOT + name;
    }

    @Override
    public boolean exists(String name) {
        return files.containsKey(path(name));
    }

    @Override
    public long length(String name) throws IOException {
        File f = files.get(path(name));
        if (f == null) throw new IOException("no such file");
        return f.bytes.length;
    }

    @Override
    public byte[] read(String name) throws IOException {
        return readPath(path(name));
    }

    /** A read by absolute path, as the native library makes it. */
    public byte[] readPath(String path) throws IOException {
        trace.add("fs", "read", path);
        if (readThrows.contains(path)) throw new IOException("synthetic read failure");
        File f = files.get(path);
        if (f == null) throw new IOException("no such file");
        return f.bytes.clone();
    }

    /** A create of a new file with {@code mode}, as the native library makes it for a missing key. */
    public void create(String path, byte[] bytes, int mode) throws IOException {
        trace.add("fs", "create", path + " mode=" + Integer.toOctalString(mode));
        if (createThrows.contains(path)) throw new IOException("synthetic create failure");
        files.put(path, new File(bytes.clone(), mode));
    }

    public String sha256(String path) {
        File f = files.get(path);
        if (f == null) return null;
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(f.bytes);
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
