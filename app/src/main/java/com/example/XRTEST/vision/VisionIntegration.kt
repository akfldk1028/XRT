package com.example.XRTEST.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import com.example.XRTEST.camera.Camera2Manager
import com.example.XRTEST.voice.VoiceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * VisionIntegration - Orchestrates camera, audio, and OpenAI Realtime API
 * Provides seamless integration for AR Glass Q&A functionality
 */
class VisionIntegration(
    private val context: Context,
    private val apiKey: String,
    private val camera2Manager: Camera2Manager,
    private val voiceManager: VoiceManager
) {
    
    companion object {
        private const val TAG = "VisionIntegration"
        private const val IMAGE_QUALITY = 40  // Context7: Ultra-low quality for speed
        private const val MAX_IMAGE_SIZE = 384 // Context7: Smaller for real-time
        private const val CAPTURE_INTERVAL_MS = 500L   // Faster capture for real-time
        private const val AUDIO_CHUNK_DURATION_MS = 50L  // Smaller chunks for lower latency
    }
    
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Components
    private lateinit var realtimeClient: RealtimeVisionClient
    private lateinit var visionAnalyzer: VisionAnalyzer  // For actual image/vision analysis
    private lateinit var audioStreamManager: AudioStreamManager
    
    // State management
    private val _state = MutableStateFlow(IntegrationState.IDLE)
    val state: StateFlow<IntegrationState> = _state.asStateFlow()
    
    private val _lastResponse = MutableStateFlow<Response?>(null)
    val lastResponse: StateFlow<Response?> = _lastResponse.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    // Control flags
    private var isActive = false
    private var captureJob: Job? = null
    private var audioJob: Job? = null
    private var lastCaptureTime = 0L
    // 🚀 Prevent duplicate TTS playback
    private var isSpeaking = false
    private var lastResponseText = ""
    
    // TTS Configuration
    private var useAndroidTtsForKorean = true  // Use Android TTS for Korean by default
    private var forceAndroidTts = false  // Force Android TTS for all languages
    
    enum class IntegrationState {
        IDLE,
        CONNECTING,
        READY,
        LISTENING,
        PROCESSING,
        RESPONDING,
        ERROR
    }
    
    data class Response(
        val text: String,
        val audioData: ByteArray? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    init {
        try {
            Log.d(TAG, "🚀 INITIALIZING VisionIntegration - Context7 Debug")
            Log.d(TAG, "🔧 Step 1: Setting up RealtimeClient...")
            setupRealtimeClient()
            Log.d(TAG, "✅ Step 1 completed: RealtimeClient setup")
            
            Log.d(TAG, "🔧 Step 2: Setting up VisionAnalyzer...")
            setupVisionAnalyzer()  // Setup vision analyzer for image recognition
            Log.d(TAG, "✅ Step 2 completed: VisionAnalyzer setup")
            
            Log.d(TAG, "🔧 Step 3: Setting up AudioManager...")
            setupAudioManager()
            Log.d(TAG, "✅ Step 3 completed: AudioManager setup")
            
            Log.d(TAG, "🔧 Step 4: Setting up Connection observers...")
            observeConnections()
            Log.d(TAG, "✅ Step 4 completed: Connection observers setup")
            
            Log.d(TAG, "🔧 Step 5: Configuring TTS settings...")
            // Set default Korean mode configuration
            // 🚀 SPEED OPTIMIZATION: Force Android TTS for all responses (much faster)
            configureTts(useAndroidForKorean = true, forceAndroid = true)
            Log.d(TAG, "✅ Step 5 completed: TTS configuration")
            
            Log.i(TAG, "VisionIntegration initialized with hybrid approach: Realtime API (audio) + Vision Analyzer (images)")
            Log.d(TAG, "✅ VISIONINTEGRATION FULLY INITIALIZED - Context7")
            
            // Start WebSocket connection asynchronously after initialization
            Log.d(TAG, "🔄 Starting WebSocket connection asynchronously...")
            connectToRealtimeAPI()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL: VisionIntegration initialization failed at step: ${e.message}", e)
            Log.e(TAG, "❌ Stack trace: ${e.stackTraceToString()}")
            throw e
        }
    }
    
    /**
     * Setup OpenAI Realtime Client (for audio conversation only)
     */
    private fun setupRealtimeClient() {
        Log.d(TAG, "🔧 Creating RealtimeVisionClient instance...")
        realtimeClient = RealtimeVisionClient(
            apiKey = apiKey,
            onAudioResponse = { audioData ->
                handleAudioResponse(audioData)
            },
            onTextResponse = { text ->
                handleTextResponse(text)
            },
            onError = { error ->
                handleError(error)
            },
            selectedVoice = "alloy",  // Default voice, can be changed
            useKorean = true  // Default to Korean mode
        )
        Log.d(TAG, "✅ RealtimeVisionClient instance created (connection deferred)")
    }
    
    /**
     * Connect to OpenAI Realtime API WebSocket asynchronously
     */
    private fun connectToRealtimeAPI() {
        coroutineScope.launch {
            try {
                Log.d(TAG, "🔗 Context7: Attempting WebSocket connection to OpenAI Realtime API...")
                _state.value = IntegrationState.CONNECTING
                
                realtimeClient.connect()
                
                Log.d(TAG, "✅ Context7: WebSocket connection initiated successfully")
                Log.d(TAG, "🎤 Voice recognition now available - '안녕' will be transcribed!")
                _state.value = IntegrationState.READY
                
                // Start session immediately for voice recognition
                Log.d(TAG, "🔄 Context7: Auto-starting session for immediate voice recognition...")
                delay(500) // Give WebSocket a moment to fully establish
                startSession()
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Context7: WebSocket connection failed", e)
                Log.e(TAG, "❌ Context7: Starting LISTENING mode anyway for testing...")
                // Even if WebSocket fails, start listening mode for testing
                _state.value = IntegrationState.READY
                delay(500)
                startSession()
            }
        }
    }
    
    /**
     * Setup Vision Analyzer (for actual image recognition with GPT-4V)
     */
    private fun setupVisionAnalyzer() {
        visionAnalyzer = VisionAnalyzer(
            apiKey = apiKey,
            onAnalysisResult = { text ->
                handleVisionResponse(text)
            },
            onError = { error ->
                handleError(error)
            }
        )
    }
    
    /**
     * Setup Audio Stream Manager
     */
    private fun setupAudioManager() {
        audioStreamManager = AudioStreamManager { audioData ->
            // Context7: Send captured audio to Realtime API using proper method
            Log.v(TAG, "🔄 Context7: AudioData received, state=${_state.value}")
            
            // Context7: Allow audio streaming in both LISTENING and PROCESSING states
            if (_state.value == IntegrationState.LISTENING || _state.value == IntegrationState.PROCESSING) {
                Log.d(TAG, "✅ Context7: Sending audioData to WebSocket - ${audioData.size} bytes")
                realtimeClient.sendAudioData(audioData)
            } else {
                Log.v(TAG, "🔄 Context7: State ${_state.value} - continuing audio stream for testing")
                realtimeClient.sendAudioData(audioData)
            }
        }
    }
    
    /**
     * Observe connection states
     */
    private fun observeConnections() {
        // Monitor Realtime API connection
        coroutineScope.launch {
            realtimeClient.connectionState.collect { connectionState ->
                when (connectionState) {
                    WebSocketManager.ConnectionState.CONNECTED -> {
                        if (_state.value == IntegrationState.CONNECTING) {
                            _state.value = IntegrationState.READY
                            Log.d(TAG, "System ready for interaction")
                        }
                    }
                    WebSocketManager.ConnectionState.CONNECTING -> {
                        _state.value = IntegrationState.CONNECTING
                    }
                    WebSocketManager.ConnectionState.ERROR -> {
                        _state.value = IntegrationState.ERROR
                    }
                    else -> {}
                }
            }
        }
        
        // Camera frame monitoring disabled - only analyze on user request
        // coroutineScope.launch {
        //     camera2Manager.frameProcessed.collect { frameData ->
        //         if (frameData != null && shouldCaptureFrame()) {
        //             processFrame(frameData)
        //         }
        //     }
        // }
    }
    
    /**
     * Initialize the integration system
     */
    fun initialize() {
        coroutineScope.launch {
            try {
                _state.value = IntegrationState.CONNECTING
                
                // Initialize audio components - Context7: Check if already initialized
                Log.d(TAG, "🎤 Context7: Checking audio initialization state...")
                Log.d(TAG, "🎤   - Current recording: ${audioStreamManager.isRecording.value}")
                
                if (!audioStreamManager.isRecording.value) {
                    Log.d(TAG, "🎤 Context7: Initializing audio recording...")
                    if (!audioStreamManager.initializeRecording()) {
                        throw Exception("Failed to initialize audio recording")
                    }
                } else {
                    Log.d(TAG, "🎤 Context7: Audio recording already active, skipping initialization")
                }
                
                Log.d(TAG, "🎤 Context7: Initializing audio playback...")
                if (!audioStreamManager.initializePlayback()) {
                    throw Exception("Failed to initialize audio playback")
                }
                
                // Connect to Realtime API
                realtimeClient.connectSync()
                
                // Wait for connection
                withTimeout(10000) {
                    while (realtimeClient.connectionState.value != WebSocketManager.ConnectionState.CONNECTED) {
                        delay(100)
                    }
                }
                
                _state.value = IntegrationState.READY
                Log.d(TAG, "VisionIntegration initialized successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed: ${e.message}")
                _state.value = IntegrationState.ERROR
                handleError("Initialization failed: ${e.message}")
            }
        }
    }
    
    /**
     * Start the Q&A session
     */
    fun startSession() {
        Log.d(TAG, "startSession called (current state: ${_state.value})")
        
        // 이미 LISTENING 상태면 무시
        if (_state.value == IntegrationState.LISTENING) {
            Log.d(TAG, "Already in LISTENING state")
            return
        }
        
        // READY 상태가 아니면 초기화 시도
        if (_state.value != IntegrationState.READY) {
            Log.w(TAG, "System not ready, attempting to initialize first...")
            initialize()
            return
        }
        
        isActive = true
        startFrameCapture()
        startAudioCapture()
        _state.value = IntegrationState.LISTENING
        
        Log.d(TAG, "Q&A session started")
    }
    
    /**
     * Stop the Q&A session
     */
    fun stopSession() {
        isActive = false
        stopFrameCapture()
        stopAudioCapture()
        realtimeClient.cancelResponse()
        _state.value = IntegrationState.READY
        
        Log.d(TAG, "Q&A session stopped")
    }
    
    /**
     * Start capturing camera frames
     */
    private fun startFrameCapture() {
        captureJob = coroutineScope.launch {
            while (isActive) {
                delay(CAPTURE_INTERVAL_MS)
                // Frame capture is handled by camera2Manager flow collection
            }
        }
    }
    
    /**
     * Stop capturing camera frames
     */
    private fun stopFrameCapture() {
        captureJob?.cancel()
        captureJob = null
    }
    
    /**
     * Start audio capture - DISABLED for emulator testing
     */
    private fun startAudioCapture() {
        // Context7: Enable OpenAI native speech recognition via PCM16 streaming
        Log.d(TAG, "🎤 Context7: Starting AudioStreamManager for OpenAI Realtime API")
        
        audioStreamManager.startRecording()
        
        // Monitor audio level for voice activity (Context7: Server VAD will handle turn detection)
        audioJob = coroutineScope.launch {
            audioStreamManager.audioLevel.collect { level ->
                if (level > 0.1f && _state.value == IntegrationState.LISTENING) {
                    // Voice activity detected - Context7: Server VAD will process this
                    Log.v(TAG, "🎤 Context7: Voice activity detected: $level (Server VAD processing)")
                }
            }
        }
    }
    
    /**
     * Stop audio capture - DISABLED for emulator testing
     */
    private fun stopAudioCapture() {
        // Context7: Stop OpenAI native speech recognition
        Log.d(TAG, "🎤 Context7: Stopping AudioStreamManager for OpenAI Realtime API")
        
        audioStreamManager.stopRecording()
        audioJob?.cancel()
        audioJob = null
    }
    
    /**
     * Check if we should capture the current frame
     */
    private fun shouldCaptureFrame(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime < CAPTURE_INTERVAL_MS) {
            return false
        }
        lastCaptureTime = now
        return isActive && _state.value == IntegrationState.LISTENING
    }
    
    /**
     * Process camera frame for vision analysis
     */
    private fun processFrame(frameData: ByteArray) {
        coroutineScope.launch {
            try {
                // Use Camera2Manager's built-in JPEG conversion
                val jpegData = camera2Manager.captureCurrentFrameAsJpeg()
                if (jpegData == null) {
                    Log.w(TAG, "Failed to capture frame as JPEG")
                    return@launch
                }
                
                // Resize if needed
                val resizedData = resizeImageIfNeeded(jpegData)
                
                // Convert JPEG to Bitmap for VisionAnalyzer
                val bitmap = BitmapFactory.decodeByteArray(resizedData, 0, resizedData.size)
                val prompt = generateContextPrompt()
                val isKorean = realtimeClient.isKoreanMode()
                Log.d(TAG, "Sending image to Vision Analyzer: ${resizedData.size} bytes, Korean: $isKorean")
                visionAnalyzer.analyzeImage(bitmap, prompt, VisionAnalyzer.MODE_GENERAL, isKorean)
                
                _state.value = IntegrationState.PROCESSING
                
            } catch (e: Exception) {
                Log.e(TAG, "Frame processing error: ${e.message}")
            }
        }
    }
    
    /**
     * Analyze colors in the current camera view
     * Specifically designed to accurately identify colors (e.g., yellow vs blue)
     */
    fun analyzeColors() {
        coroutineScope.launch {
            try {
                _isProcessing.value = true
                _state.value = IntegrationState.PROCESSING
                
                // Capture current frame
                val jpegData = camera2Manager.captureCurrentFrameAsJpeg()
                if (jpegData != null) {
                    val resizedData = resizeImageIfNeeded(jpegData)
                    val bitmap = BitmapFactory.decodeByteArray(resizedData, 0, resizedData.size)
                    val isKorean = realtimeClient.isKoreanMode()
                    
                    Log.d(TAG, "Analyzing colors with Vision Analyzer for accurate color recognition")
                    
                    // Use specialized color analysis mode
                    visionAnalyzer.analyzeColorsInImage(bitmap, isKorean)
                } else {
                    Log.w(TAG, "No frame available for color analysis")
                    handleError("Camera frame not available")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Color analysis error: ${e.message}")
                _isProcessing.value = false
                _state.value = IntegrationState.READY
            }
        }
    }
    
    /**
     * Analyze objects in the current camera view
     */
    fun analyzeObjects() {
        coroutineScope.launch {
            try {
                _isProcessing.value = true
                _state.value = IntegrationState.PROCESSING
                
                // Capture current frame
                val jpegData = camera2Manager.captureCurrentFrameAsJpeg()
                if (jpegData != null) {
                    val resizedData = resizeImageIfNeeded(jpegData)
                    val bitmap = BitmapFactory.decodeByteArray(resizedData, 0, resizedData.size)
                    val isKorean = realtimeClient.isKoreanMode()
                    
                    Log.d(TAG, "Analyzing objects with Vision Analyzer")
                    
                    // Use specialized object detection mode
                    visionAnalyzer.analyzeObjectsInImage(bitmap, isKorean)
                } else {
                    Log.w(TAG, "No frame available for object analysis")
                    handleError("Camera frame not available")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Object analysis error: ${e.message}")
                _isProcessing.value = false
                _state.value = IntegrationState.READY
            }
        }
    }
    
    /**
     * Send a text query with the current frame
     */
    fun sendQuery(query: String) {
        // 상태 체크를 더 유연하게 변경 - CONNECTING 이후 상태면 허용
        if (_state.value == IntegrationState.IDLE || _state.value == IntegrationState.ERROR) {
            Log.w(TAG, "Cannot send query: System not ready (state: ${_state.value})")
            // CONNECTING 상태로 전환 시도
            if (_state.value == IntegrationState.IDLE) {
                startSession()
            }
            return
        }
        
        Log.d(TAG, "sendQuery called with: $query (current state: ${_state.value})")
        
        coroutineScope.launch {
            try {
                _isProcessing.value = true
                _state.value = IntegrationState.PROCESSING
                
                // 🔧 TEMPORARILY USE JPEG: More reliable processing
                Log.d(TAG, "🔧 Using reliable JPEG processing...")
                val yuvBase64: String? = null  // Disable YUV for now
                
                if (yuvBase64 != null) {
                    // 🚀 Revolutionary: Skip JPEG conversion entirely!
                    Log.d(TAG, "🚀 SUCCESS: Using YUV direct Base64 - 300ms saved!")
                    val isKorean = realtimeClient.isKoreanMode()
                    val analysisMode = determineAnalysisMode(query)
                    
                    // Send Base64 directly to VisionAnalyzer with ultra-fast settings
                    val analysisRequest = "data:image/jpeg;base64,$yuvBase64"
                    // Direct GPT-4V API call would go here
                    Log.d(TAG, "🚀 YUV Base64 ready for GPT-4V: ${yuvBase64.length} chars")
                    // For now, process as bitmap
                    try {
                        val imageBytes = android.util.Base64.decode(yuvBase64, android.util.Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        val analysisMode = VisionAnalyzer.MODE_GENERAL
                        visionAnalyzer.analyzeImage(bitmap, query, analysisMode, isKorean)
                    } catch (e: Exception) {
                        Log.w(TAG, "YUV decode failed, trying JPEG: ${e.message}")
                        // Fallback to JPEG
                        val jpegData = camera2Manager.captureCurrentFrameAsJpeg()
                        if (jpegData != null) {
                            val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
                            val analysisMode = VisionAnalyzer.MODE_GENERAL
                            visionAnalyzer.analyzeImage(bitmap, query, analysisMode, isKorean)
                        }
                    }
                } else {
                    // No frame available, send text only to Realtime API
                    Log.d(TAG, "No frame available, sending text to Realtime API: $query")
                    realtimeClient.sendTextMessage(query)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Query processing error: ${e.message}")
                _isProcessing.value = false
                _state.value = IntegrationState.READY
            }
        }
    }
    
    /**
     * Send voice command
     */
    fun sendVoiceCommand() {
        if (audioStreamManager.isRecording.value) {
            // Context7: Commit audio buffer and create response manually
            realtimeClient.commitAudioBuffer()
            realtimeClient.createResponse()
            _state.value = IntegrationState.PROCESSING
            Log.d(TAG, "Voice command sent - audio committed and response creation triggered")
        }
    }
    
    /**
     * Handle audio response from API
     */
    private fun handleAudioResponse(audioData: ByteArray) {
        coroutineScope.launch {
            val isKorean = realtimeClient.isKoreanMode()
            
            // IMPORTANT: In Korean mode, OpenAI shouldn't send audio at all
            // If we receive audio in Korean mode, it's a configuration error
            if (isKorean) {
                Log.w(TAG, "WARNING: Received audio from OpenAI in Korean mode - this shouldn't happen!")
                Log.w(TAG, "Audio will be ignored. Using Android TTS for Korean responses.")
                // DO NOT play the audio, DO NOT update state to RESPONDING
                // Just ignore it completely
                return@launch
            }
            
            // English mode: Play OpenAI audio
            _state.value = IntegrationState.RESPONDING
            
            if (forceAndroidTts) {
                // Force Android TTS even for English
                Log.d(TAG, "Force Android TTS enabled, skipping OpenAI audio")
                _lastResponse.value = _lastResponse.value?.copy(
                    audioData = audioData
                ) ?: Response("", audioData)
            } else {
                // Play OpenAI audio for English
                Log.d(TAG, "Playing OpenAI audio response (English mode)")
                audioStreamManager.playAudio(audioData)
                
                // Update last response
                _lastResponse.value = _lastResponse.value?.copy(
                    audioData = audioData
                ) ?: Response("", audioData)
                
                // Wait for playback to complete
                while (audioStreamManager.isPlaying.value) {
                    delay(100)
                }
            }
            
            _state.value = IntegrationState.LISTENING
            _isProcessing.value = false
        }
    }
    
    /**
     * Handle text response from Realtime API (audio conversation)
     */
    private fun handleTextResponse(text: String) {
        coroutineScope.launch {
            Log.d(TAG, "🔊 handleTextResponse called with: ${text.take(100)}...")
            Log.d(TAG, "🔊 Full text length: ${text.length} characters")
            Log.d(TAG, "🔊 VoiceManager available: true")
            
            // 🔥 HYBRID LOGIC: Check if user is asking about images
            if (isImageQuestion(text)) {
                Log.d(TAG, "🎯 Image question detected! Switching to GPT-4V...")
                handleImageQuestion(text)
                return@launch
            }
            
            // For Realtime conversations, don't show text - just handle voice
            // Comment out text update for voice-only conversation
            // _lastResponse.value = Response(text)
            
            val isKorean = realtimeClient.isKoreanMode()
            Log.d(TAG, "🔊 Korean mode: $isKorean")
            Log.d(TAG, "🔊 Current voiceManager state:")
            Log.d(TAG, "🔊   - isSpeaking: ${voiceManager.isSpeaking.value}")
            Log.d(TAG, "🔊   - TTS provider: ${voiceManager.getTtsProvider()}")
            Log.d(TAG, "🔊   - OpenAI TTS available: ${voiceManager.isOpenAITtsAvailable()}")
            
            // Korean mode: Use OpenAI TTS for Korean responses
            if (isKorean) {
                Log.d(TAG, "🔊 Setting VoiceManager to Korean mode...")
                // Set VoiceManager to Korean mode and speak with OpenAI TTS
                voiceManager.setLanguage(true)
                
                Log.d(TAG, "🔊 About to call voiceManager.speak() with OpenAI TTS...")
                Log.d(TAG, "🔊 Text to speak: '${text.take(200)}...'")
                
                // Context7: Mute microphone during TTS to prevent feedback
                audioStreamManager.setMuted(true)
                
                voiceManager.speak(text)  // This will use OpenAI TTS
                
                Log.d(TAG, "✅ voiceManager.speak() called successfully!")
                Log.d(TAG, "🔊 Korean mode: Sent to VoiceManager for OpenAI TTS")
                
                _state.value = IntegrationState.RESPONDING
                Log.d(TAG, "🔊 State changed to RESPONDING, waiting for TTS to complete...")
                
                // Wait for TTS to complete
                var waitCount = 0
                while (voiceManager.isSpeaking.value && waitCount < 100) {  // Max 10 seconds wait
                    delay(100)
                    waitCount++
                    if (waitCount % 10 == 0) {  // Log every second
                        Log.d(TAG, "🔊 Still waiting for TTS... (${waitCount/10}s)")
                    }
                }
                
                if (waitCount >= 100) {
                    Log.w(TAG, "⚠️ TTS wait timeout after 10 seconds")
                } else {
                    Log.d(TAG, "✅ TTS completed after ${waitCount * 100}ms")
                }
                
                // Context7: Unmute microphone after TTS completes
                audioStreamManager.setMuted(false)
                
                _state.value = IntegrationState.LISTENING
                _isProcessing.value = false
            } else if (forceAndroidTts) {
                // English mode but forced to use Android TTS
                _state.value = IntegrationState.RESPONDING
                
                // Context7: Mute microphone during TTS
                audioStreamManager.setMuted(true)
                
                voiceManager.setLanguage(false)  // English
                voiceManager.speak(text)
                
                Log.d(TAG, "English mode: Using Android TTS (forced)")
                
                // Wait for TTS to complete
                while (voiceManager.isSpeaking.value) {
                    delay(100)
                }
                
                // Context7: Unmute microphone after TTS completes
                audioStreamManager.setMuted(false)
                
                _state.value = IntegrationState.LISTENING
                _isProcessing.value = false
            } else if (_state.value != IntegrationState.RESPONDING) {
                // English mode: OpenAI audio should handle it
                // This text is just for logging/display
                // Only use TTS as fallback if no audio was received
                Log.d(TAG, "English mode: Text received, audio should be playing from OpenAI")
                
                // Small delay to check if audio is playing
                delay(500)
                
                // If still not responding, use TTS as fallback
                if (_state.value != IntegrationState.RESPONDING && !audioStreamManager.isPlaying.value) {
                    Log.w(TAG, "No audio playing, using Android TTS as fallback")
                    _state.value = IntegrationState.RESPONDING
                    
                    // Context7: Mute microphone during fallback TTS
                    audioStreamManager.setMuted(true)
                    
                    voiceManager.setLanguage(false)  // English
                    voiceManager.speak(text)
                    
                    // Wait for TTS to complete
                    while (voiceManager.isSpeaking.value) {
                        delay(100)
                    }
                    
                    // Context7: Unmute microphone after TTS completes
                    audioStreamManager.setMuted(false)
                    
                    _state.value = IntegrationState.LISTENING
                    _isProcessing.value = false
                }
            }
        }
    }
    
    /**
     * Handle vision response from Chat Completions API
     */
    private fun handleVisionResponse(text: String) {
        coroutineScope.launch {
            Log.d(TAG, "🎯 VisionIntegration: handleVisionResponse called!")
            Log.d(TAG, "🎯 VisionIntegration: Vision text received: ${text.take(200)}...")
            Log.d(TAG, "🎯 VisionIntegration: Processing state: ${_isProcessing.value}")
            Log.d(TAG, "🎯 VisionIntegration: Integration state: ${_state.value}")
            
            // 🔥 Context7: Use VoiceManager directly for TTS (NO Realtime API context)
            Log.d(TAG, "🎯 VisionIntegration: Playing vision result via TTS only...")
            
            // 🚀 DUPLICATE PREVENTION: Check if already speaking or same response
            if (isSpeaking || text == lastResponseText) {
                Log.d(TAG, "⚠️ Skipping duplicate vision response: speaking=$isSpeaking, same_text=${text == lastResponseText}")
                return@launch
            }
            
            isSpeaking = true
            lastResponseText = text
            
            // Context7: Mute microphone during TTS to prevent feedback loop
            audioStreamManager.setMuted(true)
            
            voiceManager?.speak(text)
            
            _isProcessing.value = false
            _state.value = IntegrationState.RESPONDING
            
            // Context7: NO LONGER sending to Realtime API to prevent TTS duplication
            Log.d(TAG, "🎯 VisionIntegration: Vision analysis complete - TTS only, no Realtime API context")
            
            // Wait for TTS to complete, then return to LISTENING
            delay(1000) // Brief delay for TTS to start
            while (voiceManager?.isSpeaking?.value == true) {
                delay(100)
            }
            
            // 🚀 Reset duplicate prevention flags
            isSpeaking = false
            
            // Context7: Unmute microphone after TTS completes
            audioStreamManager.setMuted(false)
            _state.value = IntegrationState.LISTENING
        }
    }
    
    /**
     * Handle errors
     */
    private fun handleError(error: String) {
        Log.e(TAG, "Integration error: $error")
        _state.value = IntegrationState.ERROR
        _isProcessing.value = false
        
        // Notify user via TTS
        voiceManager.speak("An error occurred: $error")
    }
    
    /**
     * 🔥 HYBRID LOGIC: Check if user message contains image-related keywords
     */
    private fun isImageQuestion(text: String): Boolean {
        val lowerText = text.lowercase()
        val imageKeywords = listOf(
            // Korean image keywords
            "이미지", "사진", "화면", "보여", "뭐가", "무엇이", "뭐", "색깔", "컬러", "물체", "객체", "보이는", "보이네", "찍힌",
            // English image keywords  
            "image", "picture", "photo", "see", "look", "what", "show", "color", "object", "visible", "camera", "view"
        )
        
        return imageKeywords.any { keyword -> lowerText.contains(keyword) }
    }
    
    /**
     * 🎯 Handle image question by capturing current frame and analyzing
     */
    private fun handleImageQuestion(userQuestion: String) {
        coroutineScope.launch {
            try {
                Log.d(TAG, "🎥 Capturing current frame for image analysis...")
                _state.value = IntegrationState.PROCESSING
                
                // Capture current camera frame
                val jpegData = camera2Manager.captureCurrentFrameAsJpeg()
                if (jpegData == null) {
                    val errorMsg = if (realtimeClient.isKoreanMode()) {
                        "카메라에서 이미지를 가져올 수 없어요. 다시 시도해주세요."
                    } else {
                        "Cannot capture image from camera. Please try again."
                    }
                    voiceManager.speak(errorMsg)
                    _state.value = IntegrationState.LISTENING
                    return@launch
                }
                
                // Convert to bitmap and analyze
                val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
                
                // Create context-aware prompt combining user question
                val visionPrompt = if (realtimeClient.isKoreanMode()) {
                    "사용자가 \"$userQuestion\"라고 물어봤습니다. 이 이미지를 보고 자연스럽게 답변해주세요."
                } else {
                    "The user asked: \"$userQuestion\". Please look at this image and respond naturally."
                }
                
                Log.d(TAG, "🎯 Analyzing image with GPT-4V for question: ${userQuestion.take(50)}...")
                visionAnalyzer.analyzeImage(bitmap, visionPrompt, VisionAnalyzer.MODE_GENERAL, realtimeClient.isKoreanMode())
                
            } catch (e: Exception) {
                Log.e(TAG, "Error handling image question: ${e.message}")
                val errorMsg = if (realtimeClient.isKoreanMode()) {
                    "이미지 분석 중 오류가 발생했어요."
                } else {
                    "An error occurred during image analysis."
                }
                voiceManager.speak(errorMsg)
                _state.value = IntegrationState.LISTENING
            }
        }
    }
    
    /**
     * Generate context prompt for vision analysis
     */
    private fun generateContextPrompt(): String {
        val isKorean = realtimeClient.isKoreanMode()
        return if (isKorean) {
            """
                AR 안경에서 본 이미지를 분석해주세요.
                중앙의 십자선은 사용자가 주목하는 지점입니다.
                이미지, 특히 중앙 부분에 대한 유용한 정보를 한국어로만 제공해주세요.
                AR 안경 상호작용에 적합하게 간결하고 관련성 있게 답변해주세요.
                반드시 한국어로만 응답하세요. 영어는 사용하지 마세요.
            """.trimIndent()
        } else {
            """
                Analyze this image from AR glasses.
                The crosshair in the center indicates the user's focus point.
                Provide helpful information about what you see, especially around the center of the image.
                Be concise and relevant to AR glass interaction.
            """.trimIndent()
        }
    }
    
    /**
     * Convert YUV frame data to JPEG
     */
    private fun convertYuvToJpeg(yuvData: ByteArray): ByteArray {
        // Assuming NV21 format from camera
        val width = 640 // Default width, should be obtained from camera
        val height = 480 // Default height, should be obtained from camera
        
        val yuvImage = YuvImage(yuvData, ImageFormat.NV21, width, height, null)
        val outputStream = ByteArrayOutputStream()
        
        yuvImage.compressToJpeg(
            Rect(0, 0, width, height),
            IMAGE_QUALITY,
            outputStream
        )
        
        return outputStream.toByteArray()
    }
    
    /**
     * Resize image if it exceeds maximum dimensions
     */
    private fun resizeImageIfNeeded(imageData: ByteArray): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
        
        if (bitmap.width <= MAX_IMAGE_SIZE && bitmap.height <= MAX_IMAGE_SIZE) {
            return imageData
        }
        
        // Calculate new dimensions maintaining aspect ratio
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val newWidth: Int
        val newHeight: Int
        
        if (bitmap.width > bitmap.height) {
            newWidth = MAX_IMAGE_SIZE
            newHeight = (MAX_IMAGE_SIZE / aspectRatio).toInt()
        } else {
            newHeight = MAX_IMAGE_SIZE
            newWidth = (MAX_IMAGE_SIZE * aspectRatio).toInt()
        }
        
        // Resize bitmap
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        
        // Convert back to JPEG
        val outputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, outputStream)
        
        bitmap.recycle()
        resizedBitmap.recycle()
        
        return outputStream.toByteArray()
    }
    
    /**
     * Change the TTS voice
     * Available voices: "alloy", "echo", "fable", "onyx", "nova", "shimmer"
     */
    fun setVoice(voice: String) {
        if (voice in RealtimeVisionClient.AVAILABLE_VOICES) {
            realtimeClient.setVoice(voice)
            
            // CRITICAL: Also update VoiceManager for OpenAI TTS
            voiceManager.setOpenAITtsVoice(voice)
            
            Log.d(TAG, "Voice changed to: $voice (both RealtimeClient and VoiceManager)")
        } else {
            Log.w(TAG, "Invalid voice: $voice")
        }
    }
    
    /**
     * Get available voice options
     */
    fun getAvailableVoices(): List<String> = RealtimeVisionClient.AVAILABLE_VOICES
    
    /**
     * Get current voice
     */
    fun getCurrentVoice(): String = realtimeClient.getCurrentVoice()
    
    /**
     * Set language mode (Korean or English)
     */
    fun setLanguageMode(useKorean: Boolean) {
        realtimeClient.setLanguageMode(useKorean)
        // VisionAnalyzer language is set per request, no need to configure here
        Log.d(TAG, "Language mode set to: ${if (useKorean) "Korean (한국어)" else "English"}")
        Log.i(TAG, "Hybrid system configured - Realtime: audio conversation, Vision: image analysis")
    }
    
    /**
     * Check if Korean mode is enabled
     */
    fun isKoreanMode(): Boolean = realtimeClient.isKoreanMode()
    
    /**
     * Configure TTS preferences
     * @param useAndroidForKorean Use Android TTS for Korean responses (recommended)
     * @param forceAndroid Force Android TTS for all languages
     */
    fun configureTts(useAndroidForKorean: Boolean = true, forceAndroid: Boolean = false) {
        useAndroidTtsForKorean = useAndroidForKorean
        forceAndroidTts = forceAndroid
        
        Log.d(TAG, "TTS Configuration: AndroidForKorean=$useAndroidForKorean, ForceAndroid=$forceAndroid")
        
        // Update VoiceManager settings
        if (useAndroidForKorean && isKoreanMode()) {
            voiceManager.setLanguage(true)  // Korean
            voiceManager.setSpeechRate(0.95f)  // Slightly slower for clarity
        }
    }
    
    /**
     * Get current TTS configuration
     */
    fun getTtsConfiguration(): Pair<Boolean, Boolean> {
        return Pair(useAndroidTtsForKorean, forceAndroidTts)
    }
    
    /**
     * Clean up resources
     */
    fun release() {
        stopSession()
        audioStreamManager.release()
        realtimeClient.destroy()
        visionAnalyzer.destroy()  // Clean up vision analyzer
        coroutineScope.cancel()
        
        Log.d(TAG, "VisionIntegration released")
    }
    
    /**
     * Determine the best analysis mode based on query content
     */
    private fun determineAnalysisMode(query: String): String {
        val lowerQuery = query.lowercase()
        
        // Color-related keywords
        val colorKeywords = listOf(
            "색상", "색깔", "색", "빨간", "파란", "노란", "초록", "검은", "흰", "보라", "분홍", 
            "color", "red", "blue", "yellow", "green", "black", "white", "purple", "pink",
            "무슨색", "어떤색", "what color"
        )
        
        // Text-related keywords  
        val textKeywords = listOf(
            "글자", "텍스트", "문자", "읽", "써있", "text", "read", "written", "글씨"
        )
        
        // Object detection keywords
        val objectKeywords = listOf(
            "무엇", "뭐가", "물체", "물건", "what", "object", "thing", "있는지", "보이는"
        )
        
        // Scene understanding keywords
        val sceneKeywords = listOf(
            "어디", "장소", "상황", "where", "scene", "situation", "여기가", "이곳"
        )
        
        return when {
            colorKeywords.any { lowerQuery.contains(it) } -> {
                Log.d(TAG, "Color query detected: $query")
                VisionAnalyzer.MODE_COLOR_ANALYSIS
            }
            textKeywords.any { lowerQuery.contains(it) } -> {
                Log.d(TAG, "Text query detected: $query") 
                VisionAnalyzer.MODE_TEXT_READING
            }
            objectKeywords.any { lowerQuery.contains(it) } -> {
                Log.d(TAG, "Object query detected: $query")
                VisionAnalyzer.MODE_OBJECT_DETECTION
            }
            sceneKeywords.any { lowerQuery.contains(it) } -> {
                Log.d(TAG, "Scene query detected: $query")
                VisionAnalyzer.MODE_SCENE_UNDERSTANDING
            }
            else -> {
                Log.d(TAG, "General query: $query")
                VisionAnalyzer.MODE_GENERAL
            }
        }
    }
    
}