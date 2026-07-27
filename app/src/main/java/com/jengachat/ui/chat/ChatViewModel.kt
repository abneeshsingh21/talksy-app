package com.jengachat.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jengachat.data.model.*
import com.jengachat.data.repository.AuthRepository
import com.jengachat.data.repository.ChatRepository
import com.jengachat.data.repository.ContactsRepository
import com.jengachat.data.repository.PhoneContact
import com.jengachat.data.repository.UserRepository
import com.jengachat.util.PermissionManager
import com.jengachat.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val contactsRepository: ContactsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    private val _currentChat = MutableStateFlow<Chat?>(null)
    val currentChat: StateFlow<Chat?> = _currentChat.asStateFlow()
    
    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()
    
    private val _groupMembers = MutableStateFlow<List<User>>(emptyList())
    val groupMembers: StateFlow<List<User>> = _groupMembers.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    val currentUserId: String?
        get() = authRepository.currentUserId
    
    init {
        loadChats()
    }
    
    fun loadChats() {
        viewModelScope.launch {
            chatRepository.observeChats().collect { chatList ->
                _chats.value = chatList
                _isLoading.value = false
            }
        }
    }
    
    fun loadChat(chatId: String) {
        viewModelScope.launch {
            chatRepository.observeChat(chatId).collect { chat ->
                _currentChat.value = chat
            }
        }
    }
    
    fun loadMessages(chatId: String) {
        viewModelScope.launch {
            chatRepository.observeMessages(chatId).collect { messageList ->
                _messages.value = messageList
            }
        }
    }
    
    fun loadGroupMembers(chatId: String) {
        viewModelScope.launch {
            _currentChat.value?.let { chat ->
                if (chat.type == ChatType.GROUP) {
                    when (val result = userRepository.getUsersByIds(chat.participants)) {
                        is Resource.Success -> {
                            _groupMembers.value = result.data ?: emptyList()
                        }
                        else -> {}
                    }
                }
            }
        }
    }
    
    fun sendMessage(chatId: String, content: String) {
        viewModelScope.launch {
            when (val result = chatRepository.sendMessage(chatId, content, null)) {
                is Resource.Success -> {
                    // Message sent successfully
                }
                is Resource.Error -> {
                    _error.value = result.message
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    fun sendMediaMessage(chatId: String, mediaUri: String, mediaType: String) {
        viewModelScope.launch {
            when (val result = chatRepository.sendMediaMessage(chatId, mediaUri, mediaType, null)) {
                is Resource.Success -> {
                    // Media sent successfully
                }
                is Resource.Error -> {
                    _error.value = result.message
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    fun sendLocationMessage(chatId: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            val locationContent = "📍 Location: $latitude, $longitude\nhttps://maps.google.com/?q=$latitude,$longitude"
            when (val result = chatRepository.sendMessage(chatId, locationContent, null)) {
                is Resource.Success -> { /* Location shared */ }
                is Resource.Error -> { _error.value = result.message }
                is Resource.Loading -> {}
            }
        }
    }

    fun sendVoiceMessage(chatId: String, base64Audio: String, durationMs: Long) {
        viewModelScope.launch {
            when (val result = chatRepository.sendVoiceMessage(chatId, base64Audio, durationMs)) {
                is Resource.Success -> { /* Voice message sent */ }
                is Resource.Error -> { _error.value = result.message }
                is Resource.Loading -> {}
            }
        }
    }

    private val _isCreatingChat = MutableStateFlow(false)
    val isCreatingChat: StateFlow<Boolean> = _isCreatingChat.asStateFlow()

    fun createOrGetPrivateChat(otherUserId: String, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _isCreatingChat.value = true
            try {
                when (val result = chatRepository.createPrivateChat(otherUserId)) {
                    is Resource.Success -> {
                        result.data?.let { chat ->
                            onSuccess(chat.id)
                        }
                    }
                    is Resource.Error -> {
                        _error.value = result.message ?: "Failed to start chat"
                    }
                    is Resource.Loading -> {}
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Error starting chat"
            } finally {
                _isCreatingChat.value = false
            }
        }
    }
    
    fun createGroupChat(
        name: String,
        memberIds: List<String>,
        onSuccess: (String) -> Unit
    ) {
        viewModelScope.launch {
            when (val result = chatRepository.createGroupChat(name, memberIds, null)) {
                is Resource.Success -> {
                    result.data?.let { chat ->
                        onSuccess(chat.id)
                    }
                }
                is Resource.Error -> {
                    _error.value = result.message
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    fun searchUsers(query: String) {
        viewModelScope.launch {
            when (val result = userRepository.searchUsers(query)) {
                is Resource.Success -> {
                    _users.value = result.data ?: emptyList()
                }
                is Resource.Error -> {
                    _error.value = result.message
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    fun leaveGroup(chatId: String) {
        viewModelScope.launch {
            when (val result = chatRepository.leaveGroup(chatId)) {
                is Resource.Success -> {
                    // Left group successfully
                }
                is Resource.Error -> {
                    _error.value = result.message
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    fun markAsRead(chatId: String, messageIds: List<String>) {
        viewModelScope.launch {
            chatRepository.markAsRead(chatId, messageIds)
        }
    }
    
    fun setTyping(chatId: String, isTyping: Boolean) {
        viewModelScope.launch {
            chatRepository.setTyping(chatId, isTyping)
        }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    // ==================== CONTACTS SYNC ====================
    
    private val _phoneContacts = MutableStateFlow<List<PhoneContact>>(emptyList())
    val phoneContacts: StateFlow<List<PhoneContact>> = _phoneContacts.asStateFlow()
    
    private val _matchedContacts = MutableStateFlow<List<User>>(emptyList())
    val matchedContacts: StateFlow<List<User>> = _matchedContacts.asStateFlow()
    
    private val _contactsSyncing = MutableStateFlow(false)
    val contactsSyncing: StateFlow<Boolean> = _contactsSyncing.asStateFlow()
    
    val hasContactsPermission: Boolean
        get() = PermissionManager.hasContactsPermission(context)
    
    /**
     * Load phone contacts and match with registered Talksy users
     * This is the main entry point for contacts sync
     */
    fun syncContacts() {
        if (!hasContactsPermission) {
            _matchedContacts.value = emptyList()
            return
        }
        
        viewModelScope.launch {
            _contactsSyncing.value = true
            try {
                // Step 1: Load phone contacts
                val contacts = contactsRepository.getPhoneContacts()
                _phoneContacts.value = contacts
                
                // Step 2: Extract emails from contacts
                val emails = contacts.mapNotNull { it.email }.filter { it.isNotBlank() }
                
                // Step 3: Match with registered Talksy users
                if (emails.isNotEmpty()) {
                    when (val result = userRepository.lookupUsersByEmails(emails)) {
                        is Resource.Success -> {
                            _matchedContacts.value = result.data.sortedBy { it.displayName }
                        }
                        is Resource.Error -> {
                            _error.value = result.message
                        }
                        is Resource.Loading -> {}
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _contactsSyncing.value = false
            }
        }
    }
    
    /**
     * Refresh matched contacts (call after permissions granted)
     */
    fun refreshContacts() {
        syncContacts()
    }
    
    /**
     * Get a phone contact by their matched email
     * Useful for displaying contact name from address book
     */
    fun getPhoneContactByEmail(email: String): PhoneContact? {
        return _phoneContacts.value.find { it.email?.equals(email, ignoreCase = true) == true }
    }
    
    // ==================== MESSAGE REACTIONS ====================
    
    /**
     * Add reaction to a message (TODO: implement in ChatRepository)
     */
    fun addReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            try {
                // TODO: Implement chatRepository.addReaction when backend is ready
                // For now, just log the intent
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
    
    /**
     * Remove reaction from a message (TODO: implement in ChatRepository)
     */
    fun removeReaction(messageId: String) {
        viewModelScope.launch {
            try {
                // TODO: Implement chatRepository.removeReaction when backend is ready
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
    
    // ==================== STARRED MESSAGES ====================
    
    /**
     * Toggle star on a message (TODO: implement in ChatRepository)
     */
    fun toggleStar(messageId: String, starred: Boolean) {
        viewModelScope.launch {
            try {
                // TODO: Implement chatRepository.toggleStar when backend is ready
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
    
    // ==================== MESSAGE FORWARD ====================
    
    /**
     * Forward a message to other conversations (TODO: implement in ChatRepository)
     */
    fun forwardMessage(messageId: String, targetConversationIds: List<String>, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                // TODO: Implement chatRepository.forwardMessage when backend is ready
                onSuccess() // Placeholder - call success for now
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun muteChat(chatId: String, isMuted: Boolean) {
        viewModelScope.launch {
            try {
                chatRepository.muteChat(chatId, isMuted)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            try {
                chatRepository.deleteChat(chatId)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}
