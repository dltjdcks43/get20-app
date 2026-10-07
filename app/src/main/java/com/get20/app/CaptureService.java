package com.get20.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.widget.Toast;

public class CaptureService extends Service {
    private static final long MAX_FRAME_AGE_NS = 40_000_000L;
    private static final Object STATE = new Object();
    private static volatile CaptureService current;
    public static volatile String status = "중지됨";
    public static volatile long frames, taps, rejected, stale;
    private volatile boolean active, armed;
    private volatile long armTime;
    private long seenArmTime, lastTimestamp;
    private HandlerThread thread;
    private Handler worker;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private PurpleDetector detector;
    private int width, height, rotation, x, y, radius;
    private final MediaProjection.Callback callback = new MediaProjection.Callback() {
        @Override public void onStop() { fail("화면 공유가 종료되었습니다"); }
        @Override public void onCapturedContentResize(int w, int h) {
            if (w != width || h != height) fail("화면 크기가 변경되었습니다. 위치를 다시 지정하세요");
        }
    };

    public static boolean isRunning() { CaptureService s = current; return s != null && s.active; }
    public static boolean isArmed() { CaptureService s = current; return s != null && s.active && s.armed; }
    public static void arm(boolean value) {
        synchronized (STATE) {
            CaptureService s = current;
            if (s == null || !s.active) return;
            s.armTime = System.nanoTime();
            s.armed = value;
            if (s.detector != null) s.detector.reset();
            status = value ? "감지 중" : "대기 중 · 대상 앱에서 감지 시작을 누르세요";
        }
    }
    public static void stopNow(Context c) {
        synchronized (STATE) {
            if (current != null) { current.armed = false; current.active = false; if (current.detector != null) current.detector.reset(); }
            status = "중지됨";
        }
        c.stopService(new Intent(c, CaptureService.class));
    }
    @Override public void onCreate() {
        super.onCreate();
        thread = new HandlerThread("purple-latest-frame", android.os.Process.THREAD_PRIORITY_DISPLAY);
        thread.start();
        worker = new Handler(thread.getLooper());
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || "STOP".equals(intent.getAction())) { stopNow(this); stopSelf(); return START_NOT_STICKY; }
        if (current == this) return START_NOT_STICKY;
        if (TapService.get() == null || !Config.valid(this)) { stopSelf(); return START_NOT_STICKY; }
        Intent data = intent.getParcelableExtra("data", Intent.class);
        if (data == null) { stopSelf(); return START_NOT_STICKY; }
        try {
            startForeground(20, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            Rect screen = Config.screen(this);
            width = screen.width(); height = screen.height(); rotation = Config.rotation(this);
            var p = Config.prefs(this);
            x = p.getInt("x", 0); y = p.getInt("y", 0); radius = p.getInt("radius", 32);
            detector = new PurpleDetector(p.getInt("hueMin", 260), p.getInt("hueMax", 315));
            current = this;
            active = true; armed = false;
            frames = taps = rejected = stale = 0;
            status = "대기 중 · 대상 앱에서 감지 시작을 누르세요";
            worker.post(() -> begin(data));
        } catch (RuntimeException e) { fail("화면 캡처 시작 실패: " + e.getClass().getSimpleName()); }
        return START_NOT_STICKY;
    }
    private Notification notification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("capture", "화면 감지", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, CaptureService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, "capture").setSmallIcon(R.drawable.ic_app)
                .setContentTitle("20 받기 · 화면 공유 중").setContentText("화면 버튼에서 감지를 시작하거나 중지할 수 있습니다")
                .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null, "중지", stop).build()).build();
    }
    private void begin(Intent data) {
        if (!active) return;
        try {
            projection = getSystemService(MediaProjectionManager.class).getMediaProjection(Activity.RESULT_OK, data);
            projection.registerCallback(callback, worker);
            // Three buffers allow acquireLatestImage to discard queued older frames.
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
            reader.setOnImageAvailableListener(this::analyze, worker);
            display = projection.createVirtualDisplay("Get20", width, height, getResources().getConfiguration().densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, worker);
            main.post(() -> { if (active && TapService.get() != null) TapService.get().showCaptureBar(); });
        } catch (RuntimeException e) { fail("캡처 실패: " + e.getClass().getSimpleName()); }
    }
    private void analyze(ImageReader source) {
        if (!active) return;
        try (Image image = source.acquireLatestImage()) {
            if (image == null) return;
            if (!armed) { detector.reset(); return; }
            if (Config.rotation(this) != rotation) { fail("화면 방향이 변경되었습니다. 위치를 다시 지정하세요"); return; }
            long timestamp = image.getTimestamp();
            long age = System.nanoTime() - timestamp;
            if (timestamp <= lastTimestamp || timestamp < armTime || age > MAX_FRAME_AGE_NS) { stale++; detector.discardFrame(); return; }
            lastTimestamp = timestamp;
            if (seenArmTime != armTime) { detector.reset(); seenArmTime = armTime; }
            if (image.getWidth() != width || image.getHeight() != height) { fail("캡처 좌표가 변경되었습니다"); return; }
            Image.Plane plane = image.getPlanes()[0];
            float fraction = detector.fraction(plane.getBuffer(), width, height, plane.getRowStride(), plane.getPixelStride(), x, y, radius);
            frames++;
            if (!detector.risingEdge(fraction, timestamp)) return;
            synchronized (STATE) {
                if (!active || !armed || seenArmTime != armTime) return;
                TapService tap = TapService.get();
                if (tap == null) { fail("접근성 서비스가 꺼졌습니다"); return; }
                if (tap.tap(x, y)) taps++; else rejected++;
            }
        } catch (RuntimeException e) { fail("프레임 처리 실패: " + e.getClass().getSimpleName()); }
    }
    private void fail(String message) {
        synchronized (STATE) { active = false; armed = false; if (detector != null) detector.reset(); status = message; }
        main.post(() -> { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); stopSelf(); });
    }
    @Override public void onDestroy() {
        synchronized (STATE) { active = false; armed = false; if (detector != null) detector.reset(); if (current == this) current = null; }
        TapService tap = TapService.get();
        if (tap != null) tap.removeOverlay();
        worker.post(() -> {
            if (reader != null) reader.setOnImageAvailableListener(null, null);
            if (display != null) { display.release(); display = null; }
            if (reader != null) { reader.close(); reader = null; }
            if (projection != null) { projection.unregisterCallback(callback); projection.stop(); projection = null; }
            thread.quitSafely();
        });
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
