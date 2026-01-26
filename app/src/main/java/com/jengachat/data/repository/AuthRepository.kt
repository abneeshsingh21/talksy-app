package com.jengachat.data.repository

import com.jengachat.data.model.User
import com.jengachat.util.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Authentication repository interface
 * Updated to work with custom Talksy server (no Firebase)
 */
interface AuthRepository {
    val currentUserId: String?
    val isLoggedIn: Boolean
    
    suspend fun signUpWithEmail(email: String, password: String, displayName: String): Resource<User>
    suspend fun signInWithEmail(email: String, password: String): Resource<User>
    suspend fun signOut(): Resource<Unit>
    suspend fun deleteAccount(): Resource<Unit>
    suspend fun updatePassword(currentPassword: String, newPassword: String): Resource<Unit>
    suspend fun resetPassword(email: String): Resource<Unit>
    fun getAuthStateFlow(): Flow<Boolean>
}
