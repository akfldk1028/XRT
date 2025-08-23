package com.example.XRTEST.vision

import android.content.Context
import android.content.SharedPreferences

/**
 * VoiceSettingsManager - Manages voice and language preferences for AR Glass Q&A System
 * Provides persistence and convenient voice descriptions
 */
class VoiceSettingsManager(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "VoiceSettings"
        private const val KEY_SELECTED_VOICE = "selected_voice"
        private const val KEY_USE_KOREAN = "use_korean"
        
        // Voice descriptions for UI
        val VOICE_DESCRIPTIONS = mapOf(
            "alloy" to VoiceInfo("Alloy", "기본 중성 음성 (Default neutral)", "🎵"),
            "echo" to VoiceInfo("Echo", "남성 음성 (Male voice)", "🎤"),
            "fable" to VoiceInfo("Fable", "영국식 억양 (British accent)", "🎭"),
            "onyx" to VoiceInfo("Onyx", "깊은 남성 음성 (Deep male)", "🎸"),
            "nova" to VoiceInfo("Nova", "여성 음성 (Female voice)", "🎪"),
            "shimmer" to VoiceInfo("Shimmer", "부드러운 여성 음성 (Soft female)", "✨")
        )
    }
    
    data class VoiceInfo(
        val displayName: String,
        val description: String,
        val emoji: String
    )
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Get the saved voice preference
     */
    fun getSavedVoice(): String {
        return prefs.getString(KEY_SELECTED_VOICE, "alloy") ?: "alloy"
    }
    
    /**
     * Save voice preference
     */
    fun saveVoice(voice: String) {
        prefs.edit().putString(KEY_SELECTED_VOICE, voice).apply()
    }
    
    /**
     * Get the saved language preference
     */
    fun isKoreanMode(): Boolean {
        return prefs.getBoolean(KEY_USE_KOREAN, true) // Default to Korean
    }
    
    /**
     * Save language preference
     */
    fun setKoreanMode(useKorean: Boolean) {
        prefs.edit().putBoolean(KEY_USE_KOREAN, useKorean).apply()
    }
    
    /**
     * Save language preference (alias for UI compatibility)
     */
    fun saveLanguageMode(useKorean: Boolean) {
        setKoreanMode(useKorean)
    }
    
    /**
     * Get voice info for display
     */
    fun getVoiceInfo(voice: String): VoiceInfo {
        return VOICE_DESCRIPTIONS[voice] ?: VoiceInfo(voice, "Unknown voice", "❓")
    }
    
    /**
     * Get all available voices with descriptions
     */
    fun getAllVoiceInfos(): List<Pair<String, VoiceInfo>> {
        return RealtimeVisionClient.AVAILABLE_VOICES.map { voice ->
            voice to getVoiceInfo(voice)
        }
    }
    
    /**
     * Get display text for current settings
     */
    fun getCurrentSettingsText(): String {
        val voice = getSavedVoice()
        val voiceInfo = getVoiceInfo(voice)
        val language = if (isKoreanMode()) "한국어" else "English"
        
        return """
            🗣️ 음성: ${voiceInfo.emoji} ${voiceInfo.displayName}
            🌐 언어: $language
            ${voiceInfo.description}
        """.trimIndent()
    }
}