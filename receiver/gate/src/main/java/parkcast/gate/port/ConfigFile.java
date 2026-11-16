package parkcast.gate.port;

/** The installed config/receiver.conf. */
public interface ConfigFile {
    byte[] read() throws Missing, Unreadable;

    final class Missing extends Exception {
        private static final long serialVersionUID = 1L;

        public Missing(String message) {
            super(message);
        }
    }

    final class Unreadable extends Exception {
        private static final long serialVersionUID = 1L;

        public Unreadable(String message) {
            super(message);
        }
    }
}
