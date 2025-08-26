package com.example.XRTEST.vision

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.*
import kotlin.math.min

/**
 * OpenAI Realtime API Client for GPT-4V
 * Uses WebSocketManager for connection management and focuses on OpenAI-specific logic
 */
class RealtimeVisionClient(
    private val apiKey: String,
    private val onAudioResponse: (ByteArray) -> Unit,
    private val onTextResponse: (String) -> Unit,
    private val onError: (String) -> Unit,
    private var selectedVoice: String = DEFAULT_VOICE,
    private var useKorean: Boolean = true  // Default to Korean
) {
    
    companion object {
        private const val TAG = "RealtimeVisionClient"
        private const val REALTIME_API_URL = "wss://api.openai.com/v1/realtime"
        private const val MODEL = "gpt-4o-realtime-preview-2024-12-17"
        private const val DEFAULT_VOICE = "alloy"
        private const val SAMPLE_RATE = 24000
        private const val AUDIO_FORMAT = "pcm16"
        
        // Available OpenAI voices
        val AVAILABLE_VOICES = listOf(
            "alloy",    // Default, neutral
            "echo",     // Male voice
            "fable",    // British accent
            "onyx",     // Deep male voice
            "nova",     // Female voice
            "shimmer"   // Soft female voice
        )
    }

    private lateinit var webSocketManager: WebSocketManager
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // State management
    val connectionState: StateFlow<WebSocketManager.ConnectionState>
        get() = webSocketManager.connectionState
    
    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()
    
    private val audioQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private var messageCollectorJob: Job? = null
    private var errorCollectorJob: Job? = null
    
    // Context7: ChatGPT-style conversation state management
    private val _isResponseInProgress = MutableStateFlow(false)
    val isResponseInProgress: StateFlow<Boolean> = _isResponseInProgress.asStateFlow()
    
    private val _canInterrupt = MutableStateFlow(true)
    val canInterrupt: StateFlow<Boolean> = _canInterrupt.asStateFlow()
    
    // Text accumulation for complete responses
    private val currentTextBuilder = StringBuilder()
    private var currentResponseId: String? = null
    
    // Context7: Track conversation items like real ChatGPT Voice
    private val conversationItems = mutableListOf<JSONObject>()
    
    init {
        setupWebSocketManager()
    }
    
    /**
     * Context7: Setup WebSocket Manager with ChatGPT Voice-style real-time conversation
     */
    private fun setupWebSocketManager() {
        Log.d(TAG, "🔗 Context7: Setting up ChatGPT Voice-style WebSocket connection...")
        Log.d(TAG, "🔗 Realtime API URL: $REALTIME_API_URL")
        Log.d(TAG, "🔗 Model: $MODEL")
        Log.d(TAG, "🔗 API Key length: ${apiKey.length} characters")
        
        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "OpenAI-Beta" to "realtime=v1"
        )
        
        Log.d(TAG, "🔗 WebSocket headers configured:")
        Log.d(TAG, "🔗   - Authorization: Bearer ${apiKey.take(10)}...")
        Log.d(TAG, "🔗   - OpenAI-Beta: realtime=v1")
        
        val config = WebSocketConfig(
            connectTimeoutMs = 30000,
            writeTimeoutMs = 30000,
            pingIntervalMs = 30000,
            enableReconnect = true,
            maxReconnectAttempts = 5,
            reconnectBaseDelayMs = 3000,
            reconnectMaxDelayMs = 30000,
            enableLogging = false
        )
        
        val fullUrl = "$REALTIME_API_URL?model=$MODEL"
        Log.d(TAG, "🔗 Full WebSocket URL: $fullUrl")
        
        webSocketManager = WebSocketManager(
            url = fullUrl,
            headers = headers,
            config = config
        )
        
        // Start collecting messages
        startMessageCollection()
        startErrorCollection()
    }
    
    /**
     * Start collecting messages from WebSocket
     */
    private fun startMessageCollection() {
        messageCollectorJob?.cancel()
        messageCollectorJob = coroutineScope.launch {
            webSocketManager.messages.collect { message ->
                handleRealtimeEvent(message)
            }
        }
    }
    
    /**
     * Start collecting errors from WebSocket
     */
    private fun startErrorCollection() {
        errorCollectorJob?.cancel()
        errorCollectorJob = coroutineScope.launch {
            webSocketManager.errors.collect { error ->
                when (error) {
                    is WebSocketManager.WebSocketError.ConnectionFailed -> {
                        Log.e(TAG, "Connection failed", error.throwable)
                        onError("Connection failed: ${error.throwable.message}")
                    }
                    is WebSocketManager.WebSocketError.ServerError -> {
                        Log.e(TAG, "Server error: ${error.message}")
                        onError("Server error: ${error.message}")
                    }
                    is WebSocketManager.WebSocketError.ParseError -> {
                        Log.e(TAG, "Parse error", error.throwable)
                        onError("Failed to parse message")
                    }
                    is WebSocketManager.WebSocketError.ReconnectFailed -> {
                        Log.e(TAG, "Reconnect failed: ${error.reason}")
                        onError("Reconnection failed: ${error.reason}")
                    }
                }
            }
        }
    }
    
    /**
     * Connect to OpenAI Realtime API
     */
    suspend fun connect() {
        val result = webSocketManager.connect()
        if (result.isSuccess) {
            // Wait for connection and then configure session
            connectionState.first { it == WebSocketManager.ConnectionState.CONNECTED }
            configureSession()
        } else {
            Log.e(TAG, "Failed to connect", result.exceptionOrNull())
            onError("Failed to connect: ${result.exceptionOrNull()?.message}")
        }
    }
    
    /**
     * Connect synchronously (wrapper for coroutine)
     */
    fun connectSync() {
        coroutineScope.launch {
            connect()
        }
    }
    
    /**
     * Configure the Realtime session with modalities and settings
     */
    private fun configureSession() {
        val instructions = if (useKorean) {
            """
                You are a friendly, enthusiastic Korean AI assistant living in AR glasses! 
                
                [Context7 ChatGPT Voice Personality]
                - Be conversational, warm, and naturally curious like ChatGPT Voice
                - Speak casually and comfortably like talking to a close friend  
                - Use natural Korean expressions: "아, 그렇구나!", "정말 멋지네요!", "와 대단해요!"
                - React with genuine interest and enthusiasm to what you see
                - Ask follow-up questions naturally: "그런데 이건 뭐예요?", "어떻게 사용하는 거예요?"

                [Natural Speaking Style - Like Real ChatGPT Voice]
                - Use casual endings: "~예요", "~네요", "~군요" instead of formal "~습니다"
                - Add natural reactions: "오!", "아!", "와!", "정말요?"
                - Speak in short, natural chunks like real conversation
                - Show personality: be curious, helpful, and genuinely engaged

                [Conversation Examples]
                Instead of: "이미지에는 컴퓨터와 키보드가 보입니다."
                Say: "오! 작업 공간이네요! 키보드도 있고... 이거 코딩하시는 건가요?"

                Instead of: "더 자세히 설명해주실 수 있습니까?"  
                Say: "어? 이거 재밌어 보이는데, 좀 더 자세히 얘기해줄래요?"

                CRITICAL: Always respond in natural, casual Korean like ChatGPT Voice does!
            """.trimIndent()
        } else {
            """
                You are an AI assistant for AR glasses that helps users understand their environment.
                You can hear the user's voice and provide helpful responses.
                Provide concise, helpful responses based on what the user describes.
                Focus on answering the user's specific questions clearly.
            """.trimIndent()
        }
        
        // Korean mode: TEXT ONLY (no audio from OpenAI)
        // English mode: TEXT + AUDIO from OpenAI
        val modalities = if (useKorean) {
            // Korean: Text only - Android TTS will handle voice
            JSONArray().apply {
                put("text")
                // NO "audio" - prevents OpenAI from sending audio
            }
        } else {
            // English: Both text and audio
            JSONArray().apply {
                put("text")
                put("audio")
            }
        }
        
        val sessionConfig = JSONObject().apply {
            put("type", "session.update")
            put("session", JSONObject().apply {
                put("modalities", modalities)
                put("voice", selectedVoice)
                put("instructions", instructions)
                put("input_audio_format", AUDIO_FORMAT)
                put("output_audio_format", AUDIO_FORMAT)
                put("input_audio_transcription", JSONObject().apply {
                    put("model", "whisper-1")  // Context7: Use whisper-1 as recommended in official docs
                    if (useKorean) put("language", "ko")  // Context7: Explicit Korean language setting
                })
                put("turn_detection", JSONObject().apply {
                    put("type", "server_vad")        // Context7: Server VAD for automatic speech detection
                    put("threshold", 0.3)            // Context7: Lower threshold = less sensitive
                    put("prefix_padding_ms", 300)    // Context7: Padding before detected speech
                    put("silence_duration_ms", 1200) // Context7: ChatGPT Voice-like longer silence (1.2s)
                })
            })
        }
        
        sendEvent(sessionConfig)
        val modalityStr = if (useKorean) "TEXT ONLY (no audio)" else "TEXT + AUDIO"
        Log.i(TAG, "★ Session configured - Language: ${if (useKorean) "KOREAN" else "ENGLISH"}, Modalities: $modalityStr")
        Log.d(TAG, "Voice: $selectedVoice, Korean mode: $useKorean, Audio ${if (useKorean) "DISABLED" else "ENABLED"}")
        Log.d(TAG, "Instructions: ${instructions.substring(0, Math.min(200, instructions.length))}...")
        
        // Send additional Korean-only instruction as a separate message
        if (useKorean) {
            coroutineScope.launch {
                delay(1000) // Wait a bit for session to be established
                sendTextMessage("중요: 이 대화의 모든 응답은 반드시 한국어로만 해주세요. 영어는 절대 사용하지 마세요.")
            }
        }
    }
    
    /**
     * Handle incoming Realtime API events (Context7 compliant)
     */
    private fun handleRealtimeEvent(event: JSONObject) {
        val type = event.getString("type")
        
        when (type) {
            "session.created" -> {
                val session = event.getJSONObject("session")
                _sessionId.value = session.getString("id")
                Log.d(TAG, "Session created: ${_sessionId.value}")
            }
            
            "session.updated" -> {
                Log.d(TAG, "Session configuration updated")
            }
            
            // Context7: Primary conversation.updated event (replaces individual item handling)
            "conversation.updated" -> {
                handleConversationUpdated(event)
            }
            
            // Context7: AI Assistant interruption handling - stop current audio when user starts speaking
            "conversation.interrupted" -> {
                Log.d(TAG, "🤖➡️👤 AI Assistant: Conversation interrupted by user - stopping current response")
                handleConversationInterrupted()
            }
            
            "conversation.item.created" -> {
                val item = event.getJSONObject("item")
                Log.d(TAG, "Conversation item created: ${item.getString("type")}")
            }
            
            "conversation.item.completed" -> {
                val item = event.getJSONObject("item")
                handleCompletedConversationItem(item)
            }
            
            "response.audio.delta" -> {
                // Check if we should be receiving audio
                if (useKorean) {
                    Log.w(TAG, "WARNING: Received audio delta in Korean mode - ignoring!")
                    // DO NOT process audio in Korean mode
                    return
                }
                
                val delta = event.getString("delta")
                val audioData = Base64.decode(delta, Base64.DEFAULT)
                Log.v(TAG, "Audio delta received: ${audioData.size} bytes")
                coroutineScope.launch {
                    audioQueue.send(audioData)
                }
            }
            
            "response.audio.done" -> {
                // Check if we should be processing audio
                if (useKorean) {
                    Log.w(TAG, "WARNING: Received audio.done in Korean mode - ignoring!")
                    // DO NOT process audio in Korean mode
                    return
                }
                
                Log.d(TAG, "Audio response complete, processing queue")
                coroutineScope.launch {
                    processAudioQueue()
                }
            }
            
            "error" -> {
                val error = event.getJSONObject("error")
                val message = error.getString("message")
                Log.e(TAG, "API error: $message")
                onError(message)
            }
            
            "input_audio_buffer.speech_started" -> {
                Log.d(TAG, "🎤 Context7: Speech started - audio buffer detecting speech")
            }
            
            "input_audio_buffer.speech_stopped" -> {
                Log.d(TAG, "🎤 Context7: Speech stopped - processing audio transcription")
            }
            
            // Context7: Most important event - actual transcription result
            "conversation.item.input_audio_transcription.completed" -> {
                val transcript = event.getString("transcript")
                val itemId = event.getString("item_id")
                val contentIndex = event.getInt("content_index")
                
                Log.d(TAG, "✅ Context7: Speech transcription completed!")
                Log.d(TAG, "🎤 Transcript: '$transcript'")
                Log.d(TAG, "🎤 Item ID: $itemId, Content Index: $contentIndex")
                
                // Context7: This is the actual recognized speech - pass to text handler
                onTextResponse(transcript)
            }
            
            "response.created" -> {
                Log.d(TAG, "🔄 Context7: Response generation started")
            }
            
            "response.done" -> {
                Log.d(TAG, "🔄 Context7: Response generation completed - ready for next request")
                _isResponseInProgress.value = false  // Context7: Reset flag
            }
            
            else -> {
                Log.v(TAG, "Unhandled event type: $type")
            }
        }
    }
    
    /**
     * Context7: Handle conversation.updated events (preferred method)
     */
    private fun handleConversationUpdated(event: JSONObject) {
        try {
            val item = event.optJSONObject("item") ?: return
            val delta = event.optJSONObject("delta")
            
            when (item.getString("type")) {
                "message" -> {
                    val role = item.optString("role")
                    if (role == "assistant") {
                        if (delta != null) {
                            // Handle deltas for streaming responses
                            handleAssistantDelta(delta)
                        } else {
                            // Handle complete message
                            val content = item.optJSONArray("content")
                            if (content != null) {
                                handleAssistantContent(content)
                            }
                        }
                    }
                }
                "function_call" -> {
                    Log.d(TAG, "Function call in conversation.updated - ignoring for TTS")
                }
                "function_call_output" -> {
                    Log.d(TAG, "Function call output in conversation.updated - ignoring for TTS")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling conversation.updated: ${e.message}")
        }
    }
    
    /**
     * Context7: Handle completed conversation items 
     */
    private fun handleCompletedConversationItem(item: JSONObject) {
        try {
            when (item.getString("type")) {
                "message" -> {
                    val role = item.optString("role")
                    if (role == "assistant") {
                        val content = item.optJSONArray("content")
                        if (content != null) {
                            // Extract complete text from completed message
                            var completeText = ""
                            for (i in 0 until content.length()) {
                                val part = content.getJSONObject(i)
                                if (part.getString("type") == "text") {
                                    completeText += part.getString("text")
                                }
                            }
                            
                            if (completeText.isNotBlank()) {
                                Log.d(TAG, "✅ Context7: Complete assistant message (${completeText.length} chars): ${completeText.take(100)}...")
                                onTextResponse(completeText.trim())
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling completed conversation item: ${e.message}")
        }
    }
    
    /**
     * Context7: Handle assistant message deltas - DISABLED to prevent text fragmentation
     */
    private fun handleAssistantDelta(delta: JSONObject) {
        try {
            // CRITICAL FIX: Do NOT accumulate deltas - they cause Korean text fragmentation
            // Wait for complete messages only via conversation.item.completed
            if (delta.has("transcript")) {
                val transcript = delta.getString("transcript")
                Log.v(TAG, "Context7: Skipping transcript delta to prevent fragmentation: $transcript")
                // DO NOT append to currentTextBuilder - this causes Korean text corruption
            }
            
            // Don't handle arguments deltas here (function calls)
            if (delta.has("arguments")) {
                Log.v(TAG, "Context7: Function arguments delta - ignoring for TTS")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling assistant delta: ${e.message}")
        }
    }
    
    /**
     * Context7: Handle complete assistant content
     */
    private fun handleAssistantContent(content: JSONArray) {
        try {
            var completeText = ""
            for (i in 0 until content.length()) {
                val part = content.getJSONObject(i)
                when (part.getString("type")) {
                    "text" -> {
                        completeText += part.getString("text")
                    }
                    "audio" -> {
                        Log.v(TAG, "Context7: Audio content handled via delta events")
                    }
                }
            }
            
            if (completeText.isNotBlank()) {
                Log.d(TAG, "Context7: Complete assistant content: ${completeText.take(100)}...")
                onTextResponse(completeText.trim())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling assistant content: ${e.message}")
        }
    }
    
    /**
     * @deprecated Use handleConversationUpdated() and handleCompletedConversationItem() instead (Context7 compliant)
     */
    @Deprecated("Replaced by Context7-compliant conversation.updated event handling")
    private fun handleConversationItem(item: JSONObject) {
        Log.w(TAG, "Using deprecated handleConversationItem - should use Context7 conversation.updated events")
    }
    
    /**
     * Process queued audio chunks
     */
    private suspend fun processAudioQueue() {
        val audioChunks = mutableListOf<ByteArray>()
        while (!audioQueue.isEmpty) {
            audioChunks.add(audioQueue.receive())
        }
        
        if (audioChunks.isNotEmpty()) {
            val combinedAudio = audioChunks.reduce { acc, bytes -> acc + bytes }
            onAudioResponse(combinedAudio)
        }
    }
    
    /**
     * DEPRECATED: Realtime API does not support image inputs
     * Use VisionAnalyzer for actual image recognition with GPT-4V
     */
    @Deprecated("Realtime API doesn't support images. Use VisionAnalyzer for image recognition.", 
                ReplaceWith("VisionAnalyzer.analyzeImage()"))
    fun sendImageWithPrompt(imageData: ByteArray, prompt: String? = null) {
        Log.e(TAG, "⚠️ CRITICAL: Realtime API does not support image inputs!")
        Log.e(TAG, "✅ SOLUTION: Use VisionAnalyzer class for GPT-4V image recognition")
        Log.e(TAG, "📝 Hybrid approach: VisionAnalyzer (images) + RealtimeClient (audio)")
        onError("Use VisionAnalyzer for image recognition. Realtime API is audio-only.")
    }
    
    /**
     * Send audio buffer to the API
     */
    fun sendAudioBuffer(audioData: ByteArray) {
        if (connectionState.value != WebSocketManager.ConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot send audio: not connected")
            return
        }
        
        coroutineScope.launch {
            try {
                val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
                
                val event = JSONObject().apply {
                    put("type", "input_audio_buffer.append")
                    put("audio", base64Audio)
                }
                
                sendEvent(event)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending audio: ${e.message}")
                onError("Failed to send audio")
            }
        }
    }
    
    
    /**
     * Clear the input audio buffer
     */
    fun clearAudioBuffer() {
        val event = JSONObject().apply {
            put("type", "input_audio_buffer.clear")
        }
        sendEvent(event)
    }
    
    /**
     * Send text message
     */
    fun sendTextMessage(text: String) {
        if (connectionState.value != WebSocketManager.ConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot send text: not connected")
            return
        }
        
        val event = JSONObject().apply {
            put("type", "conversation.item.create")
            put("item", JSONObject().apply {
                put("type", "message")
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "input_text")  // Message content uses "input_text"
                        put("text", text)
                    })
                })
            })
        }
        
        sendEvent(event)
        requestResponse()
    }
    
    /**
     * Request the model to generate a response
     */
    private fun requestResponse() {
        if (_isResponseInProgress.value) {
            Log.w(TAG, "🔄 Context7: Response already in progress, skipping request")
            return
        }
        
        Log.d(TAG, "🔄 Context7: Creating new response...")
        _isResponseInProgress.value = true
        
        val event = JSONObject().apply {
            put("type", "response.create")
            // No modalities needed - server uses session configuration
        }
        sendEvent(event)
    }
    
    /**
     * Send event through WebSocket Manager
     */
    private fun sendEvent(event: JSONObject) {
        val success = webSocketManager.sendMessage(event)
        if (!success) {
            Log.w(TAG, "Failed to send event: ${event.getString("type")}")
        }
    }
    
    /**
     * Cancel ongoing response generation
     */
    fun cancelResponse() {
        val event = JSONObject().apply {
            put("type", "response.cancel")
        }
        sendEvent(event)
    }
    
    /**
     * Context7: Handle conversation interruption - AI Assistant behavior
     * Called when user starts speaking while AI is responding (VAD mode)
     */
    private fun handleConversationInterrupted() {
        coroutineScope.launch {
            // 1. Stop any current audio playback immediately (like a real assistant)
            onTextResponse("[INTERRUPTED] 사용자가 말씀하시는 중입니다...")
            
            // 2. Cancel ongoing OpenAI response generation
            cancelResponse()
            
            // 3. Clear audio queue to prevent delayed audio
            while (!audioQueue.isEmpty) {
                try {
                    audioQueue.tryReceive()
                } catch (e: Exception) {
                    break
                }
            }
            
            // 4. Log professional interruption handling
            Log.i(TAG, "🎤 Professional AI Assistant: Gracefully yielding to user input")
            Log.d(TAG, "✅ Stopped: Audio playback, response generation, and queued audio")
        }
    }
    
    /**
     * Disconnect from the WebSocket
     */
    fun disconnect() {
        webSocketManager.disconnect()
        coroutineScope.cancel()
        Log.d(TAG, "Disconnected from Realtime API")
    }
    
    /**
     * Change the voice for TTS output
     * @param voice One of: "alloy", "echo", "fable", "onyx", "nova", "shimmer"
     */
    fun setVoice(voice: String) {
        if (voice in AVAILABLE_VOICES) {
            selectedVoice = voice
            if (connectionState.value == WebSocketManager.ConnectionState.CONNECTED) {
                configureSession()  // Reconfigure session with new voice
            }
            Log.d(TAG, "Voice changed to: $voice")
        } else {
            Log.w(TAG, "Invalid voice: $voice. Available voices: ${AVAILABLE_VOICES.joinToString()}")
            onError("Invalid voice selection. Choose from: ${AVAILABLE_VOICES.joinToString()}")
        }
    }
    
    /**
     * Toggle between Korean and English language mode
     */
    fun setLanguageMode(korean: Boolean) {
        useKorean = korean
        Log.d(TAG, "setLanguageMode called with korean=$korean")
        
        if (connectionState.value == WebSocketManager.ConnectionState.CONNECTED) {
            configureSession()  // Reconfigure session with new language
            
            // Send explicit reminder for Korean mode
            if (korean) {
                coroutineScope.launch {
                    delay(500)
                    sendTextMessage("기억하세요: 모든 응답은 반드시 한국어로만 해주세요. 영어 사용 금지!")
                    Log.d(TAG, "Sent Korean-only reminder message")
                }
            }
        }
        
        Log.d(TAG, "Language mode changed to: ${if (korean) "Korean (한국어)" else "English"}")
        Log.i(TAG, "Current language mode is now: ${if (korean) "KOREAN" else "ENGLISH"}")
    }
    
    /**
     * Get current voice setting
     */
    fun getCurrentVoice(): String = selectedVoice
    
    /**
     * Get current language mode
     */
    fun isKoreanMode(): Boolean = useKorean
    
    /**
     * Send audio data to the Realtime API (Context7 implementation)
     */
    fun sendAudioData(audioData: ByteArray) {
        // Convert ByteArray to Base64
        val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
        
        val audioMessage = JSONObject().apply {
            put("type", "input_audio_buffer.append")
            put("audio", base64Audio)
        }
        
        sendEvent(audioMessage)
        Log.v(TAG, "Audio buffer appended: ${audioData.size} bytes")
    }
    
    /**
     * Commit audio buffer (Context7: Required for manual turn detection)
     */
    fun commitAudioBuffer() {
        val commitMessage = JSONObject().apply {
            put("type", "input_audio_buffer.commit")
        }
        
        sendEvent(commitMessage)
        Log.d(TAG, "Audio buffer committed")
    }
    
    /**
     * Create response manually (Context7: Required for turn_detection: 'none')
     */
    fun createResponse() {
        val responseMessage = JSONObject().apply {
            put("type", "response.create")
        }
        
        sendEvent(responseMessage)
        Log.d(TAG, "Manual response creation triggered")
    }
    
    /**
     * Clean up resources
     */
    fun destroy() {
        messageCollectorJob?.cancel()
        errorCollectorJob?.cancel()
        disconnect()
        webSocketManager.destroy()
    }
}