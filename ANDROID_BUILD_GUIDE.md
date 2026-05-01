# 안드로이드 스튜디오 빌드 가이드

이 문서는 **Asset Manager** 안드로이드 앱을 안드로이드 스튜디오(Android Studio)에서 빌드하고 실행하는 방법을 설명합니다.

## 1. 사전 준비 사항
*   **Android Studio 설치**: [최신 버전](https://developer.android.com/studio)이 설치되어 있어야 합니다. (Koala 버전 이상 권장)
*   **JDK 21**: 이 프로젝트는 Java 21을 사용합니다. 안드로이드 스튜디오 내부 설정을 통해 설치하거나 별도로 설치해야 합니다.

## 2. 프로젝트 열기
1.  안드로이드 스튜디오를 실행합니다.
2.  **Open**을 클릭하고 프로젝트 루트 폴더 내의 `android` 폴더를 선택합니다.
    *   경로 예시: `.../asset-manager-android/android`
3.  프로젝트가 로드될 때까지 기다립니다.

## 3. JDK 설정 (중요)
Gradle 빌드 오류(`invalid source release: 21`)를 방지하기 위해 JDK 버전을 확인해야 합니다.
1.  **File > Settings** (macOS: **Android Studio > Settings**)로 이동합니다.
2.  **Build, Execution, Deployment > Build Tools > Gradle** 섹션으로 이동합니다.
3.  **Gradle JDK** 항목을 확인합니다.
    *   목록에 `jbr-21` 또는 설치된 `JDK 21`이 없다면 **Download JDK**를 눌러 **21** 버전을 다운로드하여 선택합니다.
4.  **Apply** 후 **OK**를 클릭합니다.

## 4. Gradle Sync 및 빌드
1.  상단 툴바의 **Elephant 아이콘(Sync Project with Gradle Files)**을 클릭하여 동기화합니다.
2.  에러가 없다면 상단 메뉴의 **Build > Build Bundle(s) / APK(s) > Build APK(s)**를 클릭하여 APK를 생성할 수 있습니다.
3.  실 기기 또는 에뮬레이터에서 실행하려면 **Run (녹색 재생 버튼)**을 클릭합니다.

## 5. 구글 로그인 문제 해결 (에러 코드 16/10 등)
로그인이 안 되는 경우 아래 단계를 거쳐야 합니다.

### SHA-1 지문 확인
안드로이드 스튜디오 우측의 **Gradle** 탭을 엽니다.
1.  `app > Tasks > android > signingReport`를 더블 클릭합니다.
2.  하단 **Run** 창에 표시되는 `SHA1` 지문을 복사합니다.

### 구글 클라우드 콘솔 등록
1.  [Google Cloud Console 인증 정보](https://console.cloud.google.com/apis/credentials)로 이동합니다.
2.  **OAuth 2.0 클라이언트 ID** 중 안드로이드 타입을 생성/수정합니다.
3.  복사한 **SHA-1 지문**과 패키지 명(`com.antigravity.assetmanager`)을 입력합니다.
4.  콘솔에서 **google-services.json** 파일을 다운로드하여 `android/app/` 폴더에 넣습니다.

## 6. 기타 팁
*   웹 코드(`src` 폴더)를 수정한 경우, 터미널에서 `npm run build` 후 `npx cap sync android`를 실행해야 안드로이드 프로젝트에 반영됩니다.
