package parkcast.receiver;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import parkcast.core.ReceiverRuntime;
import parkcast.core.adapter.AccReceiver;
import parkcast.core.adapter.ConfigAdapter;
import parkcast.core.adapter.LogDirFiles;
import parkcast.core.adapter.ParkCall;
import parkcast.core.adapter.ReverseReceiver;

/**
 * The receiver's only activity. It wires every port of {@link ReceiverRuntime} to its Android adapter and
 * forwards the lifecycle, window focus and the owner's exit (Back). The content is the video SurfaceView under
 * the black cover.
 */
public final class ReceiverActivity extends Activity {
    /** Display width the gate places notices against; the unit's panel is 1920 wide. */
    static final int DISPLAY_WIDTH = 1920;

    private ReceiverRuntime runtime;
    private AndroidThreads threads;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Handler main = new Handler(Looper.getMainLooper());
        AndroidScheduler scheduler = new AndroidScheduler();
        threads = new AndroidThreads(main);
        AndroidBroadcasts broadcasts = new AndroidBroadcasts(this, main);
        final ReceiverRuntime[] self = new ReceiverRuntime[1];
        ParkCall park = new ParkCall(scheduler, threads, new AndroidGearSource(), new ParkCall.Sink() {
            @Override
            public void record(String line) {
                if (self[0] != null) self[0].record(line);
            }
        });
        AndroidCoverView cover = new AndroidCoverView(this);

        runtime = new ReceiverRuntime(scheduler, park, new AccReceiver(broadcasts, threads),
                new ReverseReceiver(new ReverseOnBroadcasts(this, main)), new AndroidNoticeWindows(this, getWindowManager()),
                new LogDirFiles(new FileLogDir()), new ConfigAdapter(new FilePathReader()), new AndroidBuildInfo(),
                AndroidRegulation.source(), new NoCalls.NoDisplay(), new NoCalls.NoMirrorSession(),
                new NoCalls.NoBus(), DISPLAY_WIDTH, new AndroidAppFiles(getFilesDir()), new AndroidInterfaceQuery(),
                new NativeLibraryAdapter(), new AndroidNsdWake(this, main), new AndroidCodecFactory(), cover,
                new ExitCrashHook(), BuildConfig.APP_VERSION, BuildConfig.GIT_COMMIT, BuildConfig.UXPLAY_COMMIT);
        self[0] = runtime;
        runtime.useProcNetTcp(new AndroidProcNetTcp());
        setContentView(cover.root());
        runtime.onCreate();
    }

    @Override
    protected void onStart() {
        super.onStart();
        runtime.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        runtime.onResume();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        runtime.onWindowFocusChanged(hasFocus);
    }

    @Override
    protected void onPause() {
        runtime.onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        runtime.onStop();
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        runtime.exit();
        finish();
    }

    @Override
    protected void onDestroy() {
        runtime.onDestroy();
        threads.quit();
        super.onDestroy();
    }
}
