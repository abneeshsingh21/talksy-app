package com.jengachat.util

/**
 * App-wide constants
 */
object Constants {
    // Firebase Collections
    const val USERS_COLLECTION = "users"
    const val CHATS_COLLECTION = "chats"
    const val MESSAGES_COLLECTION = "messages"
    const val CALLS_COLLECTION = "calls"
    
    // Realtime Database Paths
    const val SIGNALING_PATH = "signaling"
    const val PRESENCE_PATH = "presence"
    
    // Storage Paths
    const val PROFILE_PHOTOS_PATH = "profile_photos"
    const val GROUP_PHOTOS_PATH = "group_photos"
    const val CHAT_MEDIA_PATH = "chat_media"
    
    // Notification
    const val NOTIFICATION_CHANNEL_MESSAGES = "messages"
    const val NOTIFICATION_CHANNEL_CALLS = "calls"
    const val CALL_CHANNEL_ID = "calls"
    const val MESSAGE_CHANNEL_ID = "messages"
    const val MESSAGE_GROUP_KEY = "message_group"
    
    // Call
    const val MAX_GROUP_CALL_PARTICIPANTS = 8
    const val CALL_TIMEOUT_SECONDS = 60
    
    // DataStore Keys
    const val PREF_THEME = "theme"
    const val PREF_NOTIFICATIONS_ENABLED = "notifications_enabled"
    const val PREF_BIOMETRIC_ENABLED = "biometric_enabled"
    
    // Intent Extras
    const val EXTRA_CHAT_ID = "chat_id"
    const val EXTRA_CALL_ID = "call_id"
    const val EXTRA_USER_ID = "user_id"
    const val EXTRA_CALL_TYPE = "call_type"
    const val EXTRA_IS_INCOMING = "is_incoming"
}
