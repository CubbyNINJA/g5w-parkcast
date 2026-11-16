package parkcast.gate.harness;

import java.util.ArrayList;
import java.util.List;

/** The AT-18 timing bounds: first 0 reply within 200 ms of the shift, image gone within 250 ms of it, 450 ms in all. */
public final class AT18Checker {
    private AT18Checker() {
    }

    /** The broken bounds joined with " and ", or "none". */
    public static String broken(long shift, long reply, long gone) {
        List<String> b = new ArrayList<>();
        if (reply - shift > 200) b.add("the 200 ms polling");
        if (gone - reply > 250) b.add("the 250 ms removal");
        if (gone - shift > 450) b.add("the 450 ms total");
        return b.isEmpty() ? "none" : String.join(" and ", b);
    }

    public static boolean accepted(long shift, long reply, long gone) {
        return "none".equals(broken(shift, reply, gone));
    }
}
