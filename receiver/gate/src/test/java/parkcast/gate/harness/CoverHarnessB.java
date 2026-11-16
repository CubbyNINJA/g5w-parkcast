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
import parkcast.gate.Receiver;
import parkcast.gate.World;
import parkcast.gate.port.ReverseSource;

/**
 * Doubles for the AIR-041 cover scenarios that the shared World cannot give: a recorder around the
 * REVERSE_ON registration, ordered against the lifecycle callbacks, and a text scan of the receiver's
 * sources and source manifest. The recorder wraps {@link World#reverse}, so its counters stay valid.
 */
public final class CoverHarnessB {
    /** One recorded event: a lifecycle step or a call on the REVERSE_ON registration. */
    public static final class Event {
        public final int start;
        public final long t, seq;
        public final String what;

        Event(int start, long t, long seq, String what) {
            this.start = start;
            this.t = t;
            this.seq = seq;
            this.what = what;
        }

        @Override
        public String toString() {
            return what + "@" + t;
        }
    }

    private final World w;
    public final List<Event> events = new ArrayList<>();
    private long seq;
    /** Makes unregister() throw after it reaches the shared double. */
    public boolean unregisterThrows;

    public CoverHarnessB(World w) {
        this.w = w;
    }

    private void record(String what) {
        events.add(new Event(w.scheduler.startIndex, w.scheduler.now(), seq++, what));
    }

    /** The current start's events with that name. */
    public List<Event> current(String what) {
        List<Event> out = new ArrayList<>();
        for (Event e : events) if (e.start == w.scheduler.startIndex && e.what.equals(what)) out.add(e);
        return out;
    }

    /** Records each registration call, then passes it to {@link World#reverse}. */
    private final ReverseSource recording = new ReverseSource() {
        @Override
        public boolean register(ReverseListener l) {
            boolean got;
            try {
                got = w.reverse.register(l);
            } catch (RuntimeException e) {
                record("register threw");
                throw e;
            }
            record("register returned");
            return got;
        }

        @Override
        public void unregister() {
            w.reverse.unregister();
            record("unregister");
            if (unregisterThrows) throw new IllegalStateException("synthetic REVERSE_ON unregister failure");
        }
    };

    /**
     * Builds the receiver on the recording double and runs its lifecycle at 0 ms, as {@link World#start()}
     * does, recording when onResume returns and when the first focus gain is delivered. Later World steps
     * see it as started.
     */
    public void install() {
        if (w.started) throw new IllegalStateException("the receiver was already started");
        final Receiver r = new Receiver(w.scheduler, w.park, w.acc, recording, w.notices, w.logs, w.config, w.build,
                w.regulation, w.screen, w.session, w.vehicle, w.displayWidth);
        w.receiver = r;
        w.started = true;
        final long focus = w.focusAt;
        final boolean inLifecycle = focus == 0 && w.focusRank == VirtualScheduler.FIRST;
        w.scheduler.script(0, VirtualScheduler.FIRST, () -> {
            record("onCreate called");
            r.onCreate();
            r.onStart();
            r.onResume();
            record("onResume returned");
            if (inLifecycle) {
                record("focus gain delivered");
                r.onWindowFocusChanged(true);
            }
        });
        if (focus >= 0 && !inLifecycle) w.scheduler.script(focus, w.focusRank, () -> {
            record("focus gain delivered");
            r.onWindowFocusChanged(true);
        });
        w.frames.startFor(r);
        w.scheduler.runUntil(0);
        w.ranUntil = 0;
    }

    // ------------------------------------------------------------ static scan

    /** The receiver's main Java sources, comments removed, keyed by path. */
    public final List<Path> sourceFiles = new ArrayList<>();
    public final List<String> sourceCode = new ArrayList<>();
    /** The source AndroidManifest.xml with comments removed, or null if there is none. */
    public String manifest;
    public Path manifestPath;

    public void scan() {
        Path root = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("specs"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("no repository root above " + System.getProperty("user.dir"));
        Path receiver = root.resolve("receiver");
        try (Stream<Path> st = Files.walk(receiver)) {
            for (Path p : st.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                String rel = receiver.relativize(p).toString().replace('\\', '/');
                if (!rel.matches("[^/]+/src/main/java/.*")) continue;
                sourceFiles.add(p);
                sourceCode.add(stripJavaComments(read(p)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (sourceFiles.isEmpty()) throw new IllegalStateException("no main sources under " + receiver);
        manifestPath = receiver.resolve("app/src/main/AndroidManifest.xml");
        if (Files.isRegularFile(manifestPath)) manifest = read(manifestPath).replaceAll("(?s)<!--.*?-->", "");
    }

    private static String read(Path p) {
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Removes // and block comments, keeping string and char literals. */
    static String stripJavaComments(String s) {
        StringBuilder b = new StringBuilder(s.length());
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && s.charAt(j) != c) j += s.charAt(j) == '\\' ? 2 : 1;
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

    /** Paths of the sources whose code (comments removed) contains {@code text}. */
    public List<String> sourcesContaining(String text) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < sourceFiles.size(); i++)
            if (sourceCode.get(i).contains(text)) out.add(sourceFiles.get(i).getFileName().toString());
        return out;
    }
}
