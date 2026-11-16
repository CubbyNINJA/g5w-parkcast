package parkcast.gate.port;

/** The ro.build.product system property. */
public interface BuildInfo {
    /** The property's value, or null when it is missing. */
    String product() throws Unreadable;

    final class Unreadable extends Exception {
        private static final long serialVersionUID = 1L;

        public Unreadable(String message) {
            super(message);
        }
    }
}
