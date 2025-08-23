package com.example.XRTEST.vision

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WebSocket Manager for OpenAI Realtime API
 * Handles connection lifecycle, reconnection, and message flow
 */
class WebSocketManager(
    private val url: String,
    private val headers: Map<String, String>,
    private val config: WebSocketConfig = WebSocketConfig()
) {
    companion object {
        private const val TAG = "WebSocketManager"
    }

    // Connection state
    private var webSocket: WebSocket? = null
    private var okHttpClient: OkHttpClient? = null
    private val isConnected = AtomicBoolean(false)
    private val isManualDisconnect = AtomicBoolean(false)
    
    // Reconnection handling
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private val reconnectScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Message handling
    private val messageChannel = Channel<String>(Channel.UNLIMITED)
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()
    
    // Event flows
    private val _messages = MutableSharedFlow<JSONObject>()
    val messages: SharedFlow<JSONObject> = _messages
    
    private val _errors = MutableSharedFlow<WebSocketError>()
    val errors: SharedFlow<WebSocketError> = _errors
    
    init {
        okHttpClient = buildOkHttpClient()
    }
    
    /**
     * Connect to WebSocket
     */
    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isConnected.get()) {
                Log.w(TAG, "Already connected")
                return@withContext Result.success(Unit)
            }
            
            isManualDisconnect.set(false)
            _connectionState.value = ConnectionState.CONNECTING
            
            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }
            
            webSocket = okHttpClient!!.newWebSocket(
                requestBuilder.build(),
                createWebSocketListener()
            )
            
            // Wait for connection with timeout
            withTimeout(config.connectTimeoutMs) {
                while (!isConnected.get() && _connectionState.value == ConnectionState.CONNECTING) {
                    delay(100)
                }
            }
            
            if (isConnected.get()) {
                Log.d(TAG, "WebSocket connected successfully")
                Result.success(Unit)
            } else {
                throw Exception("Failed to establish WebSocket connection")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            _connectionState.value = ConnectionState.ERROR
            _errors.emit(WebSocketError.ConnectionFailed(e))
            Result.failure(e)
        }
    }
    
    /**
     * Send JSON message
     */
    fun sendMessage(message: JSONObject): Boolean {
        return try {
            if (!isConnected.get()) {
                Log.w(TAG, "Cannot send message: not connected")
                false
            } else {
                val sent = webSocket?.send(message.toString()) ?: false
                if (sent) {
                    Log.v(TAG, "Sent message: ${message.optString("type", "unknown")}")
                }
                sent
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
            false
        }
    }
    
    /**
     * Send text message
     */
    fun sendText(text: String): Boolean {
        return try {
            if (!isConnected.get()) {
                Log.w(TAG, "Cannot send text: not connected")
                false
            } else {
                webSocket?.send(text) ?: false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send text", e)
            false
        }
    }
    
    /**
     * Send binary data
     */
    fun sendBinary(data: ByteArray): Boolean {
        return try {
            if (!isConnected.get()) {
                Log.w(TAG, "Cannot send binary: not connected")
                false
            } else {
                webSocket?.send(okio.ByteString.of(*data)) ?: false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send binary", e)
            false
        }
    }
    
    /**
     * Create WebSocket listener
     */
    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                isConnected.set(true)
                _connectionState.value = ConnectionState.CONNECTED
                reconnectAttempts = 0
                reconnectJob?.cancel()
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                reconnectScope.launch {
                    try {
                        val message = JSONObject(text)
                        _messages.emit(message)
                        
                        // Handle system messages
                        when (message.optString("type")) {
                            "error" -> {
                                val error = message.getJSONObject("error")
                                _errors.emit(
                                    WebSocketError.ServerError(
                                        error.optString("message", "Unknown error"),
                                        error.optString("code", "")
                                    )
                                )
                            }
                            "ping" -> {
                                // Respond to ping
                                sendMessage(JSONObject().apply {
                                    put("type", "pong")
                                })
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse message", e)
                        _errors.emit(WebSocketError.ParseError(e))
                    }
                }
            }
            
            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                Log.v(TAG, "Received binary message: ${bytes.size} bytes")
                // Handle binary messages if needed
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code - $reason")
                webSocket.close(1000, null)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code - $reason")
                handleDisconnection(code, reason)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                reconnectScope.launch {
                    _errors.emit(WebSocketError.ConnectionFailed(t))
                }
                handleDisconnection(1006, t.message ?: "Connection failed")
            }
        }
    }
    
    /**
     * Handle disconnection and potential reconnection
     */
    private fun handleDisconnection(code: Int, reason: String) {
        isConnected.set(false)
        _connectionState.value = ConnectionState.DISCONNECTED
        
        if (!isManualDisconnect.get() && config.enableReconnect) {
            if (reconnectAttempts < config.maxReconnectAttempts) {
                reconnectAttempts++
                scheduleReconnect()
            } else {
                Log.e(TAG, "Max reconnection attempts reached")
                _connectionState.value = ConnectionState.ERROR
                reconnectScope.launch {
                    _errors.emit(WebSocketError.ReconnectFailed("Max attempts reached"))
                }
            }
        }
    }
    
    /**
     * Schedule reconnection attempt
     */
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = reconnectScope.launch {
            val delay = calculateReconnectDelay()
            Log.d(TAG, "Reconnecting in ${delay}ms (attempt $reconnectAttempts/${config.maxReconnectAttempts})")
            _connectionState.value = ConnectionState.RECONNECTING
            delay(delay)
            
            try {
                connect()
            } catch (e: Exception) {
                Log.e(TAG, "Reconnection failed", e)
            }
        }
    }
    
    /**
     * Calculate reconnect delay with exponential backoff
     */
    private fun calculateReconnectDelay(): Long {
        return minOf(
            config.reconnectBaseDelayMs * (1 shl (reconnectAttempts - 1)),
            config.reconnectMaxDelayMs
        )
    }
    
    /**
     * Build OkHttp client
     */
    private fun buildOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().apply {
            connectTimeout(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
            readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket
            writeTimeout(config.writeTimeoutMs, TimeUnit.MILLISECONDS)
            pingInterval(config.pingIntervalMs, TimeUnit.MILLISECONDS)
            
            if (config.enableLogging) {
                addInterceptor(okhttp3.logging.HttpLoggingInterceptor().apply {
                    level = okhttp3.logging.HttpLoggingInterceptor.Level.HEADERS
                })
            }
            
            // Connection pool for better performance
            connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            
            // Retry on connection failure
            retryOnConnectionFailure(true)
        }.build()
    }
    
    /**
     * Disconnect from WebSocket
     */
    fun disconnect() {
        isManualDisconnect.set(true)
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        isConnected.set(false)
        _connectionState.value = ConnectionState.DISCONNECTED
        Log.d(TAG, "Disconnected from WebSocket")
    }
    
    /**
     * Clean up resources
     */
    fun destroy() {
        disconnect()
        reconnectScope.cancel()
        messageChannel.close()
        okHttpClient?.dispatcher?.executorService?.shutdown()
        okHttpClient = null
    }
    
    /**
     * Connection states
     */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        ERROR
    }
    
    /**
     * WebSocket errors
     */
    sealed class WebSocketError {
        data class ConnectionFailed(val throwable: Throwable) : WebSocketError()
        data class ServerError(val message: String, val code: String) : WebSocketError()
        data class ParseError(val throwable: Throwable) : WebSocketError()
        data class ReconnectFailed(val reason: String) : WebSocketError()
    }
}

/**
 * WebSocket configuration
 */
data class WebSocketConfig(
    val connectTimeoutMs: Long = 30000,
    val writeTimeoutMs: Long = 30000,
    val pingIntervalMs: Long = 30000,
    val enableReconnect: Boolean = true,
    val maxReconnectAttempts: Int = 5,
    val reconnectBaseDelayMs: Long = 1000,
    val reconnectMaxDelayMs: Long = 30000,
    val enableLogging: Boolean = false
)