package parkcast.gate;

import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.Suite;

/** Runs the S2 scenarios in specs/features with Cucumber-JVM; settings come from receiver/gate/build.gradle. */
@Suite
@IncludeEngines("cucumber")
public class RunCucumberTest {
}
