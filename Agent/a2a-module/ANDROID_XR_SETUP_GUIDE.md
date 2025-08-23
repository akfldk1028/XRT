# 🚀 Android XR A2A Agent System - 완전 설정 가이드

## 📋 목차
1. [가상환경 설정](#-가상환경-설정)
2. [에이전트 시스템 실행](#-에이전트-시스템-실행)
3. [Android XR 앱 연동](#-android-xr-앱-연동)
4. [AR Glass 앱 아키텍처](#-ar-glass-앱-아키텍처)
5. [트러블슈팅](#-트러블슈팅)

---

## 🐍 가상환경 설정

### ⚠️ **중요: 반드시 가상환경을 사용하세요!**
시스템 Python 오염 방지를 위해 **매번 가상환경을 활성화**해야 합니다.

```bash
# 1. 프로젝트 폴더로 이동
cd "D:\Data\05_CGXR\Android\XRTEST\Agent\a2a-module"

# 2. 가상환경 생성 (한 번만)
python -m venv venv

# 3. 가상환경 활성화 (매번 필요)
# Windows PowerShell:
.\venv\Scripts\Activate.ps1

# Windows CMD:
venv\Scripts\activate.bat

# 4. 가상환경 확인
python -c "import sys; print('Virtual env active:', 'venv' in sys.executable)"
# 출력: Virtual env active: True

# 5. 패키지 설치 (한 번만)
pip install -r requirements.txt
```

### 🔧 가상환경 직접 실행 (권장)
```bash
# 가상환경 Python 직접 사용
"D:\Data\05_CGXR\Android\XRTEST\Agent\a2a-module\venv\Scripts\python.exe" your_script.py
```

---

## 🤖 에이전트 시스템 실행

### 🎯 Android XR 전용 4개 에이전트

#### 1. **Perception Agent** (포트 8030)
- **역할**: Camera2 API, 십자가 타겟팅, ROI 추출
- **실행**: 
```bash
cd agents/claude_cli/perception
../../../venv/Scripts/python.exe server.py
```

#### 2. **Vision Agent** (포트 8031)
- **역할**: GPT-4V Realtime API, 객체 분석
- **실행**: 
```bash
cd agents/claude_cli/vision  
../../../venv/Scripts/python.exe server.py
```

#### 3. **UX/TTS Agent** (포트 8032)
- **역할**: 십자가 UI, TTS 음성 응답
- **실행**: 
```bash
cd agents/claude_cli/ux_tts
../../../venv/Scripts/python.exe server.py
```

#### 4. **Logger Agent** (포트 8033)
- **역할**: 성능 모니터링, 로깅
- **실행**: 
```bash
cd agents/claude_cli/logger
../../../venv/Scripts/python.exe server.py
```

### 🚀 전체 에이전트 일괄 실행
```bash
# 가상환경 활성화 후
venv/Scripts/python.exe start_all_agents.py
```

### ✅ 에이전트 상태 확인
```bash
# 에이전트 카드 확인
curl http://localhost:8030/.well-known/agent.json  # Perception
curl http://localhost:8031/.well-known/agent.json  # Vision
curl http://localhost:8032/.well-known/agent.json  # UX/TTS
curl http://localhost:8033/.well-known/agent.json  # Logger
```

---

## 📱 Android XR 앱 연동

### 🔗 Kotlin에서 A2A 에이전트 호출

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
}

// A2A API 인터페이스
interface A2AApiService {
    @POST("/")
    suspend fun sendMessage(@Body request: A2ARequest): Response<A2AResponse>
}

// 사용 예시
class CameraProcessor {
    private val perceptionAgent = Retrofit.Builder()
        .baseUrl("http://localhost:8030/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(A2AApiService::class.java)

    suspend fun processFrame(imageData: ByteArray) {
        val request = A2ARequest(
            jsonrpc = "2.0",
            id = "camera_frame_${System.currentTimeMillis()}",
            method = "message/send",
            params = A2AParams(
                message = A2AMessage(
                    role = "user",
                    parts = listOf(
                        A2APart(
                            kind = "text",
                            text = "Extract ROI from crosshair center",
                            mimeType = "text/plain"
                        )
                    )
                )
            )
        )
        
        val response = perceptionAgent.sendMessage(request)
        // 처리된 ROI 데이터 사용
    }
}
```

---

## 🥽 AR Glass 앱 아키텍처

### 🎯 **앱 플로우**
```
1. 카메라 권한 요청 → Camera2 API 초기화
2. 화면 중앙에 십자가 표시 (AR Overlay)
3. 사용자가 객체에 십자가를 맞춤
4. 음성으로 질문 ("이게 뭐야?", "어떻게 사용해?")
5. Perception Agent → ROI 추출
6. Vision Agent → GPT-4V 분석
7. UX/TTS Agent → 음성으로 답변
```

### 🏗️ **주요 컴포넌트**

#### 1. **MainActivity.kt** 수정사항
```kotlin
// 카메라 권한 추가
private val cameraPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { isGranted ->
    if (isGranted) {
        initializeCamera()
    }
}

// 십자가 오버레이 추가
@Composable
fun CrosshairOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        // 기존 XR 콘텐츠
        MainContent()
        
        // 십자가 중앙 표시
        Icon(
            painter = painterResource(R.drawable.ic_crosshair),
            contentDescription = "Target Crosshair",
            modifier = Modifier
                .align(Alignment.Center)
                .size(32.dp),
            tint = Color.Red
        )
    }
}
```

#### 2. **Camera2 Integration**
```kotlin
class CameraManager(private val context: Context) {
    private lateinit var cameraDevice: CameraDevice
    private lateinit var imageReader: ImageReader
    
    fun initCamera() {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        // Camera2 API 설정
    }
    
    private val imageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage()
        // A2A Perception Agent로 전송
        sendToPerceptionAgent(image)
    }
}
```

#### 3. **Voice Interaction**
```kotlin
class VoiceProcessor {
    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    
    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                       RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        speechRecognizer.startListening(intent)
    }
}
```

### 📊 **데이터 플로우**
```
카메라 프레임 → Perception Agent (ROI 추출) 
               ↓
음성 질문 → Vision Agent (GPT-4V 분석)
               ↓  
GPT 응답 → UX/TTS Agent (음성 합성)
               ↓
스피커 출력 ← Logger Agent (성능 모니터링)
```

---

## 🔧 트러블슈팅

### ❌ **포트 충돌 해결**
```bash
# 사용 중인 포트 확인
netstat -ano | findstr :8030

# 프로세스 종료
taskkill /F /PID [PID번호]
```

### ❌ **가상환경 인식 안 됨**
```bash
# 가상환경 재생성
rmdir /s venv
python -m venv venv
venv\Scripts\activate.bat
pip install -r requirements.txt
```

### ❌ **인코딩 에러 (한글/이모지)**
```bash
# Windows에서 UTF-8 설정
set PYTHONIOENCODING=utf-8
chcp 65001
venv\Scripts\python.exe your_script.py

# 또는 환경변수 설정
set PYTHONHASHSEED=0
set LANG=ko_KR.UTF-8
```

### ❌ **Claude CLI 미설치**
```bash
# Claude CLI 설치 필요
npm install -g @anthropics/claude-cli
# 또는
pip install claude-cli
```

### ⏱️ **Agent 응답 타임아웃**
```bash
# A2A 에이전트는 Claude CLI를 호출하므로 응답에 시간이 걸립니다
# 정상적인 타임아웃 시간:
# - 단순 요청: 30초-1분
# - 복잡한 코드 생성: 2-5분
# - 대용량 이미지 처리: 5-10분

# 타임아웃 중에는 기다려주세요!
# "timeout during complex processing is normal" - 정상 동작입니다
```

---

## 🧪 에이전트 테스트 방법

### 📋 **완전 테스트 절차**
```bash
# 1. 기존 프로세스 정리
netstat -ano | findstr ":803"
powershell -Command "Stop-Process -Id [PID] -Force -ErrorAction SilentlyContinue"

# 2. 가상환경 확인
cd "D:\Data\05_CGXR\Android\XRTEST\Agent\a2a-module"
.\venv\Scripts\python.exe -c "import sys; print('Virtual env:', 'venv' in sys.executable)"

# 3. 개별 에이전트 시작 (각각 별도 터미널)
cd agents\claude_cli\perception && ..\..\..\venv\Scripts\python.exe server.py
cd agents\claude_cli\vision && ..\..\..\venv\Scripts\python.exe server.py 
cd agents\claude_cli\ux_tts && ..\..\..\venv\Scripts\python.exe server.py
cd agents\claude_cli\logger && ..\..\..\venv\Scripts\python.exe server.py

# 4. 에이전트 카드 확인
curl http://localhost:8030/.well-known/agent.json  # Perception
curl http://localhost:8031/.well-known/agent.json  # Vision
curl http://localhost:8032/.well-known/agent.json  # UX/TTS
curl http://localhost:8033/.well-known/agent.json  # Logger
```

### ⚠️ **중요 주의사항**
- **타임아웃 정상**: Agent가 Claude CLI 호출 시 5-10분 소요 가능
- **포트 순서**: 반드시 8030 → 8031 → 8032 → 8033 순서로 시작
- **인코딩 설정**: 한글/이모지 오류 시 `chcp 65001` 실행
- **가상환경 필수**: 매번 `venv\Scripts\python.exe` 사용

---

## 🎯 **다음 단계**

1. **Android XR 프로젝트에서 Camera2 권한 추가**
2. **십자가 오버레이 UI 구현**
3. **A2A 통신 코드 추가**
4. **음성 인식/합성 연동**
5. **GPT-4V Realtime API 키 설정**

---

## 📞 **지원**

- **포트**: Perception(8030), Vision(8031), UX/TTS(8032), Logger(8033)
- **프로토콜**: HTTP JSON-RPC 2.0
- **실행 전 확인사항**: 
  - ✅ 가상환경 활성화 (`venv` 폴더 확인)
  - ✅ Claude CLI 설치 및 인증
  - ✅ 포트 충돌 없음 (8030-8033)
  - ✅ 인코딩 설정 (UTF-8)
  - ✅ 타임아웃 대기 준비 (최대 10분)
  - ✅ GPT API 키 설정

**🚨 중요: 매번 작업 시 가상환경을 먼저 활성화하세요!**