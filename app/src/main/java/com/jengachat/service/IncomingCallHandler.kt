package com.jengachat.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jengachat.R
import com.jengachat.data.model.Call
import com.jengachat.data.model.CallType
import com.jengachat.data.repository.CallRepository
import com.jengachat.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles incoming call detection and notification display
 * Observes incoming calls from WebSocket and shows full-screen call notification
 */
@Singleton
class IncomingCallHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callRepository: CallRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isObserving = false
    
    companion object {
        private const val TAG = "IncomingCallHandler"
        private const val CHANNEL_CALLS = "talksy_calls"
        const val CALL_NOTIFICATION_ID = 9999
        
        // Intent extras
        const val EXTRA_INCOMING_CALL = "incomingCall"
        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_CALL_TYPE = "callType"
        const val EXTRA_CALLER_ID = "callerId"
        const val EXTRA_CALLER_NAME = "callerName"
        const val EXTRA_CALLER_AVATAR = "callerAvatar"
    }
    
    init {
        createNotificationChannel()
    }
    
    /**
     * Start observing incoming calls from WebSocket
     * Should be called when user logs in
     */
    fun startObserving() {
        if (isObserving) {
            Log.d(TAG, "Already observing incoming calls")
            return
        }
        
        isObserving = true
        Log.d(TAG, "📞 Started observing incoming calls")
        
        scope.launch {
            callRepository.observeIncomingCalls()
                .filterNotNull()
                .collect { call ->
                    Log.d(TAG, "📞 Incoming call detected: ${call.callerName} (${call.type.name})")
                    showIncomingCallNotification(call)
                }
        }
    }
    
    /**
     * Show full-screen incoming call notification
     */
    private fun showIncomingCallNotification(call: Call) {
        val callTypeText = if (call.type == CallType.VIDEO) "Video Call" else "Voice Call"
        
        // Create intent for incoming call screen
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_INCOMING_CALL, true)
            putExtra(EXTRA_CALL_ID, call.id)
            putExtra(EXTRA_CALL_TYPE, call.type.name)
            putExtra(EXTRA_CALLER_ID, call.callerId)
            putExtra(EXTRA_CALLER_NAME, call.callerName)
            putExtra(EXTRA_CALLER_AVATAR, call.callerAvatar ?: call.callerPhotoUrl)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            call.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Full screen intent for when screen is off
        val fullScreenIntent = PendingIntent.getActivity(
            context,
            call.id.hashCode() + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Decline action
        val declineIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("declineCall", true)
            putExtra(EXTRA_CALL_ID, call.id)
        }
        val declinePendingIntent = PendingIntent.getActivity(
            context,
            call.id.hashCode() + 2,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Use ringtone sound
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Incoming $callTypeText")
            .setContentText("${call.callerName} is calling...")
            .setAutoCancel(false)
            .setOngoing(true)
            .setSound(ringtoneUri)
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .addAction(R.drawable.ic_call_end, "Decline", declinePendingIntent)
            .addAction(R.drawable.ic_call, "Answer", pendingIntent)
            .setTimeoutAfter(45000) // Auto-dismiss after 45 seconds
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(CALL_NOTIFICATION_ID, notification)
        
        Log.d(TAG, "📞 Showing incoming call notification for ${call.callerName}")
    }
    
    /**
     * Cancel the incoming call notification
     */
    fun cancelIncomingCallNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(CALL_NOTIFICATION_ID)
    }
    
    /**
     * Create notification channel for calls (Android 8+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_CALLS,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming voice and video calls"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
