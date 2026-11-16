package parkcast.gate;

import java.nio.charset.Charset;
import java.util.LinkedHashSet;
import java.util.Set;
import parkcast.gate.port.ConfigFile;

/**
 * Reads video.require_park from the installed receiver.conf (AIR-023, AIR-025(a)).
 *
 * <p>Each line is trimmed as a whole, never around {@code =}. Every line whose part before the first
 * {@code =} names the key, with or without whitespace beside the {@code =}, is an occurrence. Occurrences are
 * compared as whole trimmed lines: more than one distinct line is a conflicting duplicate key; a single
 * distinct line with whitespace beside {@code =}, or a value other than lowercase {@code true} or
 * {@code false}, is an unrecognised value. Every fallback resolves to true.
 */
public final class ReceiverConfig {
    static final String KEY = "video.require_park";

    static final String READ = "read from the file";
    static final String MISSING_FILE = "the missing file";
    static final String UNREADABLE_FILE = "the unreadable file";
    static final String MISSING_KEY = "the missing key";
    static final String CONFLICT = "the conflicting duplicate key";
    static final String UNRECOGNISED = "the unrecognised value";

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final ConfigFile file;
    private boolean loaded;
    private boolean requirePark = true;
    private String cause = MISSING_FILE;

    public ReceiverConfig(ConfigFile file) {
        this.file = file;
    }

    /** Reads the file once; later calls keep the first result (the flag is read at every app start only). */
    public void load() {
        if (loaded) return;
        loaded = true;
        byte[] bytes;
        try {
            bytes = file.read();
        } catch (ConfigFile.Missing e) {
            resolve(true, MISSING_FILE);
            return;
        } catch (ConfigFile.Unreadable e) {
            resolve(true, UNREADABLE_FILE);
            return;
        } catch (RuntimeException e) {
            resolve(true, UNREADABLE_FILE);
            return;
        }
        if (bytes == null) {
            resolve(true, UNREADABLE_FILE);
            return;
        }
        parse(new String(bytes, UTF8));
    }

    private void parse(String text) {
        Set<String> occurrences = new LinkedHashSet<String>();
        boolean spaced = false;
        String value = null;
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String keyPart = line.substring(0, eq);
            if (!KEY.equals(keyPart.trim())) continue;
            String rest = line.substring(eq + 1);
            occurrences.add(line);
            spaced = !KEY.equals(keyPart) || (rest.length() > 0 && isSpace(rest.charAt(0)));
            value = rest;
        }
        if (occurrences.isEmpty()) {
            resolve(true, MISSING_KEY);
        } else if (occurrences.size() > 1) {
            resolve(true, CONFLICT);
        } else if (spaced) {
            resolve(true, UNRECOGNISED);
        } else if ("true".equals(value)) {
            resolve(true, READ);
        } else if ("false".equals(value)) {
            resolve(false, READ);
        } else {
            resolve(true, UNRECOGNISED);
        }
    }

    private static boolean isSpace(char c) {
        return c <= ' ' || Character.isWhitespace(c);
    }

    private void resolve(boolean flag, String why) {
        requirePark = flag;
        cause = why;
    }

    /** True when the parking check is on. */
    public boolean requirePark() {
        return requirePark;
    }

    /** Why the check fell back to on, or null when the file was read as written. */
    public String fallbackCause() {
        return READ.equals(cause) ? null : cause;
    }

    /** The AIR-025(a) reason phrase: {@code read from the file} or the fallback cause. */
    public String reason() {
        return cause;
    }
}
