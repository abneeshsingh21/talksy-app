package com.jengachat.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.jengachat.data.model.Chat
import com.jengachat.data.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    chatId: String,
    onBackClick: () -> Unit,
    onLeaveGroup: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val chat by viewModel.currentChat.collectAsState()
    val groupMembers by viewModel.groupMembers.collectAsState()
    var showLeaveDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(chatId) {
        viewModel.loadChat(chatId)
        viewModel.loadGroupMembers(chatId)
    }
    
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Leave Group") },
            text = { Text("Are you sure you want to leave this group?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.leaveGroup(chatId)
                        showLeaveDialog = false
                        onLeaveGroup()
                    }
                ) {
                    Text("Leave", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group Info") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Group Header
            item {
                Spacer(modifier = Modifier.height(24.dp))
                
                AsyncImage(
                    model = chat?.groupPhotoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = chat?.groupName ?: "Group",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "${groupMembers.size} members",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Divider()
            }
            
            // Action Buttons
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ActionButton(
                        icon = Icons.Default.Call,
                        label = "Voice Call",
                        onClick = { /* TODO */ }
                    )
                    ActionButton(
                        icon = Icons.Default.Videocam,
                        label = "Video Call",
                        onClick = { /* TODO */ }
                    )
                    ActionButton(
                        icon = Icons.Default.Notifications,
                        label = "Mute",
                        onClick = { /* TODO */ }
                    )
                }
                
                Divider()
            }
            
            // Members Section
            item {
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Members (${groupMembers.size})",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }
            
            items(groupMembers, key = { it.id }) { member ->
                MemberItem(member = member, isAdmin = chat?.adminIds?.contains(member.id) == true)
            }
            
            // Leave Group
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Leave Group",
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.ExitToApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(icon, contentDescription = label)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MemberItem(
    member: User,
    isAdmin: Boolean
) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.displayName,
                    fontWeight = FontWeight.Medium
                )
                if (isAdmin) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Admin",
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        },
        supportingContent = {
            Text(
                text = member.status.ifEmpty { "Available" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        },
        leadingContent = {
            AsyncImage(
                model = member.photoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    )
}
