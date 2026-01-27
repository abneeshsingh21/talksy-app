package com.jengachat.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.jengachat.data.model.CallType
import com.jengachat.service.IncomingCallHandler
import com.jengachat.ui.auth.AuthViewModel
import com.jengachat.ui.navigation.AppNavigation
import com.jengachat.ui.navigation.Screen
import com.jengachat.ui.splash.AnimatedSplashScreen
import com.jengachat.ui.theme.TalksyTheme
import com.jengachat.util.PermissionManager
import com.jengachat.util.rememberMultiplePermissionsLauncher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var incomingCallHandler: IncomingCallHandler
    
    // Store incoming call info from intent
    private var incomingCallInfo: IncomingCallInfo? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        
        // Check for incoming call from notification/FCM
        handleIncomingCallIntent(intent)
        
        setContent {
            TalksyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        incomingCallInfo = incomingCallInfo,
                        onIncomingCallHandled = { incomingCallInfo = null },
                        startIncomingCallHandler = { incomingCallHandler.startObserving() }
                    )
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle incoming call from notification when app is already running
        handleIncomingCallIntent(intent)
    }
    
    private fun handleIncomingCallIntent(intent: Intent?) {
        // Check if this is an incoming call notification
        val isIncomingCall = intent?.getBooleanExtra(IncomingCallHandler.EXTRA_INCOMING_CALL, false) ?: false
        
        if (isIncomingCall) {
            val callId = intent?.getStringExtra(IncomingCallHandler.EXTRA_CALL_ID) ?: return
            val callType = intent.getStringExtra(IncomingCallHandler.EXTRA_CALL_TYPE)
            val callerName = intent.getStringExtra(IncomingCallHandler.EXTRA_CALLER_NAME)
            
            Log.d("MainActivity", "📞 Incoming call intent: $callId from $callerName ($callType)")
            
            incomingCallInfo = IncomingCallInfo(
                callId = callId,
                callType = if (callType == "VIDEO") CallType.VIDEO else CallType.VOICE,
                callerName = callerName ?: "Unknown"
            )
            
            // Cancel the notification since we're handling the call
            incomingCallHandler.cancelIncomingCallNotification()
        }
        
        // Check for decline action
        val isDecline = intent?.getBooleanExtra("declineCall", false) ?: false
        if (isDecline) {
            val callId = intent?.getStringExtra(IncomingCallHandler.EXTRA_CALL_ID)
            Log.d("MainActivity", "📞 Declining call: $callId")
            // The CallViewModel will handle the actual rejection
            incomingCallHandler.cancelIncomingCallNotification()
        }
    }
}

data class IncomingCallInfo(
    val callId: String,
    val callType: CallType,
    val callerName: String
)

@Composable
fun MainScreen(
    incomingCallInfo: IncomingCallInfo? = null,
    onIncomingCallHandled: () -> Unit = {},
    startIncomingCallHandler: () -> Unit = {}
) {
    var showSplash by remember { mutableStateOf(true) }
    val context = LocalContext.current
    
    // Check if we need to show permission request
    var showPermissionRequest by remember { 
        mutableStateOf(false) 
    }
    
    if (showSplash) {
        AnimatedSplashScreen(
            onSplashComplete = { showSplash = false }
        )
    } else {
        val authViewModel: AuthViewModel = hiltViewModel()
        val isLoggedIn = authViewModel.isLoggedIn
        
        // Start incoming call handler when logged in
        LaunchedEffect(isLoggedIn) {
            if (isLoggedIn) {
                startIncomingCallHandler()
            }
        }
        
        // After login, check if we should show permission request
        LaunchedEffect(isLoggedIn) {
            if (isLoggedIn && 
                !PermissionManager.hasPermissionRequestBeenShown(context) &&
                PermissionManager.getMissingPermissions(context).isNotEmpty()
            ) {
                showPermissionRequest = true
            }
        }
        
        if (showPermissionRequest && isLoggedIn) {
            PermissionRequestScreen(
                onComplete = {
                    PermissionManager.markPermissionRequestShown(context)
                    showPermissionRequest = false
                },
                onSkip = {
                    PermissionManager.markPermissionRequestShown(context)
                    showPermissionRequest = false
                }
            )
        } else {
            val navController = rememberNavController()
            
            // Handle incoming call navigation
            LaunchedEffect(incomingCallInfo) {
                incomingCallInfo?.let { callInfo ->
                    Log.d("MainScreen", "📞 Navigating to incoming call: ${callInfo.callId}")
                    
                    val route = when (callInfo.callType) {
                        CallType.VIDEO -> Screen.VideoCall.createRoute(callInfo.callId, isIncoming = true)
                        CallType.VOICE -> Screen.VoiceCall.createRoute(callInfo.callId, isIncoming = true)
                    }
                    
                    navController.navigate(route) {
                        // Don't pop the backstack so user can return to chat
                        launchSingleTop = true
                    }
                    
                    onIncomingCallHandled()
                }
            }
            
            AppNavigation(
                navController = navController,
                isLoggedIn = isLoggedIn
            )
        }
    }
}

@Composable
fun PermissionRequestScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    var allGranted by remember { mutableStateOf(false) }
    var permissionResults by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    
    val permissionLauncher = rememberMultiplePermissionsLauncher { results ->
        permissionResults = results
        allGranted = results.all { it.value }
        if (allGranted) {
            onComplete()
        }
    }
    
    // Request permissions on first composition
    LaunchedEffect(Unit) {
        val missingPermissions = PermissionManager.getMissingPermissions(context)
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            onComplete()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header Icon
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(100.dp)
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxSize(),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "App Permissions",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Talksy needs a few permissions to work properly. Your privacy is our priority - all data stays on your device.",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Permission Cards
            PermissionItemCard(
                icon = Icons.Default.Contacts,
                title = "Contacts",
                description = "Find friends on Talksy",
                isGranted = permissionResults[android.Manifest.permission.READ_CONTACTS] ?: 
                    PermissionManager.hasContactsPermission(context)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            PermissionItemCard(
                icon = Icons.Default.Camera,
                title = "Camera",
                description = "Take photos and video calls",
                isGranted = permissionResults[android.Manifest.permission.CAMERA] ?: 
                    PermissionManager.hasCameraPermission(context)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            PermissionItemCard(
                icon = Icons.Default.Mic,
                title = "Microphone",
                description = "Voice messages and calls",
                isGranted = permissionResults[android.Manifest.permission.RECORD_AUDIO] ?: 
                    PermissionManager.hasMicrophonePermission(context)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            PermissionItemCard(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                description = "Get notified of new messages",
                isGranted = PermissionManager.hasNotificationPermission(context)
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Action Buttons
            Button(
                onClick = {
                    val missingPermissions = PermissionManager.getMissingPermissions(context)
                    if (missingPermissions.isNotEmpty()) {
                        permissionLauncher.launch(missingPermissions.toTypedArray())
                    } else {
                        onComplete()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (allGranted) "Continue" else "Grant Permissions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Maybe Later",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PermissionItemCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isGranted) 
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else 
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = if (isGranted) 
                        MaterialTheme.colorScheme.primary
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            AnimatedVisibility(
                visible = isGranted,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
