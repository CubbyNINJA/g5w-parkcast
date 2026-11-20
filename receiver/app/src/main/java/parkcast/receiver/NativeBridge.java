package parkcast.receiver;

import android.os.Handler;
import android.os.Looper;
import parkcast.core.port.NativeEvents;

/**
 * The JNI contract with libparkcast.so (docs/design/S3_RECEIVER.md, "JNI contract"). Java calls only the four
 * native entry points; native code calls only {@link #onNative}, from its one delivery thread. Each event is
 * posted to the main looper tagged with its generation, and dropped when it runs if the library has moved to
 * another generation since or no events sink is set, so nothing from a stopped session reaches core.
 */
final class NativeBridge {
    static final int CONNECTION_ACCEPTED = 1;
    static final int SESSION_STARTED = 2;
    static final int CODEC_CONFIG = 3;
    static final int ACCESS_UNIT = 4;
    static final int SESSION_ENDED = 5;
    static final int ADVERTISE_LOST = 6;
    static final int LOG = 7;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String LOAD_ERROR = load();
    private static volatile NativeEvents sink;

    private NativeBridge() {}

    private static String load() {
        try {
            System.loadLibrary("parkcast");
            return null;
        } catch (Throwable t) {
            return t.getClass().getName();
        }
    }

    /** Null when the library loaded, else the class name of what loading threw. */
    static String loadError() {
        return LOAD_ERROR;
    }

    static void events(NativeEvents e) {
        sink = e;
    }

    static native String nativeStart(int stage, String keyfile, String address, int width, int height, int fps);

    static native void nativeStop(int phase);

    static native int nativeGeneration();

    static native boolean nativeAccepting();

    /** Called by the native delivery thread only. Posts to the main looper; never calls core directly. */
    static void onNative(final int generation, final int kind, final int a, final int b, final long ptsMs,
            final String text, final byte[] data) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                deliver(generation, kind, a, b, ptsMs, text, data);
            }
        });
    }

    private static void deliver(int generation, int kind, int a, int b, long ptsMs, String text, byte[] data) {
        NativeEvents e = sink;
        if (e == null || generation != currentGeneration()) return;
        switch (kind) {
            case CONNECTION_ACCEPTED:
                e.connectionAccepted(generation, text);
                break;
            case SESSION_STARTED:
                e.sessionStarted(generation);
                break;
            case CODEC_CONFIG:
                e.codecConfig(generation, text, a, b, data);
                break;
            case ACCESS_UNIT:
                e.accessUnit(generation, ptsMs, data);
                break;
            case SESSION_ENDED:
                e.sessionEnded(generation);
                break;
            case ADVERTISE_LOST:
                e.advertiseLost(generation);
                break;
            case LOG:
                e.log(generation, text);
                break;
            default:
                break;
        }
    }

    private static int currentGeneration() {
        try {
            return nativeGeneration();
        } catch (Throwable t) {
            return -1;
        }
    }
}
