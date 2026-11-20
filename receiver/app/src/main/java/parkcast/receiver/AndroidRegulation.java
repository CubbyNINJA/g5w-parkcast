package parkcast.receiver;

import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import parkcast.core.adapter.RegulationAdapter;
import parkcast.core.port.RegulationBinder;
import parkcast.gate.port.RegulationSource;

/**
 * The stock regulation service behind {@link RegulationBinder} (OD-195; docs/evidence/REGULATION_API_FINDINGS.md).
 * It looks up {@code com.hkmc.regulation} with ServiceManager, wraps it with
 * {@code IRegulationManager.Stub.asInterface}, and calls only getStatus, addListener and removeListener, all
 * by reflection because the classes are hidden from the SDK. The listener is our own Binder: it answers
 * {@code IRegulationStatusListener} transaction 1 ({@code onStatusChanged(int)}, oneway) and ignores the
 * other two. Callbacks and the service's death are posted to the main thread. {@link #source()} is what
 * the receiver uses: a {@link RegulationAdapter} over this class.
 */
public final class AndroidRegulation implements RegulationBinder {
    static final String SERVICE = "com.hkmc.regulation";
    static final String MANAGER = "com.hkmc.regulation.IRegulationManager";
    static final String LISTENER = "com.hkmc.regulation.IRegulationStatusListener";
    static final int ON_STATUS_CHANGED = IBinder.FIRST_CALL_TRANSACTION;

    private final IBinder binder;
    private final Object manager;
    private final Class<?> managerClass;
    private final Class<?> listenerClass;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Listener, Registered> registered = new HashMap<Listener, Registered>();

    private AndroidRegulation(IBinder binder) throws Exception {
        this.binder = binder;
        managerClass = Class.forName(MANAGER);
        listenerClass = Class.forName(LISTENER);
        manager = Class.forName(MANAGER + "$Stub").getMethod("asInterface", IBinder.class).invoke(null, binder);
    }

    /** The receiver's regulation source. It reports unavailable when the service is missing. */
    public static RegulationSource source() {
        return new RegulationAdapter(find());
    }

    /** The service, or null when it is not registered or its classes cannot be reached. */
    static AndroidRegulation find() {
        try {
            Method get = Class.forName("android.os.ServiceManager").getMethod("getService", String.class);
            IBinder b = (IBinder) get.invoke(null, SERVICE);
            return b == null ? null : new AndroidRegulation(b);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public int getStatus() throws Exception {
        return (Integer) managerClass.getMethod("getStatus").invoke(manager);
    }

    @Override
    public void addListener(Listener l) throws Exception {
        Registered r = new Registered(l);
        binder.linkToDeath(r, 0);
        try {
            managerClass.getMethod("addListener", listenerClass).invoke(manager, r.asInterface());
        } catch (Exception e) {
            binder.unlinkToDeath(r, 0);
            throw e;
        }
        registered.put(l, r);
    }

    @Override
    public void removeListener(Listener l) throws Exception {
        Registered r = registered.remove(l);
        if (r == null) return;
        r.gone = true;
        binder.unlinkToDeath(r, 0);
        managerClass.getMethod("removeListener", listenerClass).invoke(manager, r.asInterface());
    }

    /** One registered listener: the Binder the service calls, and the death recipient for the service. */
    private final class Registered extends Binder implements IBinder.DeathRecipient {
        private final Listener listener;
        private Object proxy;
        volatile boolean gone;

        Registered(Listener listener) {
            this.listener = listener;
        }

        /** The listener as an IRegulationStatusListener, wrapping this Binder. */
        Object asInterface() throws Exception {
            if (proxy == null)
                proxy = Class.forName(LISTENER + "$Stub").getMethod("asInterface", IBinder.class).invoke(null, this);
            return proxy;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws android.os.RemoteException {
            if (code == ON_STATUS_CHANGED) {
                data.enforceInterface(LISTENER);
                final int status = data.readInt();
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!gone) listener.onStatusChanged(status);
                    }
                });
                return true;
            }
            if (code == ON_STATUS_CHANGED + 1 || code == ON_STATUS_CHANGED + 2) return true;
            return super.onTransact(code, data, reply, flags);
        }

        @Override
        public void binderDied() {
            main.post(new Runnable() {
                @Override
                public void run() {
                    if (gone) return;
                    gone = true;
                    listener.onServiceLost();
                }
            });
        }
    }
}
