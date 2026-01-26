package com.jengachat.data.remote

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket client for real-time messaging
 */
@Singleton
class WebSocketClient @Inject constructor(
    private val tokenManager: TokenManager
) {
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Event flows
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<WebSocketMessage>(replay = 0, extraBufferCapacity = 100)
    val messages: SharedFlow<WebSocketMessage> = _messages.asSharedFlow()
    
    // Alias for backwards compatibility
    val incomingMessages: SharedFlow<WebSocketMessage> get() = messages

    private val _typingEvents = MutableSharedFlow<TypingEvent>(replay = 0, extraBufferCapacity = 50)
    val typingEvents: SharedFlow<TypingEvent> = _typingEvents.asSharedFlow()

    private val _presenceEvents = MutableSharedFlow<PresenceEvent>(replay = 0, extraBufferCapacity = 50)
    val presenceEvents: SharedFlow<PresenceEvent> = _presenceEvents.asSharedFlow()

    private val _callSignals = MutableSharedFlow<CallSignalEvent>(replay = 0, extraBufferCapacity = 50)
    val callSignals: SharedFlow<CallSignalEvent> = _callSignals.asSharedFlow()

    private val _readReceipts = MutableSharedFlow<ReadReceiptEvent>(replay = 0, extraBufferCapacity = 100)
    val readReceipts: SharedFlow<ReadReceiptEvent> = _readReceipts.asSharedFlow()

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    fun connect() {
        if (isConnected) return
        
        val token = tokenManager.accessToken ?: run {
            _connectionState.value = ConnectionState.Error("No access token")
            return
        }

        _connectionState.value = ConnectionState.Connecting

        val request = Request.Builder()
            .url("${NetworkClient.WS_URL}?token=$token")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                _connectionState.value = ConnectionState.Connected
                reconnectJob?.cancel()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                _connectionState.value = ConnectionState.Disconnected
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                _connectionState.value = ConnectionState.Error(t.message ?: "Connection failed")
                scheduleReconnect()
            }
        })
    }

    fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        isConnected = false
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun scheduleReconnect() {
        if (tokenManager.accessToken == null) return
        
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(5000) // Wait 5 seconds before reconnecting
            if (!isConnected && tokenManager.accessToken != null) {
                connect()
            }
        }
    }

    private fun handleMessage(text: String) {
        try {
            val jsonObj = json.parseToJsonElement(text).jsonObject
            val type = jsonObj["type"]?.jsonPrimitive?.contentOrNull ?: return
            val data = jsonObj["data"]?.jsonObject

            when (type) {
                "connected" -> {
                    // Connection confirmed
                }
                "message" -> {
                    data?.let { 
                        val msg = parseMessage(it)
                        scope.launch { _messages.emit(msg) }
                    }
                }
                "typing" -> {
                    data?.let {
                        val event = parseTypingEvent(it)
                        scope.launch { _typingEvents.emit(event) }
                    }
                }
                "presence" -> {
                    data?.let {
                        val event = parsePresenceEvent(it)
                        scope.launch { _presenceEvents.emit(event) }
                    }
                }
                "call_signal" -> {
                    data?.let {
                        val event = parseCallSignal(it)
                        scope.launch { _callSignals.emit(event) }
                    }
                }
                "read" -> {
                    data?.let {
                        val event = parseReadReceipt(it)
                        scope.launch { _readReceipts.emit(event) }
                    }
                }
                "delivered" -> {
                    // Handle delivery receipt
                }
                "pong" -> {
                    // Heartbeat response
                }
                "error" -> {
                    val errorMsg = data?.get("message")?.jsonPrimitive?.contentOrNull
                    _connectionState.value = ConnectionState.Error(errorMsg ?: "Unknown error")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseMessage(data: JsonObject): WebSocketMessage {
        return WebSocketMessage(
            messageId = data["messageId"]?.jsonPrimitive?.contentOrNull ?: "",
            conversationId = data["conversationId"]?.jsonPrimitive?.contentOrNull ?: "",
            senderId = data["senderId"]?.jsonPrimitive?.contentOrNull ?: "",
            senderUsername = data["senderUsername"]?.jsonPrimitive?.contentOrNull,
            senderDisplayName = data["senderDisplayName"]?.jsonPrimitive?.contentOrNull,
            encryptedContent = data["encryptedContent"]?.jsonPrimitive?.contentOrNull ?: "",
            messageType = data["messageType"]?.jsonPrimitive?.contentOrNull ?: "text",
            timestamp = data["timestamp"]?.jsonPrimitive?.contentOrNull ?: ""
        )
    }

    private fun parseTypingEvent(data: JsonObject): TypingEvent {
        return TypingEvent(
            conversationId = data["conversationId"]?.jsonPrimitive?.contentOrNull ?: "",
            userId = data["userId"]?.jsonPrimitive?.contentOrNull ?: "",
            isTyping = data["isTyping"]?.jsonPrimitive?.booleanOrNull ?: false
        )
    }

    private fun parsePresenceEvent(data: JsonObject): PresenceEvent {
        return PresenceEvent(
            userId = data["userId"]?.jsonPrimitive?.contentOrNull ?: "",
            isOnline = data["isOnline"]?.jsonPrimitive?.booleanOrNull ?: false,
            lastSeen = data["lastSeen"]?.jsonPrimitive?.contentOrNull
        )
    }

    private fun parseCallSignal(data: JsonObject): CallSignalEvent {
        return CallSignalEvent(
            callId = data["callId"]?.jsonPrimitive?.contentOrNull ?: "",
            senderId = data["senderId"]?.jsonPrimitive?.contentOrNull ?: "",
            signalType = data["signalType"]?.jsonPrimitive?.contentOrNull ?: "",
            encryptedPayload = data["encryptedPayload"]?.jsonPrimitive?.contentOrNull ?: "",
            timestamp = data["timestamp"]?.jsonPrimitive?.contentOrNull ?: ""
        )
    }

    private fun parseReadReceipt(data: JsonObject): ReadReceiptEvent {
        return ReadReceiptEvent(
            conversationId = data["conversationId"]?.jsonPrimitive?.contentOrNull ?: "",
            messageIds = data["messageIds"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            readBy = data["readBy"]?.jsonPrimitive?.contentOrNull ?: "",
            readAt = data["readAt"]?.jsonPrimitive?.contentOrNull ?: ""
        )
    }

    // Send methods

    fun sendMessage(conversationId: String, encryptedContent: String, messageType: String = "text", messageId: String? = null) {
        val payload = buildJsonObject {
            put("type", "message")
            putJsonObject("data") {
                put("conversationId", conversationId)
                put("encryptedContent", encryptedContent)
                put("messageType", messageType)
                messageId?.let { put("messageId", it) }
            }
        }
        webSocket?.send(payload.toString())
    }

    fun sendTyping(conversationId: String, isTyping: Boolean) {
        val payload = buildJsonObject {
            put("type", "typing")
            putJsonObject("data") {
                put("conversationId", conversationId)
                put("isTyping", isTyping)
            }
        }
        webSocket?.send(payload.toString())
    }

    fun sendReadReceipt(conversationId: String, messageIds: List<String>) {
        val payload = buildJsonObject {
            put("type", "read")
            putJsonObject("data") {
                put("conversationId", conversationId)
                putJsonArray("messageIds") {
                    messageIds.forEach { add(it) }
                }
            }
        }
        webSocket?.send(payload.toString())
    }

    fun sendCallSignal(callId: String, recipientId: String, signalType: String, encryptedPayload: String) {
        val payload = buildJsonObject {
            put("type", "call_signal")
            putJsonObject("data") {
                put("callId", callId)
                put("recipientId", recipientId)
                put("signalType", signalType)
                put("encryptedPayload", encryptedPayload)
            }
        }
        webSocket?.send(payload.toString())
    }

    fun ping() {
        val payload = buildJsonObject { put("type", "ping") }
        webSocket?.send(payload.toString())
    }
}

// Event classes
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class WebSocketMessage(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val senderUsername: String?,
    val senderDisplayName: String?,
    val encryptedContent: String,
    val messageType: String,
    val timestamp: String
)

data class TypingEvent(
    val conversationId: String,
    val userId: String,
    val isTyping: Boolean
)

data class PresenceEvent(
    val userId: String,
    val isOnline: Boolean,
    val lastSeen: String?
)

data class CallSignalEvent(
    val callId: String,
    val senderId: String,
    val signalType: String,
    val encryptedPayload: String,
    val timestamp: String
)

data class ReadReceiptEvent(
    val conversationId: String,
    val messageIds: List<String>,
    val readBy: String,
    val readAt: String
)
