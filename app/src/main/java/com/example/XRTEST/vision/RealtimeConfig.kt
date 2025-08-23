package com.example.XRTEST.vision

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Configuration manager for OpenAI Realtime API
 * Handles secure storage of API keys and settings
 */
class RealtimeConfig(
    private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "realtime_config"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_VOICE = "voice"
        private const val KEY_INSTRUCTIONS = "instructions"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_DEBUG_MODE = "debug_mode"
        
        // Default values
        const val DEFAULT_MODEL = "gpt-4o-realtime-preview-2024-12-17"
        const val DEFAULT_VOICE = "alloy"
        const val DEFAULT_TEMPERATURE = 0.8
        const val DEFAULT_MAX_TOKENS = 4096
        
        // API endpoints
        const val REALTIME_WS_URL = "wss://api.openai.com/v1/realtime"
        const val REALTIME_API_VERSION = "v1"
        
        // Audio settings
        const val AUDIO_SAMPLE_RATE = 24000
        const val AUDIO_FORMAT = "pcm16"
        
        // Available voices
        val AVAILABLE_VOICES = listOf("alloy", "echo", "shimmer")
        
        // Available models
        val AVAILABLE_MODELS = listOf(
            "gpt-4o-realtime-preview-2024-12-17",
            "gpt-4o-realtime-preview"
        )
    }
    
    private val sharedPreferences: SharedPreferences by lazy {
        createEncryptedSharedPreferences()
    }
    
    /**
     * Create encrypted shared preferences for secure storage
     */
    private fun createEncryptedSharedPreferences(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    // API Key management
    var apiKey: String
        get() = sharedPreferences.getString(KEY_API_KEY, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_API_KEY, value).apply()
    
    val hasApiKey: Boolean
        get() = apiKey.isNotEmpty()
    
    // Model configuration
    var model: String
        get() = sharedPreferences.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = sharedPreferences.edit().putString(KEY_MODEL, value).apply()
    
    // Voice configuration
    var voice: String
        get() = sharedPreferences.getString(KEY_VOICE, DEFAULT_VOICE) ?: DEFAULT_VOICE
        set(value) = sharedPreferences.edit().putString(KEY_VOICE, value).apply()
    
    // Instructions for the AI
    var instructions: String
        get() = sharedPreferences.getString(KEY_INSTRUCTIONS, getDefaultInstructions()) ?: getDefaultInstructions()
        set(value) = sharedPreferences.edit().putString(KEY_INSTRUCTIONS, value).apply()
    
    // Temperature setting
    var temperature: Float
        get() = sharedPreferences.getFloat(KEY_TEMPERATURE, DEFAULT_TEMPERATURE.toFloat())
        set(value) = sharedPreferences.edit().putFloat(KEY_TEMPERATURE, value).apply()
    
    // Max tokens setting
    var maxTokens: Int
        get() = sharedPreferences.getInt(KEY_MAX_TOKENS, DEFAULT_MAX_TOKENS)
        set(value) = sharedPreferences.edit().putInt(KEY_MAX_TOKENS, value).apply()
    
    // Debug mode
    var debugMode: Boolean
        get() = sharedPreferences.getBoolean(KEY_DEBUG_MODE, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_DEBUG_MODE, value).apply()
    
    /**
     * Get default instructions for the AI
     */
    private fun getDefaultInstructions(): String {
        return """
            You are an AI assistant for AR glasses that helps users understand their environment.
            You can see images from the user's camera and hear their voice.
            Provide concise, helpful responses about what you observe.
            Focus on answering the user's specific questions about the scene.
            Be aware that you're providing information for someone wearing AR glasses, so keep responses brief and relevant.
            Prioritize safety and accessibility information when relevant.
        """.trimIndent()
    }
    
    /**
     * Build WebSocket URL with parameters
     */
    fun buildWebSocketUrl(): String {
        return "$REALTIME_WS_URL?model=$model"
    }
    
    /**
     * Get WebSocket headers
     */
    fun getWebSocketHeaders(): Map<String, String> {
        return mapOf(
            "Authorization" to "Bearer $apiKey",
            "OpenAI-Beta" to "realtime=$REALTIME_API_VERSION"
        )
    }
    
    /**
     * Create session configuration
     */
    fun createSessionConfig(): SessionConfig {
        return SessionConfig(
            model = model,
            modalities = listOf("text", "audio"),
            instructions = instructions,
            voice = voice,
            inputAudioFormat = AUDIO_FORMAT,
            outputAudioFormat = AUDIO_FORMAT,
            inputAudioTranscription = TranscriptionSettings(
                model = "whisper-1"
            ),
            turnDetection = TurnDetectionSettings(
                type = "server_vad",
                threshold = 0.5,
                prefixPaddingMs = 300,
                silenceDurationMs = 500
            ),
            temperature = temperature.toDouble(),
            maxResponseOutputTokens = maxTokens
        )
    }
    
    /**
     * Validate configuration
     */
    fun validate(): ConfigValidation {
        val errors = mutableListOf<String>()
        
        if (apiKey.isEmpty()) {
            errors.add("API key is not set")
        } else if (!apiKey.startsWith("sk-")) {
            errors.add("Invalid API key format")
        }
        
        if (model !in AVAILABLE_MODELS) {
            errors.add("Invalid model: $model")
        }
        
        if (voice !in AVAILABLE_VOICES) {
            errors.add("Invalid voice: $voice")
        }
        
        if (temperature < 0 || temperature > 2) {
            errors.add("Temperature must be between 0 and 2")
        }
        
        if (maxTokens < 1 || maxTokens > 128000) {
            errors.add("Max tokens must be between 1 and 128000")
        }
        
        return ConfigValidation(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
    
    /**
     * Reset to default settings
     */
    fun resetToDefaults() {
        sharedPreferences.edit().apply {
            remove(KEY_MODEL)
            remove(KEY_VOICE)
            remove(KEY_INSTRUCTIONS)
            remove(KEY_TEMPERATURE)
            remove(KEY_MAX_TOKENS)
            remove(KEY_DEBUG_MODE)
            apply()
        }
    }
    
    /**
     * Clear all settings including API key
     */
    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
    
    /**
     * Session configuration data class
     */
    data class SessionConfig(
        val model: String,
        val modalities: List<String>,
        val instructions: String,
        val voice: String,
        val inputAudioFormat: String,
        val outputAudioFormat: String,
        val inputAudioTranscription: TranscriptionSettings,
        val turnDetection: TurnDetectionSettings,
        val temperature: Double,
        val maxResponseOutputTokens: Int
    )
    
    /**
     * Transcription settings
     */
    data class TranscriptionSettings(
        val model: String = "whisper-1"
    )
    
    /**
     * Turn detection settings
     */
    data class TurnDetectionSettings(
        val type: String = "server_vad",
        val threshold: Double = 0.5,
        val prefixPaddingMs: Int = 300,
        val silenceDurationMs: Int = 500
    )
    
    /**
     * Configuration validation result
     */
    data class ConfigValidation(
        val isValid: Boolean,
        val errors: List<String>
    )
}

/**
 * Extension functions for easy configuration building
 */
fun RealtimeConfig.SessionConfig.toJsonString(): String {
    return """
        {
            "type": "session.update",
            "session": {
                "model": "$model",
                "modalities": ${modalities.joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]")},
                "instructions": "$instructions",
                "voice": "$voice",
                "input_audio_format": "$inputAudioFormat",
                "output_audio_format": "$outputAudioFormat",
                "input_audio_transcription": {
                    "model": "${inputAudioTranscription.model}"
                },
                "turn_detection": {
                    "type": "${turnDetection.type}",
                    "threshold": ${turnDetection.threshold},
                    "prefix_padding_ms": ${turnDetection.prefixPaddingMs},
                    "silence_duration_ms": ${turnDetection.silenceDurationMs}
                },
                "temperature": $temperature,
                "max_response_output_tokens": $maxResponseOutputTokens
            }
        }
    """.trimIndent()
}