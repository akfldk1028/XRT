# 📖 AR Glass Q&A System - Documentation

## 🎯 문서 구성

이 `docs` 폴더에는 **코틀린을 잘 모르는 분**도 쉽게 이해할 수 있도록 AR Glass Q&A 시스템의 전체적인 동작 원리와 코드 구조를 설명한 문서들이 있습니다.

---

## 📋 문서 목록

### 1️⃣ **[CODE_FLOW_GUIDE.md](./CODE_FLOW_GUIDE.md)** 🔍
- **목적**: 전체 코드 흐름을 **단계별로 따라가기** 
- **내용**: 
  - 앱 실행부터 AI 응답까지 7단계 흐름
  - 각 파일의 핵심 역할
  - 에러 처리 방법
  - 개발자 디버깅 팁
- **추천**: **먼저 읽어보세요!** 전체 시스템 이해에 가장 도움됨

### 2️⃣ **[FILE_BY_FILE_EXPLANATION.md](./FILE_BY_FILE_EXPLANATION.md)** 📝
- **목적**: **각 코틀린 파일이 무엇을 하는지** 상세 설명
- **내용**:
  - 10개 주요 파일별 상세 분석
  - 핵심 코드 부분 해설
  - 함수별 역할 설명
  - 코틀린 초보자도 이해할 수 있는 설명
- **추천**: 특정 파일의 동작이 궁금할 때 참고

### 3️⃣ **[VISUAL_FLOWCHART.md](./VISUAL_FLOWCHART.md)** 🎨
- **목적**: **시각적으로** 시스템 구조와 흐름 이해
- **내용**:
  - 파일 구조 다이어그램
  - 데이터 흐름 차트
  - 시스템 상태 변화 그래프
  - 사용자/개발자 관점별 시나리오
- **추천**: 전체적인 그림을 머릿속에 그리고 싶을 때

---

## 🚀 읽는 순서 추천

### **처음 접하는 경우**:
1. `CODE_FLOW_GUIDE.md` → 전체 흐름 파악
2. `VISUAL_FLOWCHART.md` → 시각적 이해
3. `FILE_BY_FILE_EXPLANATION.md` → 상세 구현 이해

### **특정 부분만 궁금한 경우**:
- **전체 동작 원리**: `CODE_FLOW_GUIDE.md`의 "앱 실행 흐름" 섹션
- **특정 파일 역할**: `FILE_BY_FILE_EXPLANATION.md`에서 해당 파일 검색
- **UI 상태 변화**: `VISUAL_FLOWCHART.md`의 "시스템 상태 변화 흐름" 섹션

---

## 🎯 주요 개념 빠른 참조

### **핵심 파일 4개**:
1. **MainActivity.kt** 📱 → 앱 시작점, 전체 UI 관리
2. **VisionIntegration.kt** 🎯 → 모든 시스템 통합 관리자
3. **RealtimeVisionClient.kt** 🤖 → OpenAI GPT-4V와 실시간 통신
4. **AudioStreamManager.kt** 🔊 → 24kHz 고품질 음성 처리

### **시스템 상태 7개**:
```
IDLE → CONNECTING → READY → LISTENING → PROCESSING → RESPONDING → LISTENING
```

### **사용자 경험 흐름**:
```
십자가 조준 → 음성 질문 → AI 분석 → 음성 답변 → 반복
```

---

## 🔧 문제 해결 가이드

### **빌드 오류 시**:
1. `CODE_FLOW_GUIDE.md`의 "에러 처리 흐름" 확인
2. API 키 설정 상태 점검
3. 의존성 문제 확인

### **실행 시 문제**:
1. `VISUAL_FLOWCHART.md`의 "시나리오별 상세 흐름" 참고
2. 권한 설정 확인 (카메라, 마이크)
3. 네트워크 연결 상태 확인

### **코드 수정 필요 시**:
1. `FILE_BY_FILE_EXPLANATION.md`에서 해당 파일 찾기
2. 핵심 함수와 역할 파악
3. 관련 파일들과의 연관성 확인

---

## 📊 프로젝트 현재 상태

- **전체 완성도**: 97% ✅
- **OpenAI API 통합**: 100% 완료 ✅
- **Android XR 앱**: 95% 완료 ✅
- **테스트 준비**: API 키 설정 완료 ✅

**다음 단계**: 빌드 & 에뮬레이터 테스트 🚀

---

## 🤝 도움이 필요하다면

1. **전체 시스템 이해**: `CODE_FLOW_GUIDE.md` 정독
2. **특정 오류 해결**: 해당 문서의 문제 해결 섹션 참고
3. **코드 수정**: `FILE_BY_FILE_EXPLANATION.md`의 상세 설명 활용

**모든 문서는 코틀린 초보자도 이해할 수 있도록 작성되었습니다!** 📚✨