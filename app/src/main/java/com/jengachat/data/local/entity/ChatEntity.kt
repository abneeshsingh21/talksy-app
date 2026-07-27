package com.jengachat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jengachat.data.model.Chat
import com.jengachat.data.model.ChatType
import com.jengachat.data.model.LastMessage
import com.jengachat.data.model.MessageType

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey
    val id: String,
    val type: String = ChatType.PRIVATE.name,
    val groupName: String = "",
    val groupPhotoUrl: String = "",
    val groupDescription: String = "",
    val createdBy: String = "",
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageSenderName: String = "",
    val lastMessageType: String = MessageType.TEXT.name,
    val lastMessageTimestamp: Long? = null,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

fun ChatEntity.toDomain(): Chat {
    return Chat(
        id = id,
        type = try { ChatType.valueOf(type) } catch (e: Exception) { ChatType.PRIVATE },
        groupName = groupName,
        groupPhotoUrl = groupPhotoUrl,
        groupDescription = groupDescription,
        createdBy = createdBy,
        lastMessage = if (lastMessageText.isNotEmpty()) {
            LastMessage(
                text = lastMessageText,
                senderId = lastMessageSenderId,
                senderName = lastMessageSenderName,
                type = try { MessageType.valueOf(lastMessageType) } catch (e: Exception) { MessageType.TEXT },
                timestamp = lastMessageTimestamp
            )
        } else null,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun Chat.toEntity(currentUserId: String = ""): ChatEntity {
    return ChatEntity(
        id = id,
        type = type.name,
        groupName = groupName,
        groupPhotoUrl = groupPhotoUrl,
        groupDescription = groupDescription,
        createdBy = createdBy,
        lastMessageText = lastMessage?.text ?: "",
        lastMessageSenderId = lastMessage?.senderId ?: "",
        lastMessageSenderName = lastMessage?.senderName ?: "",
        lastMessageType = (lastMessage?.type ?: MessageType.TEXT).name,
        lastMessageTimestamp = lastMessage?.timestamp,
        unreadCount = unreadCount[currentUserId] ?: 0,
        isPinned = isPinned[currentUserId] ?: false,
        isMuted = isMuted[currentUserId] ?: false,
        createdAt = createdAt ?: System.currentTimeMillis(),
        updatedAt = updatedAt ?: System.currentTimeMillis()
    )
}
