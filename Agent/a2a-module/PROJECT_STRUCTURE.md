# 🏗️ Android XR Project Structure Guide

## 📁 Recommended Project Folder Structure

Agent들이 파일을 올바른 위치에 생성하도록 명확한 폴더 구조를 정의합니다.

### 🎯 Main Project Structure

```
projects/
└── ARGlassQA/                    # 메인 프로젝트 이름
    ├── android_app/              # Android XR 앱 코드
    │   ├── app/
    │   │   ├── src/main/java/com/xr/glasssqa/
    │   │   │   ├── MainActivity.kt           # 메인 액티비티
    │   │   │   ├── ui/
    │   │   │   │   ├── components/
    │   │   │   │   │   ├── CrosshairOverlay.kt
    │   │   │   │   │   ├── StatusDisplay.kt
    │   │   │   │   │   └── VoiceIndicator.kt
    │   │   │   │   └── screens/
    │   │   │   │       └── MainScreen.kt
    │   │   │   ├── camera/
    │   │   │   │   ├── CameraManager.kt
    │   │   │   │   ├── CameraPermissionHandler.kt
    │   │   │   │   └── FrameProcessor.kt
    │   │   │   ├── voice/
    │   │   │   │   ├── SpeechRecognitionManager.kt
    │   │   │   │   ├── TextToSpeechManager.kt
    │   │   │   │   └── VoiceCommandProcessor.kt
    │   │   │   ├── network/
    │   │   │   │   ├── A2AApiService.kt
    │   │   │   │   ├── A2AClient.kt
    │   │   │   │   └── ApiModels.kt
    │   │   │   ├── xr/
    │   │   │   │   ├── XRCoordinateMapper.kt
    │   │   │   │   ├── AROverlayManager.kt
    │   │   │   │   └── SpatialTracker.kt
    │   │   │   └── utils/
    │   │   │       ├── ImageUtils.kt
    │   │   │       ├── PermissionUtils.kt
    │   │   │       └── Constants.kt
    │   │   ├── src/main/res/
    │   │   │   ├── layout/
    │   │   │   ├── values/
    │   │   │   └── drawable/
    │   │   │       └── ic_crosshair.xml
    │   │   └── build.gradle.kts
    │   ├── gradle/
    │   ├── build.gradle.kts
    │   └── settings.gradle.kts
    │
    ├── backend_agents/           # A2A 에이전트 관련 코드 
    │   ├── shared/
    │   │   ├── models/
    │   │   │   ├── XRTypes.kt           # Android 앱과 공유할 타입 정의
    │   │   │   ├── A2AProtocol.kt
    │   │   │   └── APIModels.kt
    │   │   └── utils/
    │   │       ├── ImageProcessing.py
    │   │       ├── CoordinateTransform.py
    │   │       └── PerformanceMonitor.py
    │   ├── perception/
    │   │   ├── roi_processing/
    │   │   │   ├── ROIExtractor.py
    │   │   │   ├── FrameCropper.py
    │   │   │   └── ImageConverter.py
    │   │   ├── camera_integration/
    │   │   │   ├── Camera2Handler.py
    │   │   │   └── XRCameraInterface.py
    │   │   └── opencv_processing/
    │   │       └── ComputerVisionPipeline.py
    │   ├── vision/
    │   │   ├── gpt4v_integration/
    │   │   │   ├── GPT4VClient.py
    │   │   │   ├── RealtimeProcessor.py
    │   │   │   └── MultimodalHandler.py
    │   │   ├── analysis/
    │   │   │   ├── ObjectAnalyzer.py
    │   │   │   ├── SceneUnderstanding.py
    │   │   │   └── ContextProcessor.py
    │   │   └── response/
    │   │       ├── ResponseGenerator.py
    │   │       └── ConversationMemory.py
    │   ├── ux_tts/
    │   │   ├── ui_coordination/
    │   │   │   ├── HUDManager.py
    │   │   │   ├── CrosshairController.py
    │   │   │   └── StatusIndicator.py
    │   │   ├── tts_processing/
    │   │   │   ├── VoiceSynthesizer.py
    │   │   │   ├── AudioProcessor.py
    │   │   │   └── LanguageProcessor.py
    │   │   └── feedback/
    │   │       ├── UserFeedback.py
    │   │       └── HapticController.py
    │   └── logger/
    │       ├── performance/
    │       │   ├── MetricsCollector.py
    │       │   ├── LatencyTracker.py
    │       │   └── SystemMonitor.py
    │       ├── analytics/
    │       │   ├── UserBehavior.py
    │       │   ├── SessionTracker.py
    │       │   └── ErrorReporter.py
    │       └── logging/
    │           ├── StructuredLogger.py
    │           └── LogAggregator.py
    │
    ├── docs/                    # 프로젝트 문서
    │   ├── api/
    │   │   ├── A2A_Protocol.md
    │   │   ├── Android_API.md
    │   │   └── Agent_Endpoints.md
    │   ├── architecture/
    │   │   ├── System_Design.md
    │   │   ├── Data_Flow.md
    │   │   └── Component_Diagram.md
    │   ├── development/
    │   │   ├── Setup_Guide.md
    │   │   ├── Build_Instructions.md
    │   │   └── Testing_Guide.md
    │   └── deployment/
    │       ├── Production_Setup.md
    │       └── Performance_Tuning.md
    │
    ├── tests/                   # 테스트 코드
    │   ├── android_tests/
    │   │   ├── unit/
    │   │   │   ├── CameraManagerTest.kt
    │   │   │   ├── VoiceProcessorTest.kt
    │   │   │   └── A2AClientTest.kt
    │   │   ├── integration/
    │   │   │   ├── CameraToAgentTest.kt
    │   │   │   └── VoiceToResponseTest.kt
    │   │   └── ui/
    │   │       ├── MainScreenTest.kt
    │   │       └── CrosshairTest.kt
    │   ├── agent_tests/
    │   │   ├── perception/
    │   │   │   ├── test_roi_extraction.py
    │   │   │   └── test_camera_processing.py
    │   │   ├── vision/
    │   │   │   ├── test_gpt4v_integration.py
    │   │   │   └── test_multimodal.py
    │   │   ├── ux_tts/
    │   │   │   ├── test_tts_processing.py
    │   │   │   └── test_ui_coordination.py
    │   │   └── logger/
    │   │       └── test_performance_tracking.py
    │   └── integration_tests/
    │       ├── test_full_pipeline.py
    │       ├── test_a2a_communication.py
    │       └── test_end_to_end.py
    │
    ├── config/                  # 설정 파일
    │   ├── android/
    │   │   ├── app_config.json
    │   │   ├── camera_settings.json
    │   │   └── xr_configuration.json
    │   ├── agents/
    │   │   ├── agent_ports.json
    │   │   ├── performance_thresholds.json
    │   │   └── logging_config.json
    │   └── deployment/
    │       ├── development.env
    │       ├── staging.env
    │       └── production.env
    │
    └── scripts/                 # 유틸리티 스크립트
        ├── setup/
        │   ├── install_dependencies.sh
        │   ├── setup_android_sdk.sh
        │   └── configure_agents.sh
        ├── build/
        │   ├── build_android_app.sh
        │   ├── start_agents.sh
        │   └── deploy_system.sh
        ├── testing/
        │   ├── run_unit_tests.sh
        │   ├── run_integration_tests.sh
        │   └── performance_benchmark.sh
        └── monitoring/
            ├── check_agents.sh
            ├── monitor_performance.sh
            └── collect_logs.sh
```

## 🎯 Agent File Creation Rules

각 Agent는 다음 규칙을 **반드시** 따라야 합니다:

### 📱 Android App Files (Kotlin ONLY)
**Location**: `projects/ARGlassQA/android_app/app/src/main/java/com/xr/glassqa/`

```kotlin
// 예시: MainActivity.kt
package com.xr.glassqa

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
// ... 나머지 imports
```

### 🤖 Backend Agent Files (Python)
**Location**: `projects/ARGlassQA/backend_agents/[AGENT_NAME]/`

```python
# 예시: ROIExtractor.py
"""
ROI Extraction for Android XR Glass
Location: projects/ARGlassQA/backend_agents/perception/roi_processing/
"""

import cv2
import numpy as np
from typing import Tuple, Dict, Any
# ... 나머지 imports
```

### 📋 Documentation Files
**Location**: `projects/ARGlassQA/docs/[CATEGORY]/`

### 🧪 Test Files
**Location**: `projects/ARGlassQA/tests/[TEST_TYPE]/`

## 🚫 파일 생성 금지 위치

Agent들은 다음 위치에 파일을 생성하면 **안 됩니다**:

- ❌ `agents/claude_cli/[AGENT]/` (Agent 소스 코드 디렉토리)
- ❌ 프로젝트 루트 직접 생성
- ❌ 임의의 폴더 생성

## ✅ 올바른 파일 생성 예시

**Good** ✅:
```
Agent: "Create CameraManager.kt for Android XR"
Location: projects/ARGlassQA/android_app/app/src/main/java/com/xr/glassqa/camera/CameraManager.kt
```

**Bad** ❌:
```
Agent: "Create CameraManager.kt"  
Location: agents/claude_cli/perception/CameraManager.kt
```

## 🔄 Agent 재시작 필요

CLAUDE.md 파일을 수정한 후에는:
1. 현재 실행 중인 Agent 서버들을 모두 종료
2. Agent 서버들을 다시 시작 (새로운 CLAUDE.md 설정 적용)

```bash
# Agent 서버 종료
taskkill /F /PID [각 Agent PID]

# Agent 서버 재시작  
cd agents/claude_cli/perception && python server.py
cd agents/claude_cli/vision && python server.py
cd agents/claude_cli/ux_tts && python server.py
cd agents/claude_cli/logger && python server.py
```

이제 Agent들이 Kotlin 기반으로 Android 앱을 올바른 폴더 구조에 생성할 것입니다!