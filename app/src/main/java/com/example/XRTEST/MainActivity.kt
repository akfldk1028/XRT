package com.example.XRTEST

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.xr.compose.platform.LocalHasXrSpatialFeature
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.platform.LocalSpatialConfiguration
import androidx.xr.compose.spatial.EdgeOffset
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.OrbiterEdge
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.layout.SpatialRoundedCornerShape
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.movable
import androidx.xr.compose.subspace.layout.resizable
import androidx.xr.compose.subspace.layout.width
import com.example.XRTEST.ui.theme.XRTESTTheme
import com.example.XRTEST.ui.CrosshairOverlay
import com.example.XRTEST.ui.TextInputField
import com.example.XRTEST.ui.Camera2Preview
import com.example.XRTEST.ui.VoiceSettingsDialog
import com.example.XRTEST.ui.VoiceSettingsButton
import com.example.XRTEST.camera.Camera2Manager
import com.example.XRTEST.camera.CameraDiagnostics
import com.example.XRTEST.camera.CameraDebugHelper
import com.example.XRTEST.voice.VoiceManager
import com.example.XRTEST.network.A2AClient
import com.example.XRTEST.vision.VisionIntegration
import com.example.XRTEST.vision.VoiceSettingsManager
import com.example.XRTEST.vision.TtsConfiguration
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import com.example.XRTEST.R
import kotlinx.coroutines.delay

// 🚀 앱의 시작점: Android XR 앱의 메인 액티비티
// 이 클래스가 앱이 실행될 때 가장 먼저 시작되는 곳입니다
class MainActivity : ComponentActivity() {

    @SuppressLint("RestrictedApi")
    // ⭐ onCreate: 앱이 시작될 때 가장 먼저 실행되는 메서드
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 화면 가장자리까지 UI 확장 (상태바, 네비게이션바 영역까지 사용)
        enableEdgeToEdge()

        // Jetpack Compose UI 설정 시작
        setContent {
            // 앱의 테마 적용
            XRTESTTheme {
                // XR 공간 설정 가져오기
                val spatialConfiguration = LocalSpatialConfiguration.current
                
                // 🔀 XR 기능 지원 여부에 따른 UI 분기
                if (LocalSpatialCapabilities.current.isSpatialUiEnabled) {
                    // ✨ XR/공간 UI 모드 (3D 환경)
                    Subspace {
                        MySpatialContent(
                            // 홈 스페이스 모드로 전환하는 콜백 함수 전달
                            onRequestHomeSpaceMode = { spatialConfiguration.requestHomeSpaceMode() }
                        )
                    }
                } else {
                    // 📱 일반 2D UI 모드 (평면 화면)
                    My2DContent(
                        // 풀 스페이스 모드로 전환하는 콜백 함수 전달
                        onRequestFullSpaceMode = { spatialConfiguration.requestFullSpaceMode() }
                    )
                }
            }
        }
    }
}

// 🌌 XR 공간 UI 콘텐츠 (3D 환경에서 표시)
// VR/AR 헤드셋이나 XR 지원 기기에서 실행되는 3D 공간 UI
@SuppressLint("RestrictedApi")
@Composable
fun MySpatialContent(onRequestHomeSpaceMode: () -> Unit) {
    // 3D 공간에 떠있는 패널 생성
    // - 크기: 1280x800dp
    // - 사용자가 크기 조절 가능 (resizable)
    // - 사용자가 위치 이동 가능 (movable)
    SpatialPanel(
        SubspaceModifier
            .width(1280.dp)
            .height(800.dp)
            .resizable()
            .movable()
    ) {
        Surface {
            MainContent(
                modifier = Modifier.fillMaxSize()
            )
        }
        // 패널 주변에 떠있는 궤도 버튼 (Orbiter)
        // - 위치: 패널 상단
        // - 안쪽으로 20dp 오프셋
        // - 오른쪽 정렬
        // - 둥근 모서리 (28dp)
        Orbiter(
            position = OrbiterEdge.Top,
            offset = EdgeOffset.inner(offset = 20.dp),
            alignment = Alignment.End,
            shape = SpatialRoundedCornerShape(CornerSize(28.dp))
        ) {
            HomeSpaceModeIconButton(
                onClick = onRequestHomeSpaceMode,
                modifier = Modifier.size(56.dp)
            )
        }
    }
}

// 📱 2D 평면 UI 콘텐츠 (일반 화면에서 표시)
// XR 기능이 없는 일반 기기나 2D 모드에서 실행되는 UI
@SuppressLint("RestrictedApi")
@Composable
fun My2DContent(onRequestFullSpaceMode: () -> Unit) {
    Surface {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MainContent(modifier = Modifier.fillMaxSize())
            // XR 기능이 지원되는 기기인 경우에만 모드 전환 버튼 표시
            if (LocalHasXrSpatialFeature.current) {
                // 풀 스페이스 모드(3D)로 전환하는 버튼
                FullSpaceModeIconButton(
                    onClick = onRequestFullSpaceMode,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

// 📝 AR Glass Q&A 시스템 메인 콘텐츠 with OpenAI Realtime API
@Composable
fun MainContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // OpenAI API Key - BuildConfig에서 읽어오기
    val openaiApiKey = com.example.XRTEST.BuildConfig.OPENAI_API_KEY
    
    // API 키 검증
    var apiKeyError by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        if (openaiApiKey.isBlank() || openaiApiKey == "your-actual-openai-api-key-here") {
            apiKeyError = """
                ⚠️ OpenAI API Key가 설정되지 않았습니다!
                
                설정 방법:
                1. https://platform.openai.com/api-keys 에서 API 키 생성
                2. gradle.properties 파일에서 OPENAI_API_KEY=sk-... 설정
                3. 앱 재빌드
                
                현재 상태: API 키 없음 - Realtime API 사용 불가
            """.trimIndent()
        }
    }
    
    // AR Glass Q&A 시스템 매니저들
    val cameraManager = remember { Camera2Manager(context) }
    val voiceManager = remember { VoiceManager(context, openaiApiKey) }  // Pass API key for OpenAI TTS
    val a2aClient = remember { A2AClient() }
    
    // 카메라 진단 도구
    val cameraDiagnostics = remember { CameraDiagnostics(context) }
    val cameraDebugHelper = remember { CameraDebugHelper(context) }
    
    // 마이크 가용성 확인
    var hasMicrophone by remember { mutableStateOf(true) }
    var useTextInput by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        // AudioManager를 통해 마이크 디바이스 확인
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        hasMicrophone = audioDevices.any { device ->
            device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC ||
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
        
        // 마이크가 없으면 텍스트 입력 모드 활성화
        useTextInput = !hasMicrophone
        
        if (!hasMicrophone) {
            // Log.d("MainActivity", "No microphone detected - enabling text input mode") // Reduced logging
        }
    }
    
    // Voice Settings Manager
    val voiceSettingsManager = remember { VoiceSettingsManager(context) }
    
    // TTS Configuration Manager
    val ttsConfiguration = remember { TtsConfiguration(context) }
    
    // OpenAI Realtime API 통합
    val visionIntegration = remember {
        VisionIntegration(
            context = context,
            apiKey = openaiApiKey,
            camera2Manager = cameraManager,
            voiceManager = voiceManager
        )
    }
    
    // Apply saved voice settings to VisionIntegration
    LaunchedEffect(visionIntegration) {
        val savedVoice = voiceSettingsManager.getSavedVoice()
        val useKorean = voiceSettingsManager.isKoreanMode()
        visionIntegration.setVoice(savedVoice)
        visionIntegration.setLanguageMode(useKorean)
        
        // Apply TTS configuration
        val useAndroidForKorean = ttsConfiguration.useAndroidForKorean.value
        val forceAndroid = ttsConfiguration.forceAndroidTts.value
        visionIntegration.configureTts(useAndroidForKorean, forceAndroid)
        
        // Set VoiceManager language and speech rate
        voiceManager.setLanguage(useKorean)
        voiceManager.setSpeechRate(ttsConfiguration.speechRate.value)
    }
    
    // 시스템 상태
    var isSystemReady by remember { mutableStateOf(false) }
    var currentResponse by remember { mutableStateOf<String?>(null) }
    val integrationState by visionIntegration.state.collectAsState()
    val lastResponse by visionIntegration.lastResponse.collectAsState()
    val isProcessing by visionIntegration.isProcessing.collectAsState()
    
    // 카메라 디버그 정보
    var cameraDebugInfo by remember { mutableStateOf("") }
    var showCameraDebug by remember { mutableStateOf(false) } // 디버그 정보 기본적으로 숨김
    
    // Voice Settings Dialog state
    var showVoiceSettings by remember { mutableStateOf(false) }
    
    // 권한 요청
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        
        if (cameraGranted && audioGranted) {
            // 카메라 진단 실행 (조용히)
            // Log.d("MainActivity", "Running camera diagnostics...") // Reduced logging
            cameraDiagnostics.logDiagnostics()
            val quickCheck = cameraDiagnostics.quickWebcamCheck()
            // Log.d("MainActivity", quickCheck) // Reduced logging
            
            // 추가 디버그 진단 - DISABLED to reduce spam
            // val fullDiagnostics = cameraDebugHelper.runFullDiagnostics()
            // Log.d("MainActivity", fullDiagnostics)
            
            // 강제 카메라 ID "0" 테스트 - DISABLED to reduce spam
            // val forceTest = cameraDebugHelper.testForceCameraZero()
            // Log.d("MainActivity", "Force Camera 0 Test: $forceTest")
            
            // 권한이 승인되면 시스템 초기화
            cameraManager.initialize()
            
            // 카메라 디버그 정보 수집 (최소화)
            cameraDebugInfo = cameraManager.debugCameraInfo()
            // Log only if there's an actual problem
            if (cameraDebugInfo.contains("NO CAMERAS")) {
                Log.e("MainActivity", "Camera not detected: $cameraDebugInfo")
            }
            
            // 카메라가 없으면 에뮬레이터 설정 안내
            if (cameraDebugInfo.contains("NO CAMERAS DETECTED")) {
                Log.e("MainActivity", "⚠️ No webcam configured - see screen for instructions")
                showCameraDebug = true // 화면에 디버그 정보 강제 표시
            } else {
                // 카메라가 감지되면 시작
                voiceManager.initialize()
                cameraManager.startCamera()
                
                // 카메라 상태 확인 (조용히)
                val cameraStatus = cameraManager.getCameraStatus()
                // Log.d("MainActivity", "Camera Status: $cameraStatus") // Reduced logging
            
                // OpenAI Realtime API 초기화
                visionIntegration.initialize()
                isSystemReady = true
            }
        }
    }
    
    // 앱 시작 시 권한 요청
    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )
    }
    
    // OpenAI Realtime API 세션 시작/종료
    LaunchedEffect(integrationState) {
        if (integrationState == VisionIntegration.IntegrationState.READY && isSystemReady) {
            visionIntegration.startSession()
        }
    }
    
    // 리소스 정리
    DisposableEffect(Unit) {
        onDispose {
            visionIntegration.release()
        }
    }
    
    // Response fade out timer
    var showResponse by remember { mutableStateOf(false) }
    var responseTimer by remember { mutableStateOf(0L) }
    
    LaunchedEffect(lastResponse) {
        lastResponse?.let {
            showResponse = true
            responseTimer = System.currentTimeMillis()
            delay(5000)  // Show for 5 seconds
            if (System.currentTimeMillis() - responseTimer >= 5000) {
                showResponse = false
            }
        }
    }
    
    // AR Glass Q&A UI with Realtime API - TRUE FULL SCREEN
    Box(modifier = modifier.fillMaxSize()) {
        // 카메라 프리뷰 - 전체 화면 채우기 (진짜 FULL SCREEN, 패딩 없음)
        if (isSystemReady && !cameraDebugInfo.contains("NO CAMERAS DETECTED")) {
            // Camera2Manager와 연동된 카메라 프리뷰 - FULL SCREEN
            Camera2Preview(
                modifier = Modifier.fillMaxSize(),  // Fill entire screen edge-to-edge
                onSurfaceReady = { surface ->
                    // Camera2Manager에 프리뷰 Surface 전달
                    cameraManager.setPreviewSurface(surface)
                }
            )
        }
        
        // API 키 오류 표시
        if (apiKeyError != null) {
            Text(
                text = apiKeyError!!,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            )
        } else if (isSystemReady || showCameraDebug) {
            // 카메라 디버그 정보 표시 (에뮬레이터 설정 문제시)
            if (showCameraDebug && cameraDebugInfo.contains("NO CAMERAS DETECTED")) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "❌ 웹캠이 감지되지 않습니다!\n\n" +
                               cameraDebugInfo + "\n\n" +
                               "👆 위 지침을 따라 AVD 설정을 수정하세요.",
                        modifier = Modifier.padding(8.dp)
                    )
                    
                    TextButton(
                        onClick = { 
                            // 카메라 재검사
                            cameraManager.initialize()
                            cameraDebugInfo = cameraManager.debugCameraInfo()
                            if (!cameraDebugInfo.contains("NO CAMERAS DETECTED")) {
                                cameraManager.startCamera()
                                showCameraDebug = false
                                isSystemReady = true
                            }
                        }
                    ) {
                        Text("🔄 웹캠 설정 후 다시 시도")
                    }
                }
            } else if (isSystemReady) {
                // 십자가 오버레이 표시 (타겟팅 상태 반영)
                CrosshairOverlay(
                    isActive = true,
                    isTargeting = integrationState == VisionIntegration.IntegrationState.PROCESSING ||
                               integrationState == VisionIntegration.IntegrationState.RESPONDING,
                    modifier = Modifier.fillMaxSize()
                )
                
                // AI 응답 표시 - 상단에 페이드 인/아웃 애니메이션과 함께
                AnimatedVisibility(
                    visible = showResponse && lastResponse != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = lastResponse?.text ?: "",
                            color = Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                            fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth(0.9f)
                        )
                    }
                }
                
                // 🎤 마이크 플로팅 버튼 - 화면 하단 중앙에 현대적인 디자인
                FloatingActionButton(
                    onClick = {
                        // 음성 인식 토글
                        if (voiceManager.isListening.value) {
                            voiceManager.stopListening()
                        } else {
                            voiceManager.startListening()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp)  // 충분한 공간 확보
                        .size(72.dp),  // 적당한 크기
                    containerColor = if (voiceManager.isListening.collectAsState().value) {
                        Color(0xFFE91E63)  // Material Pink - 녹음 중
                    } else {
                        Color(0xFF673AB7)  // Material Deep Purple - 대기 중
                    },
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 12.dp,
                        pressedElevation = 16.dp
                    ),
                    shape = RoundedCornerShape(50)  // 완전히 둥근 모양
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (voiceManager.isListening.collectAsState().value) {
                                android.R.drawable.ic_btn_speak_now
                            } else {
                                android.R.drawable.ic_btn_speak_now
                            }
                        ),
                        contentDescription = "Voice Recording",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)  // 적당한 아이콘 크기
                    )
                }
                
                // 상태 표시 - 상단 왼쪽 코너에 최소화
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    val statusIcon = when (integrationState) {
                        VisionIntegration.IntegrationState.CONNECTING -> "🔌"
                        VisionIntegration.IntegrationState.LISTENING -> "🎤"
                        VisionIntegration.IntegrationState.PROCESSING -> "⏳"
                        VisionIntegration.IntegrationState.RESPONDING -> "💬"
                        VisionIntegration.IntegrationState.ERROR -> "❌"
                        else -> "🎯"
                    }
                    
                    Box(
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = statusIcon,
                            fontSize = 20.sp
                        )
                    }
                }
                
                // 최소화된 컨트롤 - 하단 우측 코너에 작게
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                
                    // 📸 캡처 버튼 - 작은 플로팅 버튼
                    FloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                if (integrationState != VisionIntegration.IntegrationState.READY && 
                                    integrationState != VisionIntegration.IntegrationState.LISTENING) {
                                    visionIntegration.startSession()
                                    delay(1000)
                                }
                                
                                val jpegData = cameraManager.captureCurrentFrameAsJpeg()
                                if (jpegData != null) {
                                    visionIntegration.sendQuery("What do you see in this image?")
                                } else {
                                    Log.e("MainActivity", "❌ Failed to capture image")
                                }
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.9f),
                        elevation = FloatingActionButtonDefaults.elevation(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_camera),
                            contentDescription = "Capture",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    // 🎤/⌨️ 입력 모드 전환 - 작은 플로팅 버튼
                    if (hasMicrophone) {
                        FloatingActionButton(
                            onClick = { useTextInput = !useTextInput },
                            modifier = Modifier.size(48.dp),
                            containerColor = Color(0xFF2196F3).copy(alpha = 0.9f),
                            elevation = FloatingActionButtonDefaults.elevation(4.dp)
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (useTextInput) android.R.drawable.ic_btn_speak_now 
                                         else android.R.drawable.ic_menu_edit
                                ),
                                contentDescription = "Input Mode",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    // 🎙️ 음성 설정 버튼 - Voice Settings
                    FloatingActionButton(
                        onClick = { showVoiceSettings = true },
                        modifier = Modifier.size(48.dp),
                        containerColor = Color(0xFF9C27B0).copy(alpha = 0.9f), // Purple for settings
                        elevation = FloatingActionButtonDefaults.elevation(4.dp)
                    ) {
                        Text(
                            text = "🎙️",
                            fontSize = 20.sp
                        )
                    }
                    
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                    
                    // 🔊 TTS 테스트 버튼
                    FloatingActionButton(
                        onClick = { 
                            // Simple TTS test
                            voiceManager.speak("테스트 음성입니다. 한국어 TTS가 제대로 작동하는지 확인합니다.")
                        },
                        modifier = Modifier.size(48.dp),
                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.9f), // Green for test
                        elevation = FloatingActionButtonDefaults.elevation(4.dp)
                    ) {
                        Text(
                            text = "🔊",
                            fontSize = 20.sp
                        )
                    }
                }
                
                // 텍스트 입력 필드 - 하단에 플로팅
                if (useTextInput || !hasMicrophone) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.9f)
                            .padding(bottom = 20.dp)
                            .background(
                                Color.Black.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(8.dp)
                    ) {
                        TextInputField(
                            enabled = isSystemReady,
                            onSendQuery = { query ->
                                visionIntegration.sendQuery(query)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            // 처리 중 표시 제거 - 상태 아이콘으로 충분함
            
            // 최소화된 카메라 디버그 오버레이 (우측 상단 코너)
            if (showCameraDebug && cameraDebugInfo.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                        )
                        .padding(4.dp)
                ) {
                    val simplifiedDebug = when {
                        cameraDebugInfo.contains("NO CAMERAS") -> "📷 ❌"
                        cameraDebugInfo.contains("device(s) found") -> {
                            val count = cameraDebugInfo.filter { it.isDigit() }.firstOrNull() ?: '0'
                            "📷 ✓ ($count)"
                        }
                        else -> "📷 ${cameraManager.getCameraStatus()}"
                    }
                    
                    Text(
                        text = simplifiedDebug,
                        color = Color.White.copy(alpha = 0.8f),
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp
                    )
                }
            }
        } else {
            // 시스템 준비 중
            Text(
                text = "🚀 Initializing AR Glass Q&A System with OpenAI GPT-4V...\nGranting camera and microphone permissions required.",
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // Voice Settings Dialog
        if (showVoiceSettings) {
            VoiceSettingsDialog(
                onDismiss = { showVoiceSettings = false },
                voiceSettingsManager = voiceSettingsManager,
                visionIntegration = visionIntegration
            )
        }
    }
    
    // 음성 명령 처리 (OpenAI Realtime API 통합) - 마이크가 있고 음성 모드일 때만
    LaunchedEffect(voiceManager.recognizedText.collectAsState().value) {
        val recognizedText = voiceManager.recognizedText.value
        if (recognizedText != null && isSystemReady && 
            integrationState == VisionIntegration.IntegrationState.LISTENING &&
            hasMicrophone && !useTextInput) {
            
            // OpenAI Realtime API로 질문 전송 (음성 + 비전)
            visionIntegration.sendQuery(recognizedText)
            voiceManager.clearRecognizedText()
        }
    }
    
    // 음성 인식 시작/중지 (마이크 모드에 따라)
    LaunchedEffect(useTextInput, hasMicrophone, isSystemReady) {
        if (isSystemReady) {
            if (hasMicrophone && !useTextInput) {
                // 마이크가 있고 음성 모드면 음성 인식 시작
                voiceManager.startListening()
            } else {
                // 텍스트 모드면 음성 인식 중지
                voiceManager.stopListening()
            }
        }
    }
    
    // 응답 업데이트 처리
    LaunchedEffect(lastResponse) {
        lastResponse?.let { response ->
            currentResponse = response.text
        }
    }
}

// 🔄 2D → 3D 전환 버튼
// 2D 모드에서 풀 스페이스(3D) 모드로 전환하는 버튼
@Composable
fun FullSpaceModeIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(id = R.drawable.ic_full_space_mode_switch),
            contentDescription = stringResource(R.string.switch_to_full_space_mode)
        )
    }
}

// 🔄 3D → 2D 전환 버튼  
// 3D 공간 모드에서 홈 스페이스(2D) 모드로 전환하는 버튼
@Composable
fun HomeSpaceModeIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(id = R.drawable.ic_home_space_mode_switch),
            contentDescription = stringResource(R.string.switch_to_home_space_mode)
        )
    }
}

@PreviewLightDark
@Composable
fun My2dContentPreview() {
    XRTESTTheme {
        My2DContent(onRequestFullSpaceMode = {})
    }
}

@Preview(showBackground = true)
@Composable
fun FullSpaceModeButtonPreview() {
    XRTESTTheme {
        FullSpaceModeIconButton(onClick = {})
    }
}

@PreviewLightDark
@Composable
fun HomeSpaceModeButtonPreview() {
    XRTESTTheme {
        HomeSpaceModeIconButton(onClick = {})
    }
}