package com.jengachat.ui.call

import android.media.RingtoneManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun VideoCallScreen(
    callId: String,
    isIncoming: Boolean = false,
    callerName: String = "",
    callerPhotoUrl: String = "",
    onEndCall: () -> Unit,
    viewModel: CallViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentCall by viewModel.currentCall.collectAsState()
    val callState by viewModel.callState.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isVideoEnabled by viewModel.isVideoEnabled.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()

    var callDuration by remember { mutableIntStateOf(0) }
    var showControls by remember { mutableStateOf(true) }
    var hasAnswered by remember { mutableStateOf(!isIncoming) }

    // Resolve display name from nav args or server data
    val displayName = remember(currentCall, callerName) {
        currentCall?.callerName?.takeIf { it.isNotEmpty() }
            ?: callerName.takeIf { it.isNotEmpty() }
            ?: "Unknown Caller"
    }
    val photoUrl = remember(currentCall, callerPhotoUrl) {
        currentCall?.callerAvatar?.takeIf { it.isNotEmpty() }
            ?: currentCall?.callerPhotoUrl?.takeIf { it.isNotEmpty() }
            ?: callerPhotoUrl.takeIf { it.isNotEmpty() }
    }

    // Ringtone
    val ringtone = remember {
        if (isIncoming) {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                RingtoneManager.getRingtone(context, uri)
            } catch (e: Exception) { null }
        } else null
    }

    LaunchedEffect(callId) { viewModel.observeCall(callId) }

    LaunchedEffect(isIncoming, hasAnswered) {
        if (isIncoming && !hasAnswered) ringtone?.play() else ringtone?.stop()
    }
    DisposableEffect(Unit) { onDispose { ringtone?.stop() } }

    LaunchedEffect(callState) {
        if (callState is CallState.Connected) {
            while (true) { delay(1000); callDuration++ }
        }
    }

    LaunchedEffect(showControls) {
        if (showControls && callState is CallState.Connected && hasAnswered) {
            delay(5000); showControls = false
        }
    }

    LaunchedEffect(callState) {
        if (callState is CallState.Ended) { ringtone?.stop(); delay(2000); onEndCall() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ========== REMOTE VIDEO (full screen) ==========
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A2E)),
            contentAlignment = Alignment.Center
        ) {
            if (callState is CallState.Connected && hasAnswered) {
                // Remote video placeholder — in production this would be WebRTC remote stream
                // For now shows a dark gradient with their avatar centered
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (!photoUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alpha = 0.3f
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(100.dp),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Videocam, null, Modifier.size(48.dp), Color.White.copy(0.5f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Video connecting...", color = Color.White.copy(0.5f), fontSize = 14.sp)
                    }
                }
            } else {
                // Waiting/ringing — show avatar
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PulsingAvatar(
                        photoUrl = photoUrl,
                        isRinging = !hasAnswered && isIncoming
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = displayName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when {
                            !hasAnswered && isIncoming -> "Incoming Video Call..."
                            callState is CallState.Initiating -> "Calling..."
                            callState is CallState.Ringing -> "Ringing..."
                            callState is CallState.Connecting -> "Connecting..."
                            callState is CallState.Ended -> "Call Ended"
                            else -> ""
                        },
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )

                    // Answer/Decline buttons for incoming calls
                    if (!hasAnswered && isIncoming) {
                        Spacer(modifier = Modifier.height(48.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(64.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FloatingActionButton(
                                    onClick = { ringtone?.stop(); viewModel.rejectCall(callId); onEndCall() },
                                    containerColor = Color.Red, contentColor = Color.White,
                                    modifier = Modifier.size(72.dp)
                                ) { Icon(Icons.Default.CallEnd, null, Modifier.size(36.dp)) }
                                Spacer(Modifier.height(8.dp))
                                Text("Decline", color = Color.White.copy(0.7f), fontSize = 14.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FloatingActionButton(
                                    onClick = { ringtone?.stop(); hasAnswered = true; viewModel.answerCall(callId) },
                                    containerColor = Color(0xFF4CAF50), contentColor = Color.White,
                                    modifier = Modifier.size(72.dp)
                                ) { Icon(Icons.Default.Videocam, null, Modifier.size(36.dp)) }
                                Spacer(Modifier.height(8.dp))
                                Text("Answer", color = Color.White.copy(0.7f), fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        // ========== LOCAL CAMERA PREVIEW (Picture-in-Picture) ==========
        if (isVideoEnabled && hasAnswered) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(120.dp, 160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                // Real CameraX preview
                val previewView = remember { PreviewView(context) }
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
                LaunchedEffect(isFrontCamera) {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val cameraSelector = if (isFrontCamera) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                        } catch (e: Exception) {
                            android.util.Log.e("VideoCall", "CameraX bind failed: ${e.message}")
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            }
        }

        // ========== TOP BAR ==========
        if (hasAnswered || !isIncoming) {
            AnimatedVisibility(
                visible = showControls || callState !is CallState.Connected,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = displayName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        if (callState is CallState.Connected) {
                            Text(
                                text = formatDuration(callDuration),
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.switchCamera() }) {
                        Icon(Icons.Default.FlipCameraAndroid, "Switch Camera", tint = Color.White)
                    }
                }
            }
        }

        // ========== BOTTOM CONTROLS ==========
        if (hasAnswered && callState !is CallState.Ended) {
            AnimatedVisibility(
                visible = showControls || callState !is CallState.Connected,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VideoCallControlButton(
                        icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        isActive = isMuted,
                        onClick = { viewModel.toggleMute() }
                    )
                    VideoCallControlButton(
                        icon = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        isActive = !isVideoEnabled,
                        onClick = { viewModel.toggleVideo() }
                    )
                    FloatingActionButton(
                        onClick = { viewModel.endCall(); onEndCall() },
                        containerColor = Color.Red, contentColor = Color.White,
                        modifier = Modifier.size(64.dp)
                    ) { Icon(Icons.Default.CallEnd, null, Modifier.size(32.dp)) }
                    VideoCallControlButton(
                        icon = Icons.Default.FlipCameraAndroid,
                        onClick = { viewModel.switchCamera() }
                    )
                    VideoCallControlButton(
                        icon = Icons.Default.VolumeUp,
                        onClick = { viewModel.toggleSpeaker() }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoCallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (isActive) Color.White else Color.White.copy(alpha = 0.2f),
            contentColor = if (isActive) Color.Black else Color.White
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
    }
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, secs)
    else String.format("%02d:%02d", minutes, secs)
}
