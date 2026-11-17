package parkcast.core.steps;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The static scan (feature background): the receiver's app sources, native sources, compiled classes and
 * AndroidManifest.xml, the whole app and not only the gate. "Finds no X" means no reference to X by name in
 * any of them. Java comments are not references, so they are taken out before the search; compiled classes
 * carry none.
 */
public final class ScopeSteps {
    /** One scanned file: its path and the text searched. */
    static final class Scanned {
        final Path path;
        final String text;
        final boolean manifest, classFile;

        Scanned(Path path, String text) {
            this.path = path;
            this.text = text;
            String n = path.getFileName().toString();
            this.manifest = n.equals("AndroidManifest.xml");
            this.classFile = n.endsWith(".class");
        }
    }

    /** What each "finds no" phrase means, as name patterns; a file matching every pattern of one entry is a hit. */
    private static final Map<String, List<Pattern[]>> FORBIDDEN = new LinkedHashMap<>();
    /** Phrases whose rules apply to AndroidManifest.xml only. */
    private static final Set<String> MANIFEST_ONLY = new HashSet<>();

    static {
        // AIR-058: the BCM's gear-status method is getGearStatusInfo (docs/evidence/GEAR_PROBE_FINDINGS.md).
        forbid("BCM gear-status method", "getGearStatusInfo");
        forbid("REQ_GEAR_STATUS", "REQ_GEAR_STATUS");
        // AIR-064: the build is identified from ro.build.product only.
        forbid("Build.PRODUCT", "\\bBuild\\s*\\.\\s*PRODUCT\\b");
        forbid("Build.PRODUCT", "android/os/Build\\b", "\\bPRODUCT\\b");
        forbid("Build.PRODUCT", "\\bandroid\\.os\\.Build\\s*\\.\\s*PRODUCT\\b");
        // AIR-059(b): no system-off responder and no micom power service (transaction 14 is its method).
        forbid("registerSystemOffResponder", "registerSystemOffResponder");
        forbid("micom power transaction 14", "com[./]hkmc[./]micom[./]power");
        forbid("micom power transaction 14", "\\bMicomPower\\w*");
        // AIR-060: REVERSE_ON is registered in code, never by a manifest receiver; nothing reads reverse-off state.
        forbid("com.hkmc.intent.action.REVERSE_ON receiver in the manifest",
                "(?s)<receiver\\b(?:(?!</receiver>).)*com\\.hkmc\\.intent\\.action\\.REVERSE_ON");
        MANIFEST_ONLY.add("com.hkmc.intent.action.REVERSE_ON receiver in the manifest");
        forbid("REVERSE_OFF", "REVERSE_OFF");
        forbid("isRearViewOn", "isRearViewOn");
        // AIR-061(a): notices are toast windows; no overlay permission or system-alert window type.
        forbid("SYSTEM_ALERT_WINDOW", "SYSTEM_ALERT_WINDOW");
        forbid("TYPE_SYSTEM_ALERT", "TYPE_SYSTEM_ALERT");
    }

    private static void forbid(String phrase, String... all) {
        Pattern[] ps = new Pattern[all.length];
        for (int i = 0; i < all.length; i++) ps[i] = Pattern.compile(all[i]);
        FORBIDDEN.computeIfAbsent(phrase, k -> new ArrayList<>()).add(ps);
    }

    private final List<Scanned> files = new ArrayList<>();

    @When("the static scan runs")
    public void scan() {
        Path receiver = Paths.get(System.getProperty("parkcast.receiver.dir",
                Paths.get(System.getProperty("user.dir")).resolve("..").toString())).normalize();
        for (String m : new String[] {"core", "gate", "app"}) {
            collect(receiver.resolve(m).resolve("src/main"));
            collect(receiver.resolve(m).resolve("build/classes/java/main"));
        }
        collect(receiver.resolve("native"));
        boolean manifest = false, java = false;
        for (Scanned s : files) {
            manifest |= s.manifest;
            java |= s.path.toString().endsWith(".java");
        }
        if (!manifest || !java) throw new AssertionError("the static scan found no manifest or no sources under " + receiver);
    }

    @Then("it finds no {}")
    public void findsNo(String phrases) {
        if (files.isEmpty()) throw new IllegalStateException("no step ran the static scan");
        List<String> hits = new ArrayList<>();
        for (String phrase : phrases.split(" and no |, | or no | or ")) {
            List<Pattern[]> rules = FORBIDDEN.get(phrase.trim());
            if (rules == null) throw new IllegalArgumentException("the static scan has no rule for \"" + phrase + "\"");
            boolean manifestOnly = MANIFEST_ONLY.contains(phrase.trim());
            for (Scanned s : files)
                for (Pattern[] rule : rules)
                    if ((s.manifest || !manifestOnly) && matchesAll(s.text, rule)) hits.add(phrase.trim() + " in " + s.path);
        }
        if (!hits.isEmpty()) throw new AssertionError("forbidden references: " + hits);
    }

    private static boolean matchesAll(String text, Pattern[] rule) {
        for (Pattern p : rule) if (!p.matcher(text).find()) return false;
        return true;
    }

    private void collect(Path root) {
        if (!Files.isDirectory(root)) return;
        List<Path> paths;
        try (Stream<Path> st = Files.walk(root)) {
            paths = st.filter(Files::isRegularFile).collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Path p : paths) {
            String text;
            try {
                text = new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (p.toString().endsWith(".java")) text = stripComments(text);
            if (p.toString().endsWith(".xml")) text = text.replaceAll("(?s)<!--.*?-->", " ");
            files.add(new Scanned(p, text));
        }
    }

    /** Java source without its comments; string and char literals are kept as they are. */
    static String stripComments(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && s.charAt(j) != c && s.charAt(j) != '\n') j += s.charAt(j) == '\\' ? 2 : 1;
                j = Math.min(j + 1, n);
                out.append(s, i, j);
                i = j;
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                out.append(' ');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static final Pattern MAKE_TEXT = Pattern.compile("\\bToast\\s*\\.\\s*makeText\\s*\\(");
    private static final Pattern GET_VIEW = Pattern.compile("\\s*\\.\\s*getView\\s*\\(\\s*\\)");
    private static final Pattern SHOW_CALL = Pattern.compile("\\.\\s*show\\s*\\(");
    private static final Pattern SHOW_NAME = Pattern.compile("(?<![A-Za-z0-9_$])show(?![A-Za-z0-9_$])");

    /** AIR-061(a): every Toast.makeText(...) is followed by .getView(); no Toast is ever shown with show(). */
    @Then("^it finds Toast\\.makeText used only with getView\\(\\), never with show\\(\\)$")
    public void toastViewsOnly() {
        if (files.isEmpty()) throw new IllegalStateException("no step ran the static scan");
        List<String> bad = new ArrayList<>();
        int uses = 0;
        for (Scanned s : files) {
            if (s.path.toString().endsWith(".java")) {
                Matcher m = MAKE_TEXT.matcher(s.text);
                while (m.find()) {
                    uses++;
                    int close = closingParen(s.text, m.end());
                    if (close < 0 || !GET_VIEW.matcher(s.text).region(close + 1, s.text.length()).lookingAt())
                        bad.add("Toast.makeText without getView() in " + s.path);
                }
                if (s.text.contains("Toast") && SHOW_CALL.matcher(s.text).find()) bad.add("show() in " + s.path);
            } else if (s.classFile && s.text.contains("android/widget/Toast") && SHOW_NAME.matcher(s.text).find()) {
                bad.add("show in " + s.path);
            }
        }
        if (uses == 0) throw new AssertionError("the static scan found no Toast.makeText");
        if (!bad.isEmpty()) throw new AssertionError("toasts not used as views only: " + bad);
    }

    /** The index of the ')' closing a '(' just before {@code from}, or -1; literals are skipped. */
    private static int closingParen(String s, int from) {
        int depth = 1;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < s.length() && s.charAt(j) != c) j += s.charAt(j) == '\\' ? 2 : 1;
                i = j;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }
}
