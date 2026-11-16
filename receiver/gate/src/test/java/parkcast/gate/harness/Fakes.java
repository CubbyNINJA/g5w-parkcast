package parkcast.gate.harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import parkcast.gate.port.AccSource;
import parkcast.gate.port.AccStatus;
import parkcast.gate.port.BuildInfo;
import parkcast.gate.port.ConfigFile;
import parkcast.gate.port.Display;
import parkcast.gate.port.Frame;
import parkcast.gate.port.LogFiles;
import parkcast.gate.port.MirrorSession;
import parkcast.gate.port.NoticeView;
import parkcast.gate.port.NoticeWindows;
import parkcast.gate.port.ParkGetter;
import parkcast.gate.port.Placement;
import parkcast.gate.port.RegulationSource;
import parkcast.gate.port.ReverseSource;
import parkcast.gate.port.VehicleBus;
import parkcast.gate.port.Win;
import parkcast.gate.port.WindowSpec;

/** Recording doubles for every port the receiver is built with. */
public final class Fakes {
    private Fakes() {
    }

    public static final String REG = "REG", LOG = "LOG";

    // ---------------------------------------------------------------- Park getter

    /** A scripted outcome for one Park getter call: a value or a failure, at a fixed time or after a delay, or never. */
    public static final class Reply {
        public final long at;
        public final boolean absolute, never;
        public final Integer value;
        public final String fail;

        private Reply(long at, boolean absolute, boolean never, Integer value, String fail) {
            this.at = at;
            this.absolute = absolute;
            this.never = never;
            this.value = value;
            this.fail = fail;
        }

        public static Reply never() {
            return new Reply(0, false, true, null, null);
        }

        public static Reply valueAt(int v, long t) {
            return new Reply(t, true, false, v, null);
        }

        public static Reply valueAfter(int v, long d) {
            return new Reply(d, false, false, v, null);
        }

        public static Reply failAt(String why, long t) {
            return new Reply(t, true, false, null, why);
        }

        public static Reply failAfter(String why, long d) {
            return new Reply(d, false, false, null, why);
        }
    }

    public static final class Call {
        public int start, index;
        public long begin, end = -1;
        public Integer value;
        public String fail;
        public boolean done, reread;
        ParkGetter.ParkReply cb;

        @Override
        public String toString() {
            return "call#" + index + "@" + begin + "->" + (done ? end + "=" + (fail != null ? fail : value) : "pending");
        }
    }

    public static final class Park implements ParkGetter {
        private final VirtualScheduler s;
        private final Acc acc;
        /** 1 while in Park, 0 once out of it. */
        public int gear = 1;
        public boolean immediate, missing;
        public long lostAt = -1;
        public final Map<Long, Reply> byStart = new HashMap<>();
        public final Map<Integer, Reply> byIndex = new HashMap<>();
        public long chainAnchor = -1;
        public final List<Reply> chain = new ArrayList<>();
        public Reply reread, constant;
        public final List<Reply> rereadNext = new ArrayList<>();
        /** Rank of a reply that lands on this millisecond, when a step fixed the order. */
        public final Map<Long, Integer> rankAt = new HashMap<>();
        public final List<Call> calls = new ArrayList<>();
        private int index;
        private long rereadFor = Long.MIN_VALUE;
        private int nextIdx = -1;

        public Park(VirtualScheduler s, Acc acc) {
            this.s = s;
            this.acc = acc;
        }

        public void newStart() {
            index = 0;
            rereadFor = Long.MIN_VALUE;
            nextIdx = -1;
        }

        @Override
        public void call(ParkReply cb) {
            Call c = new Call();
            c.start = s.startIndex;
            c.index = index++;
            c.begin = s.now();
            c.cb = cb;
            calls.add(c);
            long r = acc.relevantResume();
            if (r >= 0 && c.begin >= r && rereadFor != r) {
                c.reread = true;
                rereadFor = r;
                nextIdx = 0;
            }
            Reply rep = resolve(c);
            if (rep == null) {
                int v = gear;
                if (immediate) {
                    finish(c, v, null);
                    return;
                }
                rep = Reply.valueAfter(v, 10);
            }
            plan(c, rep);
        }

        private Reply resolve(Call c) {
            if (missing) return Reply.failAfter("service missing", 10);
            if (lostAt >= 0 && c.begin >= lostAt) return Reply.failAfter("service lost", 10);
            if (chainAnchor >= 0) {
                Call a = find(c.start, chainAnchor);
                if (a != null) {
                    int k = c.index - a.index;
                    if (k >= 1 && k <= chain.size()) return chain.get(k - 1);
                }
            }
            Reply r = byStart.get(c.begin);
            if (r != null) return r;
            r = byIndex.get(c.index);
            if (r != null) return r;
            if (c.reread && reread != null) return reread;
            if (!c.reread && reread != null && nextIdx >= 0 && nextIdx < rereadNext.size()) return rereadNext.get(nextIdx++);
            return constant;
        }

        private Call find(int start, long begin) {
            for (Call c : calls) if (c.start == start && c.begin == begin) return c;
            return null;
        }

        private void plan(final Call c, final Reply r) {
            if (r.never) return;
            long t = r.absolute ? r.at : c.begin + r.at;
            Integer rk = rankAt.get(t);
            s.script(t, rk == null ? VirtualScheduler.AFTER : rk, () -> finish(c, r.value, r.fail));
        }

        void finish(Call c, Integer v, String fail) {
            if (c.done) return;
            c.done = true;
            c.end = s.now();
            c.value = v;
            c.fail = fail;
            if (fail != null) c.cb.failed(fail);
            else c.cb.value(v);
        }

        /** The service goes away at {@code t}: pending calls fail then, later calls fail too. */
        public void lose(long t) {
            lostAt = t;
            s.script(t, VirtualScheduler.BEFORE, () -> {
                for (Call c : new ArrayList<>(calls)) if (!c.done && c.start == s.startIndex) finish(c, null, "service lost");
            });
        }

        public List<Call> current() {
            List<Call> out = new ArrayList<>();
            for (Call c : calls) if (c.start == s.startIndex) out.add(c);
            return out;
        }
    }

    // ---------------------------------------------------------------- ACC

    public static final class Delivery {
        public final long t;
        public final int before, after;
        public final AccStatus status;
        public final boolean delivered;

        Delivery(long t, int before, int after, AccStatus status, boolean delivered) {
            this.t = t;
            this.before = before;
            this.after = after;
            this.status = status;
            this.delivered = delivered;
        }
    }

    public static final class Acc implements AccSource {
        private final VirtualScheduler s;
        private final Logs logs;
        public long planAt;
        public AccStatus planSticky = AccStatus.of(0);
        public boolean planNoSticky, planDefault = true;
        public String planThrow;
        public AccListener listener;
        public boolean regOk, regThrew, awake, changeToZeroScripted;
        public long resumeAt = -1, changeResumeAt = -1;
        public final List<Long> resumes = new ArrayList<>();
        public final List<long[]> registrations = new ArrayList<>();
        public final List<long[]> unregisters = new ArrayList<>();
        public final List<Delivery> deliveries = new ArrayList<>();

        public Acc(VirtualScheduler s, Logs logs) {
            this.s = s;
            this.logs = logs;
        }

        public void planSticky(AccStatus st, long at) {
            planDefault = false;
            planSticky = st;
            planNoSticky = false;
            planThrow = null;
            planAt = at;
        }

        public void planNoSticky(long at) {
            planDefault = false;
            planNoSticky = true;
            planThrow = null;
            planAt = at;
        }

        public void planThrow(String cause, long at) {
            planDefault = false;
            planThrow = cause;
            planNoSticky = false;
            planAt = at;
        }

        public void newStart() {
            listener = null;
            regOk = false;
            regThrew = false;
            awake = false;
            resumeAt = -1;
            changeResumeAt = -1;
            changeToZeroScripted = false;
            planSticky = AccStatus.of(0);
            planNoSticky = false;
            planThrow = null;
            planAt = 0;
            planDefault = true;
        }

        @Override
        public void register(final AccListener l) {
            registrations.add(new long[] {s.startIndex, s.now()});
            listener = l;
            final AccStatus st = planSticky;
            final boolean ns = planNoSticky;
            final String th = planThrow;
            s.script(planAt, VirtualScheduler.BEFORE, () -> {
                if (th != null) {
                    regThrew = true;
                    l.registrationFailed(th);
                    return;
                }
                regOk = true;
                AccStatus sticky = ns ? null : st;
                if (sticky != null) note(sticky, false);
                l.registered(sticky);
            });
        }

        @Override
        public void unregister() {
            unregisters.add(new long[] {s.startIndex, s.now()});
            regOk = false;
        }

        /** A broadcast arrives now; it reaches the receiver only while its registration stands. */
        public void deliver(AccStatus st) {
            int before = logs.lines.size();
            boolean d = regOk && listener != null;
            if (d) {
                note(st, true);
                listener.changed(st);
            }
            deliveries.add(new Delivery(s.now(), before, logs.lines.size(), st, d));
        }

        private void note(AccStatus st, boolean change) {
            boolean a = st.isInteger() && st.value() == 0;
            if (a && !awake) {
                resumeAt = s.now();
                if (change) changeResumeAt = s.now();
                resumes.add(s.now());
            }
            awake = a;
        }

        public long relevantResume() {
            return changeToZeroScripted ? changeResumeAt : resumeAt;
        }

        public List<long[]> currentUnregisters() {
            List<long[]> out = new ArrayList<>();
            for (long[] u : unregisters) if (u[0] == s.startIndex) out.add(u);
            return out;
        }

        public List<long[]> currentRegistrations() {
            List<long[]> out = new ArrayList<>();
            for (long[] u : registrations) if (u[0] == s.startIndex) out.add(u);
            return out;
        }
    }

    // ---------------------------------------------------------------- Notices

    public static final class View implements NoticeView {
        public final String text, kind;
        public final int start;
        public final long at;

        View(String text, String kind, int start, long at) {
            this.text = text;
            this.kind = kind;
            this.start = start;
            this.at = at;
        }
    }

    public static final class Window implements Win {
        public final View view;
        public final WindowSpec spec;
        public final long addedAt;
        public final int start, attempt;
        public long removedAt = -1;
        public final List<Long> removeCalls = new ArrayList<>();
        int throwsLeft;

        Window(View view, WindowSpec spec, long addedAt, int start, int attempt) {
            this.view = view;
            this.spec = spec;
            this.addedAt = addedAt;
            this.start = start;
            this.attempt = attempt;
        }

        public String kind() {
            return view == null ? REG : view.kind;
        }

        /** On screen at {@code t}: added at or before it and not yet removed. */
        public boolean onScreenAt(long t) {
            return addedAt <= t && (removedAt < 0 || removedAt > t);
        }
    }

    public static final class Op {
        public final int start, attempt;
        public final long t;
        public final String op, kind;
        public final View view;
        public final WindowSpec spec;
        public final String text;

        Op(int start, long t, String op, String kind, int attempt, View view, WindowSpec spec, String text) {
            this.start = start;
            this.t = t;
            this.op = op;
            this.kind = kind;
            this.attempt = attempt;
            this.view = view;
            this.spec = spec;
            this.text = text;
        }
    }

    public static final class Notices implements NoticeWindows {
        private final VirtualScheduler s;
        /** "start|kind|attempt|op" keys whose call throws. */
        public final Set<String> throwKeys = new HashSet<>();
        /** Per start: how many removal calls of its first registration-failure window throw. */
        public final Map<Integer, Integer> regRemoveThrows = new HashMap<>();
        public final List<Op> ops = new ArrayList<>();
        public final List<View> views = new ArrayList<>();
        public final List<Window> windows = new ArrayList<>();
        private final Map<String, Integer> attempts = new HashMap<>();

        public Notices(VirtualScheduler s) {
            this.s = s;
        }

        private static String kindOf(String text) {
            return text != null && text.toLowerCase().contains("logging") ? LOG : REG;
        }

        private int attempt(String kind) {
            Integer a = attempts.get(s.startIndex + "|" + kind);
            return a == null ? 0 : a;
        }

        private void check(String kind, int attempt, String op) {
            if (throwKeys.contains(s.startIndex + "|" + kind + "|" + attempt + "|" + op))
                throw new RuntimeException("synthetic UI failure on " + op);
        }

        @Override
        public NoticeView create(String text) {
            String kind = kindOf(text);
            int a = attempt(kind) + 1;
            attempts.put(s.startIndex + "|" + kind, a);
            ops.add(new Op(s.startIndex, s.now(), "create", kind, a, null, null, text));
            check(kind, a, "create");
            View v = new View(text, kind, s.startIndex, s.now());
            views.add(v);
            return v;
        }

        @Override
        public int measure(NoticeView v, int displayWidth) {
            String kind = v instanceof View ? ((View) v).kind : REG;
            int a = attempt(kind);
            ops.add(new Op(s.startIndex, s.now(), "measure", kind, a, v instanceof View ? (View) v : null, null, null));
            check(kind, a, "measure");
            return 64;
        }

        @Override
        public Placement placementFrom(NoticeView regFailToast) {
            ops.add(new Op(s.startIndex, s.now(), "placement", REG, attempt(REG), null, null, null));
            return new Placement(0x51, 0, 96);
        }

        @Override
        public Win add(NoticeView v, WindowSpec spec) {
            View view = v instanceof View ? (View) v : null;
            String kind = view != null ? view.kind : REG;
            int a = attempt(kind);
            ops.add(new Op(s.startIndex, s.now(), "add", kind, a, view, spec, view != null ? view.text : null));
            check(kind, a, "add");
            Window w = new Window(view, spec, s.now(), s.startIndex, a);
            if (REG.equals(kind)) {
                Integer n = regRemoveThrows.remove(s.startIndex);
                if (n != null) w.throwsLeft = n;
            }
            windows.add(w);
            return w;
        }

        @Override
        public void remove(Win w) {
            ops.add(new Op(s.startIndex, s.now(), "remove", w instanceof Window ? ((Window) w).kind() : REG, 0, null, null, null));
            if (!(w instanceof Window)) throw new IllegalArgumentException("not a window this double added: " + w);
            Window x = (Window) w;
            x.removeCalls.add(s.now());
            if (x.throwsLeft > 0) {
                x.throwsLeft--;
                throw new RuntimeException("synthetic UI failure on remove");
            }
            if (x.removedAt < 0) x.removedAt = s.now();
        }

        public List<Window> windows(String kind, Integer start) {
            List<Window> out = new ArrayList<>();
            for (Window w : windows) if (w.kind().equals(kind) && (start == null || w.start == start)) out.add(w);
            return out;
        }

        public List<Op> ops(String op, String kind, Integer start) {
            List<Op> out = new ArrayList<>();
            for (Op o : ops)
                if ((op == null || o.op.equals(op)) && (kind == null || o.kind.equals(kind)) && (start == null || o.start == start))
                    out.add(o);
            return out;
        }
    }

    // ---------------------------------------------------------------- Logs, config, build

    public static final class Line {
        public final int start;
        public final long t;
        public final String file, text;

        Line(int start, long t, String file, String text) {
            this.start = start;
            this.t = t;
            this.file = file;
            this.text = text;
        }

        @Override
        public String toString() {
            return t + " " + file + ": " + text;
        }
    }

    public static final class Logs implements LogFiles {
        private final VirtualScheduler s;
        public final List<Line> lines = new ArrayList<>();
        public final Map<String, Long> sizes = new LinkedHashMap<>();
        /** An append whose text contains this fails. */
        public String failOn;
        public int rotations, deletions, failures;

        public Logs(VirtualScheduler s) {
            this.s = s;
        }

        @Override
        public long size(String name) {
            Long n = sizes.get(name);
            return n == null ? 0 : n;
        }

        @Override
        public void append(String name, byte[] b) throws IOException {
            String text = new String(b, StandardCharsets.UTF_8);
            if (failOn != null && text.contains(failOn)) {
                failures++;
                throw new IOException("synthetic write failure");
            }
            sizes.put(name, size(name) + b.length);
            for (String l : text.split("\r?\n")) if (!l.isEmpty()) lines.add(new Line(s.startIndex, s.now(), name, l));
        }

        @Override
        public void rotate() {
            rotations++;
        }

        @Override
        public void deleteOlder() {
            deletions++;
        }

        public List<Line> containing(String x) {
            List<Line> out = new ArrayList<>();
            for (Line l : lines) if (l.text.contains(x)) out.add(l);
            return out;
        }
    }

    public static final class Config implements ConfigFile {
        public String text = "video.require_park=true";
        public boolean missing, unreadable;

        public void holds(String t) {
            text = t.replace(" ; ", "\n");
            missing = false;
            unreadable = false;
        }

        @Override
        public byte[] read() throws Missing, Unreadable {
            if (missing) throw new Missing("receiver.conf is missing");
            if (unreadable) throw new Unreadable("receiver.conf is unreadable");
            return text.getBytes(StandardCharsets.UTF_8);
        }
    }

    public static final class Build implements BuildInfo {
        public String product = "CVEV.CAN.0000.V015.011.260206";
        /** Recorded only; the port has no second property. */
        public String product2;
        public boolean missing, unreadable;

        @Override
        public String product() throws Unreadable {
            if (unreadable) throw new Unreadable("ro.build.product is unreadable");
            return missing ? null : product;
        }
    }

    // ---------------------------------------------------------------- Display, session, car

    public static final class Shown {
        public final long t, id;
        public final int start;

        Shown(long t, long id, int start) {
            this.t = t;
            this.id = id;
            this.start = start;
        }
    }

    public static final class Screen implements Display {
        private final VirtualScheduler s;
        public final List<Shown> shows = new ArrayList<>();
        public final List<Long> clears = new ArrayList<>();
        public final Set<Long> shownIds = new HashSet<>();

        public Screen(VirtualScheduler s) {
            this.s = s;
        }

        @Override
        public void show(Frame f) {
            shows.add(new Shown(s.now(), f.id, s.startIndex));
            shownIds.add(f.id);
        }

        @Override
        public void clear() {
            clears.add(s.now());
        }
    }

    /** Counts every call by method name. */
    public static class Counter {
        public final Map<String, Integer> counts = new LinkedHashMap<>();

        void hit(String m) {
            Integer n = counts.get(m);
            counts.put(m, n == null ? 1 : n + 1);
        }

        public int count(String m) {
            Integer n = counts.get(m);
            return n == null ? 0 : n;
        }

        public int total() {
            int t = 0;
            for (int n : counts.values()) t += n;
            return t;
        }
    }

    public static final class Session extends Counter implements MirrorSession {
        public boolean active, audioPlaying;
        public int reconnects;

        @Override
        public void muteAudio() {
            hit("muteAudio");
        }

        @Override
        public void stopAudio() {
            hit("stopAudio");
        }

        @Override
        public void disconnectAudio() {
            hit("disconnectAudio");
        }

        @Override
        public void stopConnection() {
            hit("stopConnection");
        }

        @Override
        public void disconnect() {
            hit("disconnect");
        }

        @Override
        public void reconnect() {
            hit("reconnect");
            reconnects++;
        }
    }

    public static final class Vehicle extends Counter implements VehicleBus {
        @Override
        public void sendCommand(String c) {
            hit("sendCommand");
        }

        @Override
        public void writeCan(int id, byte[] data) {
            hit("writeCan");
        }

        @Override
        public void changeSetting(String k, String v) {
            hit("changeSetting");
        }

        @Override
        public void changeRegulation(String r) {
            hit("changeRegulation");
        }

        @Override
        public void sendRegulationEvent(String e) {
            hit("sendRegulationEvent");
        }

        @Override
        public void routeFrameToHud(Frame f) {
            hit("routeFrameToHud");
        }

        @Override
        public void changeDriverAssistanceDisplay(String s) {
            hit("changeDriverAssistanceDisplay");
        }

        @Override
        public void registerParkListener(Object l) {
            hit("registerParkListener");
        }

        @Override
        public void requestBcmStatus() {
            hit("requestBcmStatus");
        }
    }

    public static final class Regulation implements RegulationSource {
        public RegulationListener listener;
        public int registers, unregisters;

        @Override
        public void register(RegulationListener l) {
            listener = l;
            registers++;
        }

        @Override
        public void unregister() {
            unregisters++;
        }
    }

    public static final class Reverse implements ReverseSource {
        public ReverseListener listener;
        public int registers, unregisters;

        @Override
        public boolean register(ReverseListener l) {
            listener = l;
            registers++;
            return true;
        }

        @Override
        public void unregister() {
            unregisters++;
        }
    }
}
