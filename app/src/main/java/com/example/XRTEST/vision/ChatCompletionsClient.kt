package com.example.XRTEST.vision

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI Chat Completions API Client for Vision (GPT-4V)
 * Handles image analysis through standard REST API
 */
class ChatCompletionsClient(
    private val apiKey: String,
    private val onTextResponse: (String) -> Unit,
    private val onError: (String) -> Unit,
    private var useKorean: Boolean = true  // Default to Korean
) {
    
    companion object {
        private const val TAG = "ChatCompletionsClient"
        private const val API_URL = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-4o"  // GPT-4 with vision capabilities
        private const val MAX_TOKENS = 500
        private const val TEMPERATURE = 0.7
        private const val TIMEOUT_SECONDS = 30L
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Send an image with text prompt for analysis
     * Uses Chat Completions API which supports vision
     */
    fun analyzeImage(imageData: ByteArray, prompt: String) {
        coroutineScope.launch {
            try {
                // Convert image to base64 with proper data URI format
                val base64Image = Base64.encodeToString(imageData, Base64.NO_WRAP)
                val dataUri = "data:image/jpeg;base64,$base64Image"
                
                // Build the request JSON
                val requestJson = JSONObject().apply {
                    put("model", MODEL)
                    put("messages", JSONArray().apply {
                        // Add system message with improved vision analysis instructions
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", if (useKorean) {
                                """
                                    You are an AI assistant for AR glasses that helps users understand their visual environment.
                                    
                                    [CRITICAL VISUAL ANALYSIS PROTOCOL]
                                    Follow these steps PRECISELY when analyzing images:
                                    
                                    1. COLOR IDENTIFICATION:
                                       - Identify colors based on RGB values and standard color perception
                                       - Common colors: red, blue, yellow, green, orange, purple, pink, brown, black, white, gray
                                       - If uncertain about a color, say "appears to be" or "looks like"
                                       - NEVER guess colors - be accurate or express uncertainty
                                    
                                    2. OBJECT RECOGNITION:
                                       - Only describe objects you can clearly identify
                                       - For ambiguous objects, say "possibly" or "might be"
                                       - Focus on obviously distinctive features
                                    
                                    3. SPATIAL ANALYSIS:
                                       - Describe positions: center, left, right, top, bottom
                                       - Mention relative sizes: large, medium, small
                                       - Note distances if relevant: near, far, background
                                    
                                    4. RESPONSE STRUCTURE:
                                       - Start with the most prominent/important elements
                                       - Be concise but accurate
                                       - Mention confidence level when uncertain
                                    
                                    5. COMMON MISTAKES TO AVOID:
                                       - Don't confuse yellow with blue or vice versa
                                       - Don't make assumptions about objects not clearly visible
                                       - Don't add details that aren't in the image
                                    
                                    [LANGUAGE RULE]
                                    IMPORTANT: Always respond in Korean language (한국어).
                                    Even though these instructions are in English, your entire response must be in Korean.
                                    반드시 한국어로만 응답하세요.
                                """.trimIndent()
                            } else {
                                """
                                    You are an AI assistant for AR glasses that helps users understand their visual environment.
                                    
                                    [VISUAL ANALYSIS PROTOCOL]
                                    1. Identify colors accurately based on standard color perception
                                    2. Describe only clearly visible objects
                                    3. Note spatial relationships and positions
                                    4. Express uncertainty when appropriate
                                    5. Be concise and accurate
                                """.trimIndent()
                            })
                        })
                        
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", JSONArray().apply {
                                // Text content
                                put(JSONObject().apply {
                                    put("type", "text")
                                    put("text", if (useKorean) {
                                        """
                                        User Question: $prompt
                                        
                                        Remember to:
                                        1. Analyze the image systematically
                                        2. Identify colors accurately (don't confuse yellow/blue)
                                        3. Only describe what you can clearly see
                                        4. Respond entirely in Korean (한국어로 답변)
                                        """.trimIndent()
                                    } else {
                                        prompt
                                    })
                                })
                                // Image content
                                put(JSONObject().apply {
                                    put("type", "image_url")
                                    put("image_url", JSONObject().apply {
                                        put("url", dataUri)
                                        put("detail", "auto")  // Can be "low", "high", or "auto"
                                    })
                                })
                            })
                        })
                    })
                    put("max_tokens", MAX_TOKENS)
                    put("temperature", TEMPERATURE)
                }
                
                Log.d(TAG, "Sending vision request with ${imageData.size} byte image")
                Log.d(TAG, "Prompt: ${prompt.take(100)}")
                
                // Create the request
                val mediaType = "application/json".toMediaType()
                val requestBody = requestJson.toString().toRequestBody(mediaType)
                
                val request = Request.Builder()
                    .url(API_URL)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build()
                
                // Execute the request
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Request failed", e)
                        onError("Network error: ${e.message}")
                    }
                    
                    override fun onResponse(call: Call, response: Response) {
                        response.use { resp ->
                            val responseBody = resp.body?.string()
                            
                            if (!resp.isSuccessful) {
                                Log.e(TAG, "API error: ${resp.code} - $responseBody")
                                handleApiError(resp.code, responseBody)
                                return
                            }
                            
                            try {
                                val jsonResponse = JSONObject(responseBody ?: "{}")
                                val choices = jsonResponse.optJSONArray("choices")
                                
                                if (choices != null && choices.length() > 0) {
                                    val firstChoice = choices.getJSONObject(0)
                                    val message = firstChoice.getJSONObject("message")
                                    val content = message.getString("content")
                                    
                                    Log.d(TAG, "Received vision response: ${content.take(100)}")
                                    onTextResponse(content)
                                } else {
                                    Log.w(TAG, "No choices in response")
                                    onError("No response content available")
                                }
                                
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing response", e)
                                onError("Failed to parse response: ${e.message}")
                            }
                        }
                    }
                })
                
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing image", e)
                onError("Failed to analyze image: ${e.message}")
            }
        }
    }
    
    /**
     * Send text-only message (for fallback or testing)
     */
    fun sendTextMessage(text: String) {
        coroutineScope.launch {
            try {
                val requestJson = JSONObject().apply {
                    put("model", MODEL)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", text)
                        })
                    })
                    put("max_tokens", MAX_TOKENS)
                    put("temperature", TEMPERATURE)
                }
                
                val mediaType = "application/json".toMediaType()
                val requestBody = requestJson.toString().toRequestBody(mediaType)
                
                val request = Request.Builder()
                    .url(API_URL)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build()
                
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Request failed", e)
                        onError("Network error: ${e.message}")
                    }
                    
                    override fun onResponse(call: Call, response: Response) {
                        response.use { resp ->
                            val responseBody = resp.body?.string()
                            
                            if (!resp.isSuccessful) {
                                handleApiError(resp.code, responseBody)
                                return
                            }
                            
                            try {
                                val jsonResponse = JSONObject(responseBody ?: "{}")
                                val choices = jsonResponse.optJSONArray("choices")
                                
                                if (choices != null && choices.length() > 0) {
                                    val firstChoice = choices.getJSONObject(0)
                                    val message = firstChoice.getJSONObject("message")
                                    val content = message.getString("content")
                                    onTextResponse(content)
                                }
                                
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing response", e)
                                onError("Failed to parse response")
                            }
                        }
                    }
                })
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending text message", e)
                onError("Failed to send message: ${e.message}")
            }
        }
    }
    
    /**
     * Handle API error responses
     */
    private fun handleApiError(code: Int, responseBody: String?) {
        val errorMessage = when (code) {
            401 -> "Invalid API key. Please check your OpenAI API key configuration."
            429 -> "Rate limit exceeded. Please try again later."
            500, 502, 503 -> "OpenAI service temporarily unavailable."
            else -> {
                // Try to parse error message from response
                try {
                    val errorJson = JSONObject(responseBody ?: "{}")
                    val error = errorJson.optJSONObject("error")
                    error?.optString("message") ?: "API error: $code"
                } catch (e: Exception) {
                    "API error: $code"
                }
            }
        }
        
        onError(errorMessage)
    }
    
    /**
     * Set language mode (Korean or English)
     */
    fun setLanguageMode(korean: Boolean) {
        useKorean = korean
        Log.d(TAG, "Language mode changed to: ${if (korean) "Korean (한국어)" else "English"}")
    }
    
    /**
     * Get current language mode
     */
    fun isKoreanMode(): Boolean = useKorean
    
    /**
     * Clean up resources
     */
    fun destroy() {
        coroutineScope.cancel()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}