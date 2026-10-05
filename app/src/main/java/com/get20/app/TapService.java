package com.get20.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.atomic.AtomicBoolean;

public class TapService extends AccessibilityService {
    private static volatile TapService instance;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final Handler main = new Handler(Looper.getMainLooper());
    private View overlay;
    private boolean bottom;
    public static TapService get() { return instance; }
    @Override protected void onServiceConnected() { instance = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { CaptureService.stopNow(this); }
    @Override public void onDestroy() {
        instance = null;
        CaptureService.stopNow(this);
        removeOverlay();
        super.onDestroy();
    }

    // Called directly on the analysis thread: no click queue or delayed retry.
    public boolean tap(int x, int y) {
        if (instance != this || !CaptureService.isArmed() || !busy.compareAndSet(false, true)) return false;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 1)).build();
        try {
            boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription g) { busy.set(false); }
                @Override public void onCancelled(GestureDescription g) { busy.set(false); }
            }, main);
            if (!accepted) busy.set(false);
            return accepted;
        } catch (RuntimeException e) {
            busy.set(false);
            return false;
        }
    }

    void removeOverlay() {
        if (overlay != null) {
            getSystemService(WindowManager.class).removeView(overlay);
            overlay = null;
        }
    }
    private void addOverlay(View view, boolean full) {
        removeOverlay();
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                full ? -1 : -2, full ? -1 : -2,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        p.gravity = full ? Gravity.TOP | Gravity.START : (bottom ? Gravity.BOTTOM : Gravity.TOP) | Gravity.CENTER_HORIZONTAL;
        p.y = full ? 0 : 64;
        p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        overlay = view;
        getSystemService(WindowManager.class).addView(view, p);
    }
    private LinearLayout bar() {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(8, 4, 8, 4);
        row.setBackgroundColor(Color.rgb(235, 245, 244));
        return row;
    }
    private void button(LinearLayout row, String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.rgb(0, 65, 60));
        b.setOnClickListener(v -> action.run());
        row.addView(b);
    }
    void showPickerBar() {
        LinearLayout row = bar();
        button(row, "원 중앙 지정", this::showPicker);
        button(row, "취소", this::removeOverlay);
        addOverlay(row, false);
        Toast.makeText(this, "대상 앱으로 이동한 뒤 ‘원 중앙 지정’을 누르세요", Toast.LENGTH_LONG).show();
    }
    @SuppressWarnings("ClickableViewAccessibility")
    private void showPicker() {
        TextView view = new TextView(this);
        view.setText("숫자 원의 중앙을 한 번 터치하세요\n위치만 저장하며 자동 터치는 시작하지 않습니다");
        view.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        view.setPadding(20, 160, 20, 0);
        view.setTextSize(18);
        view.setTextColor(Color.WHITE);
        view.setShadowLayer(6, 0, 0, Color.BLACK);
        view.setBackgroundColor(0x22000000);
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                Rect screen = Config.screen(this);
                Config.prefs(this).edit().putInt("x", Math.round(event.getRawX()))
                        .putInt("y", Math.round(event.getRawY()))
                        .putInt("width", screen.width()).putInt("height", screen.height())
                        .putInt("rotation", Config.rotation(this)).apply();
                removeOverlay();
                Toast.makeText(this, "위치 저장 완료. ‘20 받기’ 앱에서 시작하세요", Toast.LENGTH_LONG).show();
            }
            return true;
        });
        addOverlay(view, true);
    }
    void showCaptureBar() {
        LinearLayout row = bar();
        button(row, CaptureService.isArmed() ? "감지 중 · 일시정지" : "감지 시작", () -> {
            CaptureService.arm(!CaptureService.isArmed());
            showCaptureBar();
        });
        button(row, "중지", () -> CaptureService.stopNow(this));
        button(row, "↕", () -> { bottom = !bottom; showCaptureBar(); });
        addOverlay(row, false);
    }
}
