package com.jengachat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jengachat.data.model.Message
import com.jengachat.data.model.MessageStatus
import com.jengachat.data.model.MessageType

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val localId: String = "",
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val senderPhotoUrl: String = "",
    val text: String,
    val type: String = MessageType.TEXT.name,
    val status: String = MessageStatus.SENT.name,
    val mediaUrl: String = "",
    val mediaThumbnailUrl: String = "",
    val mediaName: String = "",
    val mediaSize: Long = 0L,
    val mediaDuration: Long = 0L,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long? = null,
    val editedAt: Long? = null
)

fun MessageEntity.toDomain(): Message {
    return Message(
        id = id,
        localId = localId,
        chatId = chatId,
        senderId = senderId,
        senderName = senderName,
        senderPhotoUrl = senderPhotoUrl,
        text = text,
        type = try { MessageType.valueOf(type) } catch (e: Exception) { MessageType.TEXT },
        status = try { MessageStatus.valueOf(status) } catch (e: Exception) { MessageStatus.SENT },
        mediaUrl = mediaUrl,
        mediaThumbnailUrl = mediaThumbnailUrl,
        mediaName = mediaName,
        mediaSize = mediaSize,
        mediaDuration = mediaDuration,
        isEdited = isEdited,
        isDeleted = isDeleted,
        createdAt = createdAt,
        editedAt = editedAt
    )
}

fun Message.toEntity(): MessageEntity {
    return MessageEntity(
        id = id.ifEmpty { localId },
        localId = localId,
        chatId = chatId,
        senderId = senderId,
        senderName = senderName,
        senderPhotoUrl = senderPhotoUrl,
        text = text,
        type = type.name,
        status = status.name,
        mediaUrl = mediaUrl,
        mediaThumbnailUrl = mediaThumbnailUrl,
        mediaName = mediaName,
        mediaSize = mediaSize,
        mediaDuration = mediaDuration,
        isEdited = isEdited,
        isDeleted = isDeleted,
        createdAt = createdAt ?: System.currentTimeMillis(),
        editedAt = editedAt
    )
}
