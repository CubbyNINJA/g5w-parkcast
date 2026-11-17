package parkcast.core.steps;

import java.util.Arrays;
import java.util.Objects;

/** Assertions for the steps; the build carries no JUnit API, and the gate's steps throw AssertionError too. */
final class Check {
    private Check() {
    }

    static void assertTrue(boolean c, String m) {
        if (!c) throw new AssertionError(m);
    }

    static void assertFalse(boolean c, String m) {
        if (c) throw new AssertionError(m);
    }

    static void assertNotNull(Object o, String m) {
        if (o == null) throw new AssertionError(m);
    }

    static void assertEquals(Object want, Object got) {
        assertEquals(want, got, "");
    }

    static void assertEquals(Object want, Object got, String m) {
        if (!Objects.equals(want, got))
            throw new AssertionError((m.isEmpty() ? "" : m + ": ") + "expected <" + want + "> but was <" + got + ">");
    }

    static void assertEquals(long want, long got, String m) {
        assertEquals(Long.valueOf(want), Long.valueOf(got), m);
    }

    static void assertEquals(long want, long got) {
        assertEquals(want, got, "");
    }

    static void assertArrayEquals(byte[] want, byte[] got) {
        if (!Arrays.equals(want, got))
            throw new AssertionError("expected " + Arrays.toString(want) + " but was " + Arrays.toString(got));
    }
}
