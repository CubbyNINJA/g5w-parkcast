package parkcast.core.steps;

import static parkcast.core.steps.Check.assertNotNull;
import static parkcast.core.steps.Check.assertTrue;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;

/**
 * Static scans over the whole app (S3_RECEIVER.md, "The static scan"): AIR-049, AIR-067(b), AIR-068,
 * AIR-069(b), AIR-080, AIR-081 and AIR-082. Each "it finds no ..." phrase names one rule of
 * {@link WholeAppScan}; a phrase with no rule fails, so a new Examples row cannot pass unread. The AIR-067
 * phrase is the whole S2_GATE.md list ({@link WholeAppScan#S2_LIST}), and "it finds no X category" is the
 * AIR-082 manifest read; both go through the one step so Cucumber sees no ambiguous match.
 */
public final class ScopeSteps {
    private WholeAppScan scan;
    private boolean manifestOnly;
    private final AdapterScopeSteps adapters;

    public ScopeSteps(AdapterScopeSteps adapters) {
        this.adapters = adapters;
    }

    @When("the static scan runs")
    public void scanRuns() throws Exception {
        scan = WholeAppScan.ofRepo();
        manifestOnly = false;
    }

    @When("the static scan runs with the S2_GATE.md forbidden list")
    public void scanRunsS2() throws Exception {
        scanRuns();
    }

    @When("the static scan reads AndroidManifest.xml")
    public void scanReadsManifest() throws Exception {
        scan = WholeAppScan.ofRepo();
        manifestOnly = true;
    }

    @Then("it finds no {}")
    public void findsNo(String forbidden) {
        assertNotNull(scan, "the static scan has not run");
        if (adapters.handles(forbidden)) { // the Android-adapter rules (AIR-058 to AIR-066)
            adapters.findsNo(forbidden);
            return;
        }
        assertTrue(scan.fileCount() > 0, "the static scan read no files");
        // The AIR-082 rows end in " category" and read the manifest only; every other row reads the whole app.
        assertTrue(manifestOnly == forbidden.endsWith(" category"), "\"" + forbidden + "\" after the wrong scan step");
        if (manifestOnly) assertTrue(scan.fileCount(WholeAppScan.Kind.MANIFEST) == 1, "no AndroidManifest.xml read");
        List<String> hits = scan.find(forbidden);
        assertTrue(hits.isEmpty(), "the static scan found " + forbidden + ":\n  " + String.join("\n  ", hits));
    }
}
