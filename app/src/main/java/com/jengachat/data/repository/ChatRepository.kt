package com.jengachat.data.repository

import com.jengachat.data.model.Chat
import com.jengachat.data.model.Message
import com.jengachat.util.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Chat repository interface for managing chats and messages
 */
interface ChatRepository {
    // Chat operations
    suspend fun createPrivateChat(otherUserId: String): Resource<Chat>
    suspend fun createGroupChat(name: String, participantIds: List<String>, photoUri: String?): Resource<Chat>
    suspend fun getChatById(chatId: String): Resource<Chat>
    suspend fun updateGroupInfo(chatId: String, name: String?, description: String?, photoUri: String?): Resource<Chat>
    suspend fun addParticipants(chatId: String, userIds: List<String>): Resource<Unit>
    suspend fun removeParticipant(chatId: String, userId: String): Resource<Unit>
    suspend fun leaveGroup(chatId: String): Resource<Unit>
    suspend fun deleteChat(chatId: String): Resource<Unit>
    suspend fun pinChat(chatId: String, isPinned: Boolean): Resource<Unit>
    suspend fun muteChat(chatId: String, isMuted: Boolean): Resource<Unit>
    fun observeChats(): Flow<List<Chat>>
    fun observeChat(chatId: String): Flow<Chat?>
    
    // Message operations
    suspend fun sendMessage(chatId: String, text: String, replyTo: Message?): Resource<Message>
    suspend fun sendMediaMessage(chatId: String, mediaUri: String, type: String, caption: String?): Resource<Message>
    suspend fun sendVoiceMessage(chatId: String, audioUri: String, duration: Long): Resource<Message>
    suspend fun markAsRead(chatId: String, messageIds: List<String>): Resource<Unit>
    suspend fun deleteMessage(chatId: String, messageId: String, forEveryone: Boolean): Resource<Unit>
    suspend fun addReaction(chatId: String, messageId: String, emoji: String): Resource<Unit>
    suspend fun removeReaction(chatId: String, messageId: String): Resource<Unit>
    fun observeMessages(chatId: String): Flow<List<Message>>
    suspend fun getMessages(chatId: String, limit: Int, beforeTimestamp: Long?): Resource<List<Message>>
    suspend fun searchMessages(chatId: String, query: String): Resource<List<Message>>
    
    // Typing indicator
    suspend fun setTyping(chatId: String, isTyping: Boolean): Resource<Unit>
    fun observeTyping(chatId: String): Flow<List<String>>
}
