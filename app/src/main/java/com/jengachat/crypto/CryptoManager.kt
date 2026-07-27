package com.jengachat.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cryptographic utilities for End-to-End Encryption
 * Implements Signal Protocol-inspired key exchange
 */
@Singleton
class CryptoManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "crypto_keys_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("crypto_keys_encrypted", Context.MODE_PRIVATE)
    }
    
    // ==================== KEY GENERATION ====================
    
    /**
     * Generate a new EC key pair for identity
     */
    fun generateKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        return keyGen.generateKeyPair()
    }
    
    /**
     * Generate identity key pair (long-term)
     */
    fun generateIdentityKeyPair(): KeyPair {
        val existing = getIdentityKeyPair()
        if (existing != null) return existing
        
        val keyPair = generateKeyPair()
        saveIdentityKeyPair(keyPair)
        return keyPair
    }
    
    /**
     * Generate signed pre-key
     */
    fun generateSignedPreKey(identityPrivateKey: PrivateKey): SignedPreKey {
        val keyPair = generateKeyPair()
        val preKeyId = System.currentTimeMillis().toInt() and 0x7FFFFFFF
        
        // Sign the public key with identity key
        val signature = sign(keyPair.public.encoded, identityPrivateKey)
        
        return SignedPreKey(
            id = preKeyId,
            keyPair = keyPair,
            signature = signature
        )
    }
    
    /**
     * Generate one-time pre-keys
     */
    fun generatePreKeys(count: Int, startId: Int = 1): List<PreKey> {
        return (0 until count).map { index ->
            val keyPair = generateKeyPair()
            PreKey(id = startId + index, keyPair = keyPair)
        }
    }
    
    /**
     * Generate registration ID
     */
    fun generateRegistrationId(): Int {
        return SecureRandom().nextInt(0x3FFF) + 1
    }
    
    // ==================== KEY EXCHANGE (X3DH) ====================
    
    /**
     * Perform X3DH key agreement (sender side)
     */
    fun performX3DH(
        identityKeyPair: KeyPair,
        ephemeralKeyPair: KeyPair,
        recipientIdentityPublicKey: PublicKey,
        recipientSignedPreKey: PublicKey,
        recipientOneTimePreKey: PublicKey?
    ): ByteArray {
        val dh1 = performDH(identityKeyPair.private, recipientSignedPreKey)
        val dh2 = performDH(ephemeralKeyPair.private, recipientIdentityPublicKey)
        val dh3 = performDH(ephemeralKeyPair.private, recipientSignedPreKey)
        
        val masterSecret = if (recipientOneTimePreKey != null) {
            val dh4 = performDH(ephemeralKeyPair.private, recipientOneTimePreKey)
            dh1 + dh2 + dh3 + dh4
        } else {
            dh1 + dh2 + dh3
        }
        
        return kdf(masterSecret)
    }
    
    /**
     * Perform X3DH key agreement (receiver side)
     */
    fun receiveX3DH(
        identityKeyPair: KeyPair,
        signedPreKeyPair: KeyPair,
        oneTimePreKeyPair: KeyPair?,
        senderIdentityPublicKey: PublicKey,
        senderEphemeralPublicKey: PublicKey
    ): ByteArray {
        val dh1 = performDH(signedPreKeyPair.private, senderIdentityPublicKey)
        val dh2 = performDH(identityKeyPair.private, senderEphemeralPublicKey)
        val dh3 = performDH(signedPreKeyPair.private, senderEphemeralPublicKey)
        
        val masterSecret = if (oneTimePreKeyPair != null) {
            val dh4 = performDH(oneTimePreKeyPair.private, senderEphemeralPublicKey)
            dh1 + dh2 + dh3 + dh4
        } else {
            dh1 + dh2 + dh3
        }
        
        return kdf(masterSecret)
    }
    
    /**
     * Perform Diffie-Hellman key agreement
     */
    private fun performDH(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        return keyAgreement.generateSecret()
    }
    
    /**
     * Key Derivation Function (HKDF HMAC-SHA256 Extract-and-Expand)
     */
    private fun kdf(input: ByteArray, salt: ByteArray? = null, info: ByteArray = "TalksyE2EE".toByteArray()): ByteArray {
        val actualSalt = if (salt == null || salt.isEmpty()) ByteArray(32) else salt
        val macExtract = Mac.getInstance("HmacSHA256")
        macExtract.init(SecretKeySpec(actualSalt, "HmacSHA256"))
        val prk = macExtract.doFinal(input)
        
        val macExpand = Mac.getInstance("HmacSHA256")
        macExpand.init(SecretKeySpec(prk, "HmacSHA256"))
        macExpand.update(info)
        macExpand.update(1.toByte())
        return macExpand.doFinal()
    }
    
    /**
     * Derive session key from shared secret
     */
    fun deriveSessionKey(sharedSecret: ByteArray): ByteArray {
        return kdf(sharedSecret)
    }
    
    // ==================== ENCRYPTION ====================
    
    /**
     * Encrypt message with AES-GCM
     */
    fun encrypt(plaintext: ByteArray, key: ByteArray): EncryptedData {
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key.copyOfRange(0, 32.coerceAtMost(key.size)), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        
        val ciphertext = cipher.doFinal(plaintext)
        
        return EncryptedData(iv = iv, ciphertext = ciphertext)
    }
    
    /**
     * Decrypt message with AES-GCM
     */
    fun decrypt(encryptedData: EncryptedData, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key.copyOfRange(0, 32.coerceAtMost(key.size)), "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, encryptedData.iv))
        
        return cipher.doFinal(encryptedData.ciphertext)
    }
    
    /**
     * Encrypt string message
     */
    fun encryptMessage(message: String, sessionKey: ByteArray): String {
        val encrypted = encrypt(message.toByteArray(Charsets.UTF_8), sessionKey)
        val combined = encrypted.iv + encrypted.ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }
    
    /**
     * Decrypt string message
     */
    fun decryptMessage(encryptedBase64: String, sessionKey: ByteArray): String {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val ciphertext = combined.copyOfRange(12, combined.size)
        val decrypted = decrypt(EncryptedData(iv, ciphertext), sessionKey)
        return String(decrypted, Charsets.UTF_8)
    }
    
    // ==================== SIGNING ====================
    
    /**
     * Sign data with private key
     */
    fun sign(data: ByteArray, privateKey: PrivateKey): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }
    
    /**
     * Verify signature with public key
     */
    fun verify(data: ByteArray, signature: ByteArray, publicKey: PublicKey): Boolean {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initVerify(publicKey)
        sig.update(data)
        return sig.verify(signature)
    }
    
    // ==================== KEY SERIALIZATION ====================
    
    fun publicKeyToBase64(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }
    
    fun base64ToPublicKey(base64: String): PublicKey {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(bytes)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(keySpec)
    }
    
    // Alias for backwards compatibility
    fun publicKeyFromBase64(base64: String): PublicKey = base64ToPublicKey(base64)
    
    fun privateKeyToBase64(privateKey: PrivateKey): String {
        return Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
    }
    
    fun base64ToPrivateKey(base64: String): PrivateKey {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        val keySpec = PKCS8EncodedKeySpec(bytes)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePrivate(keySpec)
    }
    
    fun signatureToBase64(signature: ByteArray): String {
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }
    
    fun base64ToSignature(base64: String): ByteArray {
        return Base64.decode(base64, Base64.NO_WRAP)
    }
    
    // ==================== KEY STORAGE ====================
    
    private fun saveIdentityKeyPair(keyPair: KeyPair) {
        prefs.edit().apply {
            putString("identity_public", publicKeyToBase64(keyPair.public))
            putString("identity_private", privateKeyToBase64(keyPair.private))
        }.apply()
    }
    
    fun getIdentityKeyPair(): KeyPair? {
        val publicBase64 = prefs.getString("identity_public", null) ?: return null
        val privateBase64 = prefs.getString("identity_private", null) ?: return null
        
        return KeyPair(
            base64ToPublicKey(publicBase64),
            base64ToPrivateKey(privateBase64)
        )
    }
    
    fun saveRegistrationId(registrationId: Int) {
        prefs.edit().putInt("registration_id", registrationId).apply()
    }
    
    fun getRegistrationId(): Int {
        var regId = prefs.getInt("registration_id", 0)
        if (regId == 0) {
            regId = generateRegistrationId()
            saveRegistrationId(regId)
        }
        return regId
    }
    
    fun clearAllKeys() {
        prefs.edit().clear().apply()
    }
}

data class SignedPreKey(
    val id: Int,
    val keyPair: KeyPair,
    val signature: ByteArray
)

data class PreKey(
    val id: Int,
    val keyPair: KeyPair
)

data class EncryptedData(
    val iv: ByteArray,
    val ciphertext: ByteArray
)
