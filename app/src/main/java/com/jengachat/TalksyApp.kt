package com.jengachat

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TalksyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Create notification channels
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Messages channel
            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Chat message notifications"
                enableVibration(true)
                enableLights(true)
            }

            // Calls channel
            val callsChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming call notifications"
                enableVibration(true)
                enableLights(true)
                setSound(null, null) // We'll use custom ringtone
            }

            // Missed calls channel
            val missedCallsChannel = NotificationChannel(
                CHANNEL_MISSED_CALLS,
                "Missed Calls",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Missed call notifications"
            }

            // General channel
            val generalChannel = NotificationChannel(
                CHANNEL_GENERAL,
                "General",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General app notifications"
            }

            notificationManager.createNotificationChannels(
                listOf(messagesChannel, callsChannel, missedCallsChannel, generalChannel)
            )
        }
    }

    companion object {
        const val CHANNEL_MESSAGES = "talksy_messages"
        const val CHANNEL_CALLS = "talksy_calls"
        const val CHANNEL_MISSED_CALLS = "talksy_missed_calls"
        const val CHANNEL_GENERAL = "talksy_general"
    }
}
