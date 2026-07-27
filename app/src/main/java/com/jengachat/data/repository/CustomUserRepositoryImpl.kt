package com.jengachat.data.repository

import com.jengachat.data.model.User
import com.jengachat.data.remote.*
import com.jengachat.util.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User Repository Implementation using custom Talksy Server
 */
@Singleton
class CustomUserRepositoryImpl @Inject constructor(
    private val networkClient: NetworkClient,
    private val tokenManager: TokenManager
) : UserRepository {

    private val api get() = networkClient.api
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // User cache
    private val _usersCache = MutableStateFlow<Map<String, User>>(emptyMap())
    private val _presenceCache = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    override suspend fun getCurrentUser(): Resource<User> {
        return try {
            val response = api.getMe()
            
            if (response.isSuccessful && response.body()?.success == true) {
                val user = response.body()!!.data!!.toUser()
                cacheUser(user)
                Resource.Success(user)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to get current user")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get current user")
        }
    }

    override suspend fun getUserById(userId: String): Resource<User> {
        // Check cache first
        _usersCache.value[userId]?.let { return Resource.Success(it) }
        
        return try {
            val response = api.getUser(userId)
            
            if (response.isSuccessful && response.body()?.success == true) {
                val user = response.body()!!.data!!.toUser()
                cacheUser(user)
                Resource.Success(user)
            } else {
                Resource.Error(response.body()?.error ?: "User not found")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get user")
        }
    }

    override suspend fun getUsersByIds(userIds: List<String>): Resource<List<User>> {
        return try {
            val users = mutableListOf<User>()
            
            for (userId in userIds) {
                when (val result = getUserById(userId)) {
                    is Resource.Success -> users.add(result.data)
                    is Resource.Error -> { /* Skip failed users */ }
                    is Resource.Loading -> { /* Skip */ }
                }
            }
            
            Resource.Success(users)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get users")
        }
    }

    override suspend fun searchUsers(query: String): Resource<List<User>> {
        return try {
            val response = api.searchUsers(query)
            
            if (response.isSuccessful && response.body()?.success == true) {
                val users = response.body()!!.data?.map { it.toUser() } ?: emptyList()
                users.forEach { cacheUser(it) }
                Resource.Success(users)
            } else {
                Resource.Error(response.body()?.error ?: "Search failed")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Search failed")
        }
    }

    override suspend fun updateProfile(
        displayName: String?,
        status: String?,
        photoUrl: String?
    ): Resource<User> {
        return try {
            val response = api.updateProfile(
                UpdateProfileRequest(
                    displayName = displayName,
                    statusText = status,
                    avatarUrl = photoUrl
                )
            )
            if (response.isSuccessful && response.body()?.success == true) {
                val user = response.body()!!.data!!.toUser()
                cacheUser(user)
                Resource.Success(user)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to update profile")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update profile")
        }
    }

    override suspend fun updateProfilePhoto(photoUri: String): Resource<String> {
        return try {
            val response = api.updateProfile(UpdateProfileRequest(avatarUrl = photoUri))
            if (response.isSuccessful && response.body()?.success == true) {
                val avatarUrl = response.body()!!.data!!.avatarUrl ?: photoUri
                Resource.Success(avatarUrl)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to update photo")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update photo")
        }
    }

    override suspend fun setOnlineStatus(isOnline: Boolean): Resource<Unit> {
        return try {
            val response = api.updatePresence(mapOf("status" to if (isOnline) "online" else "offline"))

            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to update status")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update status")
        }
    }

    override suspend fun blockUser(userId: String): Resource<Unit> {
        return try {
            val response = api.blockUser(userId)

            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to block user")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to block user")
        }
    }

    override suspend fun unblockUser(userId: String): Resource<Unit> {
        return try {
            val response = api.unblockUser(userId)

            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to unblock user")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to unblock user")
        }
    }

    override suspend fun addContact(userId: String): Resource<Unit> {
        // Server doesn't have a specific contacts endpoint
        // For now, this is a no-op
        return Resource.Success(Unit)
    }

    override suspend fun removeContact(userId: String): Resource<Unit> {
        // Server doesn't have a specific contacts endpoint
        // For now, this is a no-op
        return Resource.Success(Unit)
    }

    override fun observeUser(userId: String): Flow<User?> {
        // Start loading user if not in cache
        scope.launch {
            if (!_usersCache.value.containsKey(userId)) {
                getUserById(userId)
            }
        }
        return _usersCache.map { it[userId] }
    }

    override fun observeUserPresence(userId: String): Flow<Boolean> {
        return _presenceCache.map { it[userId] ?: false }
    }

    // Helper method to cache users
    private fun cacheUser(user: User) {
        _usersCache.update { cache -> cache + (user.id to user) }
        _presenceCache.update { cache -> cache + (user.id to user.isOnline) }
    }

    /**
     * Lookup users by email addresses (for contacts sync)
     * Returns list of Talksy users whose email matches any in the provided list
     */
    override suspend fun lookupUsersByEmails(emails: List<String>): Resource<List<User>> {
        if (emails.isEmpty()) return Resource.Success(emptyList())
        
        return try {
            val request = EmailLookupRequest(emails = emails.map { it.lowercase().trim() })
            val response = api.lookupUsersByEmails(request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                val users = response.body()!!.data.map { it.toUser() }
                users.forEach { cacheUser(it) }
                Resource.Success(users)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to lookup users")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to lookup users by email")
        }
    }

    /**
     * Lookup users by phone numbers (for contacts sync)
     * Returns list of Talksy users whose phone matches any in the provided list
     */
    override suspend fun lookupUsersByPhones(phones: List<String>): Resource<List<User>> {
        if (phones.isEmpty()) return Resource.Success(emptyList())
        
        return try {
            // Normalize phone numbers
            val normalizedPhones = phones.map { it.replace(Regex("[\\s\\-\\(\\)\\.]"), "") }
            val request = PhoneLookupRequest(phones = normalizedPhones)
            val response = api.lookupUsersByPhones(request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                val users = response.body()!!.data.map { it.toUser() }
                users.forEach { cacheUser(it) }
                Resource.Success(users)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to lookup users")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to lookup users by phone")
        }
    }
}
