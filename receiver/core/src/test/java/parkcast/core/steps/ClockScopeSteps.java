package parkcast.core.steps;

import io.cucumber.java.en.Then;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Static-scan steps for the clock (AIR-065) and the stock regulation state (AIR-066), over the files the
 * shared static scan read.
 */
public final class ClockScopeSteps {
    private static final Pattern IMPLEMENTS_SCHEDULER =
            Pattern.compile("\\bimplements\\b[^{]*\\b(?:parkcast\\.gate\\.port\\.)?Scheduler\\b");
    private static final Pattern UPTIME = Pattern.compile("\\bSystemClock\\s*\\.\\s*uptimeMillis\\s*\\(\\s*\\)");
    private static final Pattern HANDLER = Pattern.compile("\\bHandler\\b");
    private static final Pattern POST_AT_TIME = Pattern.compile("\\.\\s*postAtTime\\s*\\(");
    private static final Pattern WALL_CLOCK = Pattern.compile("currentTimeMillis|elapsedRealtime|nanoTime");
    private static final Pattern REGULATION = Pattern.compile("com[./]hkmc[./]regulation");
    private static final Pattern RECEIVE_ONLY = Pattern.compile("\\bRegulationSource\\b");
    private static final Pattern WRITE = Pattern.compile("\\bset[A-Z]\\w*|\\btransact\\b");

    private final ScopeSteps scope;

    public ClockScopeSteps(ScopeSteps scope) {
        this.scope = scope;
    }

    private static String unix(ScopeSteps.Scanned s) {
        return s.path.toString().replace('\\', '/');
    }

    @Then("^the Scheduler's Android adapter uses Handler with SystemClock\\.uptimeMillis\\(\\)$")
    public void schedulerOnUptime() {
        List<ScopeSteps.Scanned> adapters = new ArrayList<>();
        for (ScopeSteps.Scanned s : scope.scanned())
            if (unix(s).contains("/app/src/main/") && unix(s).endsWith(".java") && IMPLEMENTS_SCHEDULER.matcher(s.text).find())
                adapters.add(s);
        if (adapters.size() != 1) throw new AssertionError("Android Scheduler adapters in the app: " + paths(adapters));
        ScopeSteps.Scanned a = adapters.get(0);
        List<String> missing = new ArrayList<>();
        if (!HANDLER.matcher(a.text).find()) missing.add("Handler");
        if (!UPTIME.matcher(a.text).find()) missing.add("SystemClock.uptimeMillis()");
        if (!POST_AT_TIME.matcher(a.text).find()) missing.add("Handler.postAtTime");
        if (WALL_CLOCK.matcher(a.text).find()) missing.add("no other clock");
        if (!missing.isEmpty()) throw new AssertionError(a.path + " lacks " + missing);
    }

    @Then("^the gate's sources read no time from System\\.currentTimeMillis\\(\\), SystemClock\\.elapsedRealtime\\(\\) or System\\.nanoTime\\(\\)$")
    public void gateHasNoClock() {
        int gate = 0;
        List<String> hits = new ArrayList<>();
        for (ScopeSteps.Scanned s : scope.scanned()) {
            String u = unix(s);
            if (!u.contains("/gate/src/main/") && !u.contains("/gate/build/classes/")) continue;
            gate++;
            if (WALL_CLOCK.matcher(s.text).find()) hits.add(u);
        }
        if (gate == 0) throw new AssertionError("the static scan read no gate sources");
        if (!hits.isEmpty()) throw new AssertionError("the gate reads a clock in " + hits);
    }

    @Then("com.hkmc.regulation is used only to receive notifications")
    public void regulationReceiveOnly() {
        List<String> bad = new ArrayList<>();
        for (ScopeSteps.Scanned s : scope.scanned()) {
            if (!REGULATION.matcher(s.text).find()) continue;
            if (!RECEIVE_ONLY.matcher(s.text).find()) bad.add("not a RegulationSource: " + unix(s));
            if (WRITE.matcher(s.text).find()) bad.add("a setter or transact in " + unix(s));
        }
        if (!bad.isEmpty()) throw new AssertionError("com.hkmc.regulation used other than to receive: " + bad);
    }

    private static List<String> paths(List<ScopeSteps.Scanned> l) {
        List<String> out = new ArrayList<>();
        for (ScopeSteps.Scanned s : l) out.add(unix(s));
        return out;
    }
}
