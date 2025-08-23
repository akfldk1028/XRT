package com.example.XRTEST.network

import com.google.gson.annotations.SerializedName

/**
 * A2A Protocol Data Models for Agent Communication
 */
data class A2ARequest(
    @SerializedName("jsonrpc") val jsonrpc: String = "2.0",
    @SerializedName("id") val id: String,
    @SerializedName("method") val method: String = "message/send",
    @SerializedName("params") val params: A2AParams
)

data class A2AParams(
    @SerializedName("message") val message: A2AMessage
)

data class A2AMessage(
    @SerializedName("messageId") val messageId: String,
    @SerializedName("taskId") val taskId: String,
    @SerializedName("contextId") val contextId: String = "ar_glass_qa",
    @SerializedName("parts") val parts: List<A2APart>
)

data class A2APart(
    @SerializedName("kind") val kind: String = "text",
    @SerializedName("text") val text: String,
    @SerializedName("mimeType") val mimeType: String = "text/plain"
)

data class A2AResponse(
    @SerializedName("jsonrpc") val jsonrpc: String,
    @SerializedName("id") val id: String,
    @SerializedName("result") val result: A2AResult?
)

data class A2AResult(
    @SerializedName("artifacts") val artifacts: List<A2AArtifact>?
)

data class A2AArtifact(
    @SerializedName("parts") val parts: List<A2APart>?
)

/**
 * Agent endpoints configuration
 */
object AgentEndpoints {
    const val PERCEPTION_PORT = 8030
    const val VISION_PORT = 8031
    const val UX_TTS_PORT = 8032
    const val LOGGER_PORT = 8033
    
    const val BASE_URL = "http://localhost"
    
    val PERCEPTION_URL = "$BASE_URL:$PERCEPTION_PORT/"
    val VISION_URL = "$BASE_URL:$VISION_PORT/"
    val UX_TTS_URL = "$BASE_URL:$UX_TTS_PORT/"
    val LOGGER_URL = "$BASE_URL:$LOGGER_PORT/"
}
