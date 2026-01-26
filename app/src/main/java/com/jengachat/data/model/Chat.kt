package com.jengachat.data.model

/**
 * Chat/Conversation model - can be 1-on-1 or group
 */
data class Chat(
    val id: String = "",
    val type: ChatType = ChatType.PRIVATE,
    val participants: List<String> = emptyList(),
    val participantDetails: Map<String, ParticipantInfo> = emptyMap(),
    val groupName: String = "",
    val groupPhotoUrl: String = "",
    val groupDescription: String = "",
    val adminIds: List<String> = emptyList(),
    val createdBy: String = "",
    val lastMessage: LastMessage? = null,
    val unreadCount: Map<String, Int> = emptyMap(),
    val isPinned: Map<String, Boolean> = emptyMap(),
    val isMuted: Map<String, Boolean> = emptyMap(),
    val typingUsers: List<String> = emptyList(),
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

/**
 * Participant info stored in chat for quick access
 */
data class ParticipantInfo(
    val displayName: String = "",
    val photoUrl: String = "",
    val fcmToken: String = "",
    val isOnline: Boolean = false
)

/**
 * Last message preview
 */
data class LastMessage(
    val text: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val type: MessageType = MessageType.TEXT,
    val timestamp: Long? = null
)

/**
 * Chat types
 */
enum class ChatType {
    PRIVATE,  // 1-on-1
    GROUP     // Group chat
}
