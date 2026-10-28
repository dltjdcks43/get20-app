package com.get20.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    private TextView status, location;
    private EditText radius, hueMin, hueMax;
    private Button start, select;
    private boolean requesting;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            status.setText(CaptureService.status + "\n접근성: " + (TapService.get() == null ? "꺼짐" : "켜짐")
                    + " · 분석 " + CaptureService.frames + " 프레임\n터치 요청 " + CaptureService.taps
                    + " · 거절 " + CaptureService.rejected + " · 오래된 프레임 " + CaptureService.stale);
            var p = Config.prefs(MainActivity.this);
            location.setText(Config.valid(MainActivity.this) ? "원 중앙: (" + p.getInt("x", 0) + ", " + p.getInt("y", 0) + ") px" : "원 중앙을 지정해주세요. 화면 방향이 바뀌면 다시 지정해야 합니다.");
            start.setEnabled(!CaptureService.isRunning() && !requesting);
            select.setEnabled(!CaptureService.isRunning());
            radius.setEnabled(!CaptureService.isRunning()); hueMin.setEnabled(!CaptureService.isRunning()); hueMax.setEnabled(!CaptureService.isRunning());
            handler.postDelayed(this, 500);
        }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        DiagnosticLog.init(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 24, 28, 24);
        root.setBackgroundColor(Color.rgb(246, 249, 249));
        scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            var b = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(b.left, b.top, b.right, b.bottom); return insets;
        });
        setContentView(scroll);
        text(root, "20 받기", 32);
        text(root, "보라색이 나타나는 순간, 지정한 원 중앙을 터치합니다.", 17);
        status = text(root, "중지됨", 15);
        text(root, "1. 접근성 허용", 21);
        button(root, "접근성 권한 안내 / 설정", this::accessibilityGuide);
        text(root, "2. 고정 위치 지정", 21);
        location = text(root, "", 15);
        select = button(root, "화면에서 원 중앙 지정", () -> {
            if (TapService.get() == null) { accessibilityGuide(); return; }
            TapService.get().showPickerBar();
            moveTaskToBack(true);
        });
        text(root, "대상 앱으로 이동한 뒤 떠 있는 ‘원 중앙 지정’을 누르고 원의 가운데를 터치하세요.", 14);
        radius = field(root, "감지 반경 (물리 픽셀, 8~150)", Config.prefs(this).getInt("radius", 32));
        hueMin = field(root, "보라색 Hue 최솟값 (0~359)", Config.prefs(this).getInt("hueMin", 260));
        hueMax = field(root, "보라색 Hue 최댓값 (1~360)", Config.prefs(this).getInt("hueMax", 315));
        text(root, "흰색 숫자 주변의 보라색이 포함되도록 반경을 정하세요. 기본 범위는 260~315°입니다.", 14);
        text(root, "3. 화면 공유 후 감지 시작", 21);
        start = button(root, "시작 · 화면 공유 허용", this::requestStart);
        button(root, "중지", () -> CaptureService.stopNow(this));
        text(root, "전체 화면 공유를 허용하고 대상 앱에서 떠 있는 ‘감지 시작’을 누르세요. ↕ 버튼은 제어 막대를 위/아래로 옮깁니다. 원을 가리지 않게 배치하세요.", 14);
        text(root, "4. 기기 비교용 진단 로그", 21);
        text(root, "실행 후 로그를 공유해주세요. 모델·해상도·감지 비율·처리 시간·제스처 완료/취소를 기록합니다. 새 화면 공유를 시작하면 이전 로그가 교체됩니다.", 14);
        button(root, "진단 로그 보기", this::showLog);
        button(root, "진단 로그 공유", () -> {
            Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "20 받기 진단 로그")
                    .putExtra(Intent.EXTRA_TEXT, DiagnosticLog.snapshot());
            startActivity(Intent.createChooser(share, "진단 로그 공유"));
        });
        button(root, "100~200ms 동작 시험 화면", () -> startActivity(new Intent(this, DemoActivity.class)));
        text(root, "OCR·루팅 없이 색상만 감지합니다. 화면은 메모리에서만 처리하고 저장·전송하지 않습니다. 연속 보라색에는 한 번만 터치하며 사라진 뒤 재등장하면 다시 터치합니다. 실제 반응 속도는 기기와 화면 캡처 속도에 따라 달라집니다.", 14);
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), PackageManager.PackageInfoFlags.of(0)).versionName;
            text(root, "Made by Hwarang · © 2026 · v" + version, 12).setGravity(Gravity.CENTER);
        } catch (PackageManager.NameNotFoundException ignored) {
            text(root, "Made by Hwarang · © 2026", 12).setGravity(Gravity.CENTER);
        }
    }
    private void showLog() {
        String log = DiagnosticLog.snapshot();
        TextView content = new TextView(this);
        content.setText(log); content.setTextSize(11); content.setTextIsSelectable(true); content.setPadding(24, 16, 24, 16);
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        new AlertDialog.Builder(this).setTitle("진단 로그").setView(scroll)
                .setPositiveButton("복사", (dialog, which) -> {
                    getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("20 받기 진단 로그", log));
                    Toast.makeText(this, "진단 로그를 복사했습니다", Toast.LENGTH_SHORT).show();
                }).setNegativeButton("닫기", null).show();
    }
    private TextView text(LinearLayout root, String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size);
        t.setTextColor(Color.rgb(22, 48, 46)); t.setPadding(0, 14, 0, 12); root.addView(t); return t;
    }
    private Button button(LinearLayout root, String value, Runnable action) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setOnClickListener(v -> action.run()); root.addView(b); return b;
    }
    private EditText field(LinearLayout root, String label, int value) {
        TextView title = text(root, label, 14);
        EditText e = new EditText(this); e.setId(View.generateViewId()); title.setLabelFor(e.getId());
        e.setInputType(InputType.TYPE_CLASS_NUMBER); e.setSingleLine(); e.setText(String.valueOf(value)); root.addView(e); return e;
    }
    private void accessibilityGuide() {
        new AlertDialog.Builder(this).setTitle("접근성 권한 안내")
                .setMessage("‘설치된 앱’ 또는 ‘다운로드한 앱’에서 ‘20 받기’를 켜세요.\n\n이 권한은 사용자가 정한 위치를 자동 터치하고 제어 버튼을 띄우는 데 사용합니다. 화면 텍스트·입력 내용은 읽지 않습니다.\n\nAPK 설치 후 ‘제한된 설정’이 표시되면 시스템 설정 → 앱 → 20 받기 → 우측 상단 메뉴에서 ‘제한된 설정 허용’ 후 다시 켜세요. 메뉴 명칭은 기기에 따라 다릅니다.")
                .setPositiveButton("접근성 설정 열기", (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("닫기", null).show();
    }
    private boolean saveSettings() {
        try {
            int r = Integer.parseInt(radius.getText().toString());
            int lo = Integer.parseInt(hueMin.getText().toString()), hi = Integer.parseInt(hueMax.getText().toString());
            if (r < 8 || r > 150 || lo < 0 || hi > 360 || lo >= hi) throw new NumberFormatException();
            Config.prefs(this).edit().putInt("radius", r).putInt("hueMin", lo).putInt("hueMax", hi).apply();
            return true;
        } catch (NumberFormatException e) { Toast.makeText(this, "반경과 Hue 범위를 확인하세요", Toast.LENGTH_LONG).show(); return false; }
    }
    private void requestStart() {
        if (requesting || CaptureService.isRunning()) return;
        if (TapService.get() == null) { accessibilityGuide(); return; }
        if (!Config.valid(this)) { Toast.makeText(this, "원 중앙을 먼저 지정하세요", Toast.LENGTH_LONG).show(); return; }
        if (!saveSettings()) return;
        requesting = true;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        else captureConsent();
    }
    private void captureConsent() {
        MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
        startActivityForResult(manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()), 20);
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 11) captureConsent();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 20) return;
        requesting = false;
        if (result == RESULT_OK && data != null && TapService.get() != null && Config.valid(this)) {
            startForegroundService(new Intent(this, CaptureService.class).putExtra("data", data));
            moveTaskToBack(true);
        }
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
