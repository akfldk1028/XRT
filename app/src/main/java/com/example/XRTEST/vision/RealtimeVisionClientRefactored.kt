package com.example.XRTEST.vision

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Refactored OpenAI Realtime API Client using WebSocketManager
 * Separates WebSocket management from business logic
 */
class RealtimeVisionClientRefactored(
    private val apiKey: String,
    private val onAudioResponse: (ByteArray) -> Unit,
    private val onTextResponse: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    
    companion object {
        private const val TAG = "RealtimeVisionClientRefactored"
        private const val REALTIME_API_URL = "wss://api.openai.com/v1/realtime"
        private const val MODEL = "gpt-4o-realtime-preview-2024-12-17"
        private const val VOICE = "alloy"
        private const val SAMPLE_RATE = 24000
        private const val AUDIO_FORMAT = "pcm16"
    }
    
    private val webSocketManager: WebSocketManager
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var sessionId: String? = null
    
    // Audio buffer management
    private val audioBuffer = mutableListOf<ByteArray>()
    private var isAudioBuffering = false
    
    init {
        // Configure WebSocket with OpenAI specific settings
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
        
        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "OpenAI-Beta" to "realtime=v1"
        )
        
        webSocketManager = WebSocketManager(
            url = "$REALTIME_API_URL?model=$MODEL",
            headers = headers,
            config = config
        )
        
        // Subscribe to WebSocket events
        setupEventListeners()
    }
    
    /**
     * Setup event listeners for WebSocket messages
     */
    private fun setupEventListeners() {
        // Listen for connection state changes
        coroutineScope.launch {
            webSocketManager.connectionState.collect { state ->
                when (state) {
                    WebSocketManager.ConnectionState.CONNECTED -> {
                        Log.d(TAG, "Connected to OpenAI Realtime API")
                        configureSession()
                    }
                    WebSocketManager.ConnectionState.ERROR -> {
                        onError("Connection error")
                    }
                    else -> {
                        Log.d(TAG, "Connection state: $state")
                    }
                }
            }
        }
        
        // Listen for messages
        coroutineScope.launch {
            webSocketManager.messages.collect { message ->
                handleRealtimeEvent(message)
            }
        }
        
        // Listen for errors
        coroutineScope.launch {
            webSocketManager.errors.collect { error ->
                when (error) {
                    is WebSocketManager.WebSocketError.ConnectionFailed -> {
                        onError("Connection failed: ${error.throwable.message}")
                    }
                    is WebSocketManager.WebSocketError.ServerError -> {
                        onError("Server error: ${error.message}")
                    }
                    is WebSocketManager.WebSocketError.ParseError -> {
                        onError("Parse error: ${error.throwable.message}")
                    }
                    is WebSocketManager.WebSocketError.ReconnectFailed -> {
                        onError("Reconnect failed: ${error.reason}")
                    }
                }
            }
        }
    }
    
    /**
     * Connect to OpenAI Realtime API
     */
    suspend fun connect() {
        webSocketManager.connect()
    }
    
    /**
     * Configure session with OpenAI specific settings
     */
    private fun configureSession() {
        val sessionConfig = JSONObject().apply {
            put("type", "session.update")
            put("session", JSONObject().apply {
                put("modalities", JSONArray().apply {
                    put("text")
                    put("audio")
                })
                put("instructions", "You are a helpful assistant for an AR Glass application. " +
                    "Analyze images and answer questions about what you see. " +
                    "Provide clear, concise responses suitable for AR display.")
                put("voice", VOICE)
                put("input_audio_format", AUDIO_FORMAT)
                put("output_audio_format", AUDIO_FORMAT)
                put("input_audio_transcription", JSONObject().apply {
                    put("model", "whisper-1")
                })
                put("turn_detection", JSONObject().apply {
                    put("type", "server_vad")
                    put("threshold", 0.5)
                    put("prefix_padding_ms", 300)
                    put("silence_duration_ms", 200)
                })
                put("tools", JSONArray())
                put("tool_choice", "auto")
                put("temperature", 0.8)
                put("max_response_output_tokens", "inf")
            })
        }
        
        webSocketManager.sendMessage(sessionConfig)
        Log.d(TAG, "Session configuration sent")
    }
    
    /**
     * Handle events from OpenAI Realtime API
     */
    private fun handleRealtimeEvent(event: JSONObject) {
        val eventType = event.optString("type")
        
        when (eventType) {
            "session.created" -> {
                val session = event.getJSONObject("session")
                sessionId = session.getString("id")
                Log.d(TAG, "Session created: $sessionId")
            }
            
            "session.updated" -> {
                Log.d(TAG, "Session updated")
            }
            
            "conversation.item.created" -> {
                val item = event.getJSONObject("item")
                if (item.getString("role") == "assistant") {
                    Log.d(TAG, "Assistant response started")
                }
            }
            
            "response.audio_transcript.delta" -> {
                val delta = event.getString("delta")
                Log.v(TAG, "Transcript delta: $delta")
            }
            
            "response.audio_transcript.done" -> {
                val transcript = event.getString("transcript")
                Log.d(TAG, "Final transcript: $transcript")
                onTextResponse(transcript)
            }
            
            "response.audio.delta" -> {
                val audioDelta = event.getString("delta")
                val audioBytes = Base64.decode(audioDelta, Base64.NO_WRAP)
                
                if (isAudioBuffering) {
                    audioBuffer.add(audioBytes)
                } else {
                    onAudioResponse(audioBytes)
                }
            }
            
            "response.audio.done" -> {
                Log.d(TAG, "Audio response complete")
                if (isAudioBuffering && audioBuffer.isNotEmpty()) {
                    val totalSize = audioBuffer.sumOf { it.size }
                    val completeAudio = ByteArray(totalSize)
                    var offset = 0
                    audioBuffer.forEach { chunk ->
                        System.arraycopy(chunk, 0, completeAudio, offset, chunk.size)
                        offset += chunk.size
                    }
                    onAudioResponse(completeAudio)
                    audioBuffer.clear()
                }
            }
            
            "response.done" -> {
                val response = event.getJSONObject("response")
                val usage = response.optJSONObject("usage")
                usage?.let {
                    Log.d(TAG, "Usage - Input tokens: ${it.optInt("input_tokens")}, " +
                        "Output tokens: ${it.optInt("output_tokens")}")
                }
            }
            
            "error" -> {
                val error = event.getJSONObject("error")
                val errorMessage = error.getString("message")
                Log.e(TAG, "Server error: $errorMessage")
                onError(errorMessage)
            }
            
            else -> {
                Log.v(TAG, "Event: $eventType")
            }
        }
    }
    
    /**
     * Send image with text prompt
     */
    fun sendImageWithPrompt(imageData: ByteArray, prompt: String) {
        val base64Image = Base64.encodeToString(imageData, Base64.NO_WRAP)
        
        val message = JSONObject().apply {
            put("type", "conversation.item.create")
            put("item", JSONObject().apply {
                put("type", "message")
                put("role", "user")
                put("content", JSONArray().apply {
                    // Add text content
                    put(JSONObject().apply {
                        put("type", "input_text")
                        put("text", prompt)
                    })
                    // Add image content
                    put(JSONObject().apply {
                        put("type", "input_image")
                        put("image", "data:image/jpeg;base64,$base64Image")
                    })
                })
            })
        }
        
        webSocketManager.sendMessage(message)
        
        // Trigger response generation
        val generateResponse = JSONObject().apply {
            put("type", "response.create")
        }
        webSocketManager.sendMessage(generateResponse)
        
        Log.d(TAG, "Sent image (${imageData.size} bytes) with prompt: $prompt")
    }
    
    /**
     * Send audio buffer for processing
     */
    fun sendAudioBuffer(audioData: ByteArray) {
        // Convert to base64
        val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
        
        val message = JSONObject().apply {
            put("type", "input_audio_buffer.append")
            put("audio", base64Audio)
        }
        
        webSocketManager.sendMessage(message)
        Log.v(TAG, "Sent audio buffer: ${audioData.size} bytes")
    }
    
    /**
     * Commit audio buffer and trigger response
     */
    fun commitAudioBuffer() {
        val message = JSONObject().apply {
            put("type", "input_audio_buffer.commit")
        }
        
        webSocketManager.sendMessage(message)
        Log.d(TAG, "Audio buffer committed")
    }
    
    /**
     * Clear audio buffer
     */
    fun clearAudioBuffer() {
        val message = JSONObject().apply {
            put("type", "input_audio_buffer.clear")
        }
        
        webSocketManager.sendMessage(message)
        audioBuffer.clear()
        Log.d(TAG, "Audio buffer cleared")
    }
    
    /**
     * Send text message
     */
    fun sendTextMessage(text: String) {
        val message = JSONObject().apply {
            put("type", "conversation.item.create")
            put("item", JSONObject().apply {
                put("type", "message")
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "input_text")
                        put("text", text)
                    })
                })
            })
        }
        
        webSocketManager.sendMessage(message)
        
        // Trigger response
        val generateResponse = JSONObject().apply {
            put("type", "response.create")
        }
        webSocketManager.sendMessage(generateResponse)
        
        Log.d(TAG, "Sent text message: $text")
    }
    
    /**
     * Cancel current response generation
     */
    fun cancelResponse() {
        val message = JSONObject().apply {
            put("type", "response.cancel")
        }
        
        webSocketManager.sendMessage(message)
        Log.d(TAG, "Response cancelled")
    }
    
    /**
     * Get connection state
     */
    fun getConnectionState(): Flow<WebSocketManager.ConnectionState> {
        return webSocketManager.connectionState
    }
    
    /**
     * Check if connected
     */
    fun isConnected(): Boolean {
        return webSocketManager.connectionState.value == WebSocketManager.ConnectionState.CONNECTED
    }
    
    /**
     * Disconnect from API
     */
    fun disconnect() {
        webSocketManager.disconnect()
        Log.d(TAG, "Disconnected from OpenAI Realtime API")
    }
    
    /**
     * Clean up resources
     */
    fun destroy() {
        coroutineScope.cancel()
        webSocketManager.destroy()
        Log.d(TAG, "Resources cleaned up")
    }
}