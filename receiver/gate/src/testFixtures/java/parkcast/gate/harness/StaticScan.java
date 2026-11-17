package parkcast.gate.harness;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Text scan of the receiver's main sources and compiled classes. Run from the module directory. */
public final class StaticScan {
    public final List<Path> files = new ArrayList<>();
    public final List<String> systemOffHits = new ArrayList<>();
    public final List<String> micomHits = new ArrayList<>();

    private static final String[] MICOM = {
        "com.hkmc.micom.power", "com/hkmc/micom/power", "MicomPower", "IMicomPower"
    };

    public static StaticScan run() {
        StaticScan s = new StaticScan();
        // The gate module directory; core sets parkcast.gate.dir when it reruns these scenarios (OD-189).
        Path base = Paths.get(System.getProperty("parkcast.gate.dir", System.getProperty("user.dir")));
        s.collect(base.resolve("src/main/java"), ".java");
        s.collect(base.resolve("build/classes/java/main"), ".class");
        for (Path p : s.files) {
            String text;
            try {
                text = new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (text.contains("registerSystemOffResponder")) s.systemOffHits.add(p.toString());
            boolean micom = false;
            for (String m : MICOM) micom |= text.contains(m);
            if (text.contains("transact") && text.contains("IBinder")) micom = true;
            if (micom) s.micomHits.add(p.toString());
        }
        return s;
    }

    private void collect(Path root, String ext) {
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> st = Files.walk(root)) {
            files.addAll(st.filter(p -> p.toString().endsWith(ext)).collect(Collectors.toList()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
