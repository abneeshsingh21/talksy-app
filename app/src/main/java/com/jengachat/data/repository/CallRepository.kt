package com.jengachat.data.repository

import com.jengachat.data.model.*
import com.jengachat.util.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Call repository interface for voice/video calls
 */
interface CallRepository {
    // Call operations
    suspend fun initiateCall(
        participantIds: List<String>,
        callType: CallType,
        chatId: String?,
        isGroupCall: Boolean
    ): Resource<Call>
    
    suspend fun initiateCallForConversation(
        conversationId: String,
        callType: CallType
    ): Resource<Call>
    
    suspend fun getCall(callId: String): Resource<Call>
    suspend fun updateCallStatus(callId: String, status: CallStatus): Resource<Unit>
    suspend fun endCall(callId: String, reason: CallEndReason): Resource<Unit>
    suspend fun joinCall(callId: String): Resource<Call>
    suspend fun leaveCall(callId: String): Resource<Unit>
    suspend fun updateParticipantStatus(callId: String, participantId: String, status: ParticipantStatus): Resource<Unit>
    suspend fun toggleMute(callId: String, isMuted: Boolean): Resource<Unit>
    suspend fun toggleVideo(callId: String, isVideoEnabled: Boolean): Resource<Unit>
    suspend fun addParticipantToCall(callId: String, userId: String): Resource<Unit>
    
    // Signaling for WebRTC
    suspend fun sendSignalingMessage(message: SignalingMessage): Resource<Unit>
    fun observeSignalingMessages(callId: String): Flow<SignalingMessage>
    suspend fun deleteSignalingMessages(callId: String): Resource<Unit>
    
    // Call observation
    fun observeCall(callId: String): Flow<Call?>
    fun observeIncomingCalls(): Flow<Call?>
    suspend fun getCallHistory(): Resource<List<Call>>
}
