package com.example.XRTEST.voice

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * VoiceManager for AR Glass Q&A System
 * Now supports both Android TTS and OpenAI TTS
 */
class VoiceManager(
    private val context: Context,
    private val apiKey: String? = null  // OpenAI API key for TTS
) {
    
    companion object {
        private const val TAG = "VoiceManager"
    }

    // TTS Providers
    enum class TtsProvider {
        ANDROID_TTS,    // Android built-in TTS
        OPENAI_TTS      // OpenAI TTS API (premium)
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var openAITts: OpenAITtsManager? = null
    private var isTtsReady = false
    private var currentLanguage: Locale = Locale.KOREAN  // Default to Korean
    private var speechRate: Float = 1.0f  // Normal speed
    private var ttsProvider: TtsProvider = TtsProvider.OPENAI_TTS  // MUST use OpenAI TTS - premium quality required!

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _recognizedText = MutableStateFlow<String?>(null)
    val recognizedText: StateFlow<String?> = _recognizedText.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()
    
    private val _ttsLanguage = MutableStateFlow("ko")  // "ko" for Korean, "en" for English
    val ttsLanguage: StateFlow<String> = _ttsLanguage.asStateFlow()

    // Context7: setupRecognitionListener() removed - OpenAI Realtime API handles speech recognition natively

    fun initialize() {
        // Context7: No longer using Android SpeechRecognizer - OpenAI Realtime API handles everything
        Log.d(TAG, "🎤 Context7: VoiceManager initialized for OpenAI Realtime API")
        Log.d(TAG, "🎤 Context7: Speech recognition: OpenAI gpt-4o-transcribe model")
        Log.d(TAG, "🎤 Context7: Turn detection: Server VAD with 500ms silence threshold")
        
        // Initialize OpenAI TTS if API key is provided
        if (apiKey != null) {
            openAITts = OpenAITtsManager(context, apiKey)
            // 🔥 MUST USE OPENAI TTS - 사용자 명시적 요구
            ttsProvider = TtsProvider.OPENAI_TTS
            Log.d(TAG, "🎤 USING OPENAI TTS EXCLUSIVELY - User demanded OpenAI TTS only!")
        } else {
            Log.w(TAG, "No API key provided, OpenAI TTS not available")
            ttsProvider = TtsProvider.ANDROID_TTS
        }
        
        // Initialize Android TTS as backup
        try {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        // Set Korean as default language
                        val result = tts?.setLanguage(Locale.KOREAN)
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.e(TAG, "Korean TTS not supported, falling back to default")
                            tts?.setLanguage(Locale.getDefault())
                        } else {
                            Log.d(TAG, "Korean TTS initialized successfully")
                        }
                        
                        // Set speech rate for better clarity
                        tts?.setSpeechRate(speechRate)
                        tts?.setPitch(1.0f)  // Normal pitch
                        
                        isTtsReady = true
                        Log.d(TAG, "Android TTS initialized successfully - Provider: $ttsProvider")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error configuring Android TTS", e)
                        isTtsReady = false
                    }
                } else {
                    Log.e(TAG, "Android TTS initialization failed with status: $status")
                    isTtsReady = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create TextToSpeech instance", e)
            tts = null
            isTtsReady = false
        }
    }

    fun startListening() {
        // Context7: VoiceManager now delegates to OpenAI Realtime API native speech recognition
        // The actual audio capture and transcription is handled by:
        // AudioStreamManager → RealtimeVisionClient → OpenAI Server VAD → conversation.item.input_audio_transcription.completed
        
        Log.d(TAG, "🎤 Context7: startListening() - OpenAI Realtime API native speech recognition")
        Log.d(TAG, "🎤 Context7: Using gpt-4o-transcribe model with Server VAD")
        Log.d(TAG, "🎤 Context7: PCM16 24kHz audio streaming to WebSocket")
        
        _isListening.value = true
        Log.d(TAG, "✅ Context7: OpenAI native speech recognition active")
        Log.d(TAG, "🎤 Context7: Waiting for conversation.item.input_audio_transcription.completed events")
    }

    fun stopListening() {
        // Context7: OpenAI Realtime API handles stop automatically via Server VAD
        _isListening.value = false
        Log.d(TAG, "🎤 Context7: OpenAI native speech recognition stopped")
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        
        // Context7: Improved duplicate prevention - only stop if really necessary
        if (_isSpeaking.value) {
            Log.d(TAG, "🔄 Context7: New TTS request while speaking - allowing OpenAI TTS to handle")
            // Let OpenAI TTS manager handle the transition smoothly
        }
        
        Log.d(TAG, "🎤 Context7: speak() called with provider: $ttsProvider, OpenAI available: ${openAITts != null}")
        
        when (ttsProvider) {
            TtsProvider.OPENAI_TTS -> {
                // Use OpenAI TTS (premium quality) - process full text at once
                if (openAITts != null) {
                    Log.d(TAG, "Using OpenAI TTS for: ${text.take(100)}...")
                    _isSpeaking.value = true
                    openAITts?.speak(text)
                    
                    // Monitor OpenAI TTS state
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        openAITts?.isSpeaking?.collect { isOpenAISpeaking ->
                            _isSpeaking.value = isOpenAISpeaking
                        }
                    }
                } else {
                    Log.w(TAG, "OpenAI TTS not available, falling back to Android TTS")
                    ttsProvider = TtsProvider.ANDROID_TTS
                    speakWithAndroidTts(text)
                }
            }
            
            TtsProvider.ANDROID_TTS -> {
                Log.d(TAG, "Using Android TTS (fallback)")
                // Use Android TTS (fallback)
                speakWithAndroidTts(text)
            }
        }
    }
    
    /**
     * Stop any current speech
     */
    fun stop() {
        when (ttsProvider) {
            TtsProvider.OPENAI_TTS -> {
                openAITts?.stop()
            }
            TtsProvider.ANDROID_TTS -> {
                tts?.stop()
            }
        }
        _isSpeaking.value = false
    }
    
    private fun speakWithAndroidTts(text: String) {
        if (isTtsReady && text.isNotBlank()) {
            // Stop any current speech to prevent text corruption
            tts?.stop()
            
            _isSpeaking.value = true
            
            // Configure TTS completion listener
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "Android TTS started: $utteranceId")
                }
                
                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    Log.d(TAG, "Android TTS completed: $utteranceId")
                }
                
                @Deprecated("Deprecated in API 15")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    Log.e(TAG, "Android TTS error: $utteranceId")
                }
            })
            
            // Clean text and ensure proper Korean formatting
            val cleanText = text.trim().replace(Regex("\\s+"), " ")
            
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "response")
            Log.d(TAG, "Speaking with Android TTS: $cleanText (Language: ${currentLanguage.displayName})")
        }
    }
    
    /**
     * Set TTS language (Korean or English)
     * @param useKorean true for Korean, false for English
     */
    fun setLanguage(useKorean: Boolean) {
        val newLanguage = if (useKorean) Locale.KOREAN else Locale.US
        
        if (isTtsReady) {
            val result = tts?.setLanguage(newLanguage)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported: ${newLanguage.displayName}")
            } else {
                currentLanguage = newLanguage
                _ttsLanguage.value = if (useKorean) "ko" else "en"
                Log.d(TAG, "TTS language changed to: ${newLanguage.displayName}")
            }
        }
    }
    
    /**
     * Set TTS speech rate
     * @param rate Speech rate (0.5 = half speed, 1.0 = normal, 2.0 = double speed)
     */
    fun setSpeechRate(rate: Float) {
        if (rate in 0.5f..2.0f) {
            speechRate = rate
            tts?.setSpeechRate(rate)
            Log.d(TAG, "Speech rate set to: $rate")
        }
    }
    
    /**
     * Speak text in specific language (temporary override)
     */
    fun speakInLanguage(text: String, useKorean: Boolean) {
        if (isTtsReady) {
            // Temporarily set language for this utterance
            val tempLanguage = if (useKorean) Locale.KOREAN else Locale.US
            tts?.setLanguage(tempLanguage)
            speak(text)
            // Restore original language after speaking
            tts?.setLanguage(currentLanguage)
        }
    }
    
    /**
     * Check if Korean TTS is available
     */
    fun isKoreanTtsAvailable(): Boolean {
        return tts?.isLanguageAvailable(Locale.KOREAN) == TextToSpeech.LANG_AVAILABLE ||
               tts?.isLanguageAvailable(Locale.KOREAN) == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
               tts?.isLanguageAvailable(Locale.KOREAN) == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
    }
    
    /**
     * Set TTS Provider (OpenAI TTS or Android TTS)
     */
    fun setTtsProvider(provider: TtsProvider) {
        ttsProvider = provider
        Log.d(TAG, "TTS provider changed to: $provider")
    }
    
    /**
     * Get current TTS provider
     */
    fun getTtsProvider(): TtsProvider = ttsProvider
    
    /**
     * Check if OpenAI TTS is available
     */
    fun isOpenAITtsAvailable(): Boolean = openAITts != null
    
    /**
     * Set OpenAI TTS voice
     */
    fun setOpenAITtsVoice(voice: String) {
        openAITts?.setVoice(voice)
    }
    
    /**
     * Set OpenAI TTS speed
     */
    fun setOpenAITtsSpeed(speed: Float) {
        openAITts?.setSpeechSpeed(speed)
    }
    
    /**
     * Get available OpenAI TTS voices
     */
    fun getOpenAITtsVoices(): List<String> = OpenAITtsManager.AVAILABLE_VOICES

    fun cleanup() {
        // Context7: No SpeechRecognizer to destroy - OpenAI Realtime API handles everything
        tts?.shutdown()
        openAITts?.release()
        Log.d(TAG, "🎤 Context7: VoiceManager cleaned up - OpenAI Realtime API session will be handled by VisionIntegration")
    }
    
    fun clearRecognizedText() {
        _recognizedText.value = null
    }
}
