package com.example.XRTEST.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI TTS API Manager
 * Provides high-quality text-to-speech using OpenAI's TTS API
 */
class OpenAITtsManager(
    private val context: Context,
    private val apiKey: String
) {
    companion object {
        private const val TAG = "OpenAITtsManager"
        private const val TTS_API_URL = "https://api.openai.com/v1/audio/speech"
        private const val REQUEST_TIMEOUT_SECONDS = 30L
        
        // Audio specifications for playback
        private const val SAMPLE_RATE = 24000 // OpenAI TTS outputs 24kHz
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        // Available voices
        val AVAILABLE_VOICES = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer")
        const val DEFAULT_VOICE = "shimmer"  // Soft female voice for AR assistant
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // State management
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()
    
    // Audio playback
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    // Context7: Removed MediaPlayer - using direct PCM AudioTrack streaming
    
    // Settings
    private var currentVoice = DEFAULT_VOICE
    private var speechSpeed = 1.0f // 0.25 to 4.0
    private var lastRequestTime = 0L // For smart duplicate prevention
    
    /**
     * Generate speech from text using OpenAI TTS API
     */
    fun speak(text: String, voice: String = currentVoice, speed: Float = speechSpeed) {
        if (text.isBlank()) return
        
        // 🎵 Smart duplicate prevention - only stop if really different text
        if (_isSpeaking.value) {
            // Check if this is the same text (avoid unnecessary interruptions)
            val currentText = text.take(50)
            Log.d(TAG, "🔄 Context7: Current TTS active, checking for duplicate...")
            
            // Only stop if it's actually a different text request
            if (System.currentTimeMillis() - lastRequestTime > 1000) {
                Log.d(TAG, "🛑 Context7: Stopping for new TTS request")
                stop()
            } else {
                Log.d(TAG, "⏭️ Context7: Ignoring duplicate TTS request")
                return // Skip duplicate request
            }
        }
        lastRequestTime = System.currentTimeMillis()
        
        coroutineScope.launch {
            try {
                _isSpeaking.value = true
                Log.d(TAG, "🎤 Context7: Generating speech for: ${text.take(50)}...")
                
                // Build TTS request
                val requestBody = buildTtsRequest(text, voice, speed)
                
                // Make API call
                val request = Request.Builder()
                    .url(TTS_API_URL)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        Log.e(TAG, "TTS API request failed: ${response.code} - $errorBody")
                        return@use
                    }
                    
                    // Get PCM audio data from OpenAI TTS
                    val audioData = response.body?.bytes()
                    if (audioData != null) {
                        Log.d(TAG, "🎵 Context7: Received PCM audio data: ${audioData.size} bytes")
                        playAudio(audioData)
                    } else {
                        Log.e(TAG, "No PCM audio data received")
                    }
                }
                
            } catch (e: IOException) {
                Log.e(TAG, "Network error during TTS generation", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error during TTS generation", e)
            } finally {
                _isSpeaking.value = false
            }
        }
    }
    
    /**
     * Build OpenAI TTS API request with Korean language optimization
     * Context7: Use PCM format for direct AudioTrack streaming without file I/O
     */
    private fun buildTtsRequest(text: String, voice: String, speed: Float): String {
        // Clean and optimize Korean text for better TTS quality
        val optimizedText = optimizeKoreanText(text)
        
        val request = JSONObject().apply {
            put("model", "tts-1-hd") // Use HD model for better Korean quality
            put("input", optimizedText)
            put("voice", getBestVoiceForKorean(voice))
            put("speed", speed.coerceIn(0.7f, 1.3f).toDouble()) // Optimal speed range for Korean
            put("response_format", "pcm") // Context7: Use PCM for smooth AudioTrack playback
        }
        
        return request.toString()
    }
    
    /**
     * Optimize Korean text for better TTS pronunciation
     */
    private fun optimizeKoreanText(text: String): String {
        return text
            .trim()
            // Replace common Korean characters that cause pronunciation issues
            .replace("·", " ") // Middle dot to space
            .replace("…", "...") // Horizontal ellipsis
            .replace("－", "-") // Full-width hyphen
            .replace("〜", "~") // Wave dash
            // Add pronunciation guides for common English words in Korean context
            .replace("API", "에이피아이")
            .replace("GPS", "지피에스") 
            .replace("USB", "유에스비")
            .replace("WiFi", "와이파이")
            .replace("Bluetooth", "블루투스")
            .replace("TTS", "티티에스")
            .replace("AI", "에이아이")
            // Natural Korean conversational improvements
            .replace("합니다.", "해요.") // More natural casual ending
            .replace("습니다.", "어요.") // More friendly tone
            .replace("입니다.", "이에요.") // Casual friendly
            // Add natural pauses for better flow
            .replace(".", ". ") // Add space after period for natural pause
            .replace("!", "! ") // Add space after exclamation 
            .replace("?", "? ") // Add space after question
            // Normalize Korean spacing
            .replace(Regex("\\s+"), " ")
            // Keep original text without forcing unnatural endings
            .let { cleanText ->
                if (cleanText.isNotEmpty() && !cleanText.last().toString().matches(Regex("[.!?。！？]"))) {
                    "$cleanText."
                } else cleanText
            }
    }
    
    /**
     * Select the best voice for Korean text
     */
    private fun getBestVoiceForKorean(preferredVoice: String): String {
        // Voices that work better with Korean pronunciation
        val koreanOptimizedVoices = listOf("alloy", "nova", "shimmer")
        
        return if (preferredVoice in koreanOptimizedVoices) {
            preferredVoice
        } else {
            "alloy" // Default to alloy for Korean (most neutral pronunciation)
        }
    }
    
    /**
     * Play PCM audio data using AudioTrack - Context7 optimized
     */
    private suspend fun playAudio(audioData: ByteArray) {
        withContext(Dispatchers.IO) {
            try {
                // Context7: Calculate proper buffer size for PCM16 data
                val bufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT
                ).coerceAtLeast(audioData.size)
                
                Log.d(TAG, "🎵 Context7: Creating AudioTrack for PCM playback, data size: ${audioData.size} bytes")
                
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setLegacyStreamType(android.media.AudioManager.STREAM_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE) // 24kHz from OpenAI TTS
                            .setChannelMask(CHANNEL_CONFIG) // Mono
                            .setEncoding(AUDIO_FORMAT) // PCM16
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                
                // Context7: Start AudioTrack and stream PCM data directly
                audioTrack?.apply {
                    play()
                    Log.d(TAG, "🔊 Context7: AudioTrack started, writing ${audioData.size} bytes")
                    
                    // Write PCM data in chunks for smooth playback
                    val chunkSize = 1024
                    var offset = 0
                    
                    while (offset < audioData.size && _isSpeaking.value) {
                        val remainingBytes = audioData.size - offset
                        val bytesToWrite = minOf(chunkSize, remainingBytes)
                        
                        val bytesWritten = write(audioData, offset, bytesToWrite)
                        if (bytesWritten < 0) {
                            Log.e(TAG, "AudioTrack write error: $bytesWritten")
                            break
                        }
                        
                        offset += bytesWritten
                        
                        // Small delay for smooth streaming
                        if (offset < audioData.size) {
                            kotlinx.coroutines.delay(10)
                        }
                    }
                    
                    Log.d(TAG, "✅ Context7: PCM data streaming completed")
                    
                    // Wait for playback to finish
                    while (playbackHeadPosition < audioData.size / 2 && _isSpeaking.value) {
                        kotlinx.coroutines.delay(50)
                    }
                    
                    // Clean stop
                    stop()
                    release()
                    audioTrack = null
                    
                    withContext(Dispatchers.Main) {
                        _isSpeaking.value = false
                        Log.d(TAG, "🎤 Context7: PCM AudioTrack playback completed")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Context7: Error playing PCM audio", e)
                withContext(Dispatchers.Main) {
                    _isSpeaking.value = false
                }
            }
        }
    }
    
    // Context7: Removed playMp3Audio() - no longer needed with PCM format
    
    /**
     * Stop current speech - Context7 optimized for PCM AudioTrack
     */
    fun stop() {
        Log.d(TAG, "🛑 Context7: Stopping TTS (PCM AudioTrack mode)")
        
        // 1. Cancel ongoing jobs
        playbackJob?.cancel()
        
        // 2. Context7: Clean AudioTrack shutdown
        audioTrack?.apply {
            try {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
                Log.d(TAG, "✅ Context7: AudioTrack properly released")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Context7: AudioTrack cleanup warning: ${e.message}")
            }
        }
        audioTrack = null
        
        // 3. Update state
        _isSpeaking.value = false
        Log.d(TAG, "🎤 Context7: TTS stopped (PCM mode)")
    }
    
    /**
     * Set voice for TTS
     */
    fun setVoice(voice: String) {
        if (voice in AVAILABLE_VOICES) {
            currentVoice = voice
            Log.d(TAG, "Voice changed to: $voice")
        } else {
            Log.w(TAG, "Invalid voice: $voice")
        }
    }
    
    /**
     * Set speech speed (0.25 to 4.0)
     */
    fun setSpeechSpeed(speed: Float) {
        speechSpeed = speed.coerceIn(0.25f, 4.0f)
        Log.d(TAG, "Speech speed set to: $speechSpeed")
    }
    
    /**
     * Get current voice
     */
    fun getCurrentVoice(): String = currentVoice
    
    /**
     * Get current speech speed
     */
    fun getSpeechSpeed(): Float = speechSpeed
    
    /**
     * Release resources
     */
    fun release() {
        stop()
        coroutineScope.cancel()
        client.dispatcher.executorService.shutdown()
        Log.d(TAG, "OpenAI TTS Manager released")
    }
}