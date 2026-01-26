package com.jengachat.data.repository

import com.jengachat.crypto.CryptoManager
import com.jengachat.data.model.*
import com.jengachat.data.remote.*
import com.jengachat.util.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Call Repository Implementation using custom Talksy Server with E2EE
 */
@Singleton
class CustomCallRepositoryImpl @Inject constructor(
    private val networkClient: NetworkClient,
    private val tokenManager: TokenManager,
    private val webSocketClient: WebSocketClient,
    private val cryptoManager: CryptoManager
) : CallRepository {

    private val api get() = networkClient.api
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Call state
    private val _currentCall = MutableStateFlow<Call?>(null)
    private val _incomingCalls = MutableStateFlow<Call?>(null)
    private val _signalingMessages = MutableSharedFlow<SignalingMessage>()

    init {
        // Listen for call signals via WebSocket
        scope.launch {
            webSocketClient.callSignals.collect { signal ->
                handleCallSignal(signal)
            }
        }
    }

    override suspend fun initiateCall(
        participantIds: List<String>,
        callType: CallType,
        chatId: String?,
        isGroupCall: Boolean
    ): Resource<Call> {
        return try {
            // First we need a conversation - if chatId not provided, we need to handle it
            val conversationId = chatId ?: return Resource.Error("Conversation ID required")
            
            val request = InitiateCallRequest(
                conversationId = conversationId,
                callType = if (callType == CallType.VIDEO) "video" else "voice"
            )

            val response = api.initiateCall(request)

            if (response.isSuccessful && response.body()?.success == true) {
                val callDto = response.body()!!.data!!
                val call = callDto.toCall()
                _currentCall.value = call
                
                // Send call signal via WebSocket to notify participants
                val callPayload = "{\"callType\":\"${if (callType == CallType.VIDEO) "video" else "voice"}\",\"action\":\"incoming_call\"}"
                participantIds.forEach { participantId ->
                    webSocketClient.sendCallSignal(
                        callId = call.id,
                        recipientId = participantId,
                        signalType = "offer",
                        encryptedPayload = callPayload
                    )
                }
                
                Resource.Success(call)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to initiate call")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to initiate call")
        }
    }

    override suspend fun getCall(callId: String): Resource<Call> {
        return try {
            val response = api.getCall(callId)

            if (response.isSuccessful && response.body()?.success == true) {
                val call = response.body()!!.data!!.toCall()
                _currentCall.value = call
                Resource.Success(call)
            } else {
                Resource.Error(response.body()?.error ?: "Call not found")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get call")
        }
    }

    override suspend fun updateCallStatus(callId: String, status: CallStatus): Resource<Unit> {
        return try {
            val response = api.updateCallStatus(callId, mapOf("status" to status.name.lowercase()))

            if (response.isSuccessful && response.body()?.success == true) {
                _currentCall.update { call ->
                    call?.copy(status = status)
                }
                Resource.Success(Unit)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to update status")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update status")
        }
    }

    override suspend fun endCall(callId: String, reason: CallEndReason): Resource<Unit> {
        return try {
            val response = api.endCall(callId, mapOf("reason" to reason.name.lowercase()))

            if (response.isSuccessful && response.body()?.success == true) {
                // Notify participants via WebSocket
                _currentCall.value?.participants?.forEach { participant ->
                    webSocketClient.sendCallSignal(
                        callId = callId,
                        recipientId = participant.id,
                        signalType = "hangup",
                        encryptedPayload = reason.name
                    )
                }
                
                _currentCall.value = null
                Resource.Success(Unit)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to end call")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to end call")
        }
    }

    override suspend fun joinCall(callId: String): Resource<Call> {
        return try {
            val response = api.joinCall(callId)

            if (response.isSuccessful && response.body()?.success == true) {
                val call = response.body()!!.data!!.toCall()
                _currentCall.value = call
                _incomingCalls.value = null
                
                // Send answer signal
                webSocketClient.sendCallSignal(
                    callId = callId,
                    recipientId = call.callerId,
                    signalType = "answer",
                    encryptedPayload = "accepted"
                )
                
                Resource.Success(call)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to join call")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to join call")
        }
    }

    override suspend fun leaveCall(callId: String): Resource<Unit> {
        return try {
            val response = api.leaveCall(callId)

            if (response.isSuccessful && response.body()?.success == true) {
                _currentCall.value = null
                Resource.Success(Unit)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to leave call")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to leave call")
        }
    }

    override suspend fun updateParticipantStatus(
        callId: String,
        participantId: String,
        status: ParticipantStatus
    ): Resource<Unit> {
        // Update locally
        _currentCall.update { call ->
            call?.copy(
                participants = call.participants.map { participant ->
                    if (participant.id == participantId) {
                        participant.copy(status = status)
                    } else participant
                }
            )
        }
        return Resource.Success(Unit)
    }

    override suspend fun toggleMute(callId: String, isMuted: Boolean): Resource<Unit> {
        return try {
            // Update server
            api.updateCallStatus(callId, mapOf("isMuted" to isMuted))
            
            // Update local state
            val userId = tokenManager.userId ?: return Resource.Error("Not logged in")
            _currentCall.update { call ->
                call?.copy(
                    participants = call.participants.map { participant ->
                        if (participant.id == userId) {
                            participant.copy(isMuted = isMuted)
                        } else participant
                    }
                )
            }
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to toggle mute")
        }
    }

    override suspend fun toggleVideo(callId: String, isVideoEnabled: Boolean): Resource<Unit> {
        return try {
            // Update server
            api.updateCallStatus(callId, mapOf("isVideoEnabled" to isVideoEnabled))
            
            // Update local state
            val userId = tokenManager.userId ?: return Resource.Error("Not logged in")
            _currentCall.update { call ->
                call?.copy(
                    participants = call.participants.map { participant ->
                        if (participant.id == userId) {
                            participant.copy(isVideoEnabled = isVideoEnabled)
                        } else participant
                    }
                )
            }
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to toggle video")
        }
    }

    override suspend fun addParticipantToCall(callId: String, userId: String): Resource<Unit> {
        return try {
            val response = api.addCallParticipant(callId, mapOf("userId" to userId))

            if (response.isSuccessful && response.body()?.success == true) {
                // Send call signal to new participant
                webSocketClient.sendCallSignal(
                    callId = callId,
                    recipientId = userId,
                    signalType = "offer",
                    encryptedPayload = "invite"
                )
                Resource.Success(Unit)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to add participant")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to add participant")
        }
    }

    // ==================== WebRTC Signaling ====================

    override suspend fun sendSignalingMessage(message: SignalingMessage): Resource<Unit> {
        return try {
            // Build a JSON payload string for the signaling data
            val payloadJson = buildString {
                append("{")
                message.sdp?.let { append("\"sdp\":\"${it.replace("\"", "\\\"")}\",") }
                message.candidate?.let { append("\"candidate\":\"${it.replace("\"", "\\\"")}\",") }
                message.sdpMid?.let { append("\"sdpMid\":\"$it\",") }
                message.sdpMLineIndex?.let { append("\"sdpMLineIndex\":$it,") }
                if (endsWith(",")) deleteCharAt(length - 1)
                append("}")
            }
            
            // Send via WebSocket for real-time delivery
            webSocketClient.sendCallSignal(
                callId = message.callId,
                recipientId = message.receiverId,
                signalType = when (message.type) {
                    SignalingType.OFFER -> "offer"
                    SignalingType.ANSWER -> "answer"
                    SignalingType.ICE_CANDIDATE -> "ice-candidate"
                    SignalingType.HANG_UP -> "hangup"
                    SignalingType.REJECT -> "reject"
                    SignalingType.BUSY -> "busy"
                },
                encryptedPayload = payloadJson
            )
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to send signaling message")
        }
    }

    override fun observeSignalingMessages(callId: String): Flow<SignalingMessage> {
        return _signalingMessages.filter { it.callId == callId }
    }

    override suspend fun deleteSignalingMessages(callId: String): Resource<Unit> {
        // No persistent storage for signaling messages
        return Resource.Success(Unit)
    }

    // ==================== Call Observation ====================

    override fun observeCall(callId: String): Flow<Call?> {
        scope.launch { getCall(callId) }
        return _currentCall.filter { it?.id == callId }
    }

    override fun observeIncomingCalls(): Flow<Call?> {
        return _incomingCalls.asStateFlow()
    }

    override suspend fun getCallHistory(): Resource<List<Call>> {
        return try {
            val response = api.getCallHistory()

            if (response.isSuccessful && response.body()?.success == true) {
                val calls = response.body()!!.data?.map { it.toCall() } ?: emptyList()
                Resource.Success(calls)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to get call history")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get call history")
        }
    }

    // ==================== Helper Methods ====================

    private fun handleCallSignal(signal: CallSignalEvent) {
        when (signal.signalType) {
            "offer" -> {
                // Incoming call
                val call = Call(
                    id = signal.callId,
                    type = CallType.VOICE, // Default, will be updated with actual type
                    status = CallStatus.RINGING,
                    callerId = signal.senderId,
                    isGroupCall = false
                )
                _incomingCalls.value = call
            }
            "answer" -> {
                // Call accepted
                _currentCall.update { call ->
                    call?.copy(status = CallStatus.CONNECTING)
                }
            }
            "ice-candidate" -> {
                // ICE candidate received - parse from encryptedPayload
                scope.launch {
                    try {
                        // The payload is a JSON string, parse it
                        val payloadJson = kotlinx.serialization.json.Json.parseToJsonElement(signal.encryptedPayload)
                        val payloadObj = payloadJson.jsonObject
                        
                        _signalingMessages.emit(
                            SignalingMessage(
                                type = SignalingType.ICE_CANDIDATE,
                                callId = signal.callId,
                                senderId = signal.senderId,
                                candidate = payloadObj["candidate"]?.jsonPrimitive?.contentOrNull ?: "",
                                sdpMid = payloadObj["sdpMid"]?.jsonPrimitive?.contentOrNull ?: "",
                                sdpMLineIndex = payloadObj["sdpMLineIndex"]?.jsonPrimitive?.intOrNull ?: 0
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            "hangup" -> {
                // Call ended
                _currentCall.value = null
                _incomingCalls.value = null
            }
            "reject" -> {
                // Call rejected
                _currentCall.update { call ->
                    call?.copy(status = CallStatus.ENDED, endReason = CallEndReason.DECLINED)
                }
            }
            "busy" -> {
                // User busy
                _currentCall.update { call ->
                    call?.copy(status = CallStatus.ENDED, endReason = CallEndReason.BUSY)
                }
            }
        }
    }
}

// Extension function to convert DTO to domain model
fun CallDto.toCall(): Call {
    return Call(
        id = id ?: callId ?: "",
        type = if (callType == "video") CallType.VIDEO else CallType.VOICE,
        status = when (status) {
            "initiating" -> CallStatus.INITIATING
            "ringing" -> CallStatus.RINGING
            "connecting" -> CallStatus.CONNECTING
            "ongoing" -> CallStatus.ONGOING
            "ended" -> CallStatus.ENDED
            else -> CallStatus.INITIATING
        },
        callerId = callerId ?: "",
        participants = (participants ?: emptyList()).map { participant ->
            CallParticipant(
                id = participant.id,
                displayName = participant.displayName ?: participant.username,
                photoUrl = participant.avatarUrl ?: "",
                status = ParticipantStatus.CONNECTED
            )
        },
        isGroupCall = (participants?.size ?: 0) > 2,
        startTime = answeredAt?.let { try { it.toLong() } catch (e: Exception) { null } },
        endTime = endedAt?.let { try { it.toLong() } catch (e: Exception) { null } },
        createdAt = createdAt?.let { try { it.toLong() } catch (e: Exception) { null } }
    )
}
