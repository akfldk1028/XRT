package com.example.XRTEST.vision

import org.json.JSONArray
import org.json.JSONObject

/**
 * Data models for OpenAI Realtime API
 */

// Session Models
data class RealtimeSession(
    val id: String,
    val model: String,
    val modalities: List<String>,
    val instructions: String?,
    val voice: String,
    val inputAudioFormat: String,
    val outputAudioFormat: String,
    val inputAudioTranscription: TranscriptionConfig?,
    val turnDetection: TurnDetectionConfig?,
    val tools: List<RealtimeTool>,
    val temperature: Double,
    val maxResponseOutputTokens: Int?
) {
    companion object {
        fun fromJSON(json: JSONObject): RealtimeSession {
            return RealtimeSession(
                id = json.getString("id"),
                model = json.getString("model"),
                modalities = json.getJSONArray("modalities").toStringList(),
                instructions = json.optString("instructions"),
                voice = json.getString("voice"),
                inputAudioFormat = json.getString("input_audio_format"),
                outputAudioFormat = json.getString("output_audio_format"),
                inputAudioTranscription = json.optJSONObject("input_audio_transcription")?.let {
                    TranscriptionConfig.fromJSON(it)
                },
                turnDetection = json.optJSONObject("turn_detection")?.let {
                    TurnDetectionConfig.fromJSON(it)
                },
                tools = json.optJSONArray("tools")?.let { array ->
                    (0 until array.length()).map {
                        RealtimeTool.fromJSON(array.getJSONObject(it))
                    }
                } ?: emptyList(),
                temperature = json.optDouble("temperature", 0.8),
                maxResponseOutputTokens = json.optInt("max_response_output_tokens")
                    .takeIf { it > 0 }
            )
        }
    }
    
    fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("model", model)
            put("modalities", JSONArray(modalities))
            instructions?.let { put("instructions", it) }
            put("voice", voice)
            put("input_audio_format", inputAudioFormat)
            put("output_audio_format", outputAudioFormat)
            inputAudioTranscription?.let { 
                put("input_audio_transcription", it.toJSON())
            }
            turnDetection?.let {
                put("turn_detection", it.toJSON())
            }
            if (tools.isNotEmpty()) {
                put("tools", JSONArray().apply {
                    tools.forEach { put(it.toJSON()) }
                })
            }
            put("temperature", temperature)
            maxResponseOutputTokens?.let { put("max_response_output_tokens", it) }
        }
    }
}

// Transcription Configuration
data class TranscriptionConfig(
    val model: String = "whisper-1"
) {
    companion object {
        fun fromJSON(json: JSONObject): TranscriptionConfig {
            return TranscriptionConfig(
                model = json.optString("model", "whisper-1")
            )
        }
    }
    
    fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("model", model)
        }
    }
}

// Turn Detection Configuration
data class TurnDetectionConfig(
    val type: String,
    val threshold: Double?,
    val prefixPaddingMs: Int?,
    val silenceDurationMs: Int?
) {
    companion object {
        fun fromJSON(json: JSONObject): TurnDetectionConfig {
            return TurnDetectionConfig(
                type = json.getString("type"),
                threshold = json.optDouble("threshold").takeIf { !it.isNaN() },
                prefixPaddingMs = json.optInt("prefix_padding_ms").takeIf { it > 0 },
                silenceDurationMs = json.optInt("silence_duration_ms").takeIf { it > 0 }
            )
        }
    }
    
    fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("type", type)
            threshold?.let { put("threshold", it) }
            prefixPaddingMs?.let { put("prefix_padding_ms", it) }
            silenceDurationMs?.let { put("silence_duration_ms", it) }
        }
    }
}

// Tool Definition
data class RealtimeTool(
    val type: String,
    val name: String,
    val description: String,
    val parameters: JSONObject
) {
    companion object {
        fun fromJSON(json: JSONObject): RealtimeTool {
            val function = json.getJSONObject("function")
            return RealtimeTool(
                type = json.getString("type"),
                name = function.getString("name"),
                description = function.getString("description"),
                parameters = function.getJSONObject("parameters")
            )
        }
    }
    
    fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("type", type)
            put("function", JSONObject().apply {
                put("name", name)
                put("description", description)
                put("parameters", parameters)
            })
        }
    }
}

// Conversation Models
data class ConversationItem(
    val id: String,
    val type: String,
    val role: String?,
    val content: List<ContentPart>,
    val status: String?,
    val callId: String?,
    val name: String?,
    val arguments: String?
) {
    companion object {
        fun fromJSON(json: JSONObject): ConversationItem {
            return ConversationItem(
                id = json.getString("id"),
                type = json.getString("type"),
                role = json.optString("role"),
                content = json.optJSONArray("content")?.let { array ->
                    (0 until array.length()).map {
                        ContentPart.fromJSON(array.getJSONObject(it))
                    }
                } ?: emptyList(),
                status = json.optString("status"),
                callId = json.optString("call_id"),
                name = json.optString("name"),
                arguments = json.optString("arguments")
            )
        }
    }
}

// Content Parts
sealed class ContentPart {
    data class Text(val text: String) : ContentPart()
    data class Audio(val audio: String, val transcript: String?) : ContentPart()
    data class Image(val image: String) : ContentPart()
    
    companion object {
        fun fromJSON(json: JSONObject): ContentPart {
            return when (json.getString("type")) {
                "text", "input_text" -> Text(json.getString("text"))
                "audio", "input_audio" -> Audio(
                    json.getString("audio"),
                    json.optString("transcript")
                )
                "image", "input_image" -> Image(json.getString("image"))
                else -> throw IllegalArgumentException("Unknown content type: ${json.getString("type")}")
            }
        }
    }
    
    fun toJSON(): JSONObject {
        return when (this) {
            is Text -> JSONObject().apply {
                put("type", "input_text")
                put("text", text)
            }
            is Audio -> JSONObject().apply {
                put("type", "input_audio")
                put("audio", audio)
                transcript?.let { put("transcript", it) }
            }
            is Image -> JSONObject().apply {
                put("type", "input_image")
                put("image", image)
            }
        }
    }
}

// Response Models
data class RealtimeResponse(
    val id: String,
    val status: String,
    val statusDetails: ResponseStatusDetails?,
    val output: List<ConversationItem>,
    val usage: ResponseUsage?
) {
    companion object {
        fun fromJSON(json: JSONObject): RealtimeResponse {
            return RealtimeResponse(
                id = json.getString("id"),
                status = json.getString("status"),
                statusDetails = json.optJSONObject("status_details")?.let {
                    ResponseStatusDetails.fromJSON(it)
                },
                output = json.optJSONArray("output")?.let { array ->
                    (0 until array.length()).map {
                        ConversationItem.fromJSON(array.getJSONObject(it))
                    }
                } ?: emptyList(),
                usage = json.optJSONObject("usage")?.let {
                    ResponseUsage.fromJSON(it)
                }
            )
        }
    }
}

data class ResponseStatusDetails(
    val type: String,
    val reason: String?,
    val error: ResponseError?
) {
    companion object {
        fun fromJSON(json: JSONObject): ResponseStatusDetails {
            return ResponseStatusDetails(
                type = json.getString("type"),
                reason = json.optString("reason"),
                error = json.optJSONObject("error")?.let {
                    ResponseError.fromJSON(it)
                }
            )
        }
    }
}

data class ResponseError(
    val type: String,
    val code: String?,
    val message: String,
    val param: String?
) {
    companion object {
        fun fromJSON(json: JSONObject): ResponseError {
            return ResponseError(
                type = json.getString("type"),
                code = json.optString("code"),
                message = json.getString("message"),
                param = json.optString("param")
            )
        }
    }
}

data class ResponseUsage(
    val totalTokens: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val inputTokenDetails: TokenDetails?,
    val outputTokenDetails: TokenDetails?
) {
    companion object {
        fun fromJSON(json: JSONObject): ResponseUsage {
            return ResponseUsage(
                totalTokens = json.getInt("total_tokens"),
                inputTokens = json.getInt("input_tokens"),
                outputTokens = json.getInt("output_tokens"),
                inputTokenDetails = json.optJSONObject("input_token_details")?.let {
                    TokenDetails.fromJSON(it)
                },
                outputTokenDetails = json.optJSONObject("output_token_details")?.let {
                    TokenDetails.fromJSON(it)
                }
            )
        }
    }
}

data class TokenDetails(
    val cachedTokens: Int,
    val textTokens: Int,
    val audioTokens: Int
) {
    companion object {
        fun fromJSON(json: JSONObject): TokenDetails {
            return TokenDetails(
                cachedTokens = json.optInt("cached_tokens"),
                textTokens = json.optInt("text_tokens"),
                audioTokens = json.optInt("audio_tokens")
            )
        }
    }
}

// Event Types
sealed class RealtimeEvent {
    abstract val type: String
    
    // Client Events
    data class SessionUpdate(
        val session: RealtimeSession
    ) : RealtimeEvent() {
        override val type = "session.update"
    }
    
    data class InputAudioBufferAppend(
        val audio: String
    ) : RealtimeEvent() {
        override val type = "input_audio_buffer.append"
    }
    
    object InputAudioBufferCommit : RealtimeEvent() {
        override val type = "input_audio_buffer.commit"
    }
    
    object InputAudioBufferClear : RealtimeEvent() {
        override val type = "input_audio_buffer.clear"
    }
    
    data class ConversationItemCreate(
        val item: ConversationItem
    ) : RealtimeEvent() {
        override val type = "conversation.item.create"
    }
    
    data class ConversationItemTruncate(
        val itemId: String,
        val contentIndex: Int,
        val audioEndMs: Int
    ) : RealtimeEvent() {
        override val type = "conversation.item.truncate"
    }
    
    data class ConversationItemDelete(
        val itemId: String
    ) : RealtimeEvent() {
        override val type = "conversation.item.delete"
    }
    
    data class ResponseCreate(
        val modalities: List<String>?,
        val instructions: String?,
        val voice: String?,
        val outputAudioFormat: String?,
        val tools: List<RealtimeTool>?,
        val toolChoice: String?,
        val temperature: Double?,
        val maxOutputTokens: Int?
    ) : RealtimeEvent() {
        override val type = "response.create"
    }
    
    object ResponseCancel : RealtimeEvent() {
        override val type = "response.cancel"
    }
    
    // Server Events
    data class SessionCreated(
        val session: RealtimeSession
    ) : RealtimeEvent() {
        override val type = "session.created"
    }
    
    data class SessionUpdated(
        val session: RealtimeSession
    ) : RealtimeEvent() {
        override val type = "session.updated"
    }
    
    data class ConversationCreated(
        val conversation: Conversation
    ) : RealtimeEvent() {
        override val type = "conversation.created"
    }
    
    data class ResponseCreated(
        val response: RealtimeResponse
    ) : RealtimeEvent() {
        override val type = "response.created"
    }
    
    data class ResponseDone(
        val response: RealtimeResponse
    ) : RealtimeEvent() {
        override val type = "response.done"
    }
    
    data class ResponseAudioDelta(
        val responseId: String,
        val itemId: String,
        val outputIndex: Int,
        val contentIndex: Int,
        val delta: String
    ) : RealtimeEvent() {
        override val type = "response.audio.delta"
    }
    
    data class ResponseAudioDone(
        val responseId: String,
        val itemId: String,
        val outputIndex: Int,
        val contentIndex: Int
    ) : RealtimeEvent() {
        override val type = "response.audio.done"
    }
    
    data class ResponseTextDelta(
        val responseId: String,
        val itemId: String,
        val outputIndex: Int,
        val contentIndex: Int,
        val delta: String
    ) : RealtimeEvent() {
        override val type = "response.text.delta"
    }
    
    data class ResponseTextDone(
        val responseId: String,
        val itemId: String,
        val outputIndex: Int,
        val contentIndex: Int,
        val text: String
    ) : RealtimeEvent() {
        override val type = "response.text.done"
    }
    
    data class RateLimitsUpdated(
        val rateLimits: List<RateLimit>
    ) : RealtimeEvent() {
        override val type = "rate_limits.updated"
    }
    
    data class Error(
        val error: ResponseError
    ) : RealtimeEvent() {
        override val type = "error"
    }
}

// Conversation
data class Conversation(
    val id: String,
    val object_: String
) {
    companion object {
        fun fromJSON(json: JSONObject): Conversation {
            return Conversation(
                id = json.getString("id"),
                object_ = json.getString("object")
            )
        }
    }
}

// Rate Limits
data class RateLimit(
    val name: String,
    val limit: Int,
    val remaining: Int,
    val resetSeconds: Double
) {
    companion object {
        fun fromJSON(json: JSONObject): RateLimit {
            return RateLimit(
                name = json.getString("name"),
                limit = json.getInt("limit"),
                remaining = json.getInt("remaining"),
                resetSeconds = json.getDouble("reset_seconds")
            )
        }
    }
}

// Extension functions
fun JSONArray.toStringList(): List<String> {
    return (0 until length()).map { getString(it) }
}