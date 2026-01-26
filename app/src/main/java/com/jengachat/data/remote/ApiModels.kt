package com.jengachat.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ==================== AUTH ====================

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val displayName: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val success: Boolean,
    val data: AuthData? = null,
    val error: String? = null
)

@Serializable
data class AuthData(
    val user: UserDto,
    val accessToken: String,
    val refreshToken: String
)

@Serializable
data class RefreshRequest(
    val refreshToken: String
)

@Serializable
data class RefreshResponse(
    val success: Boolean,
    val data: RefreshData? = null,
    val error: String? = null
)

@Serializable
data class RefreshData(
    val accessToken: String,
    val refreshToken: String
)

// ==================== USER ====================

@Serializable
data class UserDto(
    val id: String,
    val username: String,
    val email: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val statusText: String? = null,
    val isOnline: Boolean = false,
    val lastSeenAt: String? = null,
    val createdAt: String? = null
)

@Serializable
data class UserResponse(
    val success: Boolean,
    val data: UserDto? = null,
    val error: String? = null
)

@Serializable
data class UsersResponse(
    val success: Boolean,
    val data: List<UserDto> = emptyList(),
    val error: String? = null
)

@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val statusText: String? = null,
    val avatarUrl: String? = null
)

// ==================== KEYS (Signal Protocol) ====================

@Serializable
data class UploadKeysRequest(
    val identityPublicKey: String,
    val signedPreKeyId: Int,
    val signedPreKeyPublic: String,
    val signedPreKeySignature: String,
    val registrationId: Int,
    val preKeys: List<PreKeyDto>
)

@Serializable
data class PreKeyDto(
    val id: Int,
    val publicKey: String
)

@Serializable
data class KeyBundleResponse(
    val success: Boolean,
    val data: KeyBundleDto? = null,
    val error: String? = null
)

@Serializable
data class KeyBundleDto(
    val userId: String,
    val registrationId: Int,
    val identityPublicKey: String,
    val signedPreKeyId: Int,
    val signedPreKeyPublic: String,
    val signedPreKeySignature: String,
    val preKeyId: Int? = null,
    val preKeyPublic: String? = null
)

// ==================== CONVERSATIONS ====================

@Serializable
data class CreateConversationRequest(
    val participantIds: List<String>,
    @SerialName("type") val conversationType: String = "private",
    val name: String? = null,
    val avatar: String? = null
) {
    // Helper property for backwards compatibility
    val isGroup: Boolean get() = conversationType == "group"
    
    companion object {
        // Factory function for creating from isGroup boolean
        fun create(
            participantIds: List<String>,
            isGroup: Boolean,
            name: String? = null,
            avatar: String? = null
        ): CreateConversationRequest = CreateConversationRequest(
            participantIds = participantIds,
            conversationType = if (isGroup) "group" else "private",
            name = name,
            avatar = avatar
        )
    }
}

@Serializable
data class ConversationResponse(
    val success: Boolean,
    val data: ConversationDto? = null,
    val error: String? = null
)

@Serializable
data class ConversationsResponse(
    val success: Boolean,
    val data: List<ConversationDto> = emptyList(),
    val error: String? = null
)

@Serializable
data class ConversationDto(
    val id: String,
    val type: String,
    val name: String? = null,
    @SerialName("avatar") val avatarUrl: String? = null,
    val participants: List<ParticipantDto> = emptyList(),
    val lastMessage: LastMessageDto? = null,
    val lastMessageEncrypted: String? = null,
    val lastMessageAt: String? = null,
    val unreadCount: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val isNew: Boolean? = null,
    val conversationId: String? = null
) {
    val isGroup: Boolean get() = type == "group"
    val avatar: String? get() = avatarUrl
}

@Serializable
data class LastMessageDto(
    val id: String? = null,
    val senderId: String = "",
    val content: String? = null,
    val type: String = "text",
    val createdAt: String? = null
)

@Serializable
data class ParticipantDto(
    val id: String,
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val isOnline: Boolean = false,
    val role: String? = null
)

// ==================== MESSAGES ====================

@Serializable
data class SendMessageRequest(
    val conversationId: String,
    val encryptedContent: String,
    val messageType: String = "text",
    val replyToId: String? = null,
    val mediaId: String? = null
) {
    // Alias for backwards compatibility
    val type: String get() = messageType
    val content: String get() = encryptedContent
    
    companion object {
        fun create(
            conversationId: String,
            content: String,
            type: String = "text",
            replyToId: String? = null,
            mediaId: String? = null
        ): SendMessageRequest = SendMessageRequest(
            conversationId = conversationId,
            encryptedContent = content,
            messageType = type,
            replyToId = replyToId,
            mediaId = mediaId
        )
    }
}

@Serializable
data class MessageResponse(
    val success: Boolean,
    val data: MessageSentDto? = null,
    val error: String? = null
)

@Serializable
data class MessageSentDto(
    val messageId: String,
    val conversationId: String,
    val createdAt: String
)

@Serializable
data class MessagesResponse(
    val success: Boolean,
    val data: List<MessageDto> = emptyList(),
    val error: String? = null
)

@Serializable
data class MessageDto(
    val id: String,
    val senderId: String,
    val conversationId: String = "",
    val sender: ParticipantDto? = null,
    val senderUsername: String? = null,
    val senderDisplayName: String? = null,
    @SerialName("encryptedContent") val content: String? = null,
    @SerialName("messageType") val type: String = "text",
    val replyToId: String? = null,
    val replyTo: MessageDto? = null,
    val mediaId: String? = null,
    val mediaUrl: String? = null,
    @SerialName("deletedAt") val isDeletedRaw: String? = null,
    val receiptStatus: String? = null,
    val deliveredAt: String? = null,
    val readAt: String? = null,
    val createdAt: String? = null
) {
    val isDeleted: Boolean get() = isDeletedRaw != null
}

@Serializable
data class MarkReadRequest(
    val conversationId: String? = null,
    val messageIds: List<String>? = null
)

// ==================== MEDIA ====================

@Serializable
data class UploadMediaRequest(
    val encryptedData: String,
    val mimeType: String? = null,
    val fileName: String? = null,
    val conversationId: String? = null,
    val expiresIn: Long? = null
)

@Serializable
data class MediaResponse(
    val success: Boolean,
    val data: MediaDto? = null,
    val error: String? = null
)

@Serializable
data class MediaDto(
    val mediaId: String,
    val encryptedData: String? = null,
    val mimeType: String? = null,
    val originalFilename: String? = null,
    val uploaderId: String? = null,
    val createdAt: String? = null,
    val expiresAt: String? = null
)

// ==================== CALLS ====================

@Serializable
data class InitiateCallRequest(
    val conversationId: String,
    val callType: String // "voice" or "video"
)

@Serializable
data class CallResponse(
    val success: Boolean,
    val data: CallDto? = null,
    val error: String? = null
)

@Serializable
data class CallDto(
    val id: String? = null,
    val callId: String? = null,
    val conversationId: String,
    val callerId: String? = null,
    val callerUsername: String? = null,
    val callerDisplayName: String? = null,
    val callType: String,
    val status: String,
    val participants: List<ParticipantDto>? = null,
    val createdAt: String? = null,
    val answeredAt: String? = null,
    val endedAt: String? = null,
    val duration: Int? = null,
    val endReason: String? = null
)

@Serializable
data class SignalingRequest(
    val callId: String,
    val recipientId: String,
    val messageType: String, // "offer", "answer", "ice-candidate", "hangup"
    val encryptedPayload: String
)

@Serializable
data class SignalingResponse(
    val success: Boolean,
    val data: List<SignalingMessageDto>? = null,
    val error: String? = null
)

@Serializable
data class SignalingMessageDto(
    val id: String,
    val senderId: String,
    val senderUsername: String? = null,
    val messageType: String,
    val encryptedPayload: String,
    val createdAt: String
)

// ==================== CALLS ADDITIONAL ====================

@Serializable
data class CallsResponse(
    val success: Boolean,
    val data: List<CallDto> = emptyList(),
    val error: String? = null
)

@Serializable
data class CallSignal(
    val callId: String,
    val senderId: String,
    val type: String, // "offer", "answer", "ice-candidate"
    val payload: String
)

@Serializable
data class UpdateCallStatusRequest(
    val status: String
)

// ==================== REACTIONS ====================

@Serializable
data class AddReactionRequest(
    val emoji: String
)

@Serializable
data class ReactionDto(
    val id: String? = null,
    val messageId: String,
    val userId: String,
    val emoji: String,
    val createdAt: String? = null
)

@Serializable
data class ReactionResponse(
    val success: Boolean,
    val data: ReactionDto? = null,
    val error: String? = null
)

// TypingEvent is defined in WebSocketClient.kt - use that one

// ==================== UPDATE REQUESTS ====================

@Serializable
data class UpdateConversationRequest(
    val name: String? = null,
    val avatar: String? = null
)

@Serializable
data class PresenceUpdateRequest(
    val isOnline: Boolean
)

// ==================== GENERIC ====================

@Serializable
data class ApiResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
)

// ==================== PASSWORD ====================

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

@Serializable
data class ForgotPasswordRequest(
    val email: String
)

@Serializable
data class ResetPasswordRequest(
    val token: String,
    val newPassword: String
)

// ==================== CONTACTS SYNC ====================

@Serializable
data class EmailLookupRequest(
    val emails: List<String>
)

@Serializable
data class PhoneLookupRequest(
    val phones: List<String>
)
