package parkcast.core.doubles;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import parkcast.core.port.PathReader;
import parkcast.gate.harness.Fakes;
import parkcast.gate.port.ConfigFile;

/**
 * The fake file system's view of the installed config file. It serves the gate's {@link Fakes.Config} at
 * the one config path, so the gate's config steps keep working, and records every path read under the
 * trace cause "fs". Any other path does not exist.
 */
public final class FakeConfigReader implements PathReader {
    public static final String CONFIG_PATH = "/data/nemesis/apps/airplay.receiver/config/receiver.conf";

    private final Fakes.Config config;
    private final Trace trace;
    public final List<String> reads = new ArrayList<>();

    public FakeConfigReader(Fakes.Config config, Trace trace) {
        this.config = config;
        this.trace = trace;
    }

    @Override
    public byte[] read(String path) throws IOException {
        reads.add(path);
        trace.add("fs", "read", path);
        if (!CONFIG_PATH.equals(path)) throw new FileNotFoundException(path);
        try {
            return config.read();
        } catch (ConfigFile.Missing e) {
            throw new FileNotFoundException(path);
        } catch (ConfigFile.Unreadable e) {
            throw new IOException(e.getMessage());
        }
    }
}
