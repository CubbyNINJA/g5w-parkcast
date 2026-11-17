package android.os;

/** Test-only stand-in for the Android class, so a double can throw it by its real name. Not shipped. */
public class DeadObjectException extends Exception {
    private static final long serialVersionUID = 1L;

    public DeadObjectException() {
        super();
    }
}
