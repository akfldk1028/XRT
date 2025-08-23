# 🚀 Android XR A2A Agent System - 완전 설정 가이드

## 📋 목차
1. [가상환경 설정](#-가상환경-설정)
2. [에이전트 시스템 실행](#-에이전트-시스템-실행)
3. [Android XR 앱 연동](#-android-xr-앱-연동)
4. [트러블슈팅](#-트러블슈팅)
5. [에이전트 테스트](#-에이전트-테스트)

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

## 🔧 트러블슈팅

### ❌ **포트 충돌 해결**
```bash
# 사용 중인 포트 확인
netstat -ano | findstr ":803"

# 프로세스 종료
powershell -Command "Stop-Process -Id [PID] -Force -ErrorAction SilentlyContinue"
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

## 🧪 에이전트 테스트

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