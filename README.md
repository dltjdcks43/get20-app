# 20 받기

Android 14 / 15 / 16(API 34~36)용 Java 앱. 루팅·OCR 없이 MediaProjection의 RGBA 프레임에서 지정한 원 주변의 보라색을 감지하고 AccessibilityService.dispatchGesture로 원 중앙을 터치합니다.

## 설치 및 실행

1. GitHub 저장소의 **Actions → Android debug APK → 성공한 실행 → get20-debug-apk** artifact를 내려받고 압축을 풀어 `app-debug.apk`를 설치합니다.
2. 앱의 **접근성 권한 안내 / 설정**에서 `20 받기` 서비스를 켭니다. 사이드로드 앱의 제한된 설정 안내가 뜨면 시스템 앱 정보에서 제한된 설정을 허용한 뒤 다시 켭니다.
3. **화면에서 원 중앙 지정**을 누르고 대상 앱으로 이동합니다. 떠 있는 **원 중앙 지정** 버튼을 누른 뒤 숫자 원의 가운데를 한 번 터치합니다. 이 터치는 위치만 저장하며 대상 앱으로 전달하지 않습니다.
4. `20 받기`로 돌아와 반경과 Hue 범위를 확인하고 **시작 · 화면 공유 허용**을 누릅니다. **전체 화면** 공유가 필요합니다. 화면 공유 동의는 매번 필요합니다.
5. 대상 앱에서 떠 있는 **감지 시작**을 누릅니다. 제어 막대가 원을 가리지 않도록 **↕** 버튼으로 위/아래 위치를 바꿀 수 있습니다.
6. 앱/제어 막대/알림의 **중지**, 또는 Android의 화면 공유 종료로 중지합니다. 접근성 비활성화 시에도 중지합니다.

화면 회전·해상도 변경 시 자동 중지합니다. 새 방향에서 위치를 다시 지정하고 시작하세요. 잠금 또는 시스템의 캡처 종료 후 자동 재시작하지 않습니다.

## 감지 방식

- `ImageReader(RGBA_8888, maxImages=3)`와 전용 `HandlerThread`.
- 각 콜백에서 `acquireLatestImage()` 한 번만 호출. 오래된 대기 프레임은 버리고, 모든 획득 이미지를 `try-with-resources`로 닫습니다.
- Surface 캡처의 monotonic timestamp 기준 80ms보다 오래되었거나, 감지 시작 전/이미 처리한 프레임은 버립니다.
- 비트맵 복사 없이 지정 중심의 디스크 영역을 2픽셀 간격으로 샘플링. 실제 `rowStride`/`pixelStride`를 사용합니다.
- 기본 HSV Hue 260~315°, saturation ≥ 0.35, value ≥ 65/255. 실제 원의 색에 맞춰 Hue 범위를 조정할 수 있습니다.
- 영역 내 보라색 비율 ≥ 18%인 **첫 프레임**에서 즉시 1ms 제스처를 요청합니다. 다중 프레임 확인이나 의도적 클릭 지연이 없습니다.
- 비율 < 8%인 프레임을 관측해야 다시 클릭할 수 있습니다. 두 임계값 사이의 흔들림은 중복 클릭을 만들지 않습니다.
- 제스처가 이미 진행 중이거나 시스템이 요청을 거절하면 클릭을 큐에 쌓거나 뒤늦게 재시도하지 않습니다.
- 통계의 ‘터치 요청’은 Android가 접수한 dispatchGesture 횟수이며 대상 앱의 성공 횟수와 다를 수 있습니다.

기본 반경은 32 **물리 픽셀**입니다. 흰 숫자만 포함되면 놓칠 수 있으므로 보라색 배경을 충분히 포함하되 이웃 UI를 포함하지 않게 조정하세요. 원이 화면에서 고정된 위치라는 전제가 필요합니다. 다른 보라색 요소가 같은 위치에 나타나도 클릭하므로 대상 화면에서만 감지를 켜세요.

## 실제 기기 시험

앱의 **100~200ms 동작 시험 화면**에서 2/3/5/10/20이 번갈아 나타납니다. 20만 보라색이며 표시 시간을 100/150/200/1000ms로 선택할 수 있습니다.

1. 시험 화면에서 **이 시험 원의 위치 저장**.
2. 뒤로 이동해 화면 공유 시작 후 앱으로 돌아와 시험 화면을 엽니다.
3. 제어 막대에서 **감지 시작**. ‘성공’, ‘오터치’, ‘중복’을 확인합니다.
4. 100ms/150ms/200ms를 각각 확인하고 1000ms로 연속 표시 중 중복 0을 확인합니다.
5. 일시정지/재개, 중지, 화면 회전, 잠금, 접근성 비활성화도 확인합니다.

실제 100~200ms 포착 성공률은 기기 부하·디스플레이/캡처 주기·제스처 처리 지연에 따라 달라지며 보장되지 않습니다. 최신 프레임만 쓰므로 프레임 사이에서 발생했다 사라진 보라색 또는 매우 짧은 비보라색 구간은 관측하지 못할 수 있습니다. 보안 화면(FLAG_SECURE)은 캡처할 수 없습니다. 멀티윈도우의 단일 앱 공유는 좌표가 다르므로 지원하지 않습니다.

## 빌드

- JDK 17, Android SDK platform 36, AGP 8.13.0, Gradle wrapper 8.13
- `ANDROID_HOME` 또는 `local.properties`의 `sdk.dir`에 Android SDK 경로 지정
- macOS/Linux: `./gradlew testDebugUnitTest lintDebug assembleDebug`
- Windows: `gradlew.bat testDebugUnitTest lintDebug assembleDebug`
- APK: `app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions는 push / pull_request / 수동 실행마다 테스트·lint·APK 빌드를 수행합니다. APK가 없으면 artifact 업로드 단계가 실패합니다. APK와 검사 보고서는 30일간 보관합니다. debug APK는 개발용 서명이며 Play 배포용 서명은 별도로 구성해야 합니다.

인터넷 권한과 네이티브 라이브러리를 사용하지 않습니다. 화면 데이터는 기기 메모리에서만 분석하며 저장하거나 전송하지 않습니다. 접근성은 제스처와 제어 오버레이에만 사용하고 윈도우 내용 조회 권한은 요청하지 않습니다.

## Android 대응 근거

- [MediaProjection 공식 안내](https://developer.android.com/media/grow/media-projection): 세션마다 사용자 동의, mediaProjection foreground service, callback 등록 후 VirtualDisplay 한 번 생성.
- [Android 15 foreground service 변경](https://developer.android.com/about/versions/15/changes/foreground-service-types): 부팅 자동 시작 없이 사용자의 화면 동의 후 시작.
- [AGP 8.13 호환성](https://developer.android.com/build/releases/agp-8-13-0-release-notes): API 36 / Gradle 8.13 / JDK 17.
