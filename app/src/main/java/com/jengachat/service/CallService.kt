package com.jengachat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jengachat.ui.MainActivity
import com.jengachat.R
import com.jengachat.data.model.CallEndReason
import com.jengachat.data.model.CallType
import com.jengachat.data.repository.CallRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CallService : Service() {
    
    @Inject
    lateinit var callRepository: CallRepository
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        private const val TAG = "CallService"
        
        const val CHANNEL_ID = "call_channel"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_START_CALL = "action_start_call"
        const val ACTION_ANSWER_CALL = "action_answer_call"
        const val ACTION_REJECT_CALL = "action_reject_call"
        const val ACTION_END_CALL = "action_end_call"
        const val ACTION_INCOMING_CALL = "action_incoming_call"
        
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_CALL_TYPE = "extra_call_type"
        const val EXTRA_IS_INCOMING = "extra_is_incoming"
        
        fun startCall(
            context: Context,
            callId: String,
            callerName: String,
            callType: CallType
        ) {
            val intent = Intent(context, CallService::class.java).apply {
                action = ACTION_START_CALL
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_CALL_TYPE, callType.name)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun showIncomingCall(
            context: Context,
            callId: String,
            callerName: String,
            callType: CallType
        ) {
            val intent = Intent(context, CallService::class.java).apply {
                action = ACTION_INCOMING_CALL
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_CALL_TYPE, callType.name)
                putExtra(EXTRA_IS_INCOMING, true)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun endCall(context: Context) {
            val intent = Intent(context, CallService::class.java).apply {
                action = ACTION_END_CALL
            }
            context.startService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == null) {
            val notification = createOngoingCallNotification("Call", false)
            startForeground(NOTIFICATION_ID, notification)
            stopSelf()
            return START_NOT_STICKY
        }

        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Call"
        val isVideo = intent.getStringExtra(EXTRA_CALL_TYPE) == CallType.VIDEO.name
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: ""
        val isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, false)
        
        val initialNotification = if (action == ACTION_INCOMING_CALL || isIncoming) {
            createIncomingCallNotification(callId, callerName, isVideo)
        } else {
            createOngoingCallNotification(callerName, isVideo)
        }
        startForeground(NOTIFICATION_ID, initialNotification)

        when (action) {
            ACTION_START_CALL -> handleStartCall(intent)
            ACTION_INCOMING_CALL -> handleIncomingCall(intent)
            ACTION_ANSWER_CALL -> handleAnswerCall(intent)
            ACTION_REJECT_CALL -> handleRejectCall(intent)
            ACTION_END_CALL -> handleEndCall()
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        
        return START_STICKY
    }
    
    private fun handleStartCall(intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Unknown"
        val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: CallType.VOICE.name
        val isVideo = callType == CallType.VIDEO.name
        
        Log.d(TAG, "Starting ${if (isVideo) "video" else "voice"} call with $callerName")
        
        val notification = createOngoingCallNotification(callerName, isVideo)
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun handleIncomingCall(intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Unknown"
        val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: CallType.VOICE.name
        val isVideo = callType == CallType.VIDEO.name
        
        Log.d(TAG, "Incoming ${if (isVideo) "video" else "voice"} call from $callerName")
        
        val notification = createIncomingCallNotification(callId, callerName, isVideo)
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun handleAnswerCall(intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        
        serviceScope.launch {
            callRepository.joinCall(callId)
        }
        
        // Update notification to ongoing call
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Unknown"
        val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: CallType.VOICE.name
        val isVideo = callType == CallType.VIDEO.name
        
        val notification = createOngoingCallNotification(callerName, isVideo)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun handleRejectCall(intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        
        serviceScope.launch {
            callRepository.endCall(callId, CallEndReason.DECLINED)
        }
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun handleEndCall() {
        Log.d(TAG, "Ending call")
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming and ongoing call notifications"
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createIncomingCallNotification(
        callId: String,
        callerName: String,
        isVideo: Boolean
    ): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Answer action
        val answerIntent = Intent(this, CallService::class.java).apply {
            action = ACTION_ANSWER_CALL
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALLER_NAME, callerName)
            putExtra(EXTRA_CALL_TYPE, if (isVideo) CallType.VIDEO.name else CallType.VOICE.name)
        }
        val answerPendingIntent = PendingIntent.getService(
            this, 1, answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Reject action
        val rejectIntent = Intent(this, CallService::class.java).apply {
            action = ACTION_REJECT_CALL
            putExtra(EXTRA_CALL_ID, callId)
        }
        val rejectPendingIntent = PendingIntent.getService(
            this, 2, rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Incoming ${if (isVideo) "Video" else "Voice"} Call")
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(contentPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_call, "Answer", answerPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Reject", rejectPendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun createOngoingCallNotification(
        callerName: String,
        isVideo: Boolean
    ): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // End call action
        val endIntent = Intent(this, CallService::class.java).apply {
            action = ACTION_END_CALL
        }
        val endPendingIntent = PendingIntent.getService(
            this, 3, endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${if (isVideo) "Video" else "Voice"} Call")
            .setContentText("Ongoing call with $callerName")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End", endPendingIntent)
            .setOngoing(true)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
