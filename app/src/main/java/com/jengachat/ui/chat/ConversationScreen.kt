package com.jengachat.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.jengachat.util.ImageUtils
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.jengachat.data.model.ChatType
import com.jengachat.data.model.Message
import com.jengachat.data.model.MessageStatus
import com.jengachat.data.model.MessageType
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

// Emoji reactions
private val QUICK_REACTIONS = listOf("❤️", "😂", "😮", "😢", "😠", "👍")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationScreen(
    chatId: String,
    onBackClick: () -> Unit,
    onVoiceCallClick: (conversationId: String) -> Unit,
    onVideoCallClick: (conversationId: String) -> Unit,
    onGroupInfoClick: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    val currentChat by viewModel.currentChat.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val currentUserId = viewModel.currentUserId
    
    // Selected message for reactions
    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    var showReactionPicker by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isRecordingVoice by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Contact picker launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let {
            viewModel.sendMessage(chatId, "👤 Shared Contact: $it")
            Toast.makeText(context, "Contact shared!", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Permission launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isRecordingVoice = true
        } else {
            Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Gallery image picker
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val base64 = ImageUtils.uriToBase64(context, it, maxWidth = 800, quality = 80)
                if (!base64.isNullOrEmpty()) {
                    viewModel.sendMediaMessage(chatId, base64, "image")
                }
            }
            Toast.makeText(context, "Uploading image...", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Document picker
    val documentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val base64 = ImageUtils.uriToBase64(context, it, maxWidth = 800, quality = 80)
                if (!base64.isNullOrEmpty()) {
                    viewModel.sendMediaMessage(chatId, base64, "document")
                }
            }
            Toast.makeText(context, "Uploading document...", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Video picker
    val videoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val base64 = ImageUtils.uriToBase64(context, it, maxWidth = 800, quality = 80)
                if (!base64.isNullOrEmpty()) {
                    viewModel.sendMediaMessage(chatId, base64, "video")
                }
            }
            Toast.makeText(context, "Uploading video...", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Camera launcher - takes photo
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            val outputStream = java.io.ByteArrayOutputStream()
            it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream)
            val base64 = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
            viewModel.sendMediaMessage(chatId, base64, "image")
            Toast.makeText(context, "Photo sent!", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    
    LaunchedEffect(chatId) {
        viewModel.loadChat(chatId)
        viewModel.loadMessages(chatId)
    }
    
    // Mark unread messages as read when they change
    LaunchedEffect(messages) {
        val unreadMessageIds = messages
            .filter { it.senderId != currentUserId && !it.readBy.contains(currentUserId) }
            .map { it.id }
        if (unreadMessageIds.isNotEmpty()) {
            viewModel.markAsRead(chatId, unreadMessageIds)
        }
    }
    
    // Real-time typing and recording status emission
    LaunchedEffect(messageText, isRecordingVoice) {
        val isTyping = messageText.isNotEmpty() || isRecordingVoice
        viewModel.setTyping(chatId, isTyping)
    }
    
    // Auto-scroll to bottom when new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    val displayName = if (currentChat?.type == ChatType.GROUP) {
        currentChat?.groupName ?: ""
    } else {
        currentChat?.participantDetails?.entries
            ?.firstOrNull { it.key != currentUserId }
            ?.value?.displayName ?: "Chat"
    }
    
    val isOnline = if (currentChat?.type != ChatType.GROUP) {
        currentChat?.participantDetails?.entries
            ?.firstOrNull { it.key != currentUserId }
            ?.value?.isOnline ?: false
    } else false
    
    Scaffold(
        topBar = {
            // Premium gradient top bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent,
                shadowElevation = 4.dp
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
                ) {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { 
                                    if (currentChat?.type == ChatType.GROUP) {
                                        onGroupInfoClick()
                                    }
                                }
                            ) {
                                Box {
                                    AsyncImage(
                                        model = if (currentChat?.type == ChatType.GROUP) 
                                            currentChat?.groupPhotoUrl 
                                        else 
                                            currentChat?.participantDetails?.entries
                                                ?.firstOrNull { it.key != currentUserId }
                                                ?.value?.photoUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.2f)),
                                        contentScale = ContentScale.Crop
                                    )
                                    
                                    // Online indicator
                                    if (isOnline) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
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
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column {
                                    Text(
                                        text = displayName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 18.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isRecordingVoice) {
                                        Text(
                                            text = "🎙️ recording audio...",
                                            color = Color(0xFF10B981),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else if (currentChat?.typingUsers?.isNotEmpty() == true) {
                                        Text(
                                            text = "typing...",
                                            color = Color(0xFF10B981),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else if (isOnline) {
                                        Text(
                                            text = "online",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { onVoiceCallClick(chatId) }) {
                                Icon(
                                    Icons.Outlined.Call,
                                    contentDescription = "Voice Call",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = { onVideoCallClick(chatId) }) {
                                Icon(
                                    Icons.Outlined.Videocam,
                                    contentDescription = "Video Call",
                                    tint = Color.White
                                )
                            }
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "More",
                                        tint = Color.White
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (currentChat?.type == ChatType.GROUP) "Group Info" else "Contact Info") },
                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            if (currentChat?.type == ChatType.GROUP) {
                                                onGroupInfoClick()
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Mute Notifications") },
                                        leadingIcon = { Icon(Icons.Default.NotificationsOff, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.muteChat(chatId, true)
                                            Toast.makeText(context, "Notifications muted", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Clear Chat") },
                                        leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.deleteChat(chatId)
                                            Toast.makeText(context, "Chat cleared", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Messages List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 8.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Welcome message for empty chats
                    if (messages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Outlined.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "🔐 End-to-End Encrypted",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Messages are secured. Only you and $displayName can read them.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                    
                    items(messages, key = { it.id }) { message ->
                        val isOwn = message.senderId == currentUserId
                        
                        SmartMessageBubble(
                            message = message,
                            isOwn = isOwn,
                            isSelected = selectedMessageId == message.id,
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedMessageId = message.id
                                showReactionPicker = true
                            },
                            onReactionClick = { emoji ->
                                viewModel.addReaction(message.id, emoji)
                                showReactionPicker = false
                                selectedMessageId = null
                            }
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                // Smart Message Input Bar
                SmartMessageInput(
                    messageText = messageText,
                    onMessageChange = { 
                        messageText = it
                        viewModel.setTyping(chatId, it.isNotEmpty())
                    },
                    onSendClick = {
                        if (messageText.isNotBlank()) {
                            viewModel.sendMessage(chatId, messageText.trim())
                            messageText = ""
                            viewModel.setTyping(chatId, false)
                        }
                    },
                    onAttachClick = { showAttachMenu = true },
                    onVoiceClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                            == PackageManager.PERMISSION_GRANTED) {
                            isRecordingVoice = true
                            Toast.makeText(context, "Voice recording started!", Toast.LENGTH_SHORT).show()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onCameraClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
                            == PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(context, "Opening camera...", Toast.LENGTH_SHORT).show()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                )
            }
            
            // Reaction picker overlay
            AnimatedVisibility(
                visible = showReactionPicker,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                ReactionPicker(
                    onReactionSelected = { emoji ->
                        selectedMessageId?.let { msgId ->
                            viewModel.addReaction(msgId, emoji)
                        }
                        showReactionPicker = false
                        selectedMessageId = null
                    },
                    onDismiss = {
                        showReactionPicker = false
                        selectedMessageId = null
                    }
                )
            }
            
            // Attach menu bottom sheet
            if (showAttachMenu) {
                AttachmentMenu(
                    onDismiss = { showAttachMenu = false },
                    onImageClick = {
                        galleryLauncher.launch("image/*")
                        showAttachMenu = false
                    },
                    onCameraClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
                            == PackageManager.PERMISSION_GRANTED) {
                            cameraLauncher.launch(null)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                        showAttachMenu = false
                    },
                    onDocumentClick = {
                        documentLauncher.launch("*/*")
                        showAttachMenu = false
                    },
                    onLocationClick = {
                        // Send a placeholder location for now
                        viewModel.sendLocationMessage(chatId, 0.0, 0.0)
                        Toast.makeText(context, "Location shared!", Toast.LENGTH_SHORT).show()
                        showAttachMenu = false
                    },
                    onContactClick = {
                        contactPickerLauncher.launch(null)
                        showAttachMenu = false
                    }
                )
            }
        }
    }
}

@Composable
fun ReactionPicker(
    onReactionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .shadow(16.dp, RoundedCornerShape(28.dp))
            .padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QUICK_REACTIONS.forEach { emoji ->
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { onReactionSelected(emoji) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = emoji,
                            fontSize = 24.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmartMessageBubble(
    message: Message,
    isOwn: Boolean,
    isSelected: Boolean,
    onLongPress: () -> Unit,
    onReactionClick: (String) -> Unit
) {
    val alignment = if (isOwn) Alignment.End else Alignment.Start
    val bubbleColor = if (isOwn) 
        MaterialTheme.colorScheme.primary 
    else 
        MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isOwn) Color.White else MaterialTheme.colorScheme.onSurface
    
    // Detect URLs in message
    val urlPattern = Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
    val hasUrl = urlPattern.matcher(message.text).find()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) else Modifier
            ),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isOwn) 18.dp else 4.dp,
                        bottomEnd = if (isOwn) 4.dp else 18.dp
                    )
                )
                .background(bubbleColor)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress
                )
                .padding(12.dp)
        ) {
            Column {
                when (message.type) {
                    MessageType.TEXT -> {
                        // Smart text with link detection
                        if (hasUrl) {
                            Column {
                                Text(
                                    text = message.text,
                                    color = textColor,
                                    fontSize = 15.sp
                                )
                                // Link preview card placeholder
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isOwn) 
                                        Color.White.copy(alpha = 0.15f) 
                                    else 
                                        MaterialTheme.colorScheme.surface
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Outlined.Link,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = textColor.copy(alpha = 0.7f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Link",
                                            fontSize = 12.sp,
                                            color = textColor.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = message.text,
                                color = textColor,
                                fontSize = 15.sp
                            )
                        }
                    }
                    MessageType.IMAGE -> {
                        AsyncImage(
                            model = message.mediaUrl,
                            contentDescription = "Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        if (message.text.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = message.text,
                                color = textColor,
                                fontSize = 14.sp
                            )
                        }
                    }
                    MessageType.AUDIO -> {
                        // Voice message UI
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { /* Play voice */ },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (isOwn) Color.White.copy(alpha = 0.2f) 
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = textColor
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            // Waveform placeholder
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp)
                                    .background(
                                        textColor.copy(alpha = 0.2f),
                                        RoundedCornerShape(4.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "0:15",
                                fontSize = 12.sp,
                                color = textColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = message.text,
                            color = textColor,
                            fontSize = 15.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.createdAt?.let { timestamp ->
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
                        } ?: "",
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                    
                    if (isOwn) {
                        Spacer(modifier = Modifier.width(4.dp))
                        // Use MessageStatus for proper status display
                        Icon(
                            imageVector = when (message.status) {
                                MessageStatus.SENDING -> Icons.Default.Schedule  // Clock icon
                                MessageStatus.SENT -> Icons.Default.Done         // Single check
                                MessageStatus.DELIVERED -> Icons.Default.DoneAll // Double check (gray)
                                MessageStatus.READ -> Icons.Default.DoneAll      // Double check (blue)
                                MessageStatus.FAILED -> Icons.Default.ErrorOutline // Error icon
                            },
                            contentDescription = when (message.status) {
                                MessageStatus.SENDING -> "Sending"
                                MessageStatus.SENT -> "Sent"
                                MessageStatus.DELIVERED -> "Delivered"
                                MessageStatus.READ -> "Read"
                                MessageStatus.FAILED -> "Failed"
                            },
                            modifier = Modifier.size(14.dp),
                            tint = when (message.status) {
                                MessageStatus.READ -> Color(0xFF4FC3F7)  // Blue for read
                                MessageStatus.FAILED -> Color(0xFFEF5350) // Red for failed
                                else -> textColor.copy(alpha = 0.7f)
                            }
                        )
                    }
                }
            }
        }
        
        // Display reactions
        if (message.reactions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            
            // Group reactions by emoji and count
            val groupedReactions = message.reactions.values.groupingBy { it }.eachCount()
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                items(groupedReactions.toList()) { (emoji, count) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { onReactionClick(emoji) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = emoji, fontSize = 14.sp)
                            if (count > 1) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = count.toString(),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmartMessageInput(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onCameraClick: () -> Unit
) {
    val hasText = messageText.isNotBlank()
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Attach button
            IconButton(
                onClick = onAttachClick,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.Outlined.AttachFile,
                    contentDescription = "Attach",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Message input field
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageChange,
                placeholder = { Text("Message", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 120.dp),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { /* Emoji picker */ }) {
                            Icon(
                                Icons.Outlined.EmojiEmotions,
                                contentDescription = "Emoji",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!hasText) {
                            IconButton(onClick = onCameraClick) {
                                Icon(
                                    Icons.Outlined.CameraAlt,
                                    contentDescription = "Camera",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Send or Voice button
            AnimatedContent(
                targetState = hasText,
                transitionSpec = {
                    scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut()
                }
            ) { showSend ->
                IconButton(
                    onClick = if (showSend) onSendClick else onVoiceClick,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        if (showSend) Icons.Default.Send else Icons.Default.Mic,
                        contentDescription = if (showSend) "Send" else "Voice",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun AttachmentMenu(
    onDismiss: () -> Unit,
    onImageClick: () -> Unit,
    onCameraClick: () -> Unit,
    onDocumentClick: () -> Unit,
    onLocationClick: () -> Unit,
    onContactClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AttachmentOption(
                        icon = Icons.Outlined.Image,
                        label = "Gallery",
                        color = Color(0xFF4CAF50),
                        onClick = onImageClick
                    )
                    AttachmentOption(
                        icon = Icons.Outlined.CameraAlt,
                        label = "Camera",
                        color = Color(0xFFE91E63),
                        onClick = onCameraClick
                    )
                    AttachmentOption(
                        icon = Icons.Outlined.Description,
                        label = "Document",
                        color = Color(0xFF9C27B0),
                        onClick = onDocumentClick
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AttachmentOption(
                        icon = Icons.Outlined.LocationOn,
                        label = "Location",
                        color = Color(0xFF2196F3),
                        onClick = onLocationClick
                    )
                    AttachmentOption(
                        icon = Icons.Outlined.Person,
                        label = "Contact",
                        color = Color(0xFFFF9800),
                        onClick = onContactClick
                    )
                    Spacer(modifier = Modifier.size(64.dp)) // Placeholder
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun AttachmentOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.15f),
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.padding(16.dp),
                tint = color
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Keep old MessageBubble for backwards compatibility
@Composable
fun MessageBubble(
    message: Message,
    isOwn: Boolean
) {
    SmartMessageBubble(
        message = message,
        isOwn = isOwn,
        isSelected = false,
        onLongPress = {},
        onReactionClick = {}
    )
}
