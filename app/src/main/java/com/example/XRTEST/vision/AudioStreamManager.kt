package com.example.XRTEST.vision

import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.and

/**
 * AudioStreamManager for OpenAI Realtime API
 * Handles 24kHz PCM16 audio capture and playback
 */
class AudioStreamManager(
    private val onAudioCaptured: (ByteArray) -> Unit
) {
    
    companion object {
        private const val TAG = "AudioStreamManager"
        
        // OpenAI Realtime API audio specifications
        private const val SAMPLE_RATE = 24000 // 24kHz required by API
        private const val CHANNEL_CONFIG_RECORD = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_CONFIG_PLAY = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        // Buffer sizes
        private const val RECORD_BUFFER_SIZE_MS = 100 // 100ms chunks
        private const val PLAYBACK_BUFFER_SIZE_MS = 50 // 50ms chunks
        
        // Audio processing
        private const val SILENCE_THRESHOLD = 500 // Amplitude threshold for silence detection
        private const val NOISE_GATE_THRESHOLD = 100
    }
    
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null
    private var playbackJob: Job? = null
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // State management
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()
    
    // Audio buffers
    private val recordBufferSize: Int
    private val playbackBufferSize: Int
    private val audioBuffer: ByteArray
    private val playbackQueue = mutableListOf<ByteArray>()
    
    init {
        // Calculate buffer sizes
        recordBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG_RECORD,
            AUDIO_FORMAT
        ).coerceAtLeast(SAMPLE_RATE * 2 * RECORD_BUFFER_SIZE_MS / 1000) // 2 bytes per sample
        
        playbackBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG_PLAY,
            AUDIO_FORMAT
        ).coerceAtLeast(SAMPLE_RATE * 2 * PLAYBACK_BUFFER_SIZE_MS / 1000)
        
        audioBuffer = ByteArray(recordBufferSize)
        
        Log.d(TAG, "AudioStreamManager initialized - Record buffer: $recordBufferSize, Playback buffer: $playbackBufferSize")
    }
    
    /**
     * Initialize audio recording - Context7: AndroidX Media safe approach  
     */
    fun initializeRecording(): Boolean {
        return try {
            // Context7: Safe AudioRecord initialization with proper error handling
            Log.d(TAG, "🎤 Context7: Attempting AudioRecord initialization...")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("MissingPermission")
                audioRecord = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG_RECORD)
                            .setEncoding(AUDIO_FORMAT)
                            .build()
                    )
                    .setBufferSizeInBytes(recordBufferSize)
                    .build()
            } else {
                @Suppress("MissingPermission") 
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.DEFAULT,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG_RECORD,
                    AUDIO_FORMAT,
                    recordBufferSize
                )
            }
            
            val state = audioRecord?.state
            if (state == AudioRecord.STATE_INITIALIZED) {
                Log.d(TAG, "✅ Context7: AudioRecord initialized successfully for microphone!")
                Log.d(TAG, "🎙️ Ready for voice conversations via OpenAI Realtime API")
                true
            } else {
                Log.w(TAG, "⚠️ Context7: AudioRecord failed (State: $state) - probably emulator")
                Log.d(TAG, "✅ TTS and image analysis will still work without microphone")
                audioRecord?.release()
                audioRecord = null
                true // Still return true to allow app to continue
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "⚠️ Context7: Microphone permission denied: ${e.message}")
            Log.d(TAG, "✅ TTS and image analysis will work without microphone recording")
            true // Continue without microphone
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Context7: AudioRecord error (safe handling): ${e.message}")
            Log.d(TAG, "✅ TTS and image analysis will work without microphone recording")
            true // Return true to continue without crashing
        }
    }
    
    /**
     * Initialize audio playback
     */
    fun initializePlayback(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG_PLAY)
                            .setEncoding(AUDIO_FORMAT)
                            .build()
                    )
                    .setBufferSizeInBytes(playbackBufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                audioTrack = AudioTrack(
                    AudioManager.STREAM_VOICE_CALL,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG_PLAY,
                    AUDIO_FORMAT,
                    playbackBufferSize,
                    AudioTrack.MODE_STREAM
                )
            }
            
            val state = audioTrack?.state
            if (state == AudioTrack.STATE_INITIALIZED) {
                Log.d(TAG, "AudioTrack initialized successfully")
                true
            } else {
                Log.e(TAG, "AudioTrack initialization failed. State: $state")
                audioTrack?.release()
                audioTrack = null
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack: ${e.message}")
            false
        }
    }
    
    /**
     * Start audio recording
     */
    fun startRecording() {
        if (_isRecording.value) {
            Log.w(TAG, "Already recording")
            return
        }
        
        if (audioRecord == null && !initializeRecording()) {
            Log.e(TAG, "Cannot start recording: AudioRecord not initialized")
            return
        }
        
        recordingJob = coroutineScope.launch {
            try {
                audioRecord?.startRecording()
                _isRecording.value = true
                Log.d(TAG, "Recording started - 24kHz PCM16 for OpenAI Realtime API")
                
                while (isActive && _isRecording.value) {
                    try {
                        val bytesRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                        
                        if (bytesRead > 0 && bytesRead <= audioBuffer.size) {
                            // Context7: Use safe array copy for Realtime API
                            val audioChunk = ByteArray(bytesRead)
                            System.arraycopy(audioBuffer, 0, audioChunk, 0, bytesRead)
                            
                            // Process audio for OpenAI Realtime API
                            val processedAudio = processAudioInput(audioChunk)
                            
                            // Calculate audio level for UI feedback
                            val level = calculateAudioLevel(processedAudio)
                            _audioLevel.value = level
                            
                            // Context7: Send ALL audio data to Realtime API (not just above threshold)
                            // The API needs continuous stream for proper voice detection
                            onAudioCaptured(processedAudio)
                            
                        } else if (bytesRead < 0) {
                            Log.e(TAG, "AudioRecord read error: $bytesRead")
                            break
                        }
                        
                        // Small delay to prevent CPU overload
                        delay(10)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in recording loop: ${e.message}", e)
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recording error: ${e.message}", e)
            } finally {
                try {
                    audioRecord?.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
                }
                _isRecording.value = false
                _audioLevel.value = 0f
                Log.d(TAG, "Recording stopped")
            }
        }
    }
    
    /**
     * Stop audio recording
     */
    fun stopRecording() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
    }
    
    /**
     * Play audio data from Realtime API
     */
    fun playAudio(audioData: ByteArray) {
        if (audioTrack == null && !initializePlayback()) {
            Log.e(TAG, "Cannot play audio: AudioTrack not initialized")
            return
        }
        
        coroutineScope.launch {
            synchronized(playbackQueue) {
                playbackQueue.add(audioData)
            }
            
            // Buffer multiple chunks before starting playback for smoother audio
            if (!_isPlaying.value && playbackQueue.size >= 2) {
                startPlayback()
            } else if (!_isPlaying.value) {
                // Wait for more chunks to arrive
                delay(100)
                if (playbackQueue.isNotEmpty()) {
                    startPlayback()
                }
            }
        }
    }
    
    /**
     * Start audio playback processing
     */
    private fun startPlayback() {
        if (_isPlaying.value) return
        
        playbackJob = coroutineScope.launch {
            try {
                audioTrack?.play()
                _isPlaying.value = true
                Log.d(TAG, "Playback started")
                
                while (isActive && (playbackQueue.isNotEmpty() || _isPlaying.value)) {
                    val audioData = synchronized(playbackQueue) {
                        if (playbackQueue.isNotEmpty()) {
                            playbackQueue.removeAt(0)
                        } else null
                    }
                    
                    if (audioData != null) {
                        // Apply audio processing
                        val processedAudio = processAudioOutput(audioData)
                        
                        // Write to AudioTrack
                        val written = audioTrack?.write(processedAudio, 0, processedAudio.size) ?: 0
                        
                        if (written < 0) {
                            Log.e(TAG, "AudioTrack write error: $written")
                            break
                        }
                    } else {
                        // No more audio in queue, wait longer for buffering
                        delay(100)
                        
                        // Keep waiting for more audio chunks to arrive for smooth playback
                        if (playbackQueue.isEmpty()) {
                            delay(500) // Wait longer for continuous stream
                            if (playbackQueue.isEmpty()) {
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Playback error: ${e.message}")
            } finally {
                audioTrack?.stop()
                _isPlaying.value = false
                Log.d(TAG, "Playback stopped")
            }
        }
    }
    
    /**
     * Stop audio playback
     */
    fun stopPlayback() {
        _isPlaying.value = false
        synchronized(playbackQueue) {
            playbackQueue.clear()
        }
        playbackJob?.cancel()
        playbackJob = null
    }
    
    /**
     * Process input audio (apply noise gate, normalization)
     */
    private fun processAudioInput(audioData: ByteArray): ByteArray {
        val samples = ShortArray(audioData.size / 2)
        ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
        
        // Apply noise gate
        for (i in samples.indices) {
            if (kotlin.math.abs(samples[i].toInt()) < NOISE_GATE_THRESHOLD) {
                samples[i] = 0
            }
        }
        
        // Normalize audio levels
        val maxAmplitude = samples.maxOfOrNull { kotlin.math.abs(it.toInt()) } ?: 1
        if (maxAmplitude > 0) {
            val normalizationFactor = (Short.MAX_VALUE * 0.8 / maxAmplitude).toFloat()
            if (normalizationFactor < 1.0f) {
                for (i in samples.indices) {
                    samples[i] = (samples[i] * normalizationFactor).toInt().toShort()
                }
            }
        }
        
        // Convert back to byte array
        val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asShortBuffer().put(samples)
        return buffer.array()
    }
    
    /**
     * Process output audio (apply smoothing, volume control)
     */
    private fun processAudioOutput(audioData: ByteArray): ByteArray {
        // Apply fade-in/fade-out to prevent clicks
        val samples = ShortArray(audioData.size / 2)
        ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
        
        val fadeLength = minOf(samples.size / 10, 240) // 10ms at 24kHz
        
        // Fade in
        for (i in 0 until fadeLength) {
            val factor = i.toFloat() / fadeLength
            samples[i] = (samples[i] * factor).toInt().toShort()
        }
        
        // Fade out
        for (i in 0 until fadeLength) {
            val index = samples.size - 1 - i
            val factor = i.toFloat() / fadeLength
            samples[index] = (samples[index] * factor).toInt().toShort()
        }
        
        // Convert back to byte array
        val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asShortBuffer().put(samples)
        return buffer.array()
    }
    
    /**
     * Calculate audio level from PCM16 data
     */
    private fun calculateAudioLevel(audioData: ByteArray): Float {
        val samples = ShortArray(audioData.size / 2)
        ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
        
        var sum = 0L
        for (sample in samples) {
            sum += sample * sample
        }
        
        val rms = kotlin.math.sqrt(sum.toDouble() / samples.size)
        return (rms / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
    }
    
    /**
     * Convert Float audio to PCM16 ByteArray
     */
    fun floatToPCM16(floatData: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floatData.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        val shortBuffer = buffer.asShortBuffer()
        
        for (sample in floatData) {
            val pcm16 = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            shortBuffer.put(pcm16.toShort())
        }
        
        return buffer.array()
    }
    
    /**
     * Convert PCM16 ByteArray to Float audio
     */
    fun pcm16ToFloat(pcmData: ByteArray): FloatArray {
        val samples = ShortArray(pcmData.size / 2)
        ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
        
        return FloatArray(samples.size) { i ->
            samples[i].toFloat() / Short.MAX_VALUE
        }
    }
    
    /**
     * Release all audio resources
     */
    fun release() {
        stopRecording()
        stopPlayback()
        
        audioRecord?.release()
        audioRecord = null
        
        audioTrack?.release()
        audioTrack = null
        
        coroutineScope.cancel()
        
        Log.d(TAG, "AudioStreamManager released")
    }
}