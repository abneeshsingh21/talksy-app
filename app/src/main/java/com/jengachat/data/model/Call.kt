package com.jengachat.data.model

/**
 * Call model for voice/video calls
 */
data class Call(
    val id: String = "",
    val type: CallType = CallType.VOICE,
    val status: CallStatus = CallStatus.INITIATING,
    val callerId: String = "",
    val callerName: String = "",
    val callerPhotoUrl: String = "",
    val participants: List<CallParticipant> = emptyList(),
    val chatId: String = "",  // Associated chat for group calls
    val isGroupCall: Boolean = false,
    val maxParticipants: Int = 8,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val duration: Long = 0L,  // Duration in seconds
    val endReason: CallEndReason = CallEndReason.NONE,
    val createdAt: Long? = null
)

/**
 * Call participant model
 */
data class CallParticipant(
    val id: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val fcmToken: String = "",
    val status: ParticipantStatus = ParticipantStatus.PENDING,
    val isMuted: Boolean = false,
    val isVideoEnabled: Boolean = true,
    val isSpeaking: Boolean = false,
    val joinedAt: Long? = null,
    val leftAt: Long? = null
)

/**
 * Call types
 */
enum class CallType {
    VOICE,
    VIDEO
}

/**
 * Call status
 */
enum class CallStatus {
    INITIATING,     // Call is being set up
    RINGING,        // Ringing on receiver's device
    CONNECTING,     // Both parties connecting
    ONGOING,        // Call in progress
    ON_HOLD,        // Call on hold
    RECONNECTING,   // Reconnecting after network issue
    ENDED           // Call ended
}

/**
 * Participant status in call
 */
enum class ParticipantStatus {
    PENDING,        // Invitation sent
    RINGING,        // Phone is ringing
    CONNECTING,     // Joining the call
    CONNECTED,      // In the call
    ON_HOLD,        // On hold
    DECLINED,       // Declined the call
    MISSED,         // Didn't answer
    LEFT            // Left the call
}

/**
 * Reason for call ending
 */
enum class CallEndReason {
    NONE,
    COMPLETED,      // Normal end
    DECLINED,       // Receiver declined
    NO_ANSWER,      // No one answered
    BUSY,           // Receiver busy
    FAILED,         // Technical failure
    CANCELLED       // Caller cancelled
}

/**
 * WebRTC Signaling message
 */
data class SignalingMessage(
    val type: SignalingType = SignalingType.OFFER,
    val callId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val sdp: String = "",
    val candidate: String = "",
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val timestamp: Long? = null
)

/**
 * Signaling message types
 */
enum class SignalingType {
    OFFER,
    ANSWER,
    ICE_CANDIDATE,
    HANG_UP,
    REJECT,
    BUSY
}
