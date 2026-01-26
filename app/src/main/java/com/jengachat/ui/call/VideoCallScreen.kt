package com.jengachat.ui.call

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun VideoCallScreen(
    callId: String,
    isIncoming: Boolean = false,
    onEndCall: () -> Unit,
    viewModel: CallViewModel = hiltViewModel()
) {
    val currentCall by viewModel.currentCall.collectAsState()
    val callState by viewModel.callState.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isVideoEnabled by viewModel.isVideoEnabled.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    
    var callDuration by remember { mutableIntStateOf(0) }
    var showControls by remember { mutableStateOf(true) }
    
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
    
    // Auto-hide controls
    LaunchedEffect(showControls) {
        if (showControls && callState is CallState.Connected) {
            delay(5000)
            showControls = false
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Remote Video (full screen placeholder)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A2E)),
            contentAlignment = Alignment.Center
        ) {
            if (callState is CallState.Connected) {
                // Placeholder for remote video
                Icon(
                    Icons.Default.Videocam,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = Color.White.copy(alpha = 0.3f)
                )
            } else {
                // Show avatar while connecting
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PulsingAvatar(
                        photoUrl = null,
                        isRinging = callState is CallState.Ringing || callState is CallState.Initiating
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "Caller",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = when (callState) {
                            is CallState.Initiating -> "Calling..."
                            is CallState.Ringing -> "Ringing..."
                            is CallState.Connecting -> "Connecting..."
                            else -> ""
                        },
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        // Local Video Preview (picture-in-picture)
        if (isVideoEnabled && callState is CallState.Connected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(120.dp, 160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2A2A3E)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White.copy(alpha = 0.5f)
                )
            }
        }
        
        // Top Bar
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
                        text = "Caller",
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
                    Icon(
                        Icons.Default.FlipCameraAndroid,
                        contentDescription = "Switch Camera",
                        tint = Color.White
                    )
                }
            }
        }
        
        // Bottom Controls
        AnimatedVisibility(
            visible = showControls || callState !is CallState.Connected,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute Button
                VideoCallControlButton(
                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    isActive = isMuted,
                    onClick = { viewModel.toggleMute() }
                )
                
                // Video Toggle
                VideoCallControlButton(
                    icon = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    isActive = !isVideoEnabled,
                    onClick = { viewModel.toggleVideo() }
                )
                
                // End Call
                FloatingActionButton(
                    onClick = {
                        viewModel.endCall()
                        onEndCall()
                    },
                    containerColor = Color.Red,
                    contentColor = Color.White,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // Switch Camera
                VideoCallControlButton(
                    icon = Icons.Default.FlipCameraAndroid,
                    onClick = { viewModel.switchCamera() }
                )
                
                // Speaker
                VideoCallControlButton(
                    icon = Icons.Default.VolumeUp,
                    onClick = { viewModel.toggleSpeaker() }
                )
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
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
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
