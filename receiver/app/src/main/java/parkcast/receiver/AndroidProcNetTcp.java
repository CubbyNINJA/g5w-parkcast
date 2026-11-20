package parkcast.receiver;

import java.io.FileInputStream;
import java.io.IOException;
import parkcast.core.port.ProcNetTcp;

/** {@link ProcNetTcp}: the whole of /proc/net/tcp, read to end of file. */
final class AndroidProcNetTcp implements ProcNetTcp {
    @Override
    public byte[] read() throws IOException {
        return FilePathReader.Streams.readAll(new FileInputStream("/proc/net/tcp"));
    }
}
