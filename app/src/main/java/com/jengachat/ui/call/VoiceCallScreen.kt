package com.jengachat.ui.call

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun VoiceCallScreen(
    callId: String,
    isIncoming: Boolean = false,
    onEndCall: () -> Unit,
    viewModel: CallViewModel = hiltViewModel()
) {
    val currentCall by viewModel.currentCall.collectAsState()
    val callState by viewModel.callState.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
    
    var callDuration by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(callId) {
        if (isIncoming) {
            viewModel.answerCall(callId)
        }
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
                photoUrl = null,
                isRinging = callState is CallState.Ringing || callState is CallState.Initiating
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Caller Name
            Text(
                text = currentCall?.let { "Caller" } ?: "Unknown",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Call Status
            Text(
                text = when (callState) {
                    is CallState.Initiating -> "Calling..."
                    is CallState.Ringing -> "Ringing..."
                    is CallState.Connecting -> "Connecting..."
                    is CallState.Connected -> formatDuration(callDuration)
                    is CallState.OnHold -> "On Hold"
                    is CallState.Reconnecting -> "Reconnecting..."
                    is CallState.Ended -> "Call Ended"
                    else -> ""
                },
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Call Controls
            if (callState !is CallState.Ended) {
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
                        onClick = { /* TODO */ }
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
                // Call Ended - Show return button
                Button(
                    onClick = onEndCall,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Return")
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
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
