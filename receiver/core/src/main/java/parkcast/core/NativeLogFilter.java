package parkcast.core;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;
import parkcast.core.port.AppFiles;

/**
 * AIR-073's filter for UxPlay's log records, as an allowlist that fails safe: a record passes only if it holds
 * an allowlisted UxPlay text and none of the forbidden items, and then has its addresses redacted. Anything
 * else, including a record the filter cannot judge, is dropped whole. {@link #pass} returns {@code null} for a
 * drop; the caller counts it (AIR-073(c)).
 */
class NativeLogFilter {
    static final String REDACTED = "[redacted]";

    /** UxPlay's own texts that may be written (docs/evidence/UXPLAY_LOG_SIGNATURES.md, AIR-071(b)). */
    static final String[] ALLOWLIST = {
        "Mirroring initialized successfully",
        "Mirroring not initialized at SETUP",
        "raop_rtp_mirror accepting client",
        "raop_rtp_mirror width_source = ",
        "raop_rtp_mirror tcp socket was closed by client",
        "*** ERROR lost connection with client",
        "raop_rtp_mirror: received type 0x01 packet with no payload",
        "this indicates non-h264 video but Airplay features bit 42",
        "use startup option \"-h265\"",
        "video is h265 but the -h265 option was not used",
    };

    /** The native refusal records, allowed only as the whole record (AIR-053(b), OD-185). */
    static final String[] EXACT = {
        "refused url", "refused hls", "refused drm",
    };

    /** AIR-073(g): matched case-insensitively anywhere, even inside a longer word. */
    static final String[] TOKENS = {
        "pin", "user-agent", "deviceid", "device id", "aesiv", "ekey", "aeskey", "ecdh", "plist", "<?xml",
    };

    /** A key window this long or longer is enough to drop a record (AIR-083(d)). */
    static final int KEY_WINDOW = 16;

    private static final Pattern HEADER = Pattern.compile("^[A-Za-z][A-Za-z0-9-]*: ");
    private static final Pattern HEX_RUN = Pattern.compile("[0-9A-Fa-f]{32,}");
    private static final Pattern BASE64_RUN = Pattern.compile("[A-Za-z0-9+/=]{44,}");
    private static final Pattern MAC = Pattern.compile("[0-9A-Fa-f]{2}(?:[:-][0-9A-Fa-f]{2}){5}");
    private static final Pattern IPV4 = Pattern.compile("\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b");
    private static final Pattern IPV6 = Pattern.compile(
            "(?<![0-9A-Za-z:])(?:[0-9A-Fa-f]{0,4}:){2,7}[0-9A-Fa-f]{0,4}(?![0-9A-Za-z:])");

    private final AppFiles files;
    private final String keyFile;
    /** The key's bytes as ISO-8859-1 chars, once read; null until a read succeeds. */
    private String key;

    NativeLogFilter(AppFiles files, String keyFile) {
        this.files = files;
        this.keyFile = keyFile;
    }

    /** The record to write, redacted, or {@code null} to drop it. May throw; the caller drops and counts. */
    String pass(String record) {
        if (record == null || !allowlisted(record)) return null;
        for (int i = 0; i < record.length(); i++) {
            char c = record.charAt(i);
            if (c < 0x20 || c > 0x7e) return null;
        }
        if (HEADER.matcher(record).find()) return null;
        if (HEX_RUN.matcher(record).find() || BASE64_RUN.matcher(record).find()) return null;
        String lower = record.toLowerCase(Locale.ROOT);
        if (keyFile != null && lower.contains(keyFile.toLowerCase(Locale.ROOT))) return null;
        for (String t : TOKENS) if (lower.contains(t)) return null;
        if (holdsKey(record)) return null;
        String out = MAC.matcher(record).replaceAll(REDACTED);
        out = IPV4.matcher(out).replaceAll(REDACTED);
        return IPV6.matcher(out).replaceAll(REDACTED);
    }

    private static boolean allowlisted(String record) {
        for (String x : EXACT) if (record.equals(x)) return true;
        for (String a : ALLOWLIST) if (record.contains(a)) return true;
        return false;
    }

    /** True if the record holds a window of the key, or the key cannot be read to check (fail safe). */
    private boolean holdsKey(String record) {
        if (files == null || keyFile == null) return false;
        if (key == null) {
            try {
                if (!files.exists(keyFile)) return false;
                key = new String(files.read(keyFile), "ISO-8859-1");
            } catch (IOException e) {
                return true;
            } catch (RuntimeException e) {
                return true;
            }
        }
        int n = Math.min(KEY_WINDOW, key.length());
        if (n == 0) return false;
        for (int i = 0; i + n <= key.length(); i++) if (record.contains(key.substring(i, i + n))) return true;
        return false;
    }
}
