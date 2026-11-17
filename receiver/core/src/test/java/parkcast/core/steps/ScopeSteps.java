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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    static {
        // AIR-058: the BCM's gear-status method is getGearStatusInfo (docs/evidence/GEAR_PROBE_FINDINGS.md).
        forbid("BCM gear-status method", "getGearStatusInfo");
        forbid("REQ_GEAR_STATUS", "REQ_GEAR_STATUS");
        // AIR-064: the build is identified from ro.build.product only.
        forbid("Build.PRODUCT", "\\bBuild\\s*\\.\\s*PRODUCT\\b");
        forbid("Build.PRODUCT", "android/os/Build\\b", "\\bPRODUCT\\b");
        forbid("Build.PRODUCT", "\\bandroid\\.os\\.Build\\s*\\.\\s*PRODUCT\\b");
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
            for (Scanned s : files)
                for (Pattern[] rule : rules)
                    if (matchesAll(s.text, rule)) hits.add(phrase.trim() + " in " + s.path);
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
}
