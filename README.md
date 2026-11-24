# TeleNoti (v2.4.0)

안드로이드 기기(특히 **Samsung Galaxy S21 / One UI 7 / Android 15** 기준 최적화)의 앱 알림과 문자(SMS/LMS/MMS), 알림 이미지, **수신 전화 즉시 알림**을 **텔레그램 봇**으로 실시간 전달하고, 텔레그램에서 바로 **양방향 답장**까지 보낼 수 있는 앱입니다.

> 기존 설치 앱(`applicationId: com.example.telegramalramrelay`)과 `SharedPreferences("settings")` 설정 키 및 `ResetAtNoonReceiver` 컴포넌트를 그대로 유지하므로, **같은 서명 키(keystore)로 서명한 Release APK**를 덮어쓰기 설치하면 기존 봇 토큰, Chat ID, 감시 앱 목록이 유지됩니다.

---

## 주요 기능 (v2.4.0 기준)

- **수신 전화 즉시 알림 (`IncomingCallReceiver`)**
  - 부재중이 되기 전에 벨이 울리자마자(`PHONE_STATE` `RINGING`) 발신자 이름(`READ_CONTACTS`)과 전화번호(`READ_CALL_LOG`, 한국 전화번호 하이픈 포맷)를 즉시 텔레그램으로 전송
  - Android 9+ 이중 브로드캐스트 병합·중복 억제 및 오프라인 재전송 큐 연동
- **실시간 알림 포워딩 + 포그라운드 유지 (`AppKeepAliveService`)**
  - Android 15 `specialUse` 포그라운드 서비스, 부팅 자동 시작(`BootReceiver`), WorkManager 오프라인 재전송 큐(`RetryDeliveryWorker`)
  - 홈 화면 실시간 상태 히어로 카드 + 상단바 상태 알림 + 빠른 설정 타일(`TeleNotiTileService`)
- **SMS / LMS / MMS 장문 문자 및 이미지 전달**
  - `NotificationTextExtractor` + `SmsMmsEnricher`(`READ_SMS`)를 통한 긴 문자/멀티파트 텍스트 보강
  - `NotificationImageExtractor` + `TelegramApi` `sendPhoto` 멀티파트 이미지 업로드(큰 아이콘/BigPicture/알림 첨부 이미지)
- **텔레그램 양방향 답장 (`TelegramReplyPoller` / `TelegramReplyRouter`)**
  - 알림의 `RemoteInput` 액션을 추출해 텔레그램에서 **해당 전달 메시지에 스와이프 답장(Reply)** 하면 카톡/문자 등에 바로 답장 전송
  - 설정된 `chatId` 검증 후 허용된 채팅에서만 답장 실행 및 결과 피드백 회신
- **강력한 필터링 · 포맷 · 자동화**
  - 전체 설치 앱 조회(`QUERY_ALL_PACKAGES`), 시스템 앱/최근 감지 앱/패키지 직접 입력 지원
  - 키워드 포함/제외 필터, 방해 금지 시간대(Quiet Hours), 중복 알림 억제(Dedupe), 자정(`00:00`) 기준 일일 카운트 초기화
  - 추천 **"기본"** 포맷 및 앱별 개별 Chat ID / 커스텀 포맷
  - 텔레그램 `getUpdates` 기반 Chat ID 자동 조회, 다크/라이트/시스템 테마, 권한 설정 마법사(`PermissionWizardDialog`) 및 One UI 7 가이드
- **보안 강화**
  - Android Keystore AES-GCM(`SecureTokenStore`)으로 봇 토큰·Chat ID 암호화 저장 및 기존 평문 설정 자동 마이그레이션
  - 백업 비활성화(`allowBackup="false"`), 키스토어(`jks/`) 및 로컬 설정 `.gitignore` 분리

---

## GitHub Releases에 APK 올리는 방법

### 방법 1: GitHub Actions 자동 릴리즈 (권장)

[`.github/workflows/release.yml`](.github/workflows/release.yml) 워크플로우가 `v*` 태그 푸시 또는 수동 실행(`Actions -> Release APK -> Run workflow`) 시 자동으로 유닛 테스트를 실행하고 APK + `SHA256SUMS.txt`를 GitHub Releases에 업로드합니다.

#### 1) 기존 서명 키(keystore)를 GitHub Secrets에 등록하기 (기존 S21 앱 업데이트 유지용)

기존에 설치된 앱을 데이터·토큰 초기화 없이 업데이트하려면 로컬 `jks/dddd` 키스토어 정보를 GitHub 저장소의 **Settings -> Secrets and variables -> Actions -> New repository secret**에 등록하세요.

먼저 PowerShell에서 로컬 키스토어 파일을 Base64 한 줄 문자열로 변환합니다:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("jks\dddd")) | Set-Clipboard
```

그다음 아래 **4개의 Repository Secrets**를 등록합니다:

| Secret 이름 | 설명 |
| --- | --- |
| `KEYSTORE_BASE64` | 위 PowerShell 명령으로 클립보드에 복사한 Base64 문자열 |
| `KEYSTORE_PASSWORD` | 키스토어(`jks/dddd`) 비밀번호 |
| `KEY_ALIAS` | 키스토어 내 인증서 alias |
| `KEY_PASSWORD` | 키 비밀번호 |

> **4개 Secrets를 모두 등록하면**: 서명된 `TeleNoti-<태그>.apk`가 생성되어 릴리즈에 올라갑니다.  
> **Secrets를 등록하지 않으면**: 폴백 모드로 `TeleNoti-<태그>-debug.apk`와 `TeleNoti-<태그>-release-unsigned.apk`가 올라갑니다. (주의: debug APK는 기존 release 키로 설치된 S21 앱 위에 덮어쓰기 업데이트가 되지 않으므로 기존 앱 삭제 후 설치하거나 테스트용으로만 사용하세요.)

#### 2) 릴리즈 트리거하기 (둘 중 하나 선택)

- **태그 푸시로 자동 생성**:
  ```bash
  git add .
  git commit -m "Release v2.4.0"
  git push origin master
  git tag v2.4.0
  git push origin v2.4.0
  ```
- **GitHub 웹 UI에서 수동 실행**:
  1. GitHub 저장소 -> **Actions** 탭 -> **Release APK** 선택
  2. **Run workflow** 클릭 -> `tag_name`(기본값 `v2.4.0`) 확인 후 실행

완료되면 저장소의 **Releases** 탭에 APK 파일과 `SHA256SUMS.txt`, 자동 생성된 릴리즈 노트가 등록됩니다.

---

### 방법 2: 로컬에서 직접 빌드해 GitHub Releases에 수동 업로드

#### 1) 로컬 Release 서명 설정 (`keystore.properties` — Git에 커밋되지 않음)

프로젝트 루트에 `keystore.properties` 파일을 만들고 아래 내용을 입력합니다(`.gitignore`에 의해 Git 추적에서 제외됨):

```properties
storeFile=jks/dddd
storePassword=YOUR_KEYSTORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

또는 환경 변수(`ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`)를 지정해 빌드할 수도 있습니다.

#### 2) APK 빌드

```powershell
.\gradlew.bat testDebugUnitTest assembleRelease --no-daemon
```

서명된 APK는 다음 경로에 생성됩니다:

- `app/build/outputs/apk/release/app-release.apk`

#### 3) GitHub 웹에서 업로드

1. GitHub 저장소 오른쪽 **Releases** -> **Draft a new release**
2. **Choose a tag**에 `v2.4.0` 입력 -> 제목 및 설명 작성
3. **Attach binaries** 영역에 `app-release.apk`(또는 원하는 이름 예: `TeleNoti-v2.4.0.apk`) 드래그 앤 드롭
4. **Publish release** 클릭

---

## Galaxy S21 / One UI 7 업데이트 & 원격 보안 주의사항

1. **S21 기존 앱 업데이트 시**
   - 반드시 기존 설치 시 사용한 동일한 키스토어(`jks/dddd`)로 서명된 `app-release.apk`를 설치해야 기존 봇 토큰/Chat ID/앱 선택 목록이 그대로 유지됩니다.
   - 설치 후 **알림 접근 권한**, **문자(`READ_SMS`) 권한**(장문 MMS 보강용), **수신 전화(`READ_PHONE_STATE`/`READ_CALL_LOG`/`READ_CONTACTS`) 권한**, **배터리 최적화 제외(제한 없음)** 상태를 앱 내 권한 마법사 또는 가이드에서 한 번 점검하세요.
2. **원격 저장소 히스토리 주의사항**
   - 현재 로컬 작업 트리에서는 `jks/dddd`, `local.properties`, 빌드 산출물을 Git 추적에서 제거(`git rm --cached`)하고 `.gitignore`에 추가했습니다.
   - 만약 **기존 원격 GitHub 커밋 히스토리**에 이미 `jks/dddd`가 포함되어 있다면, `.gitignore` 추가만으로는 과거 커밋의 파일이 지워지지 않습니다. 저장소를 계속 공개로 유지할 경우 `git filter-repo` 또는 BFG Repo-Cleaner로 과거 히스토리에서 `jks/`를 제거하거나, 새 키/비공개 저장소 전환을 권장합니다. (원격 히스토리 덮어쓰기 전에는 반드시 로컬 백업을 먼저 확보하세요.)
