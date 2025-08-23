# 📚 Android XR Glass Q&A 시스템 - 완전 가이드

## 🎯 **시스템 개요**
Google Glass용 AR Q&A 애플리케이션 개발을 위한 완전한 A2A 에이전트 시스템

### 🏗️ **아키텍처**
```
Android XR Glass App ←→ A2A Agent System ←→ GPT-4V Realtime API
    (Kotlin/Compose)      (Python/FastAPI)         (OpenAI)
```

---

## 📂 **가이드 구성**

### 🚀 **[01. 설정 가이드](01_SETUP_GUIDE.md)**
- 가상환경 설정 및 패키지 설치
- 4개 A2A 에이전트 실행 방법
- 트러블슈팅 및 테스트 절차

### 🏗️ **[02. 앱 아키텍처](02_APP_ARCHITECTURE.md)**
- Google Glass AR 앱 전체 구조
- Camera2 API, 십자가 UI, 음성 처리
- A2A 통신 레이어 구현

### 🔧 **[03. 구현 가이드](03_IMPLEMENTATION_GUIDE.md)**
- MainActivity.kt 수정사항
- 권한 처리, UI 컴포넌트
- Kotlin 코드 예시

### 🧪 **[04. 테스트 절차](04_TESTING_GUIDE.md)**
- 에이전트별 테스트 방법
- 통합 테스트 시나리오
- 성능 최적화 가이드

---

## ⚡ **빠른 시작**

```bash
# 1. 가상환경 활성화 (필수!)
cd "D:\Data\05_CGXR\Android\XRTEST\Agent\a2a-module"
.\venv\Scripts\python.exe -c "import sys; print('Virtual env:', 'venv' in sys.executable)"

# 2. 에이전트 시작
cd agents\claude_cli\perception && ..\..\..\venv\Scripts\python.exe server.py

# 3. Android Studio에서 XR 앱 실행
```

---

## 🎯 **최종 목표**
사용자가 AR Glass로 실세계 객체를 십자가에 맞추고 음성으로 질문하면, GPT-4V가 분석하여 TTS로 답변하는 완전한 시스템

**🚨 중요: 모든 작업 전 가상환경 활성화 필수!**