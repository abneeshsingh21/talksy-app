package com.jengachat.ui.call

import android.media.RingtoneManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun VoiceCallScreen(
    callId: String,
    isIncoming: Boolean = false,
    callerName: String = "",
    callerPhotoUrl: String = "",
    onEndCall: () -> Unit,
    viewModel: CallViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val currentCall by viewModel.currentCall.collectAsState()
    val callState by viewModel.callState.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()

    var callDuration by remember { mutableIntStateOf(0) }
    var hasAnswered by remember { mutableStateOf(!isIncoming) }
    var showKeypad by remember { mutableStateOf(false) }
    var keypadInput by remember { mutableStateOf("") }

    // Resolve display name: nav arg → server call data → fallback
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

    // Ringtone for incoming calls
    val ringtone = remember {
        if (isIncoming) {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                RingtoneManager.getRingtone(context, uri)
            } catch (e: Exception) { null }
        } else null
    }

    LaunchedEffect(callId) {
        viewModel.observeCall(callId)
    }

    // Play/stop ringtone
    LaunchedEffect(isIncoming, hasAnswered) {
        if (isIncoming && !hasAnswered) {
            ringtone?.play()
        } else {
            ringtone?.stop()
        }
    }
    DisposableEffect(Unit) {
        onDispose { ringtone?.stop() }
    }

    // Timer for call duration
    LaunchedEffect(callState) {
        if (callState is CallState.Connected) {
            while (true) {
                delay(1000)
                callDuration++
            }
        }
    }

    // Handle call ended
    LaunchedEffect(callState) {
        if (callState is CallState.Ended) {
            ringtone?.stop()
            delay(2000)
            onEndCall()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A237E),
                        Color(0xFF0D1B2A),
                        Color(0xFF000000)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // Caller Avatar with pulsing animation
            PulsingAvatar(
                photoUrl = photoUrl,
                isRinging = !hasAnswered && isIncoming
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Caller Name
            Text(
                text = displayName,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Call Status
            Text(
                text = when {
                    !hasAnswered && isIncoming -> "Incoming Voice Call..."
                    callState is CallState.Initiating -> "Calling..."
                    callState is CallState.Ringing -> "Ringing..."
                    callState is CallState.Connecting -> "Connecting..."
                    callState is CallState.Connected -> formatDuration(callDuration)
                    callState is CallState.OnHold -> "On Hold"
                    callState is CallState.Reconnecting -> "Reconnecting..."
                    callState is CallState.Ended -> "Call Ended"
                    else -> ""
                },
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Show Answer/Decline buttons for incoming call that hasn't been answered
            if (!hasAnswered && isIncoming) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Decline Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FloatingActionButton(
                            onClick = {
                                ringtone?.stop()
                                viewModel.rejectCall(callId)
                                onEndCall()
                            },
                            containerColor = Color.Red,
                            contentColor = Color.White,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                Icons.Default.CallEnd,
                                contentDescription = "Decline",
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Decline",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }

                    // Answer Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FloatingActionButton(
                            onClick = {
                                ringtone?.stop()
                                hasAnswered = true
                                viewModel.answerCall(callId)
                            },
                            containerColor = Color(0xFF4CAF50),
                            contentColor = Color.White,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                Icons.Default.Call,
                                contentDescription = "Answer",
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Answer",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
            // Call Controls (after answering or for outgoing calls)
            else if (callState !is CallState.Ended) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CallControlButton(
                        icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (isMuted) "Unmute" else "Mute",
                        isActive = isMuted,
                        onClick = { viewModel.toggleMute() }
                    )

                    CallControlButton(
                        icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        label = "Speaker",
                        isActive = isSpeakerOn,
                        onClick = { viewModel.toggleSpeaker() }
                    )

                    CallControlButton(
                        icon = Icons.Default.Dialpad,
                        label = "Keypad",
                        onClick = { showKeypad = true }
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                // End Call Button
                FloatingActionButton(
                    onClick = {
                        viewModel.endCall()
                        onEndCall()
                    },
                    containerColor = Color.Red,
                    contentColor = Color.White,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        modifier = Modifier.size(36.dp)
                    )
                }
            } else {
                Text(
                    text = "Call ended",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }

        // Keypad Dialog
        if (showKeypad) {
            Dialog(onDismissRequest = { showKeypad = false }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E1E2E)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = keypadInput,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Light,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            textAlign = TextAlign.Center
                        )

                        val keys = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf("*", "0", "#")
                        )
                        keys.forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                row.forEach { key ->
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White.copy(alpha = 0.15f),
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clickable { keypadInput += key }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = key,
                                                color = Color.White,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { showKeypad = false }) {
                                Text("Close", color = Color.White.copy(alpha = 0.7f))
                            }
                            if (keypadInput.isNotEmpty()) {
                                IconButton(onClick = {
                                    if (keypadInput.isNotEmpty()) {
                                        keypadInput = keypadInput.dropLast(1)
                                    }
                                }) {
                                    Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PulsingAvatar(
    photoUrl: String?,
    isRinging: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRinging) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isRinging) 0.6f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer pulsing ring
        if (isRinging) {
            Box(
                modifier = Modifier
                    .size((120 * scale).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = alpha * 0.3f))
            )

            Box(
                modifier = Modifier
                    .size((140 * scale).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = alpha * 0.15f))
            )
        }

        // Avatar
        Surface(
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun CallControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (isActive) Color.White else Color.White.copy(alpha = 0.2f),
                contentColor = if (isActive) Color.Black else Color.White
            )
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}
