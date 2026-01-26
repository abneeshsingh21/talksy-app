package com.jengachat.data.model

/**
 * User model representing a chat user
 */
data class User(
    val id: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val status: String = "Hey there! I'm using Talksy",
    val isOnline: Boolean = false,
    val lastSeen: Long? = null,
    val fcmToken: String = "",
    val contacts: List<String> = emptyList(),
    val blockedUsers: List<String> = emptyList(),
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

/**
 * User presence status
 */
enum class UserPresence {
    ONLINE,
    OFFLINE,
    TYPING
}
