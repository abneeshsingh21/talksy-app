package com.jengachat.data.repository

import com.jengachat.crypto.CryptoManager
import com.jengachat.crypto.SessionManager
import com.jengachat.data.model.*
import com.jengachat.data.remote.*
import com.jengachat.util.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chat Repository Implementation using custom Talksy Server with E2EE
 */
@Singleton
class CustomChatRepositoryImpl @Inject constructor(
    private val networkClient: NetworkClient,
    private val tokenManager: TokenManager,
    private val webSocketClient: WebSocketClient,
    private val cryptoManager: CryptoManager,
    private val sessionManager: SessionManager
) : ChatRepository {

    private val api get() = networkClient.api
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Local cache for chats
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    private val _messagesCache = ConcurrentHashMap<String, MutableStateFlow<List<Message>>>()
    private val _typingCache = ConcurrentHashMap<String, MutableStateFlow<List<String>>>()

    init {
        // Listen to incoming WebSocket messages
        scope.launch {
            webSocketClient.incomingMessages.collect { wsMessage ->
                handleIncomingMessage(wsMessage)
            }
        }
        
        scope.launch {
            webSocketClient.typingEvents.collect { event ->
                handleTypingEvent(event)
            }
        }
        
        // Listen for message acknowledgements (server confirms message received)
        scope.launch {
            webSocketClient.messageAcks.collect { ack ->
                handleMessageAck(ack)
            }
        }
        
        // Listen for delivery receipts (recipient received message)
        scope.launch {
            webSocketClient.deliveryReceipts.collect { event ->
                handleDeliveryReceipt(event)
            }
        }
        
        // Listen for read receipts (recipient read message)
        scope.launch {
            webSocketClient.readReceipts.collect { event ->
                handleReadReceiptEvent(event)
            }
        }
    }
    
    /**
     * Handle message acknowledgement from server
     */
    private fun handleMessageAck(ack: MessageAck) {
        // Find and update the message with the server ID and SENT status
        _messagesCache.values.forEach { cache ->
            cache.value = cache.value.map { msg ->
                if (msg.localId == ack.localMessageId || msg.id == ack.localMessageId) {
                    msg.copy(
                        id = ack.serverMessageId,
                        status = MessageStatus.SENT
                    )
                } else msg
            }
        }
    }
    
    /**
     * Handle delivery receipt - recipient received the message
     */
    private fun handleDeliveryReceipt(event: DeliveryReceiptEvent) {
        val cache = _messagesCache[event.conversationId] ?: return
        cache.value = cache.value.map { msg ->
            if (event.messageIds.contains(msg.id)) {
                msg.copy(
                    status = MessageStatus.DELIVERED,
                    deliveredTo = msg.deliveredTo + event.deliveredTo
                )
            } else msg
        }
    }
    
    /**
     * Handle read receipt event - recipient read the message
     */
    private fun handleReadReceiptEvent(event: ReadReceiptEvent) {
        val cache = _messagesCache[event.conversationId] ?: return
        cache.value = cache.value.map { msg ->
            if (event.messageIds.contains(msg.id)) {
                msg.copy(
                    status = MessageStatus.READ,
                    readBy = msg.readBy + event.readBy
                )
            } else msg
        }
    }

    // ==================== Chat Operations ====================

    override suspend fun createPrivateChat(otherUserId: String): Resource<Chat> {
        return try {
            val response = api.createConversation(
                CreateConversationRequest.create(
                    participantIds = listOf(otherUserId),
                    isGroup = false
                )
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val conversationDto = response.body()!!.data!!
                val chat = conversationDto.toChat()
                updateChatCache(chat)
                
                // Initialize E2EE session for the conversation
                initializeE2EESession(chat.id, otherUserId)
                
                Resource.Success(chat)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to create chat")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to create chat")
        }
    }

    override suspend fun createGroupChat(
        name: String,
        participantIds: List<String>,
        photoUri: String?
    ): Resource<Chat> {
        return try {
            val response = api.createConversation(
                CreateConversationRequest.create(
                    participantIds = participantIds,
                    isGroup = true,
                    name = name,
                    avatar = photoUri
                )
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val chat = response.body()!!.data!!.toChat()
                updateChatCache(chat)
                
                // Initialize E2EE sessions with all participants
                participantIds.forEach { userId ->
                    initializeE2EESession(chat.id, userId)
                }
                
                Resource.Success(chat)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to create group")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to create group")
        }
    }

    override suspend fun getChatById(chatId: String): Resource<Chat> {
        return try {
            val response = api.getConversation(chatId)

            if (response.isSuccessful && response.body()?.success == true) {
                val chat = response.body()!!.data!!.toChat()
                updateChatCache(chat)
                Resource.Success(chat)
            } else {
                Resource.Error(response.body()?.error ?: "Chat not found")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get chat")
        }
    }

    override suspend fun updateGroupInfo(
        chatId: String,
        name: String?,
        description: String?,
        photoUri: String?
    ): Resource<Chat> {
        return try {
            val response = api.updateConversation(
                chatId,
                UpdateConversationRequest(
                    name = name,
                    description = description,
                    avatar = photoUri
                )
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val chat = response.body()!!.data!!.toChat()
                updateChatCache(chat)
                Resource.Success(chat)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to update group")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update group")
        }
    }

    override suspend fun addParticipants(chatId: String, userIds: List<String>): Resource<Unit> {
        return try {
            val response = api.addParticipants(chatId, mapOf("participantIds" to userIds))

            if (response.isSuccessful && response.body()?.success == true) {
                // Initialize E2EE sessions with new participants
                userIds.forEach { userId ->
                    initializeE2EESession(chatId, userId)
                }
                Resource.Success(Unit)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to add participants")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to add participants")
        }
    }

    override suspend fun removeParticipant(chatId: String, userId: String): Resource<Unit> {
        return try {
            val response = api.removeParticipant(chatId, userId)

            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to remove participant")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to remove participant")
        }
    }

    override suspend fun leaveGroup(chatId: String): Resource<Unit> {
        return try {
            val response = api.leaveConversation(chatId)

            if (response.isSuccessful && response.body()?.success == true) {
                // Remove from local cache
                _chats.update { chats -> chats.filter { it.id != chatId } }
                Resource.Success(Unit)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to leave group")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to leave group")
        }
    }

    override suspend fun deleteChat(chatId: String): Resource<Unit> {
        return try {
            // For now, leaving the conversation serves as delete
            leaveGroup(chatId)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to delete chat")
        }
    }

    override suspend fun pinChat(chatId: String, isPinned: Boolean): Resource<Unit> {
        return try {
            val userId = tokenManager.userId ?: return Resource.Error("Not logged in")
            _chats.update { chats ->
                chats.map { chat ->
                    if (chat.id == chatId) {
                        chat.copy(isPinned = chat.isPinned + (userId to isPinned))
                    } else chat
                }
            }
            api.pinChat(chatId, mapOf("isPinned" to isPinned))
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to pin chat")
        }
    }

    override suspend fun muteChat(chatId: String, isMuted: Boolean): Resource<Unit> {
        return try {
            val userId = tokenManager.userId ?: return Resource.Error("Not logged in")
            _chats.update { chats ->
                chats.map { chat ->
                    if (chat.id == chatId) {
                        chat.copy(isMuted = chat.isMuted + (userId to isMuted))
                    } else chat
                }
            }
            api.muteChat(chatId, mapOf("isMuted" to isMuted))
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to mute chat")
        }
    }

    override fun observeChats(): Flow<List<Chat>> {
        // Start loading chats from server
        scope.launch {
            loadChats()
        }
        return _chats.asStateFlow()
    }

    override fun observeChat(chatId: String): Flow<Chat?> {
        return _chats.map { chats -> chats.find { it.id == chatId } }
    }

    private suspend fun loadChats() {
        try {
            val response = api.getConversations()
            if (response.isSuccessful && response.body()?.success == true) {
                val chats = response.body()!!.data?.map { it.toChat() } ?: emptyList()
                _chats.value = chats
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==================== Message Operations ====================

    override suspend fun sendMessage(
        chatId: String,
        text: String,
        replyTo: Message?
    ): Resource<Message> {
        return try {
            val currentUserId = tokenManager.userId ?: return Resource.Error("Not logged in")
            val localMessageId = java.util.UUID.randomUUID().toString()
            
            // Encrypt the message if we have a session for this conversation
            val encryptedText = if (sessionManager.hasSession(chatId)) {
                val sessionKey = sessionManager.getSessionKey(chatId)
                if (sessionKey != null) {
                    cryptoManager.encryptMessage(text, sessionKey)
                } else text
            } else text
            
            // ===== OPTIMISTIC UPDATE - Show message IMMEDIATELY =====
            val pendingMessage = Message(
                id = localMessageId,
                localId = localMessageId,
                chatId = chatId,
                senderId = currentUserId,
                senderName = "",
                senderPhotoUrl = "",
                text = text,
                type = MessageType.TEXT,
                status = MessageStatus.SENDING,  // Show as "sending" with clock icon
                createdAt = System.currentTimeMillis(),
                replyTo = replyTo?.let {
                    ReplyMessage(
                        messageId = it.id,
                        senderId = it.senderId,
                        senderName = it.senderName,
                        text = it.text,
                        type = it.type
                    )
                }
            )
            
            // Update local cache immediately - user sees message instantly!
            updateMessageCache(chatId, pendingMessage)
            
            // ===== WEBSOCKET-FIRST: Try fast path first =====
            val wsConnected = webSocketClient.connectionState.value == ConnectionState.Connected
            
            if (wsConnected) {
                // Fast path: Send via WebSocket (instant)
                val sent = webSocketClient.sendMessage(chatId, encryptedText, "text", localMessageId)
                
                if (sent) {
                    // Update status to SENT (single check mark)
                    updateMessageStatus(chatId, localMessageId, MessageStatus.SENT)
                    return Resource.Success(pendingMessage.copy(status = MessageStatus.SENT))
                }
            }
            
            // ===== FALLBACK: Use REST API if WebSocket unavailable =====
            val request = SendMessageRequest(
                conversationId = chatId,
                messageType = "text",
                encryptedContent = encryptedText,
                replyToId = replyTo?.id
            )

            val response = api.sendMessage(request)

            if (response.isSuccessful && response.body()?.success == true) {
                val sentData = response.body()!!.data!!
                
                // Update with server message ID and SENT status
                updateMessageWithServerId(chatId, localMessageId, sentData.messageId)
                updateMessageStatus(chatId, localMessageId, MessageStatus.SENT)
                
                val message = pendingMessage.copy(
                    id = sentData.messageId,
                    status = MessageStatus.SENT
                )
                
                Resource.Success(message)
            } else {
                // Mark as failed
                updateMessageStatus(chatId, localMessageId, MessageStatus.FAILED)
                Resource.Error(response.body()?.error ?: "Failed to send message")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to send message")
        }
    }
    
    /**
     * Update message status in cache (for optimistic updates)
     */
    private fun updateMessageStatus(chatId: String, messageId: String, status: MessageStatus) {
        val cache = _messagesCache[chatId] ?: return
        cache.value = cache.value.map { msg ->
            if (msg.id == messageId || msg.localId == messageId) {
                msg.copy(status = status)
            } else msg
        }
    }
    
    /**
     * Update message with server-assigned ID
     */
    private fun updateMessageWithServerId(chatId: String, localId: String, serverId: String) {
        val cache = _messagesCache[chatId] ?: return
        cache.value = cache.value.map { msg ->
            if (msg.localId == localId) {
                msg.copy(id = serverId)
            } else msg
        }
    }

    override suspend fun sendMediaMessage(
        chatId: String,
        mediaUri: String,
        type: String,
        caption: String?
    ): Resource<Message> {
        return try {
            val currentUserId = tokenManager.userId ?: return Resource.Error("Not logged in")
            val localMessageId = java.util.UUID.randomUUID().toString()

            // ===== OPTIMISTIC UPDATE - Show media IMMEDIATELY =====
            val optimisticMessage = Message(
                id = localMessageId,
                localId = localMessageId,
                chatId = chatId,
                senderId = currentUserId,
                senderName = "",
                senderPhotoUrl = "",
                text = caption ?: "",
                type = when (type) {
                    "image" -> MessageType.IMAGE
                    "video" -> MessageType.VIDEO
                    "audio", "voice" -> MessageType.AUDIO
                    "document" -> MessageType.DOCUMENT
                    else -> MessageType.TEXT
                },
                mediaUrl = mediaUri, // Show local data immediately
                status = MessageStatus.SENDING,
                createdAt = System.currentTimeMillis()
            )
            updateMessageCache(chatId, optimisticMessage)

            // 1. Encrypt caption using E2EE session key for conversation
            val encryptedCaption = if (!caption.isNullOrEmpty() && sessionManager.hasSession(chatId)) {
                val sessionKey = sessionManager.getSessionKey(chatId)
                if (sessionKey != null) {
                    cryptoManager.encryptMessage(caption, sessionKey)
                } else caption
            } else {
                caption ?: ""
            }

            // 2. Upload encrypted media blob to server if present
            var uploadedMediaId: String? = null
            if (mediaUri.isNotEmpty()) {
                try {
                    val uploadReq = UploadMediaRequest(
                        encryptedData = mediaUri,
                        mimeType = type,
                        conversationId = chatId
                    )
                    val uploadRes = api.uploadMedia(uploadReq)
                    if (uploadRes.isSuccessful && uploadRes.body()?.success == true) {
                        uploadedMediaId = uploadRes.body()?.data?.mediaId
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatRepository", "Media upload warning: ${e.message}")
                }
            }

            // 3. Send message with mediaId & encrypted caption
            val request = SendMessageRequest(
                conversationId = chatId,
                messageType = type,
                encryptedContent = encryptedCaption,
                mediaId = uploadedMediaId
            )

            val response = api.sendMessage(request)

            if (response.isSuccessful && response.body()?.success == true) {
                val sentData = response.body()!!.data!!
                val messageType = when (type) {
                    "image" -> MessageType.IMAGE
                    "video" -> MessageType.VIDEO
                    "audio", "voice" -> MessageType.AUDIO
                    "document" -> MessageType.DOCUMENT
                    "location" -> MessageType.LOCATION
                    else -> MessageType.TEXT
                }
                val message = Message(
                    id = sentData.messageId,
                    chatId = sentData.conversationId,
                    senderId = currentUserId,
                    senderName = "",
                    senderPhotoUrl = "",
                    text = caption ?: "",
                    type = messageType,
                    mediaUrl = mediaUri,
                    createdAt = try { sentData.createdAt.toLong() } catch (e: Exception) { System.currentTimeMillis() }
                )
                updateMessageCache(chatId, message)
                Resource.Success(message)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to send media")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to send media")
        }
    }

    override suspend fun sendVoiceMessage(
        chatId: String,
        audioUri: String,
        duration: Long
    ): Resource<Message> {
        return sendMediaMessage(chatId, audioUri, "voice", null)
    }

    override suspend fun markAsRead(chatId: String, messageIds: List<String>): Resource<Unit> {
        return try {
            webSocketClient.sendReadReceipt(chatId, messageIds)
            val response = api.markRead(MarkReadRequest(conversationId = chatId, messageIds = messageIds))
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to mark as read")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to mark as read")
        }
    }

    override suspend fun deleteMessage(
        chatId: String,
        messageId: String,
        forEveryone: Boolean
    ): Resource<Unit> {
        return try {
            val response = api.deleteMessage(messageId, forEveryone)

            if (response.isSuccessful && response.body()?.success == true) {
                // Update local cache
                _messagesCache[chatId]?.update { messages ->
                    messages.map { msg ->
                        if (msg.id == messageId) {
                            msg.copy(isDeleted = true, text = "This message was deleted")
                        } else msg
                    }
                }
                Resource.Success(Unit)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to delete message")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to delete message")
        }
    }

    override suspend fun addReaction(
        chatId: String,
        messageId: String,
        emoji: String
    ): Resource<Unit> {
        return try {
            val response = api.addMessageReaction(messageId, mapOf("emoji" to emoji))

            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to add reaction")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to add reaction")
        }
    }

    override suspend fun removeReaction(chatId: String, messageId: String): Resource<Unit> {
        return try {
            val response = api.removeMessageReaction(messageId)

            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to remove reaction")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to remove reaction")
        }
    }

    override fun observeMessages(chatId: String): Flow<List<Message>> {
        if (!_messagesCache.containsKey(chatId)) {
            _messagesCache[chatId] = MutableStateFlow(emptyList())
            scope.launch {
                loadMessages(chatId)
            }
        }
        return _messagesCache[chatId]!!.asStateFlow()
    }

    override suspend fun getMessages(
        chatId: String,
        limit: Int,
        beforeTimestamp: Long?
    ): Resource<List<Message>> {
        return try {
            val response = api.getMessages(chatId, limit, beforeTimestamp?.toString())

            if (response.isSuccessful && response.body()?.success == true) {
                val messages = response.body()!!.data?.map { decryptMessage(it.toMessage()) } ?: emptyList()
                Resource.Success(messages)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to get messages")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get messages")
        }
    }

    override suspend fun searchMessages(chatId: String, query: String): Resource<List<Message>> {
        // Search locally in cached messages
        val cachedMessages = _messagesCache[chatId]?.value ?: emptyList()
        val results = cachedMessages.filter { 
            it.text.contains(query, ignoreCase = true) 
        }
        return Resource.Success(results)
    }

    // ==================== Typing Indicator ====================

    override suspend fun setTyping(chatId: String, isTyping: Boolean): Resource<Unit> {
        return try {
            webSocketClient.sendTyping(chatId, isTyping)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to send typing indicator")
        }
    }

    override fun observeTyping(chatId: String): Flow<List<String>> {
        if (!_typingCache.containsKey(chatId)) {
            _typingCache[chatId] = MutableStateFlow(emptyList())
        }
        return _typingCache[chatId]!!.asStateFlow()
    }

    // ==================== Helper Methods ====================

    private suspend fun loadMessages(chatId: String) {
        try {
            // Auto-restore E2EE session key if missing for this conversation
            if (!sessionManager.hasSession(chatId)) {
                val currentUserId = tokenManager.userId
                val chat = _chats.value.find { it.id == chatId }
                val otherUserId = chat?.participants?.find { it != currentUserId }
                if (otherUserId != null) {
                    initializeE2EESession(chatId, otherUserId)
                }
            }

            val response = api.getMessages(chatId, 50, null)
            if (response.isSuccessful && response.body()?.success == true) {
                val messages = response.body()!!.data?.map { decryptMessage(it.toMessage()) } ?: emptyList()
                _messagesCache[chatId]?.value = messages
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateChatCache(chat: Chat) {
        _chats.update { chats ->
            val index = chats.indexOfFirst { it.id == chat.id }
            if (index >= 0) {
                chats.toMutableList().apply { set(index, chat) }
            } else {
                chats + chat
            }
        }
    }

    private fun updateMessageCache(chatId: String, message: Message) {
        if (!_messagesCache.containsKey(chatId)) {
            _messagesCache[chatId] = MutableStateFlow(emptyList())
        }
        _messagesCache[chatId]?.update { messages ->
            if (messages.any { it.id == message.id }) {
                messages.map { if (it.id == message.id) message else it }
            } else {
                messages + message
            }
        }
    }

    private suspend fun initializeE2EESession(conversationId: String, userId: String) {
        try {
            if (sessionManager.hasSession(conversationId)) return

            // Fetch user's prekey bundle
            val response = api.getUserKeys(userId)
            if (response.isSuccessful && response.body()?.success == true) {
                val keys = response.body()!!.data!!
                
                // Perform X3DH key exchange
                val identityKeyPair = cryptoManager.generateIdentityKeyPair()
                val ephemeralKeyPair = cryptoManager.generateKeyPair()
                
                val theirIdentityKey = cryptoManager.publicKeyFromBase64(keys.identityPublicKey)
                val theirSignedPreKey = cryptoManager.publicKeyFromBase64(keys.signedPreKeyPublic)
                val theirPreKey = keys.preKeyPublic?.let { cryptoManager.publicKeyFromBase64(it) }
                
                val sharedSecret = cryptoManager.performX3DH(
                    identityKeyPair = identityKeyPair,
                    ephemeralKeyPair = ephemeralKeyPair,
                    recipientIdentityPublicKey = theirIdentityKey,
                    recipientSignedPreKey = theirSignedPreKey,
                    recipientOneTimePreKey = theirPreKey
                )
                
                // Derive session key
                val sessionKey = cryptoManager.deriveSessionKey(sharedSecret)
                sessionManager.saveSessionKey(conversationId, sessionKey)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleIncomingMessage(wsMessage: WebSocketMessage) {
        val chatId = wsMessage.conversationId
        val message = Message(
            id = wsMessage.messageId,
            chatId = chatId,
            senderId = wsMessage.senderId,
            senderName = wsMessage.senderDisplayName ?: wsMessage.senderUsername ?: "",
            senderPhotoUrl = "",
            text = wsMessage.encryptedContent,
            type = when (wsMessage.messageType) {
                "image" -> MessageType.IMAGE
                "video" -> MessageType.VIDEO
                "audio", "voice" -> MessageType.AUDIO
                "document" -> MessageType.DOCUMENT
                else -> MessageType.TEXT
            },
            createdAt = try { wsMessage.timestamp.toLong() } catch (e: Exception) { null }
        )
        
        updateMessageCache(chatId, decryptMessage(message))
    }

    private fun handleTypingEvent(event: TypingEvent) {
        if (!_typingCache.containsKey(event.conversationId)) {
            _typingCache[event.conversationId] = MutableStateFlow(emptyList())
        }
        
        _typingCache[event.conversationId]?.update { typingUsers ->
            if (event.isTyping) {
                if (event.userId !in typingUsers) typingUsers + event.userId else typingUsers
            } else {
                typingUsers - event.userId
            }
        }
    }

    private fun decryptMessage(message: Message): Message {
        // Try to decrypt using conversationId session key
        val chatId = message.chatId
        return if (sessionManager.hasSession(chatId)) {
            val sessionKey = sessionManager.getSessionKey(chatId)
            if (sessionKey != null) {
                try {
                    val decryptedText = cryptoManager.decryptMessage(message.text, sessionKey)
                    message.copy(text = decryptedText)
                } catch (e: Exception) {
                    // If decryption fails, return original message
                    message
                }
            } else message
        } else message
    }
}

// Extension functions for DTO conversion
fun ConversationDto.toChat(): Chat {
    return Chat(
        id = actualId,
        type = if (isGroup) ChatType.GROUP else ChatType.PRIVATE,
        participants = participants.map { it.id },
        participantDetails = participants.associate { 
            it.id to ParticipantInfo(
                displayName = it.displayName ?: it.username,
                photoUrl = it.avatarUrl ?: ""
            )
        },
        groupName = name ?: "",
        groupPhotoUrl = avatar ?: "",
        lastMessage = lastMessage?.let {
            LastMessage(
                text = it.content ?: "",
                senderId = it.senderId,
                type = when (it.type) {
                    "image" -> MessageType.IMAGE
                    "video" -> MessageType.VIDEO
                    "audio", "voice" -> MessageType.AUDIO
                    "document" -> MessageType.DOCUMENT
                    else -> MessageType.TEXT
                },
                timestamp = it.createdAt?.let { ts -> 
                    try { ts.toLong() } catch (e: Exception) { null }
                }
            )
        },
        createdAt = createdAt?.let { try { it.toLong() } catch (e: Exception) { null } },
        updatedAt = updatedAt?.let { try { it.toLong() } catch (e: Exception) { null } }
    )
}

fun MessageDto.toMessage(): Message {
    return Message(
        id = id,
        chatId = conversationId,
        senderId = senderId,
        senderName = sender?.displayName ?: sender?.username ?: "",
        senderPhotoUrl = sender?.avatarUrl ?: "",
        text = content ?: "",
        type = when (type) {
            "image" -> MessageType.IMAGE
            "video" -> MessageType.VIDEO
            "audio", "voice" -> MessageType.AUDIO
            "document" -> MessageType.DOCUMENT
            else -> MessageType.TEXT
        },
        mediaUrl = mediaUrl ?: "",
        replyTo = replyTo?.let { 
            ReplyMessage(
                messageId = it.id,
                senderId = it.senderId,
                senderName = it.sender?.displayName ?: "",
                text = it.content ?: ""
            )
        },
        isDeleted = isDeleted,
        createdAt = createdAt?.let { try { it.toLong() } catch (e: Exception) { null } }
    )
}
