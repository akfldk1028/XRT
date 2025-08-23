package com.example.XRTEST

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * WebSocket client for OpenAI Realtime API with vision capabilities
 * Handles real-time streaming of audio, text, and vision data
 */
class RealtimeVisionClient(
    private val apiKey: String,
    private val config: RealtimeConfig = RealtimeConfig()
) {
    
    data class RealtimeConfig(
        val model: String = "gpt-4o-realtime-preview-2024-12-17",
        val voice: String = "alloy",
        val instructions: String = "You are a helpful AI assistant with vision capabilities on Google Glass XR. Analyze images and provide contextual information about what you see.",
        val temperature: Double = 0.8,
        val maxResponseOutputTokens: Int = 4096,
        val tools: List<FunctionTool> = emptyList(),
        val turnDetection: TurnDetection = TurnDetection(),
        val inputAudioTranscription: TranscriptionConfig? = TranscriptionConfig(),
        val modalityTypes: Set<String> = setOf("text", "audio")
    )
    
    data class TurnDetection(
        val type: String = "server_vad",
        val threshold: Double = 0.5,
        val prefixPaddingMs: Int = 300,
        val silenceDurationMs: Int = 500
    )
    
    data class TranscriptionConfig(
        val model: String = "whisper-1"
    )
    
    data class FunctionTool(
        val type: String = "function",
        val name: String,
        val description: String,
        val parameters: JSONObject
    )
    
    sealed class RealtimeEvent {
        data class SessionCreated(val session: JSONObject) : RealtimeEvent()
        data class SessionUpdated(val session: JSONObject) : RealtimeEvent()
        data class ConversationCreated(val conversation: JSONObject) : RealtimeEvent()
        data class ConversationItemCreated(val item: JSONObject) : RealtimeEvent()
        data class InputAudioBufferSpeechStarted(val audioStartMs: Int, val itemId: String) : RealtimeEvent()
        data class InputAudioBufferSpeechStopped(val audioEndMs: Int, val itemId: String) : RealtimeEvent()
        data class InputAudioBufferCommitted(val itemId: String) : RealtimeEvent()
        data class ResponseCreated(val response: JSONObject) : RealtimeEvent()
        data class ResponseOutputItemAdded(val item: JSONObject) : RealtimeEvent()
        data class ResponseContentPartAdded(val part: JSONObject) : RealtimeEvent()
        data class ResponseAudioTranscriptDelta(val delta: String, val itemId: String) : RealtimeEvent()
        data class ResponseAudioDelta(val delta: String, val itemId: String) : RealtimeEvent()
        data class ResponseTextDelta(val delta: String, val itemId: String) : RealtimeEvent()
        data class ResponseFunctionCallArgumentsDelta(val delta: String, val callId: String) : RealtimeEvent()
        data class ResponseDone(val response: JSONObject) : RealtimeEvent()
        data class RateLimitsUpdated(val rateLimits: JSONObject) : RealtimeEvent()
        data class Error(val error: JSONObject) : RealtimeEvent()
    }
    
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    
    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val eventIdCounter = AtomicLong(0)
    
    private val _events = MutableSharedFlow<RealtimeEvent>(
        replay = 0,
        extraBufferCapacity = 100
    )
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()
    
    private val audioQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private var audioJob: Job? = null
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Connect to OpenAI Realtime API
     */
    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isConnected.get()) {
                return@withContext Result.success(Unit)
            }
            
            val request = Request.Builder()
                .url("wss://api.openai.com/v1/realtime")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("OpenAI-Beta", "realtime=v1")
                .build()
            
            val listener = RealtimeWebSocketListener()
            webSocket = client.newWebSocket(request, listener)
            
            // Wait for connection confirmation
            withTimeout(10000) {
                while (!isConnected.get()) {
                    delay(100)
                }
            }
            
            // Configure session
            updateSession()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Disconnect from the API
     */
    fun disconnect() {
        isConnected.set(false)
        audioJob?.cancel()
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
    }
    
    /**
     * Send text message
     */
    suspend fun sendText(text: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val event = JSONObject().apply {
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
            
            sendEvent(event)
            createResponse()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Send image for vision analysis
     */
    suspend fun sendImage(bitmap: Bitmap, prompt: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Convert bitmap to base64
            val base64Image = bitmapToBase64(bitmap)
            
            // Create message with image
            val contentArray = JSONArray().apply {
                // Add image
                put(JSONObject().apply {
                    put("type", "input_text")
                    put("text", "data:image/jpeg;base64,$base64Image")
                })
                
                // Add prompt if provided
                prompt?.let {
                    put(JSONObject().apply {
                        put("type", "input_text")
                        put("text", it)
                    })
                }
            }
            
            val event = JSONObject().apply {
                put("type", "conversation.item.create")
                put("item", JSONObject().apply {
                    put("type", "message")
                    put("role", "user")
                    put("content", contentArray)
                })
            }
            
            sendEvent(event)
            createResponse()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Send audio buffer
     */
    suspend fun sendAudio(audioData: ByteArray) {
        if (!isConnected.get()) return
        audioQueue.send(audioData)
    }
    
    /**
     * Commit audio buffer and get response
     */
    suspend fun commitAudio(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val event = JSONObject().apply {
                put("type", "input_audio_buffer.commit")
            }
            sendEvent(event)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Clear audio buffer
     */
    suspend fun clearAudioBuffer(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val event = JSONObject().apply {
                put("type", "input_audio_buffer.clear")
            }
            sendEvent(event)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Create response
     */
    private suspend fun createResponse() {
        val event = JSONObject().apply {
            put("type", "response.create")
        }
        sendEvent(event)
    }
    
    /**
     * Update session configuration
     */
    private suspend fun updateSession() {
        val sessionConfig = JSONObject().apply {
            put("type", "session.update")
            put("session", JSONObject().apply {
                put("modalities", JSONArray(config.modalityTypes.toList()))
                put("instructions", config.instructions)
                put("voice", config.voice)
                put("input_audio_format", "pcm16")
                put("output_audio_format", "pcm16")
                put("input_audio_transcription", config.inputAudioTranscription?.let {
                    JSONObject().apply {
                        put("model", it.model)
                    }
                })
                put("turn_detection", JSONObject().apply {
                    put("type", config.turnDetection.type)
                    put("threshold", config.turnDetection.threshold)
                    put("prefix_padding_ms", config.turnDetection.prefixPaddingMs)
                    put("silence_duration_ms", config.turnDetection.silenceDurationMs)
                })
                put("tools", JSONArray().apply {
                    config.tools.forEach { tool ->
                        put(JSONObject().apply {
                            put("type", tool.type)
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", tool.parameters)
                        })
                    }
                })
                put("tool_choice", "auto")
                put("temperature", config.temperature)
                put("max_response_output_tokens", config.maxResponseOutputTokens)
            })
        }
        
        sendEvent(sessionConfig)
    }
    
    /**
     * Send event to WebSocket
     */
    private suspend fun sendEvent(event: JSONObject) {
        if (!isConnected.get()) {
            throw IllegalStateException("Not connected to Realtime API")
        }
        
        event.put("event_id", "evt_${eventIdCounter.incrementAndGet()}")
        webSocket?.send(event.toString())
    }
    
    /**
     * Start audio streaming job
     */
    private fun startAudioStreaming() {
        audioJob?.cancel()
        audioJob = coroutineScope.launch {
            while (isActive) {
                try {
                    val audioData = audioQueue.receive()
                    if (isConnected.get()) {
                        val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
                        val event = JSONObject().apply {
                            put("type", "input_audio_buffer.append")
                            put("audio", base64Audio)
                        }
                        sendEvent(event)
                    }
                } catch (e: Exception) {
                    // Handle error
                }
            }
        }
    }
    
    /**
     * Convert Bitmap to base64 string
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val imageBytes = outputStream.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
    }
    
    /**
     * WebSocket listener
     */
    private inner class RealtimeWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            isConnected.set(true)
            startAudioStreaming()
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            coroutineScope.launch {
                try {
                    val json = JSONObject(text)
                    val event = parseEvent(json)
                    event?.let { _events.emit(it) }
                } catch (e: Exception) {
                    _events.emit(RealtimeEvent.Error(JSONObject().apply {
                        put("message", "Failed to parse message: ${e.message}")
                    }))
                }
            }
        }
        
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            // Binary messages not expected in this API
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            isConnected.set(false)
            coroutineScope.launch {
                _events.emit(RealtimeEvent.Error(JSONObject().apply {
                    put("message", "WebSocket failure: ${t.message}")
                }))
            }
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            isConnected.set(false)
            audioJob?.cancel()
        }
    }
    
    /**
     * Parse WebSocket event
     */
    private fun parseEvent(json: JSONObject): RealtimeEvent? {
        return when (json.getString("type")) {
            "session.created" -> RealtimeEvent.SessionCreated(json.getJSONObject("session"))
            "session.updated" -> RealtimeEvent.SessionUpdated(json.getJSONObject("session"))
            "conversation.created" -> RealtimeEvent.ConversationCreated(json.getJSONObject("conversation"))
            "conversation.item.created" -> RealtimeEvent.ConversationItemCreated(json.getJSONObject("item"))
            "input_audio_buffer.speech_started" -> RealtimeEvent.InputAudioBufferSpeechStarted(
                json.getInt("audio_start_ms"),
                json.getString("item_id")
            )
            "input_audio_buffer.speech_stopped" -> RealtimeEvent.InputAudioBufferSpeechStopped(
                json.getInt("audio_end_ms"),
                json.getString("item_id")
            )
            "input_audio_buffer.committed" -> RealtimeEvent.InputAudioBufferCommitted(
                json.getString("item_id")
            )
            "response.created" -> RealtimeEvent.ResponseCreated(json.getJSONObject("response"))
            "response.output_item.added" -> RealtimeEvent.ResponseOutputItemAdded(json.getJSONObject("item"))
            "response.content_part.added" -> RealtimeEvent.ResponseContentPartAdded(json.getJSONObject("part"))
            "response.audio_transcript.delta" -> RealtimeEvent.ResponseAudioTranscriptDelta(
                json.getString("delta"),
                json.getString("item_id")
            )
            "response.audio.delta" -> RealtimeEvent.ResponseAudioDelta(
                json.getString("delta"),
                json.getString("item_id")
            )
            "response.text.delta" -> RealtimeEvent.ResponseTextDelta(
                json.getString("delta"),
                json.getString("item_id")
            )
            "response.function_call_arguments.delta" -> RealtimeEvent.ResponseFunctionCallArgumentsDelta(
                json.getString("delta"),
                json.getString("call_id")
            )
            "response.done" -> RealtimeEvent.ResponseDone(json.getJSONObject("response"))
            "rate_limits.updated" -> RealtimeEvent.RateLimitsUpdated(json.getJSONObject("rate_limits"))
            "error" -> RealtimeEvent.Error(json.getJSONObject("error"))
            else -> null
        }
    }
    
    /**
     * Clean up resources
     */
    fun dispose() {
        disconnect()
        coroutineScope.cancel()
    }
}