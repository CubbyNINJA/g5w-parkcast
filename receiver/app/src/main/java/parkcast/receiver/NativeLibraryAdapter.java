package parkcast.receiver;

import parkcast.core.port.NativeEvents;
import parkcast.core.port.NativeLibrary;

/**
 * {@link NativeLibrary} over {@link NativeBridge}. A start reply of "ok TOKEN" or "fail TOKEN" becomes a
 * {@link StartResult}. If the library did not load, every start fails with the class name of what loading
 * threw, stop does nothing and status reports not accepting, generation 0. The events passed to start are
 * the ones native events are delivered to.
 */
final class NativeLibraryAdapter implements NativeLibrary {
    @Override
    public StartResult start(Stage s, StartPlan p, NativeEvents e) {
        String error = NativeBridge.loadError();
        if (error != null) return StartResult.failed(error);
        NativeBridge.events(e);
        String reply;
        try {
            reply = NativeBridge.nativeStart(s.ordinal(), p.keyfile, p.address, p.width, p.height, p.refreshHz);
        } catch (Throwable t) {
            return StartResult.failed(t.getClass().getName());
        }
        if (reply == null) return StartResult.failed("no-reply");
        if (reply.startsWith("ok ")) return StartResult.ok(reply.substring(3));
        if (reply.equals("ok")) return StartResult.ok("");
        if (reply.startsWith("fail ")) return StartResult.failed(reply.substring(5));
        return StartResult.failed(reply);
    }

    @Override
    public void stop(Phase p) {
        if (NativeBridge.loadError() != null) return;
        try {
            NativeBridge.nativeStop(p.ordinal());
        } catch (Throwable t) {
            // nativeStop never throws by contract; a broken library must not break the stop steps.
        }
    }

    @Override
    public Status status() {
        if (NativeBridge.loadError() != null) return new Status(false, 0);
        try {
            return new Status(NativeBridge.nativeAccepting(), NativeBridge.nativeGeneration());
        } catch (Throwable t) {
            return new Status(false, 0);
        }
    }
}
