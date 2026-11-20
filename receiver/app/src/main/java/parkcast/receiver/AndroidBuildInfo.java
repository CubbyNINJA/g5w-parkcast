package parkcast.receiver;

import java.lang.reflect.Method;
import parkcast.gate.port.BuildInfo;

/**
 * {@link BuildInfo}: ro.build.product, read through the hidden android.os.SystemProperties getter. An empty
 * value reads as null; a failed read throws {@link BuildInfo.Unreadable} naming what was thrown.
 */
final class AndroidBuildInfo implements BuildInfo {
    @Override
    public String product() throws Unreadable {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class);
            Object v = get.invoke(null, "ro.build.product");
            String s = v == null ? null : v.toString();
            return s == null || s.isEmpty() ? null : s;
        } catch (Exception e) {
            throw new Unreadable(e.getClass().getName());
        } catch (LinkageError e) {
            throw new Unreadable(e.getClass().getName());
        }
    }
}
