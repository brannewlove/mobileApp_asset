# 자산 관리 시스템 (Asset Manager) 운영 및 개발 매뉴얼

이 문서는 시스템의 개발 환경 설정, 안드로이드 빌드 및 설치, 그리고 소스코드 업데이트 방법을 설명합니다.

---

## 1. 개발 환경 설정 (Environment Setup)

다른 환경에서 테스트하거나 개발을 시작하려면 다음 소프트웨어가 필요합니다.

- **Node.js & NPM**: [공식 홈페이지](https://nodejs.org/)에서 LTS 버전을 설치합니다.
- **Java (JDK 17)**: 안드로이드 빌드를 위해 필요합니다. (OpenJDK 17 권장)
- **Android Studio**: 안드로이드 앱 빌드 및 에뮬레이터 실행을 위해 설치합니다.
- **Git**: 소스코드 버전 관리를 위해 필요합니다.

### 초기 설정 명령어
```bash
# 의존성 패키지 설치
npm install

# Capacitor 안드로이드 플랫폼 추가 (필요 시)
npx cap add android
```

---

## 2. 구글 인증 설정 (Google Auth)

로그인을 위해 구글 클라우드 콘솔 설정이 정확해야 합니다.

- **구글 클라우드 콘솔 설정**:
  - **패키지 이름**: `com.antigravity.assetmanager`
  - **클라이언트 ID**: `876684580795-l4nj5d5k5uh111j7oc1a1seb7877mtg6.apps.googleusercontent.com`
  - **웹 어플리케이션**: 안드로이드에서 사용하더라도 `webClientId` 형식으로 사용됩니다.
- **구글 서비스 설정 파일 (google-services.json)**:
  - **주의**: Google Cloud의 '사용자 인증 정보'에서 다운로드한 `client_secret_...json`은 **잘못된 파일**입니다.
  - **정확한 파일**: Firebase Console 또는 Google Cloud의 '설정' 메뉴에서 안드로이드 앱용으로 제공하는 **`google-services.json`**을 받아야 합니다.
  - **임시 해결**: 만약 이 파일이 없거나 잘못되었다면, 파일을 삭제하면 빌드는 성공합니다. (단, 구글 서비스 일부 기능이 제한될 수 있습니다.)
  - 경로: `android/app/google-services.json`

- **안드로이드 네이티브 코드 수정 (필수)**:
  - 구글 드라이브 권한(Scopes)을 사용하려면 `MainActivity.java` 수정이 필요합니다.
  - 경로: `android/app/src/main/java/com/antigravity/assetmanager/MainActivity.java`
  - 내용: `ModifiedMainActivityForSocialLoginPlugin` 인터페이스 구현 및 `onActivityResult` 오버라이드 (이미 자동 수정됨)

- **안드로이드 SHA-1**: 구글 콘솔 등록을 위해 필요합니다.
  - **방법 1 (터미널)**: 안드로이드 스튜디오 터미널에서 `./gradlew signingReport` 실행
  - **방법 2 (UI)**: `Gradle > App > Tasks > android > signingReport` 실행 (안 보일 경우 Settings > Experimental > 'Do not build Gradle task list...' 해제 필요)
  - 확인한 SHA-1 값을 구글 콘솔의 안드로이드 클라이언트 ID 항목에 등록해야 상용 빌드에서 로그인이 작동합니다.

---

## 3. 안드로이드 빌드 및 설치 가이드

### 단계별 절차
1. **웹 빌드**: 최신 웹 소스를 생성합니다.
   ```bash
   npm run build
   ```
2. **Capacitor 동기화**: 빌드된 소스를 안드로이드 프로젝트로 복사합니다.
   ```bash
   npx cap sync
   ```
3. **안드로이드 스튜디오 실행**:
   ```bash
   npx cap open android
   ```
4. **설치 및 실행**:
   - 기기를 연결하거나 에뮬레이터를 선택합니다.
   - 상단 **'Run' (초록색 재생 아이콘)** 버튼을 클릭하여 앱을 설치합니다.
   - APK 추출이 필요한 경우: `Build > Build Bundle(s) / APK(s) > Build APK(s)`

---

## 4. 깃허브(GitHub) 업데이트 및 동기화

최신 소스코드를 유지하고 반영하는 방법입니다.

### 최신 소스 가져오기
```bash
# 원격 저장소에서 최신 내용 받기
git pull origin main

# 변경된 패키지 반영
npm install

# 앱 소스 동기화
npx cap sync
```

### 변경 사항 업로드 (개발자 기준)
```bash
git add .
git commit -m "업데이트 내용 요약"
git push origin main
```

---

## 5. 테스트 체크리스트

새 환경에서 다음 항목이 정상 동작하는지 확인하십시오.

1. **로그인**: 구글 계정으로 로그인이 되며, 메인 화면으로 전환되는가?
2. **데이터 로드**: 구글 시트에서 자산 및 사용자 정보를 정상적으로 불러오는가?
3. **바코드 스캔**: 바코드를 입력(또는 스캔)했을 때 목록에 즉시 추가되는가?
4. **저장 및 백업**: "내역 저장 및 백업" 버튼 클릭 시 구글 드라이브에 파일이 생성되는가?
5. **추적(Trade)**: 추적 탭에서 해당 자산의 이력이 올바르게 표시되는가?

---

> [!TIP]
> **로그인이 안 될 때**: `capacitor.config.json`의 `webClientId`가 구글 콘솔의 값과 일치하는지, 그리고 현재 접속 중인 도메인(웹) 또는 앱의 서명(SHA-1)이 구글 콘솔에 등록되어 있는지 확인하세요.
