package com.example.XRTEST.vision

import android.media.*
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Audio Processor for OpenAI Realtime API
 * Handles PCM16 audio recording, playback, and format conversion
 */
class AudioProcessor(
    private val config: AudioConfig = AudioConfig()
) {
    companion object {
        private const val TAG = "AudioProcessor"
        private const val REALTIME_SAMPLE_RATE = 24000 // OpenAI Realtime API requirement
        private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
    
    // Audio components
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    
    // Processing state
    private var isRecording = false
    private var isPlaying = false
    private val processingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Audio buffers and queues
    private val recordingChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private val playbackQueue = Channel<ByteArray>(Channel.UNLIMITED)
    
    // Audio level monitoring
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()
    
    // Voice activity detection
    private val _voiceActivity = MutableStateFlow(false)
    val voiceActivity: StateFlow<Boolean> = _voiceActivity.asStateFlow()
    
    // Recording state
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()
    
    /**
     * Initialize audio components
     */
    fun initialize(): Result<Unit> {
        return try {
            initializeRecorder()
            initializePlayer()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio", e)
            Result.failure(e)
        }
    }
    
    /**
     * Initialize audio recorder
     */
    private fun initializeRecorder() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            REALTIME_SAMPLE_RATE,
            CHANNEL_CONFIG_IN,
            AUDIO_FORMAT
        )
        
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalStateException("Failed to get min buffer size for recording")
        }
        
        val bufferSize = max(minBufferSize * 2, config.recordBufferSize)
        
        @Suppress("MissingPermission")
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            REALTIME_SAMPLE_RATE,
            CHANNEL_CONFIG_IN,
            AUDIO_FORMAT,
            bufferSize
        )
        
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            throw IllegalStateException("Failed to initialize AudioRecord")
        }
        
        Log.d(TAG, "AudioRecord initialized with buffer size: $bufferSize")
    }
    
    /**
     * Initialize audio player
     */
    private fun initializePlayer() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            REALTIME_SAMPLE_RATE,
            CHANNEL_CONFIG_OUT,
            AUDIO_FORMAT
        )
        
        if (minBufferSize == AudioTrack.ERROR || minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
            throw IllegalStateException("Failed to get min buffer size for playback")
        }
        
        val bufferSize = max(minBufferSize * 2, config.playbackBufferSize)
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(REALTIME_SAMPLE_RATE)
                    .setEncoding(AUDIO_FORMAT)
                    .setChannelMask(CHANNEL_CONFIG_OUT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        
        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            audioTrack?.release()
            audioTrack = null
            throw IllegalStateException("Failed to initialize AudioTrack")
        }
        
        // Set volume
        audioTrack?.setVolume(config.playbackVolume)
        
        Log.d(TAG, "AudioTrack initialized with buffer size: $bufferSize")
    }
    
    /**
     * Start audio recording
     */
    fun startRecording(onAudioData: suspend (ByteArray) -> Unit) {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }
        
        isRecording = true
        _recordingState.value = RecordingState.RECORDING
        
        processingScope.launch {
            try {
                audioRecord?.startRecording()
                
                val bufferSize = config.recordBufferSize
                val audioBuffer = ByteArray(bufferSize)
                val frameSize = (REALTIME_SAMPLE_RATE * config.frameMs / 1000 * 2) // 2 bytes per sample
                var accumulatedBuffer = ByteArray(0)
                
                while (isRecording) {
                    val bytesRead = audioRecord?.read(audioBuffer, 0, bufferSize) ?: 0
                    
                    if (bytesRead > 0) {
                        // Accumulate audio data
                        accumulatedBuffer += audioBuffer.copyOf(bytesRead)
                        
                        // Process frames
                        while (accumulatedBuffer.size >= frameSize) {
                            val frame = accumulatedBuffer.copyOf(frameSize)
                            accumulatedBuffer = accumulatedBuffer.copyOfRange(frameSize, accumulatedBuffer.size)
                            
                            // Apply audio processing
                            val processedFrame = processAudioFrame(frame)
                            
                            // Update audio level
                            updateAudioLevel(processedFrame)
                            
                            // Voice activity detection
                            if (config.enableVAD) {
                                detectVoiceActivity(processedFrame)
                            }
                            
                            // Send processed audio
                            onAudioData(processedFrame)
                        }
                    } else if (bytesRead < 0) {
                        Log.e(TAG, "Error reading audio: $bytesRead")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recording error", e)
            } finally {
                audioRecord?.stop()
                _recordingState.value = RecordingState.IDLE
            }
        }
    }
    
    /**
     * Stop audio recording
     */
    fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        _recordingState.value = RecordingState.IDLE
        Log.d(TAG, "Recording stopped")
    }
    
    /**
     * Play audio data
     */
    fun playAudio(audioData: ByteArray) {
        processingScope.launch {
            try {
                playbackQueue.send(audioData)
                
                if (!isPlaying) {
                    startPlayback()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to queue audio for playback", e)
            }
        }
    }
    
    /**
     * Play base64 encoded audio
     */
    fun playBase64Audio(base64Audio: String) {
        try {
            val audioData = Base64.decode(base64Audio, Base64.DEFAULT)
            playAudio(audioData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode base64 audio", e)
        }
    }
    
    /**
     * Start audio playback
     */
    private fun startPlayback() {
        if (isPlaying) return
        
        isPlaying = true
        
        processingScope.launch {
            try {
                audioTrack?.play()
                
                while (isPlaying || !playbackQueue.isEmpty) {
                    val audioData = playbackQueue.tryReceive().getOrNull()
                    
                    if (audioData != null) {
                        // Apply audio processing
                        val processedAudio = if (config.enableAudioProcessing) {
                            applyAudioEffects(audioData)
                        } else {
                            audioData
                        }
                        
                        // Write to audio track
                        val written = audioTrack?.write(processedAudio, 0, processedAudio.size) ?: 0
                        
                        if (written < 0) {
                            Log.e(TAG, "Error writing audio: $written")
                            break
                        }
                    } else {
                        // No audio in queue, wait a bit
                        delay(10)
                        
                        // Stop if queue remains empty
                        if (playbackQueue.isEmpty) {
                            delay(100)
                            if (playbackQueue.isEmpty) {
                                isPlaying = false
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Playback error", e)
            } finally {
                audioTrack?.stop()
                isPlaying = false
            }
        }
    }
    
    /**
     * Stop audio playback
     */
    fun stopPlayback() {
        isPlaying = false
        audioTrack?.stop()
        audioTrack?.flush()
        
        // Clear playback queue
        processingScope.launch {
            while (!playbackQueue.isEmpty) {
                playbackQueue.tryReceive()
            }
        }
        
        Log.d(TAG, "Playback stopped")
    }
    
    /**
     * Process audio frame (noise reduction, etc.)
     */
    private fun processAudioFrame(audioData: ByteArray): ByteArray {
        if (!config.enableAudioProcessing) {
            return audioData
        }
        
        val samples = bytesToShorts(audioData)
        
        // Apply noise gate
        if (config.noiseGateThreshold > 0) {
            applyNoiseGate(samples, config.noiseGateThreshold)
        }
        
        // Apply gain
        if (config.recordGain != 1.0f) {
            applyGain(samples, config.recordGain)
        }
        
        return shortsToBytes(samples)
    }
    
    /**
     * Apply audio effects for playback
     */
    private fun applyAudioEffects(audioData: ByteArray): ByteArray {
        val samples = bytesToShorts(audioData)
        
        // Apply gain
        if (config.playbackGain != 1.0f) {
            applyGain(samples, config.playbackGain)
        }
        
        // Apply fade in/out if enabled
        if (config.enableFadeInOut) {
            applyFadeInOut(samples)
        }
        
        return shortsToBytes(samples)
    }
    
    /**
     * Apply noise gate to samples
     */
    private fun applyNoiseGate(samples: ShortArray, threshold: Float) {
        val thresholdValue = (Short.MAX_VALUE * threshold).toInt()
        
        for (i in samples.indices) {
            if (abs(samples[i].toInt()) < thresholdValue) {
                samples[i] = 0
            }
        }
    }
    
    /**
     * Apply gain to samples
     */
    private fun applyGain(samples: ShortArray, gain: Float) {
        for (i in samples.indices) {
            val amplified = (samples[i] * gain).toInt()
            samples[i] = max(Short.MIN_VALUE.toInt(), min(Short.MAX_VALUE.toInt(), amplified)).toShort()
        }
    }
    
    /**
     * Apply fade in/out effect
     */
    private fun applyFadeInOut(samples: ShortArray) {
        val fadeLength = min(samples.size / 10, REALTIME_SAMPLE_RATE / 10) // Max 100ms fade
        
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
    }
    
    /**
     * Update audio level meter
     */
    private fun updateAudioLevel(audioData: ByteArray) {
        val samples = bytesToShorts(audioData)
        var sum = 0.0
        
        for (sample in samples) {
            sum += abs(sample.toDouble())
        }
        
        val average = sum / samples.size
        val level = (average / Short.MAX_VALUE).toFloat()
        
        _audioLevel.value = level
    }
    
    /**
     * Detect voice activity
     */
    private fun detectVoiceActivity(audioData: ByteArray) {
        val level = _audioLevel.value
        val isActive = level > config.vadThreshold
        
        // Simple debouncing
        if (isActive != _voiceActivity.value) {
            processingScope.launch {
                delay(config.vadDebounceMs)
                if (isActive == (level > config.vadThreshold)) {
                    _voiceActivity.value = isActive
                }
            }
        }
    }
    
    /**
     * Convert byte array to short array (PCM16)
     */
    private fun bytesToShorts(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return shorts
    }
    
    /**
     * Convert short array to byte array (PCM16)
     */
    private fun shortsToBytes(shorts: ShortArray): ByteArray {
        val buffer = ByteBuffer.allocate(shorts.size * 2)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        for (short in shorts) {
            buffer.putShort(short)
        }
        return buffer.array()
    }
    
    /**
     * Convert audio data to base64
     */
    fun audioToBase64(audioData: ByteArray): String {
        return Base64.encodeToString(audioData, Base64.NO_WRAP)
    }
    
    /**
     * Convert base64 to audio data
     */
    fun base64ToAudio(base64Audio: String): ByteArray {
        return Base64.decode(base64Audio, Base64.DEFAULT)
    }
    
    /**
     * Set playback volume
     */
    fun setPlaybackVolume(volume: Float) {
        val clampedVolume = max(0f, min(1f, volume))
        audioTrack?.setVolume(clampedVolume)
    }
    
    /**
     * Clean up resources
     */
    fun release() {
        stopRecording()
        stopPlayback()
        
        audioRecord?.release()
        audioRecord = null
        
        audioTrack?.release()
        audioTrack = null
        
        processingScope.cancel()
        
        Log.d(TAG, "Audio processor released")
    }
    
    /**
     * Recording state
     */
    enum class RecordingState {
        IDLE,
        RECORDING,
        PAUSED
    }
}

/**
 * Audio configuration
 */
data class AudioConfig(
    val recordBufferSize: Int = 4096,
    val playbackBufferSize: Int = 8192,
    val frameMs: Int = 20, // Frame size in milliseconds
    val recordGain: Float = 1.0f,
    val playbackGain: Float = 1.0f,
    val playbackVolume: Float = 1.0f,
    val enableAudioProcessing: Boolean = true,
    val noiseGateThreshold: Float = 0.01f,
    val enableVAD: Boolean = true,
    val vadThreshold: Float = 0.02f,
    val vadDebounceMs: Long = 100,
    val enableFadeInOut: Boolean = true
)