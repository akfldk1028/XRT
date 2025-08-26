package com.example.XRTEST.vision

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Vision Analyzer using OpenAI Chat Completions API with GPT-4V
 * Provides actual image recognition and analysis capabilities
 */
class VisionAnalyzer(
    private val apiKey: String,
    private val onAnalysisResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "VisionAnalyzer"
        private const val CHAT_API_URL = "https://api.openai.com/v1/chat/completions"
        private const val VISION_MODEL = "gpt-4o" // GPT-4 with vision capabilities
        private const val MAX_IMAGE_SIZE = 2048 // Max dimension for image
        private const val JPEG_QUALITY = 85
        private const val REQUEST_TIMEOUT_SECONDS = 30L
        
        // Analysis modes
        const val MODE_GENERAL = "general"
        const val MODE_OBJECT_DETECTION = "object"
        const val MODE_TEXT_READING = "text"
        const val MODE_COLOR_ANALYSIS = "color"
        const val MODE_SCENE_UNDERSTANDING = "scene"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // State management
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()
    
    private val _lastAnalysisTime = MutableStateFlow(0L)
    val lastAnalysisTime: StateFlow<Long> = _lastAnalysisTime.asStateFlow()
    
    // Cache for recent analyses
    private val analysisCache = mutableMapOf<String, AnalysisResult>()
    private val cacheExpirationMs = 30000L // 30 seconds
    
    data class AnalysisResult(
        val result: String,
        val timestamp: Long,
        val mode: String
    )
    
    /**
     * Analyze an image with a specific prompt
     * @param bitmap The image to analyze
     * @param prompt Custom prompt for the analysis
     * @param mode Analysis mode (general, object, text, color, scene)
     * @param useKorean Whether to respond in Korean
     */
    fun analyzeImage(
        bitmap: Bitmap,
        prompt: String? = null,
        mode: String = MODE_GENERAL,
        useKorean: Boolean = true
    ) {
        coroutineScope.launch {
            try {
                _isAnalyzing.value = true
                Log.d(TAG, "Starting image analysis - Mode: $mode, Korean: $useKorean")
                
                // Convert bitmap to base64
                val base64Image = bitmapToBase64(bitmap)
                val imageHash = base64Image.hashCode().toString()
                
                // Check cache
                analysisCache[imageHash]?.let { cached ->
                    if (System.currentTimeMillis() - cached.timestamp < cacheExpirationMs &&
                        cached.mode == mode) {
                        Log.d(TAG, "Using cached analysis result")
                        onAnalysisResult(cached.result)
                        _isAnalyzing.value = false
                        return@launch
                    }
                }
                
                // Create system and user prompts based on mode and language
                val systemPrompt = createSystemPrompt(mode, useKorean)
                val userPrompt = prompt ?: createDefaultPrompt(mode, useKorean)
                
                // Build the API request
                val requestBody = buildVisionRequest(base64Image, systemPrompt, userPrompt)
                
                // Make the API call
                val request = Request.Builder()
                    .url(CHAT_API_URL)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        Log.e(TAG, "API request failed: ${response.code} - $errorBody")
                        onError("Vision analysis failed: ${response.code}")
                        return@use
                    }
                    
                    // Parse the response
                    val responseBody = response.body?.string() ?: ""
                    val jsonResponse = JSONObject(responseBody)
                    
                    // Extract the analysis result
                    val choices = jsonResponse.getJSONArray("choices")
                    if (choices.length() > 0) {
                        val message = choices.getJSONObject(0).getJSONObject("message")
                        val content = message.getString("content")
                        
                        Log.d(TAG, "Analysis successful: ${content.take(200)}...")
                        Log.d(TAG, "🎯 VisionAnalyzer: About to call onAnalysisResult callback...")
                        
                        // Cache the result
                        analysisCache[imageHash] = AnalysisResult(
                            result = content,
                            timestamp = System.currentTimeMillis(),
                            mode = mode
                        )
                        
                        // Update last analysis time
                        _lastAnalysisTime.value = System.currentTimeMillis()
                        
                        // Return the result
                        onAnalysisResult(content)
                        Log.d(TAG, "🎯 VisionAnalyzer: onAnalysisResult callback completed!")
                    } else {
                        Log.e(TAG, "No choices in API response")
                        onError("No analysis result received")
                    }
                }
                
            } catch (e: IOException) {
                Log.e(TAG, "Network error during analysis", e)
                onError("Network error: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error during image analysis", e)
                onError("Analysis failed: ${e.message}")
            } finally {
                _isAnalyzing.value = false
            }
        }
    }
    
    /**
     * Analyze image with focus on specific objects
     */
    fun analyzeObjectsInImage(bitmap: Bitmap, useKorean: Boolean = true) {
        val prompt = if (useKorean) {
            "이 이미지에서 보이는 모든 물체를 자세히 설명해주세요. 각 물체의 색상, 크기, 위치를 포함해주세요."
        } else {
            "Describe all objects visible in this image in detail, including their colors, sizes, and positions."
        }
        analyzeImage(bitmap, prompt, MODE_OBJECT_DETECTION, useKorean)
    }
    
    /**
     * Analyze colors in the image
     */
    fun analyzeColorsInImage(bitmap: Bitmap, useKorean: Boolean = true) {
        val prompt = if (useKorean) {
            "이 이미지의 주요 색상들을 정확히 식별하고 설명해주세요. 각 물체의 실제 색상을 구체적으로 알려주세요."
        } else {
            "Identify and describe the main colors in this image accurately. Specify the actual color of each object."
        }
        analyzeImage(bitmap, prompt, MODE_COLOR_ANALYSIS, useKorean)
    }
    
    /**
     * Read text from the image
     */
    fun readTextFromImage(bitmap: Bitmap, useKorean: Boolean = true) {
        val prompt = if (useKorean) {
            "이 이미지에서 보이는 모든 텍스트를 읽고 그 내용을 알려주세요."
        } else {
            "Read all text visible in this image and tell me what it says."
        }
        analyzeImage(bitmap, prompt, MODE_TEXT_READING, useKorean)
    }
    
    /**
     * Understand the scene context
     */
    fun understandScene(bitmap: Bitmap, useKorean: Boolean = true) {
        val prompt = if (useKorean) {
            "이 장면이 어디인지, 무엇이 일어나고 있는지 전체적인 상황을 설명해주세요."
        } else {
            "Describe where this scene is and what's happening, providing overall context."
        }
        analyzeImage(bitmap, prompt, MODE_SCENE_UNDERSTANDING, useKorean)
    }
    
    /**
     * Create system prompt based on mode and language
     */
    private fun createSystemPrompt(mode: String, useKorean: Boolean): String {
        val basePrompt = when (mode) {
            MODE_OBJECT_DETECTION -> {
                if (useKorean) {
                    "당신은 물체 인식 전문가입니다. 이미지에서 보이는 모든 물체를 정확히 식별하고 설명합니다."
                } else {
                    "You are an object detection expert. Identify and describe all objects visible in images accurately."
                }
            }
            MODE_COLOR_ANALYSIS -> {
                if (useKorean) {
                    """당신은 정확한 색상 분석 전문가입니다. 매우 신중하게 색상을 관찰하세요.
                    중요: 노란색과 파란색을 절대 혼동하지 마세요. 
                    - 노란색(Yellow): 밝고 따뜻한 색, 태양 색깔
                    - 파란색(Blue): 차갑고 시원한 색, 하늘/바다 색깔
                    색상을 말하기 전에 두 번 확인하세요. 정확한 색상명을 사용하세요."""
                } else {
                    """You are a precise color analysis expert. Observe colors very carefully.
                    CRITICAL: Never confuse yellow with blue or vice versa.
                    - Yellow: Bright, warm color like the sun
                    - Blue: Cool, cold color like sky/ocean
                    Double-check before stating any color. Use accurate color names."""
                }
            }
            MODE_TEXT_READING -> {
                if (useKorean) {
                    "당신은 OCR 전문가입니다. 이미지에서 텍스트를 정확히 읽고 전사합니다."
                } else {
                    "You are an OCR expert. Read and transcribe text from images accurately."
                }
            }
            MODE_SCENE_UNDERSTANDING -> {
                if (useKorean) {
                    "당신은 장면 이해 전문가입니다. 이미지의 전체적인 맥락과 상황을 파악합니다."
                } else {
                    "You are a scene understanding expert. Comprehend the overall context and situation in images."
                }
            }
            else -> { // MODE_GENERAL
                if (useKorean) {
                    """당신은 AR 안경을 위한 정밀 비전 분석 AI입니다. 사용자가 보고 있는 것을 정확히 설명합니다.
                    특히 색상 인식에 매우 신중해야 합니다:
                    - 노란색은 밝고 따뜻한 색 (태양, 바나나 색)
                    - 파란색은 차갑고 시원한 색 (하늘, 바다 색)
                    - 절대 노란색을 파란색으로, 파란색을 노란색으로 착각하지 마세요."""
                } else {
                    """You are a precision vision analysis AI for AR glasses. Describe what the user is looking at accurately.
                    Be extremely careful with color recognition:
                    - Yellow is bright and warm (sun, banana color)
                    - Blue is cool and cold (sky, ocean color) 
                    - Never confuse yellow with blue or vice versa."""
                }
            }
        }
        
        return if (useKorean) {
            "$basePrompt 중요: 모든 응답은 반드시 한국어로만 해주세요. 영어는 절대 사용하지 마세요. Only respond in Korean language."
        } else {
            "$basePrompt Respond in English."
        }
    }
    
    /**
     * Create default user prompt based on mode
     */
    private fun createDefaultPrompt(mode: String, useKorean: Boolean): String {
        return when (mode) {
            MODE_OBJECT_DETECTION -> {
                if (useKorean) "이 이미지에 무엇이 있나요?" else "What objects are in this image?"
            }
            MODE_COLOR_ANALYSIS -> {
                if (useKorean) "이 이미지의 색상을 설명해주세요." else "Describe the colors in this image."
            }
            MODE_TEXT_READING -> {
                if (useKorean) "이 이미지의 텍스트를 읽어주세요." else "Read the text in this image."
            }
            MODE_SCENE_UNDERSTANDING -> {
                if (useKorean) "이 장면을 설명해주세요." else "Describe this scene."
            }
            else -> {
                if (useKorean) "이 이미지를 설명해주세요." else "Describe this image."
            }
        }
    }
    
    /**
     * Build the vision API request JSON
     */
    private fun buildVisionRequest(
        base64Image: String,
        systemPrompt: String,
        userPrompt: String
    ): String {
        val request = JSONObject().apply {
            put("model", VISION_MODEL)
            put("messages", JSONArray().apply {
                // System message
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                
                // User message with image
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        // Text part
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", userPrompt)
                        })
                        
                        // Image part
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$base64Image")
                                put("detail", "high") // Use high detail for better accuracy
                            })
                        })
                    })
                })
            })
            
            // Optional parameters
            put("max_tokens", 500)
            put("temperature", 0.7)
        }
        
        return request.toString()
    }
    
    /**
     * Convert bitmap to base64 string
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        // Resize if needed
        val resizedBitmap = if (bitmap.width > MAX_IMAGE_SIZE || bitmap.height > MAX_IMAGE_SIZE) {
            val scale = MAX_IMAGE_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
        
        // Convert to JPEG and base64
        val outputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        val imageBytes = outputStream.toByteArray()
        
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
    }
    
    /**
     * Clear the analysis cache
     */
    fun clearCache() {
        analysisCache.clear()
        Log.d(TAG, "Analysis cache cleared")
    }
    
    /**
     * Clean up resources
     */
    fun destroy() {
        coroutineScope.cancel()
        client.dispatcher.executorService.shutdown()
        clearCache()
        Log.d(TAG, "VisionAnalyzer destroyed")
    }
}