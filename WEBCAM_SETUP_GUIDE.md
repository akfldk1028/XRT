# 🔧 Android Emulator Webcam Setup Guide

## 🚨 현재 문제
- 앱에서 카메라가 인식됨 (ID 1, 10)
- 하지만 흰색 화면만 표시 (실제 웹캠 피드 없음)
- 에뮬레이터가 호스트 PC 웹캠과 연결되지 않음

## ✅ 해결 방법

### 방법 1: AVD Manager 설정 (권장)
1. **에뮬레이터 완전 종료**
2. **Android Studio → Tools → AVD Manager**
3. **해당 AVD의 연필 아이콘(Edit) 클릭**
4. **"Show Advanced Settings" 클릭**
5. **Camera 섹션에서:**
   - Front Camera: `Webcam0` 선택
   - Back Camera: `Webcam0` 선택
6. **Finish 클릭**
7. **"Cold Boot Now" 선택** (Quick Boot 아님!)

### 방법 2: 명령줄로 실행
```bash
# AVD 목록 확인
emulator -list-avds

# 웹캠 옵션과 함께 실행
emulator -avd Pixel_XL_API_34 -camera-back webcam0 -camera-front webcam0
```

### 방법 3: Windows 권한 설정
1. **Windows 설정 → 개인 정보 → 카메라**
2. **"앱이 카메라에 액세스하도록 허용" → 켜기**
3. **"데스크톱 앱이 카메라에 액세스하도록 허용" → 켜기**
4. **Android Studio가 목록에 있는지 확인**

### 방법 4: 호스트 웹캠 확인
**Windows:**
```cmd
# 카메라 앱 실행
start microsoft.windows.camera:
```

**Mac:**
```bash
# Photo Booth 실행
open -a "Photo Booth"
```

**Linux:**
```bash
# Cheese 또는 guvcview 실행
cheese
# 또는
guvcview
```

## 🎯 테스트 방법
1. **에뮬레이터에서 기본 카메라 앱 실행**
2. **웹캠 피드가 보이는지 확인**
   - ✅ 보임 → 앱 코드 문제
   - ❌ 안보임 → AVD 설정 문제

## 🔄 대안: 테스트 패턴 사용

웹캠이 작동하지 않을 때 앱에서 테스트 패턴 활성화:

```kotlin
// MainActivity.kt 또는 Camera2Manager 사용 코드에서
camera2Manager.useTestPattern(true)
```

이렇게 하면:
- 실제 웹캠 대신 시뮬레이션된 이미지 생성
- AR Glass UI 요소 포함
- 개발/테스트 계속 가능

## 📱 추가 해결책

### Genymotion 사용 (더 나은 카메라 지원)
1. [Genymotion](https://www.genymotion.com/) 다운로드
2. 가상 디바이스 생성
3. 웹캠 자동 연동

### 실제 디바이스 사용
1. USB 디버깅 활성화
2. Android Studio에서 실제 디바이스 선택
3. 실제 카메라 하드웨어 사용

## 🐛 일반적인 문제

| 증상 | 원인 | 해결책 |
|------|------|--------|
| 흰색 화면 | 웹캠 미연결 | AVD 설정 확인 |
| 검은 화면 | 권한 거부 또는 사용 중 | Windows 권한 확인 |
| 카메라 없음 | AVD 설정 오류 | Cold Boot으로 재시작 |
| 앱 충돌 | 잘못된 카메라 ID | 로그 확인, ID 수정 |

## 📝 빠른 체크리스트
- [ ] 호스트 PC 웹캠 작동 확인
- [ ] AVD에서 Front/Back Camera를 Webcam0으로 설정
- [ ] Cold Boot으로 에뮬레이터 재시작
- [ ] Windows 카메라 권한 확인
- [ ] 에뮬레이터 카메라 앱에서 테스트
- [ ] 안되면 테스트 패턴 모드 사용

## 💡 Pro Tips
1. **항상 Cold Boot 사용**: Quick Boot은 카메라 설정을 제대로 반영하지 않을 수 있음
2. **에뮬레이터 재시작**: 설정 변경 후 반드시 재시작
3. **로그 확인**: Logcat에서 "Camera2Manager" 태그로 필터링
4. **테스트 우선**: 실제 웹캠보다 테스트 패턴으로 먼저 개발

---
*문제가 지속되면 실제 디바이스 사용을 권장합니다*