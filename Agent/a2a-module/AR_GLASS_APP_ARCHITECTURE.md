# 🥽 Google Glass AR Q&A App Architecture

## 🎯 앱 개요
**사용자가 AR Glass를 통해 실세계 객체를 십자가로 타겟팅하고, GPT-4V에게 질문하여 실시간 음성 답변을 받는 시스템**

---

## 🏗️ 시스템 아키텍처

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Android XR    │    │   A2A Agents    │    │   Cloud APIs    │
│     Glass       │    │    (Local)      │    │    (Remote)     │
├─────────────────┤    ├─────────────────┤    ├─────────────────┤
│ Camera2 API     │───▶│ Perception      │    │                 │
│ Crosshair UI    │    │ Agent :8030     │    │                 │
│ Voice Input     │    │                 │    │                 │
│ TTS Output      │    ├─────────────────┤    │                 │
│ XR Overlay      │    │ Vision Agent    │◄──▶│ GPT-4V Realtime │
│                 │    │ :8031           │    │ API             │
│                 │    │                 │    │                 │
│                 │    ├─────────────────┤    │                 │
│                 │◄───│ UX/TTS Agent    │    │                 │
│                 │    │ :8032           │    │                 │
│                 │    │                 │    │                 │
│                 │    ├─────────────────┤    │                 │
│                 │    │ Logger Agent    │    │                 │
│                 │    │ :8033           │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

---

## 📱 Android XR 앱 구조

### 🎯 **MainActivity.kt 개선사항**

```kotlin
class MainActivity : ComponentActivity() {
    // 🔐 권한 관리
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initializeARSystem()
        } else {
            showPermissionRequiredMessage()
        }
    }
    
    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initializeVoiceSystem()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 권한 요청
        requestNecessaryPermissions()
        
        setContent {
            XRTESTTheme {
                val spatialConfiguration = LocalSpatialConfiguration.current
                if (LocalSpatialCapabilities.current.isSpatialUiEnabled) {
                    // 🥽 XR Glass 모드
                    ARGlassContent(
                        onObjectQuery = { objectInfo, question ->
                            processObjectQuery(objectInfo, question)
                        }
                    )
                } else {
                    // 📱 2D 테스트 모드
                    TestModeContent()
                }
            }
        }
    }
    
    private fun requestNecessaryPermissions() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
}
```

### 🎯 **AR Glass UI 컴포넌트**

```kotlin
@Composable
fun ARGlassContent(
    onObjectQuery: (ObjectInfo, String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 📹 카메라 피드 백그라운드 (투명)
        CameraPreview(
            modifier = Modifier.fillMaxSize()
        )
        
        // 🎯 십자가 타겟팅 시스템
        CrosshairTargeting(
            modifier = Modifier.align(Alignment.Center),
            onTargetLocked = { objectInfo ->
                showTargetConfirmation(objectInfo)
            }
        )
        
        // 🎤 음성 인터페이스
        VoiceInterface(
            modifier = Modifier.align(Alignment.BottomCenter),
            onQuestionReceived = { question ->
                getCurrentTargetedObject()?.let { obj ->
                    onObjectQuery(obj, question)
                }
            }
        )
        
        // 📊 상태 표시기
        SystemStatus(
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

@Composable
fun CrosshairTargeting(
    modifier: Modifier = Modifier,
    onTargetLocked: (ObjectInfo) -> Unit
) {
    var isLocked by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        // 십자가 표시
        Icon(
            painter = painterResource(
                if (isLocked) R.drawable.ic_crosshair_locked 
                else R.drawable.ic_crosshair
            ),
            contentDescription = "Targeting Crosshair",
            modifier = Modifier.size(48.dp),
            tint = if (isLocked) Color.Green else Color.Red
        )
        
        // 타겟 확인 애니메이션
        if (isLocked) {
            AnimatedTargetConfirmation()
        }
    }
}
```

---

## 📷 Camera2 Integration

### 🎥 **CameraManager.kt**

```kotlin
class ARCameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    
    // A2A 에이전트 클라이언트
    private val perceptionAgent = A2AClient("http://localhost:8030")
    
    fun initializeCamera() {
        startBackgroundThread()
        openCamera()
    }
    
    private fun openCamera() {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList[0]
            
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED) {
                
                manager.openCamera(cameraId, stateCallback, backgroundHandler)
            }
        } catch (e: CameraAccessException) {
            Log.e("ARCamera", "카메라 열기 실패", e)
        }
    }
    
    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreviewSession()
        }
        
        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            cameraDevice = null
        }
        
        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
        }
    }
    
    private fun createCameraPreviewSession() {
        try {
            // ImageReader 설정 (ROI 추출용)
            imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1)
            imageReader?.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)
            
            // 프리뷰 세션 생성
            val surfaces = listOf(imageReader?.surface)
            cameraDevice?.createCaptureSession(surfaces, sessionCallback, backgroundHandler)
            
        } catch (e: CameraAccessException) {
            Log.e("ARCamera", "프리뷰 세션 생성 실패", e)
        }
    }
    
    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage()
        image?.let {
            // 🚀 A2A Perception Agent로 프레임 전송
            sendFrameToPerceptionAgent(it)
            it.close()
        }
    }
    
    private suspend fun sendFrameToPerceptionAgent(image: Image) {
        val imageBytes = convertImageToByteArray(image)
        
        val response = perceptionAgent.sendMessage(
            A2ARequest(
                method = "extract_crosshair_roi",
                params = mapOf(
                    "image_data" to imageBytes,
                    "crosshair_position" to "center",
                    "roi_size" to "320x320"
                )
            )
        )
        
        // ROI 데이터를 Vision Agent로 전달
        response.result?.let { roiData ->
            sendToVisionAgent(roiData)
        }
    }
}
```

---

## 🎤 Voice Processing System

### 🗣️ **VoiceProcessor.kt**

```kotlin
class VoiceProcessor(private val context: Context) {
    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private val textToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale.getDefault()
        }
    }
    
    // A2A 에이전트 클라이언트들
    private val visionAgent = A2AClient("http://localhost:8031")
    private val ttsAgent = A2AClient("http://localhost:8032")
    
    fun startListeningForQuestion() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "객체에 대해 질문하세요...")
        }
        
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { question ->
                    processQuestion(question)
                }
            }
            
            override fun onError(error: Int) {
                Log.e("Voice", "음성 인식 오류: $error")
                speakError("음성을 인식할 수 없습니다.")
            }
            
            // ... 기타 콜백들
        })
        
        speechRecognizer.startListening(intent)
    }
    
    private suspend fun processQuestion(question: String) {
        try {
            // 🤖 Vision Agent로 질문 전송 (GPT-4V 처리)
            val visionResponse = visionAgent.sendMessage(
                A2ARequest(
                    method = "analyze_object_with_question",
                    params = mapOf(
                        "question" to question,
                        "roi_data" to getCurrentROI(),
                        "use_gpt4v_realtime" to true
                    )
                )
            )
            
            visionResponse.result?.let { answer ->
                // 🔊 TTS Agent로 음성 합성
                synthesizeAndSpeak(answer.toString())
            }
            
        } catch (e: Exception) {
            Log.e("Voice", "질문 처리 실패", e)
            speakError("죄송합니다. 질문을 처리할 수 없습니다.")
        }
    }
    
    private suspend fun synthesizeAndSpeak(text: String) {
        try {
            val ttsResponse = ttsAgent.sendMessage(
                A2ARequest(
                    method = "synthesize_speech",
                    params = mapOf(
                        "text" to text,
                        "voice_style" to "natural",
                        "language" to "ko"
                    )
                )
            )
            
            // Android TTS로 재생
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            
        } catch (e: Exception) {
            // 폴백: 직접 TTS 사용
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }
    
    private fun speakError(message: String) {
        textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
    }
}
```

---

## 🌐 A2A Communication Layer

### 📡 **A2AClient.kt**

```kotlin
data class A2ARequest(
    val jsonrpc: String = "2.0",
    val id: String = UUID.randomUUID().toString(),
    val method: String,
    val params: Map<String, Any>
)

data class A2AResponse(
    val jsonrpc: String,
    val id: String,
    val result: Any? = null,
    val error: A2AError? = null
)

data class A2AError(
    val code: Int,
    val message: String,
    val data: Any? = null
)

class A2AClient(private val baseUrl: String) {
    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val service = retrofit.create(A2AService::class.java)
    
    suspend fun sendMessage(request: A2ARequest): A2AResponse {
        return withContext(Dispatchers.IO) {
            try {
                val response = service.sendMessage(request)
                if (response.isSuccessful) {
                    response.body() ?: throw Exception("응답 본문이 없습니다")
                } else {
                    throw Exception("HTTP ${response.code()}: ${response.message()}")
                }
            } catch (e: Exception) {
                A2AResponse(
                    jsonrpc = "2.0",
                    id = request.id,
                    error = A2AError(
                        code = -1,
                        message = e.message ?: "알 수 없는 오류"
                    )
                )
            }
        }
    }
}

interface A2AService {
    @POST("/")
    suspend fun sendMessage(@Body request: A2ARequest): Response<A2AResponse>
}
```

---

## 🚀 실행 Flow

### 1. **앱 시작**
```kotlin
1. 권한 요청 (카메라, 마이크)
2. A2A 에이전트 연결 확인
3. Camera2 초기화
4. XR 오버레이 활성화
```

### 2. **객체 타겟팅**
```kotlin
1. 카메라 피드 표시
2. 십자가 중앙 표시
3. 사용자가 객체에 조준
4. 타겟 락온 표시
```

### 3. **질문 처리**
```kotlin
1. 음성 인식 시작 ("이게 뭐야?")
2. Perception Agent → ROI 추출
3. Vision Agent → GPT-4V 분석
4. TTS Agent → 음성 합성
5. 스피커 출력
```

---

## ⚙️ 설정 파일

### 📋 **AndroidManifest.xml**
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<uses-feature android:name="android.hardware.camera2" android:required="true" />
<uses-feature android:name="android.software.xr.immersive" android:required="false" />
```

### 🔧 **build.gradle.kts**
```kotlin
dependencies {
    // 기존 XR 의존성들...
    
    // Camera2 API
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    
    // 네트워킹
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    
    // 권한
    implementation("androidx.activity:activity-compose:1.8.0")
}
```

---

## 🎯 **다음 구현 단계**

1. ✅ **A2A 에이전트 카드 업데이트 완료**
2. ✅ **가상환경 설정 및 가이드 작성 완료**
3. 🔄 **MainActivity에 Camera2 권한 추가**
4. 🔄 **CrosshairOverlay 컴포넌트 구현**
5. 🔄 **A2AClient 통신 레이어 구현**
6. 🔄 **VoiceProcessor 음성 처리 구현**
7. 🔄 **GPT-4V Realtime API 키 설정**

**🚨 중요: 모든 작업 전에 가상환경을 활성화하세요!**