package com.jengachat.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

data class GroupParticipant(
    val id: String,
    val name: String,
    val photoUrl: String? = null,
    val isMuted: Boolean = false,
    val isSpeaking: Boolean = false,
    val hasVideo: Boolean = true
)

@Composable
fun GroupCallScreen(
    callId: String,
    isIncoming: Boolean = false,
    onEndCall: () -> Unit,
    viewModel: CallViewModel = hiltViewModel()
) {
    val currentCall by viewModel.currentCall.collectAsState()
    val callState by viewModel.callState.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isVideoEnabled by viewModel.isVideoEnabled.collectAsState()
    
    var callDuration by remember { mutableIntStateOf(0) }
    
    // Mock participants for demo
    val participants = remember {
        listOf(
            GroupParticipant("1", "Alice", isSpeaking = true),
            GroupParticipant("2", "Bob", isMuted = true),
            GroupParticipant("3", "Charlie"),
            GroupParticipant("4", "Diana", hasVideo = false)
        )
    }
    
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
            .background(Color(0xFF0D1B2A))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = currentCall?.let { "Group Call" } ?: "Group Call",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    
                    Text(
                        text = when (callState) {
                            is CallState.Connected -> formatDuration(callDuration)
                            is CallState.Connecting -> "Connecting..."
                            is CallState.Ringing -> "Ringing..."
                            else -> ""
                        },
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${participants.size}",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            // Participants Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(participants, key = { it.id }) { participant ->
                    ParticipantTile(participant = participant)
                }
            }
            
            // Bottom Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute Button
                GroupCallControlButton(
                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (isMuted) "Unmute" else "Mute",
                    isActive = isMuted,
                    onClick = { viewModel.toggleMute() }
                )
                
                // Video Toggle
                GroupCallControlButton(
                    icon = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    label = if (isVideoEnabled) "Stop Video" else "Start Video",
                    isActive = !isVideoEnabled,
                    onClick = { viewModel.toggleVideo() }
                )
                
                // End Call
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FloatingActionButton(
                        onClick = {
                            viewModel.endCall()
                            onEndCall()
                        },
                        containerColor = Color.Red,
                        contentColor = Color.White,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Leave",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                
                // Switch Camera
                GroupCallControlButton(
                    icon = Icons.Default.FlipCameraAndroid,
                    label = "Flip",
                    onClick = { viewModel.switchCamera() }
                )
                
                // More Options
                GroupCallControlButton(
                    icon = Icons.Default.MoreVert,
                    label = "More",
                    onClick = { /* TODO */ }
                )
            }
        }
    }
}

@Composable
private fun ParticipantTile(
    participant: GroupParticipant
) {
    Box(
        modifier = Modifier
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (participant.isSpeaking) 
                    Color(0xFF2E7D32).copy(alpha = 0.3f) 
                else 
                    Color(0xFF1A2A3A)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (participant.hasVideo) {
            // Video placeholder
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (participant.photoUrl != null) {
                    AsyncImage(
                        model = participant.photoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White.copy(alpha = 0.3f)
                    )
                }
            }
        } else {
            // No video - show avatar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = participant.name.first().toString(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
        
        // Name overlay at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = participant.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                if (participant.isMuted) {
                    Icon(
                        Icons.Default.MicOff,
                        contentDescription = "Muted",
                        modifier = Modifier.size(14.dp),
                        tint = Color.Red
                    )
                }
            }
        }
        
        // Speaking indicator
        if (participant.isSpeaking) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50))
            )
        }
    }
}

@Composable
private fun GroupCallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
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
                contentDescription = label,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
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
