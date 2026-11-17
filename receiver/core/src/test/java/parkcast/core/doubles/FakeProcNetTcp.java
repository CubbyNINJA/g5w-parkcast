package parkcast.core.doubles;

import java.io.IOException;
import parkcast.core.port.ProcNetTcp;

/** {@code /proc/net/tcp}, read-only; records each read as trace cause "proc". */
public final class FakeProcNetTcp implements ProcNetTcp {
    private final Trace trace;
    public byte[] content = new byte[0];
    public boolean throwsOnRead;

    public FakeProcNetTcp(Trace trace) {
        this.trace = trace;
    }

    @Override
    public byte[] read() throws IOException {
        trace.add("proc", "read", "/proc/net/tcp");
        if (throwsOnRead) throw new IOException("synthetic");
        return content.clone();
    }
}
