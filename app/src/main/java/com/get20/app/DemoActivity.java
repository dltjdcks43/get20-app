package com.get20.app;

import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Deterministic on-device test target, including a held-purple duplicate test. */
public class DemoActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private DemoView target;
    private TextView stats;
    private int index, shown, hits, wrong, duplicates, duration = 100;
    private boolean hitThisAppearance;
    private final int[] values = {2, 3, 5, 10, 20};
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            index = (index + 1) % values.length;
            hitThisAppearance = false;
            if (values[index] == 20) shown++;
            target.invalidate(); update();
            handler.postDelayed(this, values[index] == 20 ? duration : 150);
        }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setOnApplyWindowInsetsListener((v, i) -> {
            var b = i.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(b.left, b.top, b.right, b.bottom); return i;
        });
        TextView title = new TextView(this); title.setText("시험 화면 · 보라색 20만 터치\n먼저 시험 위치 저장 → 뒤로 → 시작 → 이 화면으로 돌아와 감지 시작"); title.setTextSize(17); root.addView(title);
        stats = new TextView(this); stats.setHeight((int) (64 * getResources().getDisplayMetrics().density)); root.addView(stats);
        LinearLayout choices = new LinearLayout(this);
        for (int ms : new int[]{100, 150, 200, 1000}) {
            Button b = new Button(this); b.setText(ms + "ms");
            b.setOnClickListener(v -> { duration = ms; update(); });
            choices.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        }
        root.addView(choices);
        Button save = new Button(this); save.setText("이 시험 원의 위치 저장");
        save.setOnClickListener(v -> {
            if (CaptureService.isRunning()) { Toast.makeText(this, "감지를 중지한 뒤 위치를 저장하세요", Toast.LENGTH_SHORT).show(); return; }
            int[] pos = new int[2]; target.getLocationOnScreen(pos); Rect screen = Config.screen(this);
            Config.prefs(this).edit().putInt("x", pos[0] + target.getWidth() / 2).putInt("y", pos[1] + target.getHeight() / 2)
                    .putInt("width", screen.width()).putInt("height", screen.height()).putInt("rotation", Config.rotation(this)).apply();
            Toast.makeText(this, "시험 위치 저장 완료", Toast.LENGTH_SHORT).show();
        });
        root.addView(save);
        target = new DemoView(); root.addView(target, new LinearLayout.LayoutParams(-1, 0, 1));
        update(); setContentView(root);
    }
    private void update() { stats.setText("20 표시 " + duration + "ms · 등장 " + shown + " · 성공 " + hits + "\n오터치 " + wrong + " · 중복 " + duplicates); }
    @Override protected void onResume() { super.onResume(); handler.postDelayed(tick, 500); }
    @Override protected void onPause() { handler.removeCallbacks(tick); super.onPause(); }
    private class DemoView extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        DemoView() { super(DemoActivity.this); setBackgroundColor(Color.WHITE); }
        float radius() { return Math.min(getWidth(), getHeight()) * .25f; }
        @Override protected void onDraw(Canvas c) {
            float x = getWidth() / 2f, y = getHeight() / 2f;
            paint.setColor(values[index] == 20 ? Color.rgb(151, 54, 220) : Color.rgb(23, 135, 124));
            c.drawCircle(x, y, radius(), paint);
            paint.setColor(Color.WHITE); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(radius() * .8f);
            c.drawText(String.valueOf(values[index]), x, y - (paint.ascent() + paint.descent()) / 2, paint);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                float dx = e.getX() - getWidth() / 2f, dy = e.getY() - getHeight() / 2f;
                if (dx * dx + dy * dy <= radius() * radius()) {
                    if (values[index] != 20) wrong++;
                    else if (hitThisAppearance) duplicates++;
                    else { hits++; hitThisAppearance = true; }
                    update();
                }
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP) { performClick(); return true; }
            return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
    }
}
