package parkcast.core.port;

/**
 * {@code libparkcast} plus UxPlay, through JNI (OD-187). Java only starts it in stages, stops it in phases and
 * reads its status (AIR-055(c)). Native events come back through {@link NativeEvents}.
 */
public interface NativeLibrary {
    /** The start stages, called in this order; each runs only if the one before it passed. */
    enum Stage {
        /** Step 0 stdio redirect, {@code raop_init}, then {@code raop_init2} with the key file. */
        KEYCHECK,
        /** The five ports on the plan's address; listen, never accept. */
        BIND,
        /** One {@code /dev/socket/mdnsd} connect probe and, once it connects, both registrations. */
        ADVERTISE,
        /** Starts the listener thread. */
        ACCEPT
    }

    /** The stop phases, in AIR-070's order. */
    enum Phase {
        ACCEPT, SESSION, REGISTRATIONS, PORTS
    }

    StartResult start(Stage s, StartPlan p, NativeEvents e);

    void stop(Phase p);

    Status status();

    /** What every stage is given. */
    final class StartPlan {
        public final String keyfile;
        public final String address;

        public StartPlan(String keyfile, String address) {
            this.keyfile = keyfile;
            this.address = address;
        }

        /** The display the receiver advertises in its info (AIR-051(a)): 1920x720 at 60 frames per second. */
        public final int width = 1920, height = 720, refreshHz = 60;
        /** HEVC is never offered: no -h265 option and no HEVC feature bit (AIR-052(a)). */
        public final boolean h265 = false;
    }

    /** A stage's outcome: passed or failed, with a token naming the failure or the success. */
    final class StartResult {
        /** KEYCHECK's token when step 0, the stdio redirect, failed. No library call was made. */
        public static final String STDIO_FAILED = "stdio-failed";
        /** ADVERTISE's token for a probe that connected and both registrations that succeeded. */
        public static final String MDNSD_UP = "mdnsd up";

        public final boolean ok;
        public final String token;

        public StartResult(boolean ok, String token) {
            this.ok = ok;
            this.token = token;
        }

        public static StartResult ok(String token) {
            return new StartResult(true, token);
        }

        public static StartResult failed(String token) {
            return new StartResult(false, token);
        }
    }

    /** The library's state as {@code status()} reports it. */
    final class Status {
        public final boolean accepting;
        public final int generation;

        public Status(boolean accepting, int generation) {
            this.accepting = accepting;
            this.generation = generation;
        }
    }
}
