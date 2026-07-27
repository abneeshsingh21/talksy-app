package com.jengachat.ui.chat

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.jengachat.data.model.Chat
import com.jengachat.data.model.ChatType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChatClick: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onProfileClick: () -> Unit,
    onCallHistoryClick: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val chats by viewModel.chats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    
    // Filter chats based on search query
    val filteredChats = if (searchQuery.isBlank()) {
        chats
    } else {
        chats.filter { chat ->
            val displayName = if (chat.type == ChatType.GROUP) {
                chat.groupName ?: ""
            } else {
                chat.participantDetails.values.firstOrNull()?.displayName ?: ""
            }
            displayName.contains(searchQuery, ignoreCase = true) ||
            chat.lastMessage?.text?.contains(searchQuery, ignoreCase = true) == true
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            topBar = {
                // Premium gradient app bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            )
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Talksy",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    "End-to-End Encrypted",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { showSearch = !showSearch }) {
                                    Icon(
                                        if (showSearch) Icons.Outlined.Close else Icons.Outlined.Search,
                                        contentDescription = "Search",
                                        tint = Color.White
                                    )
                                }
                                IconButton(onClick = onCallHistoryClick) {
                                    Icon(
                                        Icons.Outlined.Call,
                                        contentDescription = "Calls",
                                        tint = Color.White
                                    )
                                }
                                IconButton(onClick = onProfileClick) {
                                    Icon(
                                        Icons.Outlined.Person,
                                        contentDescription = "Profile",
                                        tint = Color.White
                                    )
                                }
                                Box {
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = "More Options",
                                            tint = Color.White
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("New Group") },
                                            leadingIcon = { Icon(Icons.Default.GroupAdd, contentDescription = null) },
                                            onClick = {
                                                showMenu = false
                                                onNewChatClick()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Call History") },
                                            leadingIcon = { Icon(Icons.Default.Call, contentDescription = null) },
                                            onClick = {
                                                showMenu = false
                                                onCallHistoryClick()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("My Profile") },
                                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                            onClick = {
                                                showMenu = false
                                                onProfileClick()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onNewChatClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .shadow(12.dp, CircleShape)
                        .size(64.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "New Chat",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Search bar
                AnimatedVisibility(
                    visible = showSearch,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search chats...") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = "Search")
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Outlined.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )
                }
                
                Box(modifier = Modifier.weight(1f)) {
                    if (isLoading && chats.isEmpty()) {
                        // Shimmer loading effect
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            repeat(5) {
                                ShimmerChatItem()
                            }
                        }
                    } else if (filteredChats.isEmpty()) {
                        // Premium empty state
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                modifier = Modifier.size(120.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(32.dp)
                                        .fillMaxSize(),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(32.dp))
                            
                            Text(
                                text = "Start a Conversation",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = "Your messages are end-to-end encrypted.\nTap the + button to start chatting securely.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(32.dp))
                            
                            FilledTonalButton(
                                onClick = onNewChatClick,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("New Conversation", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            // Pinned chats section
                            val currentUid = viewModel.currentUserId ?: ""
                            val pinnedChats = filteredChats.filter { it.isPinned[currentUid] == true }
                            val regularChats = filteredChats.filter { it.isPinned[currentUid] != true }
                            
                            if (pinnedChats.isNotEmpty()) {
                                item {
                                    SectionHeader("Pinned")
                                }
                                items(pinnedChats, key = { it.id }) { chat ->
                                    PremiumChatItem(
                                        chat = chat,
                                        currentUserId = viewModel.currentUserId ?: "",
                                        onClick = { onChatClick(chat.id) },
                                        isPinned = true
                                    )
                                }
                            }
                            
                            if (regularChats.isNotEmpty()) {
                                if (pinnedChats.isNotEmpty()) {
                                    item {
                                        SectionHeader("All Chats")
                                    }
                                }
                                items(regularChats, key = { it.id }) { chat ->
                                    PremiumChatItem(
                                        chat = chat,
                                        currentUserId = viewModel.currentUserId ?: "",
                                        onClick = { onChatClick(chat.id) },
                                        isPinned = false
                                    )
                                }
                            }
                            
                            // Bottom padding for FAB
                            item {
                                Spacer(modifier = Modifier.height(88.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
private fun ShimmerChatItem() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            )
        }
    }
}

@Composable
fun PremiumChatItem(
    chat: Chat,
    currentUserId: String,
    onClick: () -> Unit,
    isPinned: Boolean = false
) {
    val displayName = if (chat.type == ChatType.GROUP) {
        chat.groupName
    } else {
        chat.participantDetails.entries
            .firstOrNull { it.key != currentUserId }
            ?.value?.displayName ?: "Unknown"
    }
    
    val photoUrl = if (chat.type == ChatType.GROUP) {
        chat.groupPhotoUrl
    } else {
        chat.participantDetails.entries
            .firstOrNull { it.key != currentUserId }
            ?.value?.photoUrl ?: ""
    }
    
    val isOnline = if (chat.type != ChatType.GROUP) {
        chat.participantDetails.entries
            .firstOrNull { it.key != currentUserId }
            ?.value?.isOnline ?: false
    } else false
    
    val unreadCount = chat.unreadCount[currentUserId] ?: 0
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (unreadCount > 0) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            else 
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (unreadCount > 0) 2.dp else 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with online indicator
            Box {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                
                // Online indicator
                if (isOnline) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                    }
                }
                
                // Group indicator
                if (chat.type == ChatType.GROUP) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                            .align(Alignment.BottomEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = displayName,
                            fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        if (isPinned) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    chat.lastMessage?.timestamp?.let { timestamp ->
                        Text(
                            text = formatChatTime(Date(timestamp)),
                            fontSize = 12.sp,
                            fontWeight = if (unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (unreadCount > 0) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(5.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Typing indicator or last message
                        if (chat.typingUsers.isNotEmpty()) {
                            Text(
                                text = "typing...",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        } else {
                            // Read receipt icon - simplified without isRead field
                            if (chat.lastMessage?.senderId == currentUserId) {
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            
                            Text(
                                text = chat.lastMessage?.text ?: "No messages yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    // Unread badge
                    AnimatedVisibility(
                        visible = unreadCount > 0,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Keep old ChatItem for backwards compatibility
@Composable
fun ChatItem(
    chat: Chat,
    currentUserId: String,
    onClick: () -> Unit
) {
    PremiumChatItem(chat = chat, currentUserId = currentUserId, onClick = onClick)
}

private fun formatChatTime(date: Date): String {
    val now = Calendar.getInstance()
    val messageTime = Calendar.getInstance().apply { time = date }
    
    return when {
        now.get(Calendar.DATE) == messageTime.get(Calendar.DATE) -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        }
        now.get(Calendar.DATE) - messageTime.get(Calendar.DATE) == 1 -> {
            "Yesterday"
        }
        now.get(Calendar.WEEK_OF_YEAR) == messageTime.get(Calendar.WEEK_OF_YEAR) -> {
            SimpleDateFormat("EEE", Locale.getDefault()).format(date)
        }
        else -> {
            SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(date)
        }
    }
}
