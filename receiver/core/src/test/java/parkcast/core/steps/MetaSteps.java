package parkcast.core.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import parkcast.core.adapter.LogDirFiles;
import parkcast.core.adapter.ReverseReceiver;
import parkcast.core.doubles.FakeBroadcasts;
import parkcast.core.port.Broadcasts;
import parkcast.core.port.LogDir;
import parkcast.gate.World;
import parkcast.gate.harness.Fakes;
import parkcast.gate.port.ReverseSource;

/**
 * The meta profile (S3_RECEIVER.md, "The meta profile"; OD-189): reruns S2's own scenarios, selected by a tag
 * expression from the gate's feature files, in process with the gate's glue, while the gate's {@link World}
 * binds one port to a real Android adapter over a fake platform source. The run passes only if Cucumber exits
 * 0, at least one scenario ran, every scenario passed with nothing undefined, pending or skipped, and the
 * adapter was in fact exercised.
 */
public final class MetaSteps {
    /** The receiver's log directory on the unit (AIR-062). */
    static final String LOG_DIR = "/data/nemesis/logs/airplay-receiver/";
    private String tags;
    private String adapter;
    private byte exit = -1;
    private int scenarios;
    private final Map<String, Integer> statuses = new LinkedHashMap<>();
    private String report = "";
    /** Calls the bound adapter made on its fake platform source during the run. */
    private static int adapterCalls;
    /** Every write, rename and delete the fake file system recorded during the run, as "op path". */
    private static final List<String> fsWrites = new ArrayList<>();

    @Given("the scenarios selected by the tag expression {string} are bound to the Android REVERSE_ON receiver adapter driven by a fake broadcast source")
    public void boundToReverse(String tags) {
        this.tags = tags;
        this.adapter = "reverse";
    }

    @Given("the scenarios selected by the tag expression {string} are bound to the Android LogFiles adapter over a fake file system rooted at \\/data\\/nemesis\\/logs\\/airplay-receiver\\/")
    public void boundToLogs(String tags) {
        this.tags = tags;
        this.adapter = "logs";
    }

    @When("those scenarios run")
    public void run() throws IOException {
        Path root = repoRoot();
        Path features = root.resolve("specs").resolve("features");
        Path json = Files.createTempFile("meta-", ".json");
        List<String> args = new ArrayList<>();
        args.add("--glue");
        args.add("parkcast.gate.steps");
        args.add("--tags");
        args.add(tags);
        args.add("--plugin");
        args.add("json:" + json.toAbsolutePath().toUri());
        args.add("--monochrome");
        for (String f : gateFeatures(root)) args.add(features.resolve(f).toString());

        Properties saved = new Properties();
        for (String k : System.getProperties().stringPropertyNames())
            if (k.startsWith("cucumber.")) saved.setProperty(k, System.getProperty(k));
        String gateDir = System.getProperty("parkcast.gate.dir");
        for (String k : saved.stringPropertyNames()) System.clearProperty(k);
        System.setProperty("cucumber.publish.quiet", "true");
        System.setProperty("parkcast.gate.dir", root.resolve("receiver").resolve("gate").toString());
        adapterCalls = 0;
        fsWrites.clear();
        bind(adapter);
        try {
            exit = io.cucumber.core.cli.Main.run(args.toArray(new String[0]),
                    Thread.currentThread().getContextClassLoader());
        } finally {
            unbind();
            System.clearProperty("cucumber.publish.quiet");
            for (String k : saved.stringPropertyNames()) System.setProperty(k, saved.getProperty(k));
            if (gateDir == null) System.clearProperty("parkcast.gate.dir");
            else System.setProperty("parkcast.gate.dir", gateDir);
        }
        report = new String(Files.readAllBytes(json), StandardCharsets.UTF_8);
        Files.deleteIfExists(json);
        Matcher m = Pattern.compile("\"type\"\\s*:\\s*\"scenario\"").matcher(report);
        while (m.find()) scenarios++;
        m = Pattern.compile("\"status\"\\s*:\\s*\"(\\w+)\"").matcher(report);
        while (m.find()) {
            Integer n = statuses.get(m.group(1));
            statuses.put(m.group(1), n == null ? 1 : n + 1);
        }
    }

    @Then("every one of them passes against that adapter")
    public void allPass() {
        if (exit != 0) throw new AssertionError("Cucumber exited " + exit + " for " + tags + "; statuses " + statuses);
        if (scenarios == 0) throw new AssertionError("no scenario ran for " + tags);
        for (String s : statuses.keySet())
            if (!s.equals("passed"))
                throw new AssertionError(statuses.get(s) + " step(s) " + s + " in " + scenarios + " scenarios for " + tags);
        if (!statuses.containsKey("passed")) throw new AssertionError("no step passed for " + tags);
        if (adapterCalls == 0)
            throw new AssertionError("no scenario of " + scenarios + " went through the " + adapter + " adapter");
    }

    @Then("the fake file system recorded no write outside \\/data\\/nemesis\\/logs\\/airplay-receiver\\/")
    public void noWriteOutsideLogDir() {
        if (fsWrites.isEmpty()) throw new AssertionError("the fake file system recorded no write at all");
        List<String> outside = new ArrayList<>();
        for (String op : fsWrites)
            for (String tok : op.split(" "))
                if (tok.startsWith("/") && !tok.startsWith(LOG_DIR)) outside.add(op);
        if (!outside.isEmpty()) throw new AssertionError("written outside " + LOG_DIR + ": " + outside);
    }

    // ------------------------------------------------------------------ bindings

    private static void bind(String adapter) {
        if (adapter.equals("reverse")) World.reverseBinding = MetaSteps::reverseAdapter;
        else if (adapter.equals("logs")) World.logsBinding = MetaSteps::logsAdapter;
        else throw new IllegalStateException("unknown adapter " + adapter);
    }

    private static void unbind() {
        World.reverseBinding = null;
        World.logsBinding = null;
    }

    /**
     * A fake broadcast source standing in for Context.registerReceiver, driven by the gate's REVERSE_ON double:
     * registration goes through the double (so its counts, throws and returned intent still apply), and when a
     * step makes the double deliver, REVERSE_OFF and a REVERSE_ON carrying extras are broadcast. After every
     * registration REVERSE_OFF and a sticky-looking REVERSE_ON are broadcast too. Each broadcast reaches only
     * a live receiver whose filter holds its action.
     */
    static final class ReverseBroadcasts implements Broadcasts {
        static final String REVERSE_OFF = "com.hkmc.intent.action.REVERSE_OFF";
        private final Fakes.Reverse fake;
        private Receiver live;
        private List<String> filter = new ArrayList<>();

        ReverseBroadcasts(Fakes.Reverse fake) {
            this.fake = fake;
        }

        @Override
        public Object mainHandler() {
            return null;
        }

        @Override
        public Message register(Receiver r, String[] actions, String permission, Object handler) {
            boolean got = fake.register(new ReverseSource.ReverseListener() {
                @Override
                public void reverseOn() {
                    send(REVERSE_OFF, new FakeBroadcasts.Intent(false).put("isRearViewOn", Boolean.FALSE));
                    send(FakeBroadcasts.REVERSE_ON, new FakeBroadcasts.Intent(false).put("isRearViewOn", Boolean.TRUE));
                }
            });
            adapterCalls++;
            live = r;
            filter = new ArrayList<>(java.util.Arrays.asList(actions));
            send(REVERSE_OFF, new FakeBroadcasts.Intent(false));
            FakeBroadcasts.Intent sticky = new FakeBroadcasts.Intent(true);
            send(FakeBroadcasts.REVERSE_ON, sticky);
            return got ? sticky : null;
        }

        @Override
        public void unregister(Receiver r) {
            if (r == live) live = null;
            fake.unregister();
        }

        private void send(String action, FakeBroadcasts.Intent m) {
            Receiver r = live;
            if (r != null && filter.contains(action)) r.onReceive(m);
        }
    }

    static ReverseSource reverseAdapter(Fakes.Reverse fake) {
        return new ReverseReceiver(new ReverseBroadcasts(fake));
    }

    /**
     * A fake file system standing in for java.io under the log folder, backed by the gate's log-folder double:
     * lengths, appends, the rename of receiver.log to receiver.log.1 and the delete of receiver.log.1 go
     * through the double (so its sizes, rotations, deletions and armed faults still apply). Every write,
     * rename and delete is recorded by path; one the double has no file for fails as a missing directory.
     */
    static final class LogFs implements LogDir {
        private static final String CUR = LOG_DIR + "receiver.log";
        private static final String OLD = LOG_DIR + Fakes.Logs.older("receiver.log");
        private final Fakes.Logs fake;

        LogFs(Fakes.Logs fake) {
            this.fake = fake;
        }

        @Override
        public long length(String path) throws IOException {
            adapterCalls++;
            if (path.equals(CUR) || path.equals(OLD)) return fake.size(path.substring(LOG_DIR.length()));
            return 0;
        }

        @Override
        public void append(String path, byte[] b) throws IOException {
            adapterCalls++;
            fsWrites.add("write " + path);
            if (!path.equals(CUR)) throw new IOException("no such directory for " + path);
            fake.append(path.substring(LOG_DIR.length()), b);
        }

        @Override
        public void rename(String from, String to) throws IOException {
            adapterCalls++;
            fsWrites.add("rename " + from + " " + to);
            if (!from.equals(CUR) || !to.equals(OLD)) throw new IOException("no such file " + from);
            fake.rotate();
        }

        @Override
        public void delete(String path) throws IOException {
            adapterCalls++;
            fsWrites.add("delete " + path);
            if (!path.equals(OLD)) throw new IOException("no such file " + path);
            fake.deleteOlder();
        }
    }

    static parkcast.gate.port.LogFiles logsAdapter(Fakes.Logs fake) {
        return new LogDirFiles(new LogFs(fake));
    }

    // ------------------------------------------------------------------ paths

    private static Path repoRoot() {
        Path d = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (d != null && !Files.isDirectory(d.resolve("specs").resolve("features"))) d = d.getParent();
        if (d == null) throw new IllegalStateException("no specs/features above " + System.getProperty("user.dir"));
        return d;
    }

    /** The "gate" lines of specs/java-features.txt: the S2 feature files. */
    private static List<String> gateFeatures(Path root) throws IOException {
        List<String> out = new ArrayList<>();
        for (String line : Files.readAllLines(root.resolve("specs").resolve("java-features.txt"), StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] p = t.split("\\s+");
            if (p.length == 2 && p[0].equals("gate")) out.add(p[1]);
        }
        if (out.isEmpty()) throw new IllegalStateException("specs/java-features.txt has no gate lines");
        return out;
    }
}
