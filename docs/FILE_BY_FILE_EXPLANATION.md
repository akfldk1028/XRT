# 📝 파일별 상세 코드 설명 - 코틀린 초보자용

## 🎯 이 문서의 목적
코틀린을 잘 모르는 분도 **각 파일이 무엇을 하는지** 쉽게 이해할 수 있도록 설명합니다.

---

## 1️⃣ **MainActivity.kt** - 앱의 시작점 🚀

### **이 파일이 하는 일**:
- 앱이 켜지면 **가장 먼저 실행**되는 파일
- 화면을 그리고, 권한을 요청하고, 다른 모든 시스템을 시작시킴

### **핵심 코드 부분**:
```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 📱 앱이 시작될 때 실행되는 함수
        super.onCreate(savedInstanceState)
        setContent {
            // 🎨 화면 UI를 그리기 시작
            XRTESTTheme {
                if (LocalSpatialCapabilities.current.isSpatialUiEnabled) {
                    // ✨ VR/AR 모드 (3D 화면)
                    MySpatialContent()
                } else {
                    // 📱 일반 모드 (평면 화면)  
                    My2DContent()
                }
            }
        }
    }
}
```

### **주요 함수들**:
- `onCreate()`: 앱 시작할 때 한 번 실행
- `My2DContent()`: 일반 스마트폰 화면 UI 그리기
- `MySpatialContent()`: VR/AR 3D 공간 UI 그리기

### **여기서 시작되는 것들**:
1. 카메라 매니저 초기화
2. 음성 매니저 초기화  
3. OpenAI 비전 통합 시스템 초기화
4. 권한 요청 (카메라, 마이크)

---

## 2️⃣ **VisionIntegration.kt** - 전체 시스템 지휘관 🎯

### **이 파일이 하는 일**:
- **모든 시스템을 통합 관리**하는 핵심 파일
- 카메라 + 음성 + OpenAI를 하나로 연결

### **핵심 코드 부분**:
```kotlin
class VisionIntegration(
    private val context: Context,
    private val apiKey: String,           // OpenAI API 키
    private val camera2Manager: Camera2Manager,  // 카메라 시스템
    private val voiceManager: VoiceManager       // 음성 시스템
) {
    
    fun startSession() {
        // 🔗 OpenAI에 연결 시작
        realtimeClient.connect()
        _state.value = IntegrationState.CONNECTING
    }
    
    fun sendQuery(question: String) {
        // 📸 카메라에서 현재 이미지 가져오기
        val currentFrame = camera2Manager.getCurrentFrame()
        
        // 🤖 OpenAI에게 이미지 + 질문 전송
        realtimeClient.sendImageWithPrompt(currentFrame, question)
        
        _state.value = IntegrationState.PROCESSING
    }
}
```

### **상태 관리**:
```kotlin
enum class IntegrationState {
    IDLE,           // 😴 대기 중
    CONNECTING,     // 🔌 OpenAI 연결 중
    READY,          // ✅ 준비 완료
    LISTENING,      // 👂 음성 입력 기다리는 중
    PROCESSING,     // 🤖 AI가 생각하는 중
    RESPONDING,     // 💬 AI가 답변하는 중
    ERROR           // ❌ 오류 발생
}
```

### **이 파일의 역할**:
1. **통합 관리**: 모든 시스템을 하나로 연결
2. **상태 추적**: 현재 시스템이 뭘 하고 있는지 관리
3. **데이터 전달**: 카메라 → OpenAI → 음성 출력 연결

---

## 3️⃣ **RealtimeVisionClient.kt** - OpenAI와 대화하는 담당자 🤖

### **이 파일이 하는 일**:
- **OpenAI GPT-4V API와 실시간 WebSocket 통신**
- 이미지 + 음성을 보내고, AI 답변을 받아옴

### **핵심 코드 부분**:
```kotlin
class RealtimeVisionClient(
    private val apiKey: String,
    private val onAudioResponse: (ByteArray) -> Unit,  // 음성 응답 콜백
    private val onTextResponse: (String) -> Unit,      // 텍스트 응답 콜백
    private val onError: (String) -> Unit              // 에러 콜백
) {
    
    fun connect() {
        // 🌐 OpenAI 서버에 WebSocket 연결
        val request = Request.Builder()
            .url("wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview-2024-12-17")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("OpenAI-Beta", "realtime=v1")
            .build()
        
        webSocket = client.newWebSocket(request, createWebSocketListener())
    }
    
    fun sendImageWithPrompt(imageData: ByteArray, prompt: String?) {
        // 📸 이미지를 Base64로 변환
        val base64Image = Base64.encodeToString(imageData, Base64.NO_WRAP)
        
        // 📤 OpenAI에게 전송할 JSON 메시지 만들기
        val event = JSONObject().apply {
            put("type", "conversation.item.create")
            put("item", JSONObject().apply {
                put("type", "message")
                put("role", "user")
                put("content", JSONArray().apply {
                    // 이미지 추가
                    put(JSONObject().apply {
                        put("type", "input_image")
                        put("image", base64Image)
                    })
                    // 텍스트 질문 추가
                    if (!prompt.isNullOrEmpty()) {
                        put(JSONObject().apply {
                            put("type", "input_text")
                            put("text", prompt)
                        })
                    }
                })
            })
        }
        
        // 🚀 전송!
        sendEvent(event)
        requestResponse() // AI에게 답변 요청
    }
}
```

### **이벤트 처리**:
```kotlin
private fun handleRealtimeEvent(event: JSONObject) {
    val type = event.getString("type")
    
    when (type) {
        "response.audio.delta" -> {
            // 🔊 음성 응답 조각이 도착함
            val delta = event.getString("delta")
            val audioData = Base64.decode(delta, Base64.DEFAULT)
            // 음성 재생 대기열에 추가
        }
        
        "response.text.delta" -> {
            // 📝 텍스트 응답 조각이 도착함
            val delta = event.getString("delta")
            onTextResponse(delta) // UI에 텍스트 표시
        }
        
        "error" -> {
            // ❌ 에러 발생
            val error = event.getJSONObject("error")
            val message = error.getString("message")
            onError(message)
        }
    }
}
```

### **이 파일의 핵심**:
1. **WebSocket 연결**: OpenAI와 실시간 소통
2. **이미지 전송**: 카메라 사진을 AI에게 보냄
3. **응답 수신**: AI의 음성+텍스트 답변 받기
4. **에러 처리**: 연결 끊김, API 오류 등 처리

---

## 4️⃣ **AudioStreamManager.kt** - 고품질 음성 처리 🔊

### **이 파일이 하는 일**:
- **24kHz 고품질 음성** 녹음 및 재생
- OpenAI API에 맞는 정확한 음성 포맷 처리

### **핵심 코드 부분**:
```kotlin
class AudioStreamManager(
    private val onAudioCaptured: (ByteArray) -> Unit
) {
    companion object {
        private const val SAMPLE_RATE = 24000      // 24kHz (OpenAI 요구사항)
        private const val CHANNEL_CONFIG_RECORD = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_CONFIG_PLAY = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT  // 16비트
    }
    
    fun startRecording() {
        // 🎤 마이크에서 24kHz로 녹음 시작
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG_RECORD,
            AUDIO_FORMAT,
            bufferSize
        )
        
        audioRecord?.startRecording()
        
        // 🔄 별도 스레드에서 계속 녹음
        recordingJob = coroutineScope.launch {
            while (isRecording) {
                val buffer = ByteArray(bufferSize)
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                
                if (bytesRead > 0) {
                    // 📊 노이즈 제거 및 음량 정규화
                    val processedAudio = processAudioInput(buffer)
                    onAudioCaptured(processedAudio)
                }
            }
        }
    }
    
    fun playAudio(audioData: ByteArray) {
        // 🔊 24kHz 음성을 스피커로 재생
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG_PLAY,
            AUDIO_FORMAT,
            audioData.size,
            AudioTrack.MODE_STREAM
        )
        
        audioTrack?.play()
        audioTrack?.write(audioData, 0, audioData.size)
    }
}
```

### **음성 처리 과정**:
1. **녹음**: 마이크 → 24kHz PCM16 형식
2. **노이즈 제거**: 배경 소음 필터링
3. **정규화**: 음량 적정 수준으로 조정
4. **전송**: OpenAI에게 Base64로 인코딩해서 전송
5. **수신**: OpenAI에서 24kHz 음성 응답 받기
6. **재생**: 스피커로 고품질 음성 출력

---

## 5️⃣ **Camera2Manager.kt** - 카메라 전문가 📷

### **이 파일이 하는 일**:
- **Android Camera2 API로 카메라 제어**
- 실시간으로 사진을 찍어서 AI 분석용으로 제공

### **핵심 코드 부분**:
```kotlin
class Camera2Manager(private val context: Context) {
    
    fun startCamera() {
        // 📷 카메라 매니저 가져오기
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        try {
            // 📋 후면 카메라 찾기
            val cameraId = cameraManager.cameraIdList.first { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.LENS_FACING) == 
                    CameraCharacteristics.LENS_FACING_BACK
            }
            
            // 🔧 카메라 설정
            cameraManager.openCamera(cameraId, cameraDeviceCallback, backgroundHandler)
            
        } catch (e: Exception) {
            Log.e(TAG, "Camera initialization failed: ${e.message}")
        }
    }
    
    private fun createCameraPreviewSession() {
        // 📸 연속 사진 촬영을 위한 세션 만들기
        val reader = ImageReader.newInstance(
            PREVIEW_WIDTH,    // 1920픽셀
            PREVIEW_HEIGHT,   // 1080픽셀  
            ImageFormat.JPEG, // JPEG 포맷
            2                 // 최대 2장 버퍼
        )
        
        reader.setOnImageAvailableListener({ reader ->
            // 📸 새 사진이 찍힐 때마다 실행
            val image = reader.acquireLatestImage()
            processFrame(image) // 이미지 처리
            image.close()
        }, backgroundHandler)
    }
    
    private fun processFrame(image: Image) {
        // 🖼️ 이미지를 JPEG 바이트 배열로 변환
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        
        // 📏 이미지 크기 조정 (OpenAI 요구사항: 최대 1024x1024)
        val resizedBytes = resizeImageForAPI(bytes)
        
        // 📤 VisionIntegration에게 전달
        onFrameProcessed(resizedBytes)
    }
}
```

### **카메라 작업 순서**:
1. **권한 확인**: 카메라 사용 권한 체크
2. **카메라 열기**: 후면 카메라 활성화
3. **프리뷰 시작**: 실시간 영상 표시
4. **자동 촬영**: 1초마다 사진 촬영
5. **이미지 처리**: 크기 조정, 포맷 변환
6. **전달**: VisionIntegration으로 이미지 전송

---

## 6️⃣ **VoiceManager.kt** - 음성 인식 전문가 🎤

### **이 파일이 하는 일**:
- **음성을 텍스트로 변환** (Speech-to-Text)
- **텍스트를 음성으로 변환** (Text-to-Speech) - 백업용

### **핵심 코드 부분**:
```kotlin
class VoiceManager(private val context: Context) {
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    
    fun startListening() {
        // 🎧 음성 인식 시작
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // 실시간 결과
        }
        
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                // ✅ 음성 인식 완료!
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    _recognizedText.value = recognizedText
                    Log.d(TAG, "Recognized: $recognizedText")
                }
            }
            
            override fun onError(error: Int) {
                // ❌ 음성 인식 실패
                Log.e(TAG, "Speech recognition error: $error")
                _recognizedText.value = null
            }
        })
        
        speechRecognizer?.startListening(intent)
    }
    
    fun speak(text: String) {
        // 🗣️ 텍스트를 음성으로 변환 (백업용)
        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH, // 이전 음성 중단하고 새로 재생
            null,
            "utteranceId"
        )
    }
}
```

### **음성 처리 흐름**:
1. **듣기 시작**: `startListening()` 호출
2. **음성 수신**: 마이크로 사용자 음성 입력
3. **텍스트 변환**: "이게 뭐야?" → 텍스트
4. **결과 전달**: MainActivity에서 인식된 텍스트 감지
5. **질문 전송**: VisionIntegration.sendQuery() 호출

---

## 7️⃣ **CrosshairOverlay.kt** - 십자가 UI 🎯

### **이 파일이 하는 일**:
- **화면 중앙에 십자가 표시**
- 시스템 상태에 따라 색상과 애니메이션 변경

### **핵심 코드 부분**:
```kotlin
@Composable
fun CrosshairOverlay(
    isActive: Boolean = true,
    isTargeting: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (isActive) {
            // ⊕ 십자가 그리기
            Canvas(
                modifier = Modifier
                    .size(60.dp)
                    .align(Alignment.Center)
            ) {
                val crosshairColor = when {
                    isTargeting -> Color(0xFFFFA500)  // 🟠 주황색 (처리 중)
                    else -> Color.White               // ⚪ 흰색 (대기)
                }
                
                val strokeWidth = 3.dp.toPx()
                val crossSize = 30.dp.toPx()
                
                // 세로선 그리기
                drawLine(
                    color = crosshairColor,
                    start = Offset(center.x, center.y - crossSize/2),
                    end = Offset(center.x, center.y + crossSize/2),
                    strokeWidth = strokeWidth
                )
                
                // 가로선 그리기  
                drawLine(
                    color = crosshairColor,
                    start = Offset(center.x - crossSize/2, center.y),
                    end = Offset(center.x + crossSize/2, center.y),
                    strokeWidth = strokeWidth
                )
                
                // 가운데 점
                drawCircle(
                    color = crosshairColor,
                    radius = 4.dp.toPx(),
                    center = center
                )
            }
        }
    }
}
```

### **십자가 상태**:
- **⚪ 흰색**: 대기 상태 - "조준하고 질문하세요"
- **🟠 주황색**: 처리 중 - "AI가 분석하고 있어요"
- **🔴 빨간색**: 에러 상태 - "문제가 발생했어요"

---

## 🌐 **네트워크 파일들**

### **A2AClient.kt** - Agent 통신 담당자
```kotlin
// 다른 Agent들(Perception, UX/TTS, Logger)과 HTTP로 통신
class A2AClient {
    suspend fun sendMessage(agentPort: Int, message: String): String {
        // HTTP POST로 JSON-RPC 2.0 메시지 전송
    }
}
```

### **A2AModels.kt** - 데이터 구조 정의
```kotlin
// Agent 간 통신에 사용할 데이터 형식들
data class A2AMessage(
    val messageId: String,
    val taskId: String,
    val contextId: String,
    val parts: List<A2APart>
)
```

---

## ⚙️ **설정 파일들**

### **build.gradle.kts** - 빌드 설정
```kotlin
dependencies {
    // 📷 카메라 라이브러리
    implementation("androidx.camera:camera-camera2:1.4.0")
    
    // 🌐 네트워크 라이브러리  
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // 🔐 JSON 처리
    implementation("org.json:json:20230618")
    
    // OpenAI API 키 설정
    buildConfigField("String", "OPENAI_API_KEY", "\"${project.findProperty("OPENAI_API_KEY")}\"")
}
```

### **AndroidManifest.xml** - 권한 설정
```xml
<!-- 📷 카메라 권한 -->
<uses-permission android:name="android.permission.CAMERA" />

<!-- 🎤 마이크 권한 -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- 🌐 인터넷 권한 -->
<uses-permission android:name="android.permission.INTERNET" />
```

---

## 🚀 **전체 실행 흐름 요약**

1. **MainActivity** → 앱 시작, UI 그리기
2. **권한 요청** → 카메라, 마이크 권한
3. **VisionIntegration** → 전체 시스템 초기화
4. **RealtimeVisionClient** → OpenAI 연결
5. **대기 상태** → LISTENING, 십자가 흰색
6. **사용자 질문** → VoiceManager 음성 인식
7. **이미지 캡처** → Camera2Manager 사진 촬영  
8. **AI 요청** → 이미지+텍스트를 OpenAI에 전송
9. **AI 응답** → 24kHz 음성으로 답변 수신
10. **음성 재생** → AudioStreamManager로 고품질 재생
11. **완료** → 다시 LISTENING 상태로

**이제 각 파일이 무엇을 하는지 완전히 이해하셨나요? 🎯**