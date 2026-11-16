package parkcast.gate.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import parkcast.gate.World;
import parkcast.gate.harness.Fakes;
import parkcast.gate.port.WindowSpec;

/**
 * Steps for the registration-failure and logging-failure notices (09): how many were shown, when, with
 * which text and window form, their removal, the one retry and what exit does to them. A notice "shown"
 * is a window the UI double added; an "attempt" is a create call of that notice's kind. Every Then step
 * looks only at what the notice, log and session doubles recorded.
 */
public class NoticeSteps {
    /** The y the notice double's placementFrom hands out, and the height its measure returns. */
    private static final int PLACEMENT_Y = 96, MEASURED_H = 64;
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");

    private final World w;
    /** The records found by "exactly N records contain", for "in order they contain". */
    private List<Fakes.Line> theyRecords;

    public NoticeSteps(World w) {
        this.w = w;
    }

    // ------------------------------------------------------------ setup

    /** Nothing more to set up: the scenario's other events are already given. */
    @Given("^nothing else (?:happens|changes)$")
    public void nothingElse() {
    }

    // ------------------------------------------------------------ counts of shown notices

    @Then("exactly one registration-failure notice was shown")
    public void oneRegNotice() {
        oneRegNoticeIn(w.scheduler.startIndex, "this start");
    }

    @Then("exactly one registration-failure notice was shown in the whole run")
    public void oneRegNoticeWholeRun() {
        oneRegNoticeIn(null, "the whole run");
    }

    private void oneRegNoticeIn(Integer start, String where) {
        w.settle();
        List<Fakes.Window> ws = w.notices.windows(Fakes.REG, start);
        if (ws.size() != 1)
            throw new AssertionError(ws.size() + " registration-failure notices shown in " + where + ", added at "
                    + addedTimes(ws));
    }

    @Then("^exactly (one|two|three|four|five|six|\\d+) records contain \"([^\"]*)\"$")
    public void exactlyNRecords(String n, String x) {
        w.settle();
        int want = number(n);
        List<Fakes.Line> ls = new ArrayList<>();
        for (Fakes.Line l : w.lines()) if (l.text.contains(x)) ls.add(l);
        if (ls.size() != want)
            throw new AssertionError(ls.size() + " records contain \"" + x + "\", expected " + want + ": " + ls);
        theyRecords = ls;
    }

    @Then("^in order they contain ((?:\"[^\"]*\"(?:, | and )?)+)$")
    public void inOrderTheyContain(String list) {
        if (theyRecords == null) throw new IllegalStateException("\"exactly N records contain\" must come first");
        List<String> want = new ArrayList<>();
        Matcher m = QUOTED.matcher(list);
        while (m.find()) want.add(m.group(1));
        if (want.size() != theyRecords.size())
            throw new AssertionError(want.size() + " texts named for " + theyRecords.size() + " records: " + theyRecords);
        for (int i = 0; i < want.size(); i++)
            if (!theyRecords.get(i).text.contains(want.get(i)))
                throw new AssertionError("record " + (i + 1) + " does not contain \"" + want.get(i) + "\": " + theyRecords);
    }

    // ------------------------------------------------------------ text and placement of both notices

    @Then("^exactly one (registration|logging)-failure notice was shown, at (\\d+) ms, with text exactly \"(.*)\"$")
    public void oneNoticeWithText(String which, long t, String text) {
        w.settle();
        String kind = kindOf(which);
        List<Fakes.Window> ws = w.notices.windows(kind, w.scheduler.startIndex);
        if (ws.size() != 1)
            throw new AssertionError(ws.size() + " " + which + "-failure notices shown, added at " + addedTimes(ws));
        Fakes.Window win = ws.get(0);
        if (win.addedAt != t) throw new AssertionError("the " + which + "-failure notice was shown at " + win.addedAt + " ms");
        String got = win.view == null ? null : win.view.text;
        if (!text.equals(got)) throw new AssertionError("the " + which + "-failure notice text is \"" + got + "\"");
    }

    @Then("each notice was added as its own window")
    public void eachOwnWindow() {
        w.settle();
        int start = w.scheduler.startIndex;
        List<Fakes.Window> reg = w.notices.windows(Fakes.REG, start), log = w.notices.windows(Fakes.LOG, start);
        if (reg.size() != 1 || log.size() != 1)
            throw new AssertionError(reg.size() + " registration-failure and " + log.size() + " logging-failure windows");
        if (reg.get(0) == log.get(0) || reg.get(0).view == log.get(0).view)
            throw new AssertionError("both notices share one window or view");
        int adds = w.notices.ops("add", null, start).size();
        if (adds != 2) throw new AssertionError(adds + " add calls were made for the two notices");
    }

    @Then("the registration-failure window has y=Y and the logging-failure window has y=Y+H")
    public void windowsStacked() {
        w.settle();
        Fakes.Window reg = only(Fakes.REG), log = only(Fakes.LOG);
        if (reg.spec.y != PLACEMENT_Y)
            throw new AssertionError("registration-failure window y=" + reg.spec.y + ", expected Y=" + PLACEMENT_Y);
        if (log.spec.y != PLACEMENT_Y + MEASURED_H)
            throw new AssertionError("logging-failure window y=" + log.spec.y + ", expected Y+H=" + (PLACEMENT_Y + MEASURED_H));
    }

    @Then("each window was removed by its own removal call at exactly {int} ms")
    public void eachRemovedAt(int t) {
        w.settle();
        List<Fakes.Window> ws = currentWindows();
        if (ws.isEmpty()) throw new AssertionError("no notice window was added");
        for (Fakes.Window win : ws)
            if (win.removeCalls.size() != 1 || win.removeCalls.get(0) != t || win.removedAt != t)
                throw new AssertionError("the " + win.kind() + " window added at " + win.addedAt + " ms had removal calls at "
                        + win.removeCalls + " ms");
    }

    // ------------------------------------------------------------ notice failures change nothing else

    @Given("mirroring is active with audio playing")
    public void mirroringWithAudio() {
        w.session.active = true;
        w.session.audioPlaying = true;
    }

    @Given("^the registration-failure notice (is shown|fails to show|fails to show and its retry is shown|fails to show and its retry fails to show|is shown and its removal call throws|is shown and both its removal calls throw)$")
    public void regNoticeOutcome(String outcome) {
        int start = w.scheduler.startIndex;
        if (outcome.startsWith("fails to show")) throwOn(start, 1, "add");
        if (outcome.endsWith("its retry fails to show")) throwOn(start, 2, "add");
        if (outcome.endsWith("its removal call throws")) w.notices.regRemoveThrows.put(start, 1);
        if (outcome.endsWith("both its removal calls throw")) w.notices.regRemoveThrows.put(start, 2);
    }

    @Then("audio was never muted, stopped or disconnected")
    public void audioUntouched() {
        w.settle();
        assertAudioUntouched(w);
    }

    @Then("the iPhone connection was never stopped or disconnected")
    public void connectionUntouched() {
        w.settle();
        assertConnectionUntouched(w);
    }

    static void assertAudioUntouched(World w) {
        int m = w.session.count("muteAudio"), s = w.session.count("stopAudio"), d = w.session.count("disconnectAudio");
        if (m + s + d != 0)
            throw new AssertionError("audio muted " + m + ", stopped " + s + ", disconnected " + d + " times");
    }

    static void assertConnectionUntouched(World w) {
        int s = w.session.count("stopConnection"), d = w.session.count("disconnect");
        if (s + d != 0) throw new AssertionError("iPhone connection stopped " + s + ", disconnected " + d + " times");
    }

    // ------------------------------------------------------------ one notice per app start

    @Given("one registration-failure notice was shown during the previous app start")
    public void oneNoticePreviousStart() {
        w.acc.planThrow("exception", 0);
        w.runFor(2000);
    }

    @Then("exactly one registration-failure notice was shown since the restart")
    public void oneRegNoticeSinceRestart() {
        oneRegNoticeIn(w.scheduler.startIndex, "the start since the restart");
    }

    // ------------------------------------------------------------ the notice's window

    /** The registration-failure add call and window a step named, for "that add call", "its", "that window". */
    private Fakes.Op thatAdd;
    private Fakes.Window thatWindow;

    @Then("^exactly one registration-failure notice add call was made, at (?:exactly )?(\\d+) ms$")
    public void oneRegAddAt(long t) {
        w.settle();
        List<Fakes.Op> adds = w.notices.ops("add", Fakes.REG, w.scheduler.startIndex);
        if (adds.size() != 1) throw new AssertionError(adds.size() + " registration-failure add calls, at " + times(adds));
        if (adds.get(0).t != t) throw new AssertionError("the registration-failure add call was made at " + adds.get(0).t + " ms");
        thatAdd = adds.get(0);
        thatWindow = windowAddedAt(t);
    }

    @Then("that add call has window type TYPE_TOAST and a view from the toast-view factory")
    public void thatAddIsToast() {
        Fakes.Op a = requireAdd();
        if (a.spec == null || a.spec.type != WindowSpec.TYPE_TOAST)
            throw new AssertionError("add call window type is " + (a.spec == null ? "missing" : a.spec.type));
        if (a.view == null || !w.notices.views.contains(a.view))
            throw new AssertionError("the added view was not made by the toast-view factory");
    }

    @Then("its flags include FLAG_NOT_FOCUSABLE and FLAG_NOT_TOUCHABLE and not FLAG_KEEP_SCREEN_ON")
    public void thatAddFlags() {
        int f = requireAdd().spec.flags;
        if ((f & WindowSpec.FLAG_NOT_FOCUSABLE) == 0 || (f & WindowSpec.FLAG_NOT_TOUCHABLE) == 0
                || (f & WindowSpec.FLAG_KEEP_SCREEN_ON) != 0)
            throw new AssertionError("window flags are 0x" + Integer.toHexString(f));
    }

    @Then("^its view's text is exactly \"(.*)\"$")
    public void thatViewText(String text) {
        Fakes.Op a = requireAdd();
        String got = a.view == null ? null : a.view.text;
        if (!text.equals(got)) throw new AssertionError("the view's text is \"" + got + "\"");
    }

    /** The UI double offers no Toast.show(), dialog, notification or activity: every add must be a toast window. */
    @Then("^no Toast\\.show\\(\\) call, other window type, dialog, notification or activity was recorded$")
    public void onlyToastWindows() {
        w.settle();
        for (Fakes.Op o : w.notices.ops("add", null, null))
            if (o.spec == null || o.spec.type != WindowSpec.TYPE_TOAST)
                throw new AssertionError("a window of type " + (o.spec == null ? "missing" : o.spec.type) + " was added at "
                        + o.t + " ms");
    }

    @Then("the registration-failure notice was shown at {int} ms")
    public void regNoticeShownAt(int t) {
        w.settle();
        thatWindow = windowAddedAt(t);
        if (thatWindow == null)
            throw new AssertionError("no registration-failure notice was shown at " + t + " ms; added at "
                    + addedTimes(w.notices.windows(Fakes.REG, w.scheduler.startIndex)));
    }

    @Then("its window is on screen at {int} ms")
    public void onScreenAt(int t) {
        Fakes.Window win = requireWindow();
        if (!win.onScreenAt(t))
            throw new AssertionError("the window added at " + win.addedAt + " ms is not on screen at " + t + " ms (removed at "
                    + win.removedAt + " ms)");
    }

    @Then("^exactly one removal call for (?:it|the registration-failure window) was made, at (?:exactly )?(\\d+) ms$")
    public void oneRemovalAt(long t) {
        w.settle();
        Fakes.Window win = thatWindow != null ? thatWindow : only(Fakes.REG);
        if (win.removeCalls.size() != 1 || win.removeCalls.get(0) != t)
            throw new AssertionError("the registration-failure window added at " + win.addedAt + " ms had removal calls at "
                    + win.removeCalls + " ms");
    }

    @Then("no other registration-failure notice add call was made")
    public void noOtherRegAdd() {
        w.settle();
        List<Fakes.Op> adds = w.notices.ops("add", Fakes.REG, w.scheduler.startIndex);
        if (adds.size() != 1) throw new AssertionError(adds.size() + " registration-failure add calls, at " + times(adds));
    }

    // ------------------------------------------------------------ focus

    @Given("the receiver's window first gains focus at {int} ms")
    public void focusAt(int t) {
        w.focusAt = t;
    }

    @Then("no view was created, measured or added before {int} ms")
    public void noViewBefore(int t) {
        w.settle();
        for (Fakes.Op o : w.notices.ops(null, null, w.scheduler.startIndex))
            if (o.t < t && ("create".equals(o.op) || "measure".equals(o.op) || "add".equals(o.op)))
                throw new AssertionError("a view was " + o.op + (o.op.endsWith("e") ? "d" : "ed") + " at " + o.t + " ms");
    }

    // ------------------------------------------------------------ attempts and the one retry

    @Given("^the UI double throws on (create|add) for (the first registration-failure notice attempt|both registration-failure notice attempts)$")
    public void uiThrows(String op, String which) {
        int start = w.scheduler.startIndex;
        throwOn(start, 1, op);
        if (which.startsWith("both")) throwOn(start, 2, op);
    }

    @Then("^the first registration-failure notice attempt(?: since the restart)? was made at (\\d+) ms$")
    public void firstAttemptAt(long t) {
        List<Fakes.Op> cs = attempts();
        if (cs.isEmpty()) throw new AssertionError("no registration-failure notice attempt was made");
        if (cs.get(0).t != t) throw new AssertionError("the first registration-failure notice attempt was made at " + cs.get(0).t + " ms");
    }

    @Then("no registration-failure notice window exists from {int} ms until {int} ms")
    public void noWindowBetween(int a, int b) {
        w.settle();
        for (Fakes.Window win : w.notices.windows(Fakes.REG, w.scheduler.startIndex))
            if (win.addedAt < b && (win.removedAt < 0 || win.removedAt > a))
                throw new AssertionError("a registration-failure window was on screen from " + win.addedAt + " ms (removed at "
                        + win.removedAt + " ms)");
    }

    @Then("no registration-failure notice attempt was made from {int} ms to {int} ms")
    public void noAttemptBetween(int a, int b) {
        for (Fakes.Op o : attempts())
            if (o.t >= a && o.t <= b) throw new AssertionError("a registration-failure notice attempt was made at " + o.t + " ms");
    }

    @Then("exactly one more attempt, with the same text and form, was made at exactly {int} ms")
    public void oneMoreAttempt(int t) {
        List<Fakes.Op> cs = attempts();
        if (cs.size() != 2) throw new AssertionError(cs.size() + " registration-failure notice attempts, at " + times(cs));
        Fakes.Op first = cs.get(0), retry = cs.get(1);
        if (retry.t != t) throw new AssertionError("the second attempt was made at " + retry.t + " ms");
        if (first.text == null || !first.text.equals(retry.text))
            throw new AssertionError("attempt texts differ: \"" + first.text + "\" then \"" + retry.text + "\"");
        Fakes.Op firstAdd = addOf(1), retryAdd = addOf(2);
        if (retryAdd == null || retryAdd.spec == null) throw new AssertionError("the retry added no window");
        WindowSpec r = retryAdd.spec;
        if (firstAdd != null && firstAdd.spec != null) {
            WindowSpec f = firstAdd.spec;
            if (f.type != r.type || f.flags != r.flags || f.gravity != r.gravity || f.x != r.x || f.y != r.y)
                throw new AssertionError("the retry's window form differs from the first attempt's");
        } else if (r.type != WindowSpec.TYPE_TOAST) {
            throw new AssertionError("the retry's window type is " + r.type);
        }
        thatWindow = null;
        for (Fakes.Window win : w.notices.windows(Fakes.REG, w.scheduler.startIndex)) if (win.attempt == 2) thatWindow = win;
        if (thatWindow == null) throw new AssertionError("the retry's window was not added");
    }

    @Then("that window was removed by one removal call at exactly {int} ms")
    public void thatWindowRemovedAt(int t) {
        w.settle();
        Fakes.Window win = thatWindow != null ? thatWindow : only(Fakes.REG);
        if (win.removeCalls.size() != 1 || win.removeCalls.get(0) != t)
            throw new AssertionError("the registration-failure window added at " + win.addedAt + " ms had removal calls at "
                    + win.removeCalls + " ms");
    }

    @Then("no further registration-failure notice attempt was made")
    public void noFurtherAttempt() {
        List<Fakes.Op> cs = attempts();
        if (cs.size() != 2) throw new AssertionError(cs.size() + " registration-failure notice attempts, at " + times(cs));
    }

    @Then("registration-failure notice attempts were made only at {int} ms and {int} ms")
    public void attemptsOnlyAt(int a, int b) {
        List<Long> got = times(attempts());
        if (got.size() != 2 || got.get(0) != a || got.get(1) != b)
            throw new AssertionError("registration-failure notice attempts were made at " + got + " ms");
    }

    @Then("no registration-failure notice window exists at any time")
    public void noRegWindow() {
        w.settle();
        List<Fakes.Window> ws = w.notices.windows(Fakes.REG, w.scheduler.startIndex);
        if (!ws.isEmpty()) throw new AssertionError("registration-failure windows added at " + addedTimes(ws));
    }

    @Then("no registration-failure notice attempt was made after {int} ms")
    public void noAttemptAfter(int t) {
        for (Fakes.Op o : attempts())
            if (o.t > t) throw new AssertionError("a registration-failure notice attempt was made at " + o.t + " ms");
    }

    @Then("no removal call was made at {int} ms")
    public void noRemovalAt(int t) {
        w.settle();
        for (Fakes.Op o : w.notices.ops("remove", null, w.scheduler.startIndex))
            if (o.t == t) throw new AssertionError("a removal call was made at " + t + " ms");
    }

    // ------------------------------------------------------------ helpers

    /** The current start's registration-failure notice attempts: one create call each. */
    private List<Fakes.Op> attempts() {
        w.settle();
        return w.notices.ops("create", Fakes.REG, w.scheduler.startIndex);
    }

    /** The add call of the current start's registration-failure attempt {@code n}, or null if it made none. */
    private Fakes.Op addOf(int n) {
        for (Fakes.Op o : w.notices.ops("add", Fakes.REG, w.scheduler.startIndex)) if (o.attempt == n) return o;
        return null;
    }

    private void throwOn(int start, int attempt, String op) {
        w.notices.throwKeys.add(start + "|" + Fakes.REG + "|" + attempt + "|" + op);
    }

    private Fakes.Window windowAddedAt(long t) {
        for (Fakes.Window win : w.notices.windows(Fakes.REG, w.scheduler.startIndex)) if (win.addedAt == t) return win;
        return null;
    }

    private Fakes.Op requireAdd() {
        if (thatAdd == null) throw new IllegalStateException("no step named the registration-failure add call");
        return thatAdd;
    }

    private Fakes.Window requireWindow() {
        if (thatWindow == null) throw new IllegalStateException("no step named the registration-failure window");
        return thatWindow;
    }

    static List<Long> times(List<Fakes.Op> ops) {
        List<Long> out = new ArrayList<>();
        for (Fakes.Op o : ops) out.add(o.t);
        return out;
    }

    private Fakes.Window only(String kind) {
        List<Fakes.Window> ws = w.notices.windows(kind, w.scheduler.startIndex);
        if (ws.size() != 1) throw new AssertionError(ws.size() + " " + kind + " windows, added at " + addedTimes(ws));
        return ws.get(0);
    }

    private List<Fakes.Window> currentWindows() {
        List<Fakes.Window> out = new ArrayList<>();
        for (Fakes.Window win : w.notices.windows) if (win.start == w.scheduler.startIndex) out.add(win);
        return out;
    }

    static List<Long> addedTimes(List<Fakes.Window> ws) {
        List<Long> out = new ArrayList<>();
        for (Fakes.Window win : ws) out.add(win.addedAt);
        return out;
    }

    private static String kindOf(String which) {
        return "logging".equals(which) ? Fakes.LOG : Fakes.REG;
    }

    private static int number(String n) {
        switch (n) {
            case "one": return 1;
            case "two": return 2;
            case "three": return 3;
            case "four": return 4;
            case "five": return 5;
            case "six": return 6;
            default: return Integer.parseInt(n);
        }
    }
}
