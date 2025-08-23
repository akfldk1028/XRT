package com.example.XRTEST.vision

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * TTS Configuration Manager for AR Glass Q&A System
 * Manages Text-to-Speech settings for optimal Korean/English support
 */
class TtsConfiguration(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "tts_configuration"
        private const val KEY_USE_ANDROID_FOR_KOREAN = "use_android_for_korean"
        private const val KEY_FORCE_ANDROID_TTS = "force_android_tts"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_PREFERRED_VOICE = "preferred_voice"
        
        const val TTS_MODE_AUTO = "auto"  // Auto-select based on language
        const val TTS_MODE_ANDROID = "android"  // Always use Android TTS
        const val TTS_MODE_OPENAI = "openai"  // Always use OpenAI (when available)
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Configuration states
    private val _ttsMode = MutableStateFlow(TTS_MODE_AUTO)
    val ttsMode: StateFlow<String> = _ttsMode.asStateFlow()
    
    private val _useAndroidForKorean = MutableStateFlow(true)
    val useAndroidForKorean: StateFlow<Boolean> = _useAndroidForKorean.asStateFlow()
    
    private val _forceAndroidTts = MutableStateFlow(false)
    val forceAndroidTts: StateFlow<Boolean> = _forceAndroidTts.asStateFlow()
    
    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()
    
    private val _preferredVoice = MutableStateFlow("alloy")
    val preferredVoice: StateFlow<String> = _preferredVoice.asStateFlow()
    
    init {
        loadConfiguration()
    }
    
    /**
     * Load saved configuration from SharedPreferences
     */
    private fun loadConfiguration() {
        _useAndroidForKorean.value = prefs.getBoolean(KEY_USE_ANDROID_FOR_KOREAN, true)
        _forceAndroidTts.value = prefs.getBoolean(KEY_FORCE_ANDROID_TTS, false)
        _speechRate.value = prefs.getFloat(KEY_SPEECH_RATE, 1.0f)
        _preferredVoice.value = prefs.getString(KEY_PREFERRED_VOICE, "alloy") ?: "alloy"
        
        // Determine TTS mode based on settings
        _ttsMode.value = when {
            _forceAndroidTts.value -> TTS_MODE_ANDROID
            _useAndroidForKorean.value -> TTS_MODE_AUTO
            else -> TTS_MODE_OPENAI
        }
    }
    
    /**
     * Save configuration to SharedPreferences
     */
    private fun saveConfiguration() {
        prefs.edit().apply {
            putBoolean(KEY_USE_ANDROID_FOR_KOREAN, _useAndroidForKorean.value)
            putBoolean(KEY_FORCE_ANDROID_TTS, _forceAndroidTts.value)
            putFloat(KEY_SPEECH_RATE, _speechRate.value)
            putString(KEY_PREFERRED_VOICE, _preferredVoice.value)
            apply()
        }
    }
    
    /**
     * Set TTS mode
     * @param mode One of TTS_MODE_AUTO, TTS_MODE_ANDROID, TTS_MODE_OPENAI
     */
    fun setTtsMode(mode: String) {
        _ttsMode.value = mode
        
        when (mode) {
            TTS_MODE_AUTO -> {
                _useAndroidForKorean.value = true
                _forceAndroidTts.value = false
            }
            TTS_MODE_ANDROID -> {
                _useAndroidForKorean.value = true
                _forceAndroidTts.value = true
            }
            TTS_MODE_OPENAI -> {
                _useAndroidForKorean.value = false
                _forceAndroidTts.value = false
            }
        }
        
        saveConfiguration()
    }
    
    /**
     * Set whether to use Android TTS for Korean
     */
    fun setUseAndroidForKorean(use: Boolean) {
        _useAndroidForKorean.value = use
        saveConfiguration()
    }
    
    /**
     * Set whether to force Android TTS for all languages
     */
    fun setForceAndroidTts(force: Boolean) {
        _forceAndroidTts.value = force
        saveConfiguration()
    }
    
    /**
     * Set speech rate for TTS
     * @param rate Speech rate (0.5 to 2.0)
     */
    fun setSpeechRate(rate: Float) {
        if (rate in 0.5f..2.0f) {
            _speechRate.value = rate
            saveConfiguration()
        }
    }
    
    /**
     * Set preferred OpenAI voice
     * @param voice Voice name (alloy, echo, fable, onyx, nova, shimmer)
     */
    fun setPreferredVoice(voice: String) {
        _preferredVoice.value = voice
        saveConfiguration()
    }
    
    /**
     * Get TTS recommendation based on language
     * @param isKorean Whether the content is in Korean
     * @return true to use Android TTS, false to use OpenAI
     */
    fun shouldUseAndroidTts(isKorean: Boolean): Boolean {
        return when (_ttsMode.value) {
            TTS_MODE_ANDROID -> true
            TTS_MODE_OPENAI -> false
            TTS_MODE_AUTO -> isKorean && _useAndroidForKorean.value
            else -> isKorean && _useAndroidForKorean.value
        }
    }
    
    /**
     * Get configuration summary for display
     */
    fun getConfigurationSummary(): String {
        return """
            TTS Mode: ${_ttsMode.value}
            Use Android for Korean: ${_useAndroidForKorean.value}
            Force Android TTS: ${_forceAndroidTts.value}
            Speech Rate: ${_speechRate.value}
            Preferred Voice: ${_preferredVoice.value}
        """.trimIndent()
    }
    
    /**
     * Reset to default configuration
     */
    fun resetToDefaults() {
        _ttsMode.value = TTS_MODE_AUTO
        _useAndroidForKorean.value = true
        _forceAndroidTts.value = false
        _speechRate.value = 1.0f
        _preferredVoice.value = "alloy"
        saveConfiguration()
    }
}