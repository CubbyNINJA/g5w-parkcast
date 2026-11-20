package parkcast.core.steps;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The whole-app static scan (S3_RECEIVER.md, "The static scan"; AIR-067(b)). It reads every main Java source
 * of every receiver module (comments removed, literals kept), every compiled main class (its constant pool:
 * class names, member references as {@code owner.name(}, and strings), the native sources and the added lines
 * of the native patches, and AndroidManifest.xml (comments removed). Each source is cut into statements at
 * {@code ; { }} so a rule can ask for two tokens in one statement, such as a bind and a port.
 *
 * <p>Constants the compiler inlines (port numbers, START_STICKY, FLAG_ACTIVITY_REORDER_TO_FRONT) are only
 * visible in the sources, so those rules read sources only. The port interfaces of the S2 doubles
 * (MirrorSession, VehicleBus) are declarations, not stock APIs; their zero-call rule is checked at run time.
 */
final class WholeAppScan {
    /** The AIR-067 phrase: the S2_GATE.md forbidden list, widened to the whole app. */
    static final String S2_LIST = "AudioManager, audio-focus or audio-session API, HUD display id, vehicle setter, "
            + "CAN access, stock regulation setter, registerSystemOffResponder, micom power transaction 14, "
            + "REVERSE_OFF or isRearViewOn";

    enum Kind { SOURCE, CLASS, NATIVE, MANIFEST }

    private static final class Unit {
        final Kind kind;
        final String file;
        final String text;
        final List<String> statements = new ArrayList<>();

        Unit(Kind kind, String file, String text, List<String> statements) {
            this.kind = kind;
            this.file = file;
            this.text = text;
            for (String s : statements) {
                String t = s.replaceAll("\\s+", " ").trim();
                if (!t.isEmpty()) this.statements.add(t);
            }
        }
    }

    /** One pattern, or two that must meet in one statement, over the kinds of unit it reads. */
    private static final class Part {
        final String label;
        final Pattern p;
        final Pattern with;
        final boolean wholeFile;
        final Kind[] kinds;

        Part(String label, String p, String with, boolean wholeFile, Kind... kinds) {
            this.label = label;
            this.p = Pattern.compile(p);
            this.with = with == null ? null : Pattern.compile(with);
            this.wholeFile = wholeFile;
            this.kinds = kinds.length == 0 ? Kind.values() : kinds;
        }
    }

    private static final Kind[] CODE = {Kind.SOURCE, Kind.CLASS, Kind.NATIVE};
    private static final Kind[] SRC = {Kind.SOURCE, Kind.NATIVE};
    private static final String SOCKET = "(?i)\\b(bind|connect|send\\w*|InetSocketAddress|Socket|ServerSocket|DatagramSocket|DatagramPacket)\\b";
    private static final String BIND = "(?i)\\bbind\\b";
    private static final String CONNECT = "(?i)\\b(connect|send\\w*|InetSocketAddress|Socket|DatagramPacket)\\b";
    private static final String P7000 = "\\b(7000|0x1[bB]58)\\b|\"1[bB]58\"";
    private static final String DIPO_PORTS = "\\b(5001|5020|6000|6011|6020|6030)\\b";
    private static final String START = "\\b(startActivity|startActivities|startActivityForResult|startService|startForegroundService)\\b";

    private static final Map<String, List<Part>> RULES = new LinkedHashMap<>();

    private static void rule(String phrase, Part... parts) {
        RULES.put(phrase, java.util.Arrays.asList(parts));
    }

    private static Part any(String label, String p, Kind... kinds) {
        return new Part(label, p, null, false, kinds);
    }

    private static Part both(String label, String p, String with, Kind... kinds) {
        return new Part(label, p, with, false, kinds);
    }

    static {
        // AIR-049 and AIR-050(a)
        rule("bind, connect or send to port 7000", both("socket call naming 7000", P7000, SOCKET, SRC));
        rule("bind to port 5001, 5020, 6000, 6011, 6020 or 6030", both("bind naming a DiPO port", DIPO_PORTS, BIND, SRC));
        rule("connect or send to port 5001, 5020, 6000, 6011, 6020 or 6030",
                both("connect or send naming a DiPO port", DIPO_PORTS, CONNECT, SRC));
        rule("stop, signal or reconfiguration of DiPO, mdnsd, hostapd, dnsmasq or wpa_supplicant",
                any("network daemon named", "(?i)\\b(hostapd|dnsmasq|wpa_supplicant|dipo)\\b", CODE),
                any("process signal", "\\b(sendSignal|killProcess|killBackgroundProcesses)\\b|\"(/system/bin/)?kill\\b", CODE),
                both("mdnsd stopped or signalled", "\\bmdnsd\\b", "(?i)\\b(stop|kill|signal|restart|ctl)", CODE));
        rule("/dev/socket/mdns other than as part of /dev/socket/mdnsd", any("mdns socket", "/dev/socket/mdns(?!d)", CODE));
        rule("ndc", any("ndc", "\\bndc\\b", CODE));
        rule("ctl.start", any("ctl.start", "ctl\\.start", CODE));
        rule("ctl.stop", any("ctl.stop", "ctl\\.stop", CODE));
        rule("setprop", any("setprop", "\\bsetprop\\b", CODE));
        rule("su call", any("su in a literal", "\"([^\"\\\\]*[\\s/])?su(\\s[^\"\\\\]*)?\"", CODE));
        rule("nemesis root call", any("nemesis root", "nemesis\\s+root|xbin/nemesis|root\\.sock|nemesis\\.sock", CODE));
        rule("registerService on NsdManager", any("registerService", "\\bregisterService\\b", CODE));
        rule("resolveService on NsdManager", any("resolveService", "\\bresolveService\\b", CODE));
        // AIR-068
        rule("android.intent.action.BOOT_COMPLETED receiver", any("BOOT_COMPLETED", "BOOT_COMPLETED"));
        rule("service element in the manifest", any("<service>", "^<service\\b", Kind.MANIFEST));
        rule("receiver element in the manifest", any("<receiver>", "^<receiver\\b", Kind.MANIFEST));
        rule("AlarmManager", any("alarms", "\\bAlarmManager\\b|\\bALARM_SERVICE\\b|\"alarm\"", CODE));
        rule("SystemProperties.set", any("property setter", "SystemProperties\\s*\\.\\s*set\\w*\\s*\\(|\\bproperty_set\\b|__system_property_set", CODE));
        rule("remount", any("remount", "(?i)remount", CODE));
        rule("WifiManager configuration or hotspot method", any("Wi-Fi configuration or hotspot",
                "\\b(addNetwork|updateNetwork|removeNetwork|enableNetwork|disableNetwork|saveConfiguration"
                        + "|getConfiguredNetworks|setWifiEnabled|startSoftAp|stopSoftAp|startTethering|stopTethering"
                        + "|tether|untether|startLocalOnlyHotspot|\\w*WifiAp\\w*)\\b", CODE));
        rule("WifiConfiguration", any("WifiConfiguration", "\\bWifiConfiguration\\b", CODE));
        // AIR-069(b)
        rule("START_STICKY or START_REDELIVER_INTENT", any("sticky service", "\\b(START_STICKY|START_REDELIVER_INTENT)\\b", SRC));
        rule("startActivity or startService naming the receiver", any("self start", START, CODE));
        rule("Thread.setDefaultUncaughtExceptionHandler that relaunches", new Part("relaunching crash handler",
                "setDefaultUncaughtExceptionHandler", START + "|\\bAlarmManager\\b|\\bPendingIntent\\b|new Intent\\b"
                        + "|\\bProcessBuilder\\b|Runtime\\.getRuntime|\\.exec\\(", true, CODE));
        // AIR-080 and AIR-081
        rule("moveTaskToFront", any("moveTaskToFront", "\\bmoveTaskToFront\\b", CODE));
        rule("startActivity naming the receiver's own activity", any("self start", START, CODE));
        rule("FLAG_ACTIVITY_REORDER_TO_FRONT", any("reorder flag", "FLAG_ACTIVITY_REORDER_TO_FRONT", SRC));
        rule("Presentation", any("Presentation", "\\bPresentation\\b", CODE));
        rule("DisplayManager", any("DisplayManager", "\\bDisplayManager\\b|\\bDISPLAY_SERVICE\\b", CODE));
        rule("createVirtualDisplay", any("createVirtualDisplay", "\\bcreateVirtualDisplay\\b", CODE));
        rule("setLaunchDisplayId", any("setLaunchDisplayId", "\\bsetLaunchDisplayId\\b", CODE));
        // AIR-082: the manifest only
        rule("android.intent.category.LAUNCHER category", any("launcher", "android\\.intent\\.category\\.LAUNCHER\\b", Kind.MANIFEST));
        rule("android.intent.category.HOME category", any("home", "android\\.intent\\.category\\.HOME\\b", Kind.MANIFEST));
        // AIR-054(a): no audio output
        rule("AudioTrack", any("AudioTrack", "\\bAudioTrack\\b", CODE));
        rule("AudioManager", any("AudioManager", "\\bAudioManager\\b|\\bAUDIO_SERVICE\\b|\"audio\"", CODE));
        rule("requestAudioFocus", any("requestAudioFocus", "\\brequestAudioFocus\\b", CODE));
        rule("abandonAudioFocus", any("abandonAudioFocus", "\\babandonAudioFocus\\w*", CODE));
        rule("AudioFocusRequest", any("AudioFocusRequest", "\\bAudioFocusRequest\\b", CODE));
        // AIR-067(b): S2_GATE.md lines 30-32
        rule(S2_LIST,
                any("AudioManager", "\\bAudioManager\\b|\\bAUDIO_SERVICE\\b|\"audio\"", CODE),
                any("audio focus", "\\b(requestAudioFocus|abandonAudioFocus\\w*|AudioFocusRequest|OnAudioFocusChange\\w*|AUDIOFOCUS_\\w*)\\b", CODE),
                any("audio session", "\\b(AudioSession\\w*|getAudioSessionId|generateAudioSessionId|setAudioSessionId|MediaSession\\w*)\\b", CODE),
                any("HUD display id", "(?i)hud\\w*display\\w*id|display\\w*id\\w*hud|IUSMService|hkmc[./]usm|setHUD_", CODE),
                any("vehicle setter", "(?i)\\b\\w*vehicle\\w*\\s*\\.\\s*set\\w*\\s*\\(|hkmc[./]vehicle", CODE),
                any("CAN access", "\\b(CanManager|AF_CAN|PF_CAN|ICan\\w*)\\b|/dev/can|(?i)socketcan|hkmc[./]can\\b", CODE),
                any("regulation setter", "(?i)\\b\\w*regulation\\w*\\s*\\.\\s*set\\w*\\s*\\(|\\bsetRegulation\\w*", CODE),
                any("registerSystemOffResponder", "registerSystemOffResponder", CODE),
                // AIR-059(b) forbids the micom POWER service (transaction 14); AIR-058 requires com.hkmc.micom.
                // VehicleInfoManager.getGearPStatus(), so this rule names the power service only (owner, OD-197).
                any("micom power", "(?i)micom\\w*power|\\bIMicomPower\\w*|hkmc[./]micom[./]power", CODE),
                any("binder transaction", "\\.transact\\s*\\(|IBinder\\.transact\\(", CODE),
                any("REVERSE_OFF", "\\bREVERSE_OFF\\b", CODE),
                any("isRearViewOn", "\\bisRearViewOn\\b", CODE));
    }

    private final Path root;
    private final List<Unit> units = new ArrayList<>();

    private WholeAppScan(Path root) throws IOException {
        this.root = root;
        Path receiver = root.resolve("receiver");
        if (!Files.isDirectory(receiver)) throw new IllegalStateException("no receiver/ under " + root);
        try (Stream<Path> all = Files.walk(receiver)) {
            for (Path p : (Iterable<Path>) all.filter(Files::isRegularFile).sorted()::iterator) add(p);
        }
    }

    /** The scan of this checkout: the nearest directory above the working directory that holds specs/. */
    static WholeAppScan ofRepo() throws IOException {
        Path d = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (d != null && !Files.isDirectory(d.resolve("specs"))) d = d.getParent();
        if (d == null) throw new IllegalStateException("no specs/ above " + System.getProperty("user.dir"));
        return new WholeAppScan(d);
    }

    /** The scan of another tree, such as a fixture with a planted reference. */
    static WholeAppScan of(Path root) throws IOException {
        return new WholeAppScan(root);
    }

    private void add(Path p) throws IOException {
        String rel = root.relativize(p).toString().replace('\\', '/');
        String name = p.getFileName().toString();
        if (rel.matches("receiver/[^/]+/src/main/java/.*\\.java")) {
            String t = stripComments(read(p));
            units.add(new Unit(Kind.SOURCE, rel, t, java.util.Arrays.asList(t.split("[;{}]"))));
        } else if (rel.matches("receiver/[^/]+/build/classes/java/main/.*\\.class")) {
            List<String> pool = constantPool(p);
            units.add(new Unit(Kind.CLASS, rel, String.join("\n", pool), pool));
        // Our own JNI and host sources only: receiver/native/.cache holds third-party downloads (OpenSSL,
        // libplist, UxPlay), which native prep fetches and which are not the receiver's code (S3_RECEIVER.md).
        } else if (rel.startsWith("receiver/native/src/") && name.matches(".*\\.(c|h|cc|cpp)")) {
            String t = stripComments(read(p));
            units.add(new Unit(Kind.NATIVE, rel, t, java.util.Arrays.asList(t.split("[;{}]"))));
        } else if (rel.startsWith("receiver/native/") && name.endsWith(".patch")) {
            StringBuilder added = new StringBuilder();
            for (String line : read(p).split("\n"))
                if (line.startsWith("+") && !line.startsWith("+++")) added.append(line.substring(1)).append('\n');
            String t = stripComments(added.toString());
            units.add(new Unit(Kind.NATIVE, rel, t, java.util.Arrays.asList(t.split("[;{}]"))));
        } else if (rel.equals("receiver/app/src/main/AndroidManifest.xml")) {
            String t = read(p).replaceAll("(?s)<!--.*?-->", "");
            List<String> elements = new ArrayList<>();
            for (String e : t.split("(?=<)")) elements.add(e);
            units.add(new Unit(Kind.MANIFEST, rel, t, elements));
        }
    }

    int fileCount() {
        return units.size();
    }

    int fileCount(Kind k) {
        int n = 0;
        for (Unit u : units) if (u.kind == k) n++;
        return n;
    }

    /** Every hit of the rule a phrase names, as "file: part: statement". A phrase with no rule is an error. */
    List<String> find(String phrase) {
        List<Part> parts = RULES.get(phrase);
        if (parts == null) throw new IllegalArgumentException("the static scan has no rule for \"" + phrase + "\"");
        List<String> hits = new ArrayList<>();
        for (Part part : parts)
            for (Unit u : units) {
                if (!java.util.Arrays.asList(part.kinds).contains(u.kind)) continue;
                if (part.wholeFile) {
                    if (part.p.matcher(u.text).find() && part.with.matcher(u.text).find())
                        hits.add(u.file + ": " + part.label);
                    continue;
                }
                for (String s : u.statements)
                    if (part.p.matcher(s).find() && (part.with == null || part.with.matcher(s).find()))
                        hits.add(u.file + ": " + part.label + ": " + (s.length() > 160 ? s.substring(0, 160) + "..." : s));
            }
        return hits;
    }

    static List<String> phrases() {
        return new ArrayList<>(RULES.keySet());
    }

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    /** Removes // and block comments, keeping string and char literals (as CoverHarnessB does). */
    static String stripComments(String s) {
        StringBuilder b = new StringBuilder(s.length());
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && s.charAt(j) != c && s.charAt(j) != '\n') j += s.charAt(j) == '\\' ? 2 : 1;
                j = Math.min(j + 1, n);
                b.append(s, i, j);
                i = j;
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                b.append(' ');
            } else {
                b.append(c);
                i++;
            }
        }
        return b.toString();
    }

    /**
     * The constant pool of a class file as statements: each class as its dotted name, each field or method
     * reference as {@code owner.name(}, each string as a quoted literal, and every other UTF-8 entry as is.
     */
    static List<String> constantPool(Path p) throws IOException {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(p))) {
            if (in.readInt() != 0xCAFEBABE) throw new IOException("not a class file: " + p);
            in.readUnsignedShort();
            in.readUnsignedShort();
            int count = in.readUnsignedShort();
            String[] utf = new String[count];
            int[] tag = new int[count], a = new int[count], b = new int[count];
            for (int i = 1; i < count; i++) {
                tag[i] = in.readUnsignedByte();
                switch (tag[i]) {
                    case 1: utf[i] = in.readUTF(); break;
                    case 3: case 4: in.readInt(); break;
                    case 5: case 6: in.readLong(); i++; break;
                    case 7: case 8: case 16: case 19: case 20: a[i] = in.readUnsignedShort(); break;
                    case 15: in.readUnsignedByte(); a[i] = in.readUnsignedShort(); break;
                    case 9: case 10: case 11: case 12: case 17: case 18:
                        a[i] = in.readUnsignedShort();
                        b[i] = in.readUnsignedShort();
                        break;
                    default: throw new IOException("constant pool tag " + tag[i] + " in " + p);
                }
            }
            List<String> out = new ArrayList<>();
            boolean[] named = new boolean[count];
            for (int i = 1; i < count; i++) {
                if (tag[i] == 7) {
                    out.add(utf[a[i]].replace('/', '.'));
                    named[a[i]] = true;
                } else if (tag[i] == 8) {
                    out.add("\"" + utf[a[i]] + "\"");
                    named[a[i]] = true;
                } else if (tag[i] == 9 || tag[i] == 10 || tag[i] == 11) {
                    out.add(utf[a[a[i]]].replace('/', '.') + "." + utf[a[b[i]]] + "(");
                }
            }
            for (int i = 1; i < count; i++)
                if (tag[i] == 1 && !named[i]) out.add(utf[i].replace('/', '.'));
            return out;
        }
    }
}
