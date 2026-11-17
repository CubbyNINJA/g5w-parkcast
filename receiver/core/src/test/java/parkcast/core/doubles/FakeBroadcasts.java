package parkcast.core.doubles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import parkcast.core.port.Broadcasts;

/**
 * A broadcast source standing in for Context.registerReceiver. It records every registration (actions,
 * permission, Handler, calling thread and the time it returned) in its own list and in the trace, can make
 * registration throw or return no sticky intent for an action, and delivers broadcasts to live receivers.
 */
public final class FakeBroadcasts implements Broadcasts {
    public static final String ACC = "com.hkmc.intent.action.ACTION_ACC_STATUS_CHANGED";
    public static final String ACC_EXTRA = "EXTRA_ACC_STATUS";
    public static final String REVERSE_ON = "com.hkmc.intent.action.REVERSE_ON";

    /** An Intent: its extras, and whether it is being delivered as the initial sticky broadcast. */
    public static final class Intent implements Message {
        public final Map<String, Object> extras = new LinkedHashMap<>();
        public final boolean initialSticky;

        public Intent(boolean initialSticky) {
            this.initialSticky = initialSticky;
        }

        public Intent put(String k, Object v) {
            extras.put(k, v);
            return this;
        }

        @Override
        public boolean hasExtra(String k) {
            return extras.containsKey(k);
        }

        @Override
        public Object extra(String k) {
            return extras.get(k);
        }

        @Override
        public boolean initialSticky() {
            return initialSticky;
        }
    }

    public static final class Registration {
        public final List<String> actions;
        public final String permission;
        public final Object handler;
        public final Thread thread;
        public final Receiver receiver;
        /** The trace entry written when the call returned or threw. */
        public Trace.Entry entry;
        public boolean threw, unregistered;

        Registration(String[] actions, String permission, Object handler, Receiver receiver) {
            this.actions = actions == null ? new ArrayList<String>() : new ArrayList<>(Arrays.asList(actions));
            this.permission = permission;
            this.handler = handler;
            this.thread = Thread.currentThread();
            this.receiver = receiver;
        }
    }

    /** The token standing for a Handler on the main looper. */
    public final Object mainHandler = new Object() {
        @Override
        public String toString() {
            return "Handler(main looper)";
        }
    };
    /** The main looper's thread: the thread that built this double, which runs the scheduler. */
    public final Thread mainThread = Thread.currentThread();
    private final Trace trace;
    public final List<Registration> registrations = new ArrayList<>();
    /** Actions whose registration throws, with what it throws. */
    public final Map<String, RuntimeException> throwOn = new HashMap<>();
    /** The sticky intent per action; an action not here returns null. */
    public final Map<String, Intent> sticky = new HashMap<>();

    public FakeBroadcasts(Trace trace) {
        this.trace = trace;
        sticky.put(ACC, new Intent(false).put(ACC_EXTRA, 0));
    }

    @Override
    public Object mainHandler() {
        return mainHandler;
    }

    @Override
    public Message register(Receiver r, String[] actions, String permission, Object handler) {
        Registration reg = new Registration(actions, permission, handler, r);
        registrations.add(reg);
        String details = String.join(",", reg.actions) + " permission=" + permission + " handler=" + handler
                + " thread=" + reg.thread.getName();
        for (String a : reg.actions) {
            RuntimeException e = throwOn.get(a);
            if (e != null) {
                reg.threw = true;
                reg.entry = trace.add("broadcast", "register", details + " threw=" + e.getClass().getName());
                throw e;
            }
        }
        reg.entry = trace.add("broadcast", "register", details);
        Intent s = null;
        for (String a : reg.actions) if (sticky.containsKey(a)) s = sticky.get(a);
        return s;
    }

    @Override
    public void unregister(Receiver r) {
        for (Registration reg : registrations) if (reg.receiver == r && !reg.threw) reg.unregistered = true;
        trace.add("broadcast", "unregister", "");
    }

    /** Registrations whose filter holds {@code action}. */
    public List<Registration> of(String action) {
        List<Registration> out = new ArrayList<>();
        for (Registration reg : registrations) if (reg.actions.contains(action)) out.add(reg);
        return out;
    }

    /** A broadcast of {@code action} arrives now, on the calling (main) thread, at every live receiver. */
    public void deliver(String action, Intent m) {
        for (Registration reg : of(action)) if (!reg.threw && !reg.unregistered) reg.receiver.onReceive(m);
    }
}
