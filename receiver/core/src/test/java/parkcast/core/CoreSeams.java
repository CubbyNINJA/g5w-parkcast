package parkcast.core;

/** Test-only access to the runtime's package-private parts, for steps that need to reach past the ports. */
public final class CoreSeams {
    private CoreSeams() {
    }

    /** Hands one native record to the runtime's event sink, as the library's log callback would. */
    public static void deliver(ReceiverRuntime r, String record) {
        r.events().log(0, record);
    }

    /** Swaps in a filter that throws on every record (AIR-072(b)). */
    public static void throwingFilter(ReceiverRuntime r) {
        r.sessionLog().useFilter(new NativeLogFilter(null, null) {
            @Override
            String pass(String record) {
                throw new IllegalStateException("synthetic filter failure");
            }
        });
    }

    /** A frame for steps that pump the gate directly, standing in for the decoder's output. */
    public static void frame(ReceiverRuntime r, parkcast.gate.port.Frame f) {
        r.onFrame(f);
    }
}
