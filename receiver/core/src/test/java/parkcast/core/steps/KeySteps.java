package parkcast.core.steps;

import static parkcast.core.steps.Check.assertArrayEquals;
import static parkcast.core.steps.Check.assertEquals;
import static parkcast.core.steps.Check.assertFalse;
import static parkcast.core.steps.Check.assertNotNull;
import static parkcast.core.steps.Check.assertTrue;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import parkcast.core.doubles.FakeFileSystem;
import parkcast.core.doubles.FakeNativeLibrary;
import parkcast.core.doubles.Trace;
import parkcast.gate.harness.Fakes;

/** Steps for the identity key file (AIR-070, AIR-083). */
public final class KeySteps {
    private static final String KEYFILE = "keyfile=";

    private final S3World w;
    private String hashAfterFirst;
    private String hashAfterSecond;

    public KeySteps(S3World w) {
        this.w = w;
    }

    @Given("the fake app-data directory holds no key file")
    public void noKeyFile() {
        w.fs.files.remove(S3World.KEY_PATH);
    }

    @Given("the key file exists and every read of it throws IOException")
    public void readThrows() {
        w.seedKey(FakeFileSystem.VALID_KEY);
        w.fs.readThrows.add(S3World.KEY_PATH);
    }

    @Given("the key file exists and is zero-length")
    public void zeroLength() {
        w.seedKey(new byte[0]);
    }

    @Given("the key file holds the corrupt key fixture")
    public void corrupt() {
        w.seedKey(FakeFileSystem.CORRUPT_KEY);
    }

    @Given("no key file exists and creating it throws IOException")
    public void createThrows() {
        w.fs.files.remove(S3World.KEY_PATH);
        w.fs.createThrows.add(S3World.KEY_PATH);
    }

    @When("the receiver runs for {int} ms and exit\\() is called")
    public void runThenExit(int ms) {
        w.runFor(ms);
        w.runtime.exit();
        hashAfterFirst = w.fs.sha256(S3World.KEY_PATH);
    }

    @When("the receiver is started again and runs for {int} ms")
    public void startAgain(int ms) {
        w.restart();
        w.runFor(ms);
        hashAfterSecond = w.fs.sha256(S3World.KEY_PATH);
    }

    private String keyfile(int start) {
        List<Trace.Entry> e = w.trace.of("native", "raop_init2", start);
        assertEquals(1, e.size(), "raop_init2 calls in start " + start + ": " + e);
        assertTrue(e.get(0).details.startsWith(KEYFILE), "no keyfile: " + e.get(0));
        return e.get(0).details.substring(KEYFILE.length());
    }

    @Then("in the first start, raop_init2 was passed as keyfile a path inside the fake app-data directory")
    public void firstKeyfile() {
        String k = keyfile(1);
        assertTrue(k.startsWith(FakeFileSystem.ROOT) && k.length() > FakeFileSystem.ROOT.length()
                && !k.contains(".."), "keyfile outside app data: " + k);
    }

    @Then("a key file was created at that path, readable and writable only by the app's uid")
    public void createdOwnerOnly() {
        String k = keyfile(1);
        List<Trace.Entry> c = w.trace.of("fs", "create", 1);
        assertEquals(1, c.size(), "creates: " + c);
        assertEquals(k + " mode=" + Integer.toOctalString(FakeNativeLibrary.KEY_MODE), c.get(0).details);
        FakeFileSystem.File f = w.fs.files.get(k);
        assertNotNull(f, "no key file at " + k);
        assertEquals(0600, f.mode, "mode " + Integer.toOctalString(f.mode));
    }

    @Then("in the second start, raop_init2 was passed the same keyfile path")
    public void sameKeyfile() {
        assertEquals(keyfile(1), keyfile(2));
    }

    @Then("the key file's SHA-256 hash after the second start equals its hash after the first")
    public void sameHash() {
        assertNotNull(hashAfterFirst, "no key file after the first start");
        assertEquals(hashAfterFirst, hashAfterSecond);
    }

    @Then("the fake file system recorded no write, rename, chmod or delete of the key file in the second start")
    public void untouchedInSecond() {
        for (Trace.Entry e : w.trace.entries) {
            if (e.start != 2 || !e.cause.equals("fs")) continue;
            assertTrue(e.name.equals("read"), "file change in the second start: " + e);
        }
    }

    @Then("no log record contains the key file's path or name")
    public void keyNotLogged() {
        for (Fakes.Line l : w.g.logs.lines)
            assertFalse(l.text.contains(S3World.KEY_PATH) || l.text.contains(S3World.KEY_NAME), "key in log: " + l.text);
    }

    @Then("the key file's bytes and mode are unchanged")
    public void unchanged() {
        FakeFileSystem.File f = w.fs.files.get(S3World.KEY_PATH);
        assertNotNull(f, "the key file is gone");
        assertArrayEquals(w.seededKey, f.bytes);
        assertEquals(w.seededMode, f.mode);
        for (Trace.Entry e : w.trace.current("fs", "create"))
            assertFalse(e.details.startsWith(S3World.KEY_PATH), "key file recreated: " + e);
    }

    @Then("no rename to the key file's path was recorded")
    public void noRename() {
        for (Trace.Entry e : w.trace.current("fs", "rename"))
            assertFalse(e.details.endsWith(S3World.KEY_PATH), "rename to the key file: " + e);
        assertFalse(w.fs.files.containsKey(S3World.KEY_PATH), "a key file appeared");
    }
}
