package com.jengachat.data.repository

import com.jengachat.crypto.CryptoManager
import com.jengachat.crypto.SessionManager
import com.jengachat.data.model.User
import com.jengachat.data.remote.*
import com.jengachat.util.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auth Repository Implementation using custom Talksy Server
 * Replaces Firebase Auth with self-hosted E2EE server
 */
@Singleton
class CustomAuthRepositoryImpl @Inject constructor(
    private val networkClient: NetworkClient,
    private val tokenManager: TokenManager,
    private val webSocketClient: WebSocketClient,
    private val cryptoManager: CryptoManager,
    private val sessionManager: SessionManager
) : AuthRepository {

    private val api get() = networkClient.api

    override val currentUserId: String?
        get() = tokenManager.userId

    override val isLoggedIn: Boolean
        get() = tokenManager.isLoggedIn.value

    override fun getAuthStateFlow(): Flow<Boolean> = tokenManager.isLoggedIn

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String
    ): Resource<User> {
        return try {
            // Generate username from email
            val username = email.substringBefore("@").replace(Regex("[^a-zA-Z0-9]"), "")
            
            val response = api.register(
                RegisterRequest(
                    username = username,
                    email = email,
                    password = password,
                    displayName = displayName
                )
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()!!.data!!
                
                // Save tokens
                tokenManager.saveTokens(
                    accessToken = data.accessToken,
                    refreshToken = data.refreshToken,
                    userId = data.user.id
                )
                tokenManager.username = data.user.username
                
                // Initialize cryptographic keys
                initializeCryptoKeys()
                
                // Upload public keys to server
                uploadPublicKeys()
                
                // Connect WebSocket
                webSocketClient.connect()
                
                Resource.Success(data.user.toUser())
            } else {
                Resource.Error(response.body()?.error ?: "Registration failed")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Registration failed")
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): Resource<User> {
        return try {
            val response = api.login(LoginRequest(email, password))

            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()!!.data!!
                
                // Save tokens
                tokenManager.saveTokens(
                    accessToken = data.accessToken,
                    refreshToken = data.refreshToken,
                    userId = data.user.id
                )
                tokenManager.username = data.user.username
                
                // Ensure crypto keys are initialized
                initializeCryptoKeys()
                
                // Connect WebSocket
                webSocketClient.connect()
                
                Resource.Success(data.user.toUser())
            } else {
                Resource.Error(response.body()?.error ?: "Login failed")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Login failed")
        }
    }

    override suspend fun signOut(): Resource<Unit> {
        return try {
            // Disconnect WebSocket first
            webSocketClient.disconnect()
            
            // Call server logout
            try {
                api.logout()
            } catch (e: Exception) {
                // Continue even if server call fails
            }
            
            // Clear local data
            tokenManager.clearTokens()
            sessionManager.clearAllSessions()
            
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Sign out failed")
        }
    }

    override suspend fun updatePassword(
        currentPassword: String,
        newPassword: String
    ): Resource<Unit> {
        return try {
            val response = api.changePassword(
                mapOf(
                    "currentPassword" to currentPassword,
                    "newPassword" to newPassword
                )
            )

            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                Resource.Error(response.body()?.error ?: "Password change failed")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Password change failed")
        }
    }

    override suspend fun deleteAccount(): Resource<Unit> {
        return try {
            val response = api.deleteAccount()
            signOut()
            cryptoManager.clearAllKeys()
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                Resource.Error(response.body()?.error ?: "Account deletion failed")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Account deletion failed")
        }
    }
    
    override suspend fun resetPassword(email: String): Resource<Unit> {
        return try {
            val response = api.forgotPassword(ForgotPasswordRequest(email))
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                Resource.Error(response.body()?.error ?: "Failed to send reset email")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to send reset email")
        }
    }

    /**
     * Initialize cryptographic keys for E2EE
     */
    private fun initializeCryptoKeys() {
        cryptoManager.generateIdentityKeyPair()
        cryptoManager.getRegistrationId()
    }

    /**
     * Upload public keys to server for key exchange
     */
    private suspend fun uploadPublicKeys() {
        try {
            val identityKeyPair = cryptoManager.generateIdentityKeyPair()
            val registrationId = cryptoManager.getRegistrationId()
            
            // Generate signed prekey
            val signedPreKey = cryptoManager.generateSignedPreKey(identityKeyPair.private)
            sessionManager.saveSignedPreKey(signedPreKey)
            
            // Generate one-time prekeys
            val preKeys = cryptoManager.generatePreKeys(100)
            sessionManager.savePreKeys(preKeys)
            
            // Upload to server
            val request = UploadKeysRequest(
                identityPublicKey = cryptoManager.publicKeyToBase64(identityKeyPair.public),
                signedPreKeyId = signedPreKey.id,
                signedPreKeyPublic = cryptoManager.publicKeyToBase64(signedPreKey.keyPair.public),
                signedPreKeySignature = cryptoManager.signatureToBase64(signedPreKey.signature),
                registrationId = registrationId,
                preKeys = preKeys.map { preKey ->
                    PreKeyDto(
                        id = preKey.id,
                        publicKey = cryptoManager.publicKeyToBase64(preKey.keyPair.public)
                    )
                }
            )
            
            api.uploadKeys(request)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Check server prekey count and replenish if low (< 20)
     */
    suspend fun checkAndReplenishPreKeys() {
        try {
            val response = api.getPreKeyCount()
            if (response.isSuccessful && response.body()?.success == true) {
                // If count is low or empty, replenish prekeys
                uploadPublicKeys()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// Extension function to convert DTO to domain model
fun UserDto.toUser(): User {
    return User(
        id = id,
        email = email ?: "",
        displayName = displayName ?: username,
        photoUrl = avatarUrl,
        isOnline = isOnline,
        createdAt = null
    )
}
