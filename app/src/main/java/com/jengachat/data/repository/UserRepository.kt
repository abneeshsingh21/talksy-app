package com.jengachat.data.repository

import com.jengachat.data.model.User
import com.jengachat.util.Resource
import kotlinx.coroutines.flow.Flow

/**
 * User repository interface for managing user data
 */
interface UserRepository {
    suspend fun getCurrentUser(): Resource<User>
    suspend fun getUserById(userId: String): Resource<User>
    suspend fun getUsersByIds(userIds: List<String>): Resource<List<User>>
    suspend fun searchUsers(query: String): Resource<List<User>>
    suspend fun updateProfile(displayName: String?, status: String?, photoUrl: String?): Resource<User>
    suspend fun updateProfilePhoto(photoUri: String): Resource<String>
    suspend fun setOnlineStatus(isOnline: Boolean): Resource<Unit>
    suspend fun blockUser(userId: String): Resource<Unit>
    suspend fun unblockUser(userId: String): Resource<Unit>
    suspend fun addContact(userId: String): Resource<Unit>
    suspend fun removeContact(userId: String): Resource<Unit>
    fun observeUser(userId: String): Flow<User?>
    fun observeUserPresence(userId: String): Flow<Boolean>
    
    // Contacts sync - match phone contacts with registered users
    suspend fun lookupUsersByEmails(emails: List<String>): Resource<List<User>>
    suspend fun lookupUsersByPhones(phones: List<String>): Resource<List<User>>
}
