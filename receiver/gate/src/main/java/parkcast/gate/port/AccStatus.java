package parkcast.gate.port;

/** The EXTRA_ACC_STATUS extra of one ACC broadcast: an int, or one of two markers. */
public final class AccStatus {
    public static final AccStatus MISSING = new AccStatus(0, "missing");
    public static final AccStatus NON_INTEGER = new AccStatus(0, "non-integer");

    private final int value;
    private final String marker;

    private AccStatus(int value, String marker) {
        this.value = value;
        this.marker = marker;
    }

    public static AccStatus of(int value) {
        return new AccStatus(value, null);
    }

    public boolean isInteger() {
        return marker == null;
    }

    /** The integer status; only meaningful when {@link #isInteger()}. */
    public int value() {
        return value;
    }

    @Override
    public String toString() {
        return marker == null ? Integer.toString(value) : marker;
    }
}
