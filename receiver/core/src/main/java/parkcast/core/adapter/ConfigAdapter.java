package parkcast.core.adapter;

import java.io.FileNotFoundException;
import java.io.IOException;
import parkcast.core.port.PathReader;
import parkcast.gate.port.ConfigFile;

/**
 * The gate's {@link ConfigFile} over the one installed config path (AIR-063). It only reads: nothing here
 * creates, writes or repairs the file. A missing file is {@link ConfigFile.Missing}; any other read
 * failure is {@link ConfigFile.Unreadable}.
 */
public final class ConfigAdapter implements ConfigFile {
    public static final String PATH = "/data/nemesis/apps/airplay.receiver/config/receiver.conf";

    private final PathReader reader;

    public ConfigAdapter(PathReader reader) {
        this.reader = reader;
    }

    @Override
    public byte[] read() throws Missing, Unreadable {
        try {
            return reader.read(PATH);
        } catch (FileNotFoundException e) {
            throw new Missing("receiver.conf is missing");
        } catch (IOException e) {
            throw new Unreadable("receiver.conf is unreadable");
        }
    }
}
