package com.jengachat.data.remote

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * High-performance WebSocket client for real-time messaging
 * Optimized for WhatsApp-like instant delivery
 */
@Singleton
class WebSocketClient @Inject constructor(
    private val tokenManager: TokenManager
) {
    private var webSocket: WebSocket? = null
    @Volatile
    private var isConnected = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    
    // Message queue for offline messages
    private val pendingMessages = ConcurrentLinkedQueue<PendingMessage>()
    
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
    
    // Message acknowledgements for tracking delivery status
    private val _messageAcks = MutableSharedFlow<MessageAck>(replay = 0, extraBufferCapacity = 100)
    val messageAcks: SharedFlow<MessageAck> = _messageAcks.asSharedFlow()
    
    // Delivery receipts (when recipient receives message)
    private val _deliveryReceipts = MutableSharedFlow<DeliveryReceiptEvent>(replay = 0, extraBufferCapacity = 100)
    val deliveryReceipts: SharedFlow<DeliveryReceiptEvent> = _deliveryReceipts.asSharedFlow()

    // Optimized client with faster ping interval for quicker dead connection detection
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket
        .pingInterval(15, TimeUnit.SECONDS)    // Faster ping for quick dead connection detection (was 30s)
        .connectTimeout(10, TimeUnit.SECONDS)  // Quick connect timeout
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
                reconnectAttempts = 0  // Reset on successful connection
                _connectionState.value = ConnectionState.Connected
                reconnectJob?.cancel()
                
                // Send frame-level auth
                try {
                    webSocket.send("{\"type\":\"auth\",\"data\":{\"token\":\"$token\"}}")
                } catch (e: Exception) {
                    android.util.Log.e("WebSocketClient", "Auth frame error: ${e.message}")
                }
                
                // Flush any pending messages that were queued while offline
                flushPendingMessages()
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
        reconnectAttempts = 0
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Faster reconnection with exponential backoff
     * Starts at 1 second, doubles each time, max 30 seconds
     */
    private fun scheduleReconnect() {
        if (tokenManager.accessToken == null) return
        
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            // Exponential backoff: 1s, 2s, 4s, 8s, 16s, max 30s
            val delayMs = min(1000L * (1 shl reconnectAttempts), 30000L)
            reconnectAttempts++
            
            delay(delayMs)
            if (!isConnected && tokenManager.accessToken != null) {
                connect()
            }
        }
    }
    
    /**
     * Flush pending messages after reconnection
     */
    private fun flushPendingMessages() {
        scope.launch {
            while (pendingMessages.isNotEmpty()) {
                val pending = pendingMessages.poll() ?: break
                if (isConnected) {
                    sendMessageInternal(pending)
                } else {
                    // Put back if disconnected again
                    enqueuePendingMessage(pending)
                    break
                }
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
                "message_ack" -> {
                    // Server acknowledged our message
                    data?.let {
                        val ack = parseMessageAck(it)
                        scope.launch { _messageAcks.emit(ack) }
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
                    // Handle delivery receipt - recipient received the message
                    data?.let {
                        val event = parseDeliveryReceipt(it)
                        scope.launch { _deliveryReceipts.emit(event) }
                    }
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
    
    private fun parseMessageAck(data: JsonObject): MessageAck {
        return MessageAck(
            localMessageId = data["localMessageId"]?.jsonPrimitive?.contentOrNull ?: "",
            serverMessageId = data["serverMessageId"]?.jsonPrimitive?.contentOrNull ?: "",
            status = data["status"]?.jsonPrimitive?.contentOrNull ?: "sent",
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
            conversationId = data["conversationId"]?.jsonPrimitive?.contentOrNull,
            senderId = data["senderId"]?.jsonPrimitive?.contentOrNull ?: "",
            senderName = data["senderName"]?.jsonPrimitive?.contentOrNull,
            senderAvatar = data["senderAvatar"]?.jsonPrimitive?.contentOrNull,
            signalType = data["signalType"]?.jsonPrimitive?.contentOrNull ?: "",
            callType = data["callType"]?.jsonPrimitive?.contentOrNull,
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
    
    private fun parseDeliveryReceipt(data: JsonObject): DeliveryReceiptEvent {
        return DeliveryReceiptEvent(
            conversationId = data["conversationId"]?.jsonPrimitive?.contentOrNull ?: "",
            messageIds = data["messageIds"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            deliveredTo = data["deliveredTo"]?.jsonPrimitive?.contentOrNull ?: "",
            deliveredAt = data["deliveredAt"]?.jsonPrimitive?.contentOrNull ?: ""
        )
    }

    // Send methods

    /**
     * Send message with queuing support for offline scenarios
     * Returns true if sent immediately, false if queued
     */
    fun sendMessage(conversationId: String, encryptedContent: String, messageType: String = "text", localMessageId: String? = null): Boolean {
        val pending = PendingMessage(
            localMessageId = localMessageId ?: System.currentTimeMillis().toString(),
            conversationId = conversationId,
            encryptedContent = encryptedContent,
            messageType = messageType,
            timestamp = System.currentTimeMillis()
        )
        
        return if (isConnected) {
            sendMessageInternal(pending)
            true
        } else {
            // Queue for later when reconnected (capped at 1000 items)
            enqueuePendingMessage(pending)
            false
        }
    }

    private fun enqueuePendingMessage(pending: PendingMessage) {
        while (pendingMessages.size >= 1000) {
            pendingMessages.poll()
        }
        pendingMessages.offer(pending)
    }
    
    private fun sendMessageInternal(pending: PendingMessage) {
        val payload = buildJsonObject {
            put("type", "message")
            putJsonObject("data") {
                put("conversationId", pending.conversationId)
                put("encryptedContent", pending.encryptedContent)
                put("messageType", pending.messageType)
                put("localMessageId", pending.localMessageId)
            }
        }
        webSocket?.send(payload.toString())
    }

    fun sendTyping(conversationId: String, isTyping: Boolean) {
        if (!isConnected) return
        
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
        if (!isConnected) return
        
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
    
    /**
     * Send delivery confirmation when message is received
     */
    fun sendDeliveredReceipt(conversationId: String, messageIds: List<String>) {
        if (!isConnected) return
        
        val payload = buildJsonObject {
            put("type", "delivered")
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
    
    /**
     * Check if there are pending messages in queue
     */
    fun hasPendingMessages(): Boolean = pendingMessages.isNotEmpty()
    
    /**
     * Get count of pending messages
     */
    fun getPendingMessageCount(): Int = pendingMessages.size
}

// Pending message for offline queue
data class PendingMessage(
    val localMessageId: String,
    val conversationId: String,
    val encryptedContent: String,
    val messageType: String,
    val timestamp: Long
)

// Message acknowledgement from server
data class MessageAck(
    val localMessageId: String,
    val serverMessageId: String,
    val status: String,  // "sent", "stored", "error"
    val timestamp: String
)

// Delivery receipt event
data class DeliveryReceiptEvent(
    val conversationId: String,
    val messageIds: List<String>,
    val deliveredTo: String,
    val deliveredAt: String
)

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
    val conversationId: String?,
    val senderId: String,
    val senderName: String?,
    val senderAvatar: String?,
    val signalType: String,
    val callType: String?,
    val encryptedPayload: String,
    val timestamp: String
)

data class ReadReceiptEvent(
    val conversationId: String,
    val messageIds: List<String>,
    val readBy: String,
    val readAt: String
)
