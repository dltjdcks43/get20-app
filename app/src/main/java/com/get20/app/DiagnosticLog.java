package com.get20.app;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

/** Bounded numeric frame ring; formatting and disk writes run off the capture thread. */
public final class DiagnosticLog {
    private static final int CAPACITY = 1800;
    private static final long[][] data = new long[CAPACITY][7];
    private static final ArrayDeque<String> events = new ArrayDeque<>();
    private static int next, count;
    private static long sequence, startNs, startWall;
    private static AtomicFile file;
    private static Handler io;
    private static boolean dirty, session;
    private static String saved = "아직 진단 로그가 없습니다.";
    private DiagnosticLog() { }

    public static synchronized void init(Context context) {
        if (io != null) return;
        file = new AtomicFile(new File(context.getFilesDir(), "diagnostic-log.txt"));
        try { saved = new String(file.readFully(), StandardCharsets.UTF_8); }
        catch (java.io.IOException ignored) { }
        HandlerThread thread = new HandlerThread("diagnostic-log-io");
        thread.start(); io = new Handler(thread.getLooper());
        io.postDelayed(new Runnable() {
            @Override public void run() { persist(); io.postDelayed(this, 3000); }
        }, 3000);
    }
    public static synchronized void begin(Context context, String settings) {
        init(context);
        next = count = 0; sequence = 0; events.clear();
        startNs = System.nanoTime(); startWall = System.currentTimeMillis(); session = true;
        event("SESSION version=" + BuildConfig.VERSION_NAME + " manufacturer=" + Build.MANUFACTURER
                + " model=" + Build.MODEL + " Android=" + Build.VERSION.RELEASE + " SDK=" + Build.VERSION.SDK_INT);
        event(settings);
    }
    public static synchronized void event(String message) {
        if (!session) return;
        if (events.size() == 240) events.removeFirst();
        events.addLast((System.nanoTime() - startNs) / 1000 + "us " + message);
        dirty = true;
    }
    // code: 0=analyzed, 1=stale/duplicate/pre-arm discarded, 2=trigger.
    public static synchronized void frame(long timestamp, long ageNs, long analysisNs, float fraction, int code) {
        if (!session) return;
        long[] row = data[next];
        row[0] = ++sequence; row[1] = (timestamp - startNs) / 1000;
        row[2] = ageNs / 1000; row[3] = analysisNs / 1000;
        row[4] = Math.round(fraction * 10000); row[5] = code;
        row[6] = (System.nanoTime() - startNs) / 1000;
        next = (next + 1) % CAPACITY; count = Math.min(count + 1, CAPACITY); dirty = true;
    }
    public static String snapshot() {
        long[][] copy; String[] notes; long wall;
        synchronized (DiagnosticLog.class) {
            if (!session) return saved;
            wall = startWall; notes = events.toArray(new String[0]); copy = new long[count][];
            for (int i = 0; i < count; i++) copy[i] = data[(next - count + i + CAPACITY) % CAPACITY].clone();
        }
        StringBuilder out = new StringBuilder("20 받기 진단 로그\n시작: ").append(new java.util.Date(wall))
                .append("\n시간: 세션 시작 기준 us(1000us=1ms). 프레임 시간은 캡처 timestamp 기준.\n")
                .append("제스처 완료는 Android 처리 완료이며 20원 획득 성공을 뜻하지 않습니다.\n")
                .append("화면 이미지·텍스트는 저장하지 않습니다. 최근 최대 1800프레임, 240이벤트.\n\n");
        for (String note : notes) out.append(note).append('\n');
        out.append("\nframe,capture_us,age_us,analysis_us,purple_0_to_10000,code,recorded_us\n");
        for (long[] row : copy) {
            for (int i = 0; i < row.length; i++) { if (i > 0) out.append(','); out.append(row[i]); }
            out.append('\n');
        }
        return out.toString();
    }
    public static void flush() { if (io != null) io.post(DiagnosticLog::persist); }
    private static void persist() {
        synchronized (DiagnosticLog.class) { if (!dirty) return; dirty = false; }
        byte[] bytes = snapshot().getBytes(StandardCharsets.UTF_8);
        FileOutputStream stream = null;
        try { stream = file.startWrite(); stream.write(bytes); file.finishWrite(stream); }
        catch (java.io.IOException e) {
            file.failWrite(stream);
            synchronized (DiagnosticLog.class) { dirty = true; }
        }
    }
}
