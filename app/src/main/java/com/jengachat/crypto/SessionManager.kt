package com.jengachat.crypto

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jengachat.data.remote.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyPair
import java.security.PublicKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session Manager for E2EE conversations
 * Manages session keys for each conversation
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager
) {
    private val prefs = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "sessions_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("sessions_encrypted", Context.MODE_PRIVATE)
    }
    
    // Cache of session keys: conversationId -> sessionKey
    private val sessionCache = mutableMapOf<String, ByteArray>()
    
    /**
     * Get or create a session key for a conversation
     */
    fun getSessionKey(conversationId: String): ByteArray? {
        // Check cache first
        sessionCache[conversationId]?.let { return it }
        
        // Try to load from storage
        val stored = prefs.getString("session_$conversationId", null)
        if (stored != null) {
            val key = Base64.decode(stored, Base64.NO_WRAP)
            sessionCache[conversationId] = key
            return key
        }
        
        return null
    }
    
    /**
     * Store a session key for a conversation
     */
    fun saveSessionKey(conversationId: String, sessionKey: ByteArray) {
        sessionCache[conversationId] = sessionKey
        prefs.edit()
            .putString("session_$conversationId", Base64.encodeToString(sessionKey, Base64.NO_WRAP))
            .apply()
    }
    
    /**
     * Initialize a session with another user using their key bundle
     * This performs X3DH key exchange
     */
    fun initializeSession(
        conversationId: String,
        recipientKeyBundle: KeyBundleDto
    ): ByteArray {
        // Get or generate our identity key pair
        val identityKeyPair = cryptoManager.generateIdentityKeyPair()
        
        // Generate ephemeral key pair for this session
        val ephemeralKeyPair = cryptoManager.generateKeyPair()
        
        // Parse recipient's keys
        val recipientIdentityPublicKey = cryptoManager.base64ToPublicKey(recipientKeyBundle.identityPublicKey)
        val recipientSignedPreKey = cryptoManager.base64ToPublicKey(recipientKeyBundle.signedPreKeyPublic)
        
        // Parse one-time prekey if available
        val recipientOneTimePreKey: PublicKey? = recipientKeyBundle.preKeyPublic?.let {
            cryptoManager.base64ToPublicKey(it)
        }
        
        // Perform X3DH
        val sessionKey = cryptoManager.performX3DH(
            identityKeyPair = identityKeyPair,
            ephemeralKeyPair = ephemeralKeyPair,
            recipientIdentityPublicKey = recipientIdentityPublicKey,
            recipientSignedPreKey = recipientSignedPreKey,
            recipientOneTimePreKey = recipientOneTimePreKey
        )
        
        // Store the session
        saveSessionKey(conversationId, sessionKey)
        
        // Store the ephemeral public key (needed for message header)
        saveEphemeralPublicKey(conversationId, ephemeralKeyPair.public)
        
        return sessionKey
    }
    
    /**
     * Receive a session from another user
     */
    fun receiveSession(
        conversationId: String,
        senderIdentityPublicKeyBase64: String,
        senderEphemeralPublicKeyBase64: String,
        usedPreKeyId: Int?,
        signedPreKeyId: Int
    ): ByteArray? {
        val identityKeyPair = cryptoManager.getIdentityKeyPair() ?: return null
        
        // Load signed prekey (should be stored)
        val signedPreKeyPair = loadSignedPreKey(signedPreKeyId) ?: return null
        
        // Load one-time prekey if used
        val oneTimePreKeyPair = usedPreKeyId?.let { loadPreKey(it) }
        
        val senderIdentityPublicKey = cryptoManager.base64ToPublicKey(senderIdentityPublicKeyBase64)
        val senderEphemeralPublicKey = cryptoManager.base64ToPublicKey(senderEphemeralPublicKeyBase64)
        
        // Perform X3DH receiving side
        val sessionKey = cryptoManager.receiveX3DH(
            identityKeyPair = identityKeyPair,
            signedPreKeyPair = signedPreKeyPair,
            oneTimePreKeyPair = oneTimePreKeyPair,
            senderIdentityPublicKey = senderIdentityPublicKey,
            senderEphemeralPublicKey = senderEphemeralPublicKey
        )
        
        // Mark prekey as used
        usedPreKeyId?.let { removePreKey(it) }
        
        // Store the session
        saveSessionKey(conversationId, sessionKey)
        
        return sessionKey
    }
    
    /**
     * Encrypt a message for a conversation
     */
    fun encryptMessage(conversationId: String, plaintext: String): String? {
        val sessionKey = getSessionKey(conversationId) ?: return null
        return cryptoManager.encryptMessage(plaintext, sessionKey)
    }
    
    /**
     * Decrypt a message from a conversation
     */
    fun decryptMessage(conversationId: String, ciphertext: String): String? {
        val sessionKey = getSessionKey(conversationId) ?: return null
        return try {
            cryptoManager.decryptMessage(ciphertext, sessionKey)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if session exists for conversation
     */
    fun hasSession(conversationId: String): Boolean {
        return getSessionKey(conversationId) != null
    }
    
    /**
     * Delete session for conversation
     */
    fun deleteSession(conversationId: String) {
        sessionCache.remove(conversationId)
        prefs.edit().remove("session_$conversationId").apply()
    }
    
    /**
     * Clear all sessions
     */
    fun clearAllSessions() {
        sessionCache.clear()
        prefs.edit().clear().apply()
    }
    
    // ==================== PREKEY STORAGE ====================
    
    private fun saveEphemeralPublicKey(conversationId: String, publicKey: PublicKey) {
        prefs.edit()
            .putString("ephemeral_$conversationId", cryptoManager.publicKeyToBase64(publicKey))
            .apply()
    }
    
    fun saveSignedPreKey(signedPreKey: SignedPreKey) {
        val keyPairJson = """
            {
                "public": "${cryptoManager.publicKeyToBase64(signedPreKey.keyPair.public)}",
                "private": "${cryptoManager.privateKeyToBase64(signedPreKey.keyPair.private)}"
            }
        """.trimIndent()
        
        prefs.edit()
            .putString("signed_prekey_${signedPreKey.id}", keyPairJson)
            .putString("signed_prekey_sig_${signedPreKey.id}", cryptoManager.signatureToBase64(signedPreKey.signature))
            .putInt("current_signed_prekey_id", signedPreKey.id)
            .apply()
    }
    
    fun loadSignedPreKey(id: Int): KeyPair? {
        val json = prefs.getString("signed_prekey_$id", null) ?: return null
        
        // Simple JSON parsing
        val publicMatch = Regex("\"public\":\\s*\"([^\"]+)\"").find(json)
        val privateMatch = Regex("\"private\":\\s*\"([^\"]+)\"").find(json)
        
        if (publicMatch == null || privateMatch == null) return null
        
        val publicKey = cryptoManager.base64ToPublicKey(publicMatch.groupValues[1])
        val privateKey = cryptoManager.base64ToPrivateKey(privateMatch.groupValues[1])
        
        return KeyPair(publicKey, privateKey)
    }
    
    fun savePreKeys(preKeys: List<PreKey>) {
        preKeys.forEach { preKey ->
            val keyPairJson = """
                {
                    "public": "${cryptoManager.publicKeyToBase64(preKey.keyPair.public)}",
                    "private": "${cryptoManager.privateKeyToBase64(preKey.keyPair.private)}"
                }
            """.trimIndent()
            
            prefs.edit()
                .putString("prekey_${preKey.id}", keyPairJson)
                .apply()
        }
    }
    
    fun loadPreKey(id: Int): KeyPair? {
        val json = prefs.getString("prekey_$id", null) ?: return null
        
        val publicMatch = Regex("\"public\":\\s*\"([^\"]+)\"").find(json)
        val privateMatch = Regex("\"private\":\\s*\"([^\"]+)\"").find(json)
        
        if (publicMatch == null || privateMatch == null) return null
        
        val publicKey = cryptoManager.base64ToPublicKey(publicMatch.groupValues[1])
        val privateKey = cryptoManager.base64ToPrivateKey(privateMatch.groupValues[1])
        
        return KeyPair(publicKey, privateKey)
    }
    
    fun removePreKey(id: Int) {
        prefs.edit().remove("prekey_$id").apply()
    }
    
    fun getStoredPreKeyCount(): Int {
        return prefs.all.keys.count { it.startsWith("prekey_") }
    }
}
