package parkcast.core;

import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.Suite;

/** Runs the receiver's S3 scenarios with Cucumber-JVM; settings come from receiver/core/build.gradle. */
@Suite
@IncludeEngines("cucumber")
public class RunCucumberTest {
}
