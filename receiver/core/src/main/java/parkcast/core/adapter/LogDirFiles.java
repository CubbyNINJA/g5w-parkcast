package parkcast.core.adapter;

import java.io.IOException;
import parkcast.core.port.LogDir;
import parkcast.gate.port.LogFiles;

/**
 * The gate's {@link LogFiles} over the one log folder, /data/nemesis/logs/airplay-receiver/ (AIR-062). It
 * touches only receiver.log and receiver.log.1 there: rotation renames the current file to the older one,
 * and deleting the older file removes receiver.log.1. A name that would leave the folder is refused, and
 * the folder itself is never created, chmodded or chowned (AIR-062(b)).
 */
public final class LogDirFiles implements LogFiles {
    public static final String ROOT = "/data/nemesis/logs/airplay-receiver/";
    public static final String FILE = "receiver.log";
    public static final String OLDER = FILE + ".1";

    private final LogDir dir;

    public LogDirFiles(LogDir dir) {
        this.dir = dir;
    }

    @Override
    public long size(String name) throws IOException {
        return dir.length(path(name));
    }

    @Override
    public void append(String name, byte[] b) throws IOException {
        dir.append(path(name), b);
    }

    @Override
    public void rotate() throws IOException {
        dir.rename(ROOT + FILE, ROOT + OLDER);
    }

    @Override
    public void deleteOlder() throws IOException {
        dir.delete(ROOT + OLDER);
    }

    private static String path(String name) throws IOException {
        if (name == null || name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.contains(".."))
            throw new IOException("log name outside " + ROOT + ": " + name);
        return ROOT + name;
    }
}
