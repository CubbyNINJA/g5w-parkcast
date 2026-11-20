package parkcast.receiver;

import parkcast.core.port.CrashHook;

/**
 * {@link CrashHook}: the default uncaught-exception handler. It hands the throwable to the sink, then ends the
 * process. It never brings anything back up (AIR-069(a), (b)).
 */
final class ExitCrashHook implements CrashHook {
    @Override
    public void install(final CrashSink sink) {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    sink.crashed(e);
                } catch (Throwable ignored) {
                    // The process ends either way.
                } finally {
                    System.exit(1);
                }
            }
        });
    }
}
