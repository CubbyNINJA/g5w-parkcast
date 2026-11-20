package parkcast.receiver;

import android.content.Context;
import java.lang.reflect.Method;
import parkcast.core.port.GearSource;

/**
 * {@link GearSource} over the stock vehicle-info wrapper (AIR-058(a), GEAR_PROBE_FINDINGS.md):
 * {@code com.hkmc.micom.VehicleInfoManager(Context).getGearPStatus()}, which returns the service's cached Park
 * state and sends nothing to the MICOM. {@code automotive.jar} is on the unit's BOOTCLASSPATH, so the class
 * loads by name; it is not in the SDK, hence reflection. Only that one getter is ever called: no listener, no
 * BCM gear-status request (AIR-058(b)). {@link parkcast.core.adapter.ParkCall} calls this on its worker thread
 * with the 200 ms timeout, and any throw (missing class, SecurityException, binder failure) reaches the gate
 * as unavailable, so video stays blocked (AIR-058(c), AMB-98).
 */
final class AndroidGearSource implements GearSource {
    private final Context context;
    private Object manager;
    private Method getter;

    AndroidGearSource(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public synchronized int getGearPStatus() throws Exception {
        if (getter == null) {
            Class<?> c = Class.forName("com.hkmc.micom.VehicleInfoManager");
            manager = c.getConstructor(Context.class).newInstance(context);
            getter = c.getMethod("getGearPStatus");
        }
        return ((Number) getter.invoke(manager)).intValue();
    }
}
