package com.example.XRTEST.network

import android.util.Log
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * A2A Client for communicating with Agent servers
 */
class A2AClient {
    
    companion object {
        private const val TAG = "A2AClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
    
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Send message to Perception Agent for camera/ROI processing
     */
    suspend fun sendToPerceptionAgent(
        message: String,
        taskId: String = "camera_processing"
    ): String? = withContext(Dispatchers.IO) {
        sendMessage(
            url = AgentEndpoints.PERCEPTION_URL,
            agentName = "Perception",
            message = message,
            taskId = taskId
        )
    }

    /**
     * Send message to Vision Agent for AI analysis
     */
    suspend fun sendToVisionAgent(
        message: String,
        imageData: String? = null,
        taskId: String = "vision_analysis"
    ): String? = withContext(Dispatchers.IO) {
        val fullMessage = if (imageData != null) {
            "$message\n\nImage data: $imageData"
        } else {
            message
        }
        
        sendMessage(
            url = AgentEndpoints.VISION_URL,
            agentName = "Vision",
            message = fullMessage,
            taskId = taskId
        )
    }

    /**
     * Send message to UX/TTS Agent for UI/audio processing
     */
    suspend fun sendToUXAgent(
        message: String,
        taskId: String = "ux_processing"
    ): String? = withContext(Dispatchers.IO) {
        sendMessage(
            url = AgentEndpoints.UX_TTS_URL,
            agentName = "UX/TTS",
            message = message,
            taskId = taskId
        )
    }

    /**
     * Send message to Logger Agent for metrics/logging
     */
    suspend fun sendToLoggerAgent(
        message: String,
        taskId: String = "logging"
    ): String? = withContext(Dispatchers.IO) {
        sendMessage(
            url = AgentEndpoints.LOGGER_URL,
            agentName = "Logger",
            message = message,
            taskId = taskId
        )
    }

    /**
     * Generic method to send A2A message to any agent
     */
    private suspend fun sendMessage(
        url: String,
        agentName: String,
        message: String,
        taskId: String
    ): String? {
        return try {
            Log.d(TAG, "Sending to $agentName Agent: $message")
            
            val request = A2ARequest(
                id = "${agentName.lowercase()}_${System.currentTimeMillis()}",
                params = A2AParams(
                    message = A2AMessage(
                        messageId = "msg_${System.currentTimeMillis()}",
                        taskId = taskId,
                        parts = listOf(
                            A2APart(text = message)
                        )
                    )
                )
            )
            
            val jsonRequest = gson.toJson(request)
            val requestBody = jsonRequest.toRequestBody(JSON_MEDIA_TYPE)
            
            val httpRequest = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .build()
            
            val response = httpClient.newCall(httpRequest).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val a2aResponse = gson.fromJson(responseBody, A2AResponse::class.java)
                
                // Extract response text
                val responseText = a2aResponse?.result?.artifacts?.firstOrNull()
                    ?.parts?.firstOrNull()?.text
                
                Log.d(TAG, "$agentName Agent response: ${responseText?.take(100)}...")
                return responseText
                
            } else {
                Log.e(TAG, "$agentName Agent HTTP error: ${response.code}")
                return null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error communicating with $agentName Agent", e)
            return null
        }
    }

    /**
     * Check if agent is available
     */
    suspend fun checkAgentHealth(agentUrl: String, agentName: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = Request.Builder()
                .url("${agentUrl}.well-known/agent-card.json")
                .get()
                .build()
                
            val response = httpClient.newCall(request).execute()
            val isHealthy = response.isSuccessful
            
            Log.d(TAG, "$agentName Agent health: ${if (isHealthy) "OK" else "ERROR"}")
            isHealthy
            
        } catch (e: Exception) {
            Log.e(TAG, "$agentName Agent health check failed", e)
            false
        }
    }

    /**
     * Check all agents status
     */
    suspend fun checkAllAgentsHealth(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        return@withContext mapOf(
            "Perception" to checkAgentHealth(AgentEndpoints.PERCEPTION_URL, "Perception"),
            "Vision" to checkAgentHealth(AgentEndpoints.VISION_URL, "Vision"),
            "UX/TTS" to checkAgentHealth(AgentEndpoints.UX_TTS_URL, "UX/TTS"),
            "Logger" to checkAgentHealth(AgentEndpoints.LOGGER_URL, "Logger")
        )
    }
}
