package com.jengachat.data.model

/**
 * Message model for chat messages
 */
data class Message(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String = "",
    val text: String = "",
    val type: MessageType = MessageType.TEXT,
    val mediaUrl: String = "",
    val mediaThumbnailUrl: String = "",
    val mediaName: String = "",
    val mediaSize: Long = 0L,
    val mediaDuration: Long = 0L,  // For audio/video in seconds
    val replyTo: ReplyMessage? = null,
    val reactions: Map<String, String> = emptyMap(),  // userId to emoji
    val readBy: List<String> = emptyList(),
    val deliveredTo: List<String> = emptyList(),
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedFor: List<String> = emptyList(),
    val createdAt: Long? = null,
    val editedAt: Long? = null
)

/**
 * Reply message info
 */
data class ReplyMessage(
    val messageId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val type: MessageType = MessageType.TEXT
)

/**
 * Message types
 */
enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,         // Voice message
    DOCUMENT,
    LOCATION,
    CONTACT,
    STICKER,
    GIF,
    CALL_LOG,      // Call started/ended log
    SYSTEM         // System message (user joined, left, etc.)
}

/**
 * Message status for UI
 */
enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}
