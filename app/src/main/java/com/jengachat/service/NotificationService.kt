package com.jengachat.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jengachat.TalksyApp
import com.jengachat.R
import com.jengachat.data.remote.CallSignalEvent
import com.jengachat.data.remote.WebSocketClient
import com.jengachat.data.remote.WebSocketMessage
import com.jengachat.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Background service for handling WebSocket notifications
 * Replaces Firebase Cloud Messaging with our custom WebSocket server
 */
@AndroidEntryPoint
class NotificationService : Service() {
    
    @Inject
    lateinit var webSocketClient: WebSocketClient
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        private const val TAG = "NotificationService"
        const val NOTIFICATION_ID = 1001
        const val TYPE_MESSAGE = "message"
        const val TYPE_CALL = "call"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Notification service created")
        startForegroundNotification()
        observeWebSocket()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
    
    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "service_channel",
                "Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(this, "service_channel")
            .setContentTitle("Talksy")
            .setContentText("Connected and receiving messages")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun observeWebSocket() {
        serviceScope.launch {
            webSocketClient.incomingMessages
                .catch { e -> Log.e(TAG, "WebSocket error", e) }
                .collect { message ->
                    handleWebSocketMessage(message)
                }
        }
        
        // Also observe call signals for incoming call notifications
        serviceScope.launch {
            webSocketClient.callSignals
                .catch { e -> Log.e(TAG, "Call signal error", e) }
                .collect { signal ->
                    if (signal.signalType == "offer") {
                        handleIncomingCall(signal)
                    }
                }
        }
    }
    
    private fun handleWebSocketMessage(message: WebSocketMessage) {
        // Handle incoming message notification
        handleNewMessage(message)
    }
    
    private fun handleNewMessage(message: WebSocketMessage) {
        val chatId = message.conversationId
        val senderName = message.senderDisplayName ?: message.senderUsername ?: "Someone"
        val messageText = message.encryptedContent
        
        showMessageNotification(chatId, senderName, messageText)
    }
    
    private fun handleIncomingCall(signal: CallSignalEvent) {
        val callId = signal.callId
        val callerName = "Incoming Call" // Would need to fetch caller info
        val isVideo = signal.encryptedPayload.contains("video", ignoreCase = true)
        
        showCallNotification(callId, callerName, isVideo)
    }
    
    private fun showMessageNotification(chatId: String, senderName: String, message: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("chatId", chatId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, chatId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, TalksyApp.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(senderName)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(chatId.hashCode(), notification)
    }
    
    private fun showCallNotification(callId: String, callerName: String, isVideo: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("callId", callId)
            putExtra("isIncomingCall", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, callId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val callTypeText = if (isVideo) "Video call" else "Voice call"
        
        val notification = NotificationCompat.Builder(this, TalksyApp.CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(callerName)
            .setContentText("Incoming $callTypeText")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(callId.hashCode(), notification)
    }
}
