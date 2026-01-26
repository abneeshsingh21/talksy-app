package com.jengachat.ui.chat

import android.content.Intent
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.jengachat.data.model.User
import com.jengachat.util.rememberMultiplePermissionsLauncher
import com.jengachat.util.PermissionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(
    onBackClick: () -> Unit,
    onChatCreated: (String) -> Unit,
    onNewGroupClick: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    val users by viewModel.users.collectAsState()
    val matchedContacts by viewModel.matchedContacts.collectAsState()
    val contactsSyncing by viewModel.contactsSyncing.collectAsState()
    val hasContactsPermission = viewModel.hasContactsPermission
    val context = LocalContext.current
    
    // Permission launcher
    val permissionLauncher = rememberMultiplePermissionsLauncher { results ->
        if (results.all { it.value }) {
            viewModel.syncContacts()
        }
    }
    
    // Sync contacts on first load if permission granted
    LaunchedEffect(Unit) {
        if (hasContactsPermission) {
            viewModel.syncContacts()
        }
    }
    
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            viewModel.searchUsers(searchQuery)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Chat") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (contactsSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name or email") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
            }
            
            // New Group Option
            item {
                ListItem(
                    headlineContent = { 
                        Text("New Group", fontWeight = FontWeight.SemiBold) 
                    },
                    supportingContent = {
                        Text("Create a group with your contacts", fontSize = 13.sp)
                    },
                    leadingContent = {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    },
                    modifier = Modifier.clickable(onClick = onNewGroupClick)
                )
            }
            
            // Permission Request Card (if needed)
            if (!hasContactsPermission) {
                item {
                    ContactsPermissionCard(
                        onRequestPermission = {
                            permissionLauncher.launch(
                                arrayOf(android.Manifest.permission.READ_CONTACTS)
                            )
                        }
                    )
                }
            }
            
            // On Talksy Section (Matched Contacts)
            if (hasContactsPermission && matchedContacts.isNotEmpty() && searchQuery.isEmpty()) {
                item {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    SectionHeader(
                        title = "On Talksy",
                        subtitle = "${matchedContacts.size} contacts"
                    )
                }
                
                items(matchedContacts, key = { it.id }) { user ->
                    UserItem(
                        user = user,
                        onClick = {
                            viewModel.createOrGetPrivateChat(user.id) { chatId ->
                                onChatCreated(chatId)
                            }
                        }
                    )
                }
            }
            
            // Search Results Section
            if (searchQuery.length >= 2) {
                item {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    SectionHeader(
                        title = "Search Results",
                        subtitle = if (users.isEmpty()) "No users found" else "${users.size} found"
                    )
                }
                
                items(users, key = { it.id }) { user ->
                    UserItem(
                        user = user,
                        onClick = {
                            viewModel.createOrGetPrivateChat(user.id) { chatId ->
                                onChatCreated(chatId)
                            }
                        }
                    )
                }
                
                if (users.isEmpty()) {
                    item {
                        EmptySearchState()
                    }
                }
            }
            
            // Invite Friends Option
            if (hasContactsPermission && searchQuery.isEmpty()) {
                item {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    InviteFriendsCard(
                        onInvite = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, 
                                    "Hey! Let's chat on Talksy - secure, private messaging. Download now!")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Invite via"))
                        }
                    )
                }
            }
            
            // Empty state when no permission and no search
            if (!hasContactsPermission && searchQuery.isEmpty()) {
                item {
                    EmptyContactsState()
                }
            }
            
            // Bottom padding
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )
        subtitle?.let {
            Text(
                text = it,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ContactsPermissionCard(
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Contacts,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Find Your Contacts",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Allow access to your contacts to easily find friends who are on Talksy",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ContactPhone, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enable Contacts")
            }
        }
    }
}

@Composable
fun InviteFriendsCard(
    onInvite: () -> Unit
) {
    ListItem(
        headlineContent = { 
            Text("Invite Friends", fontWeight = FontWeight.SemiBold) 
        },
        supportingContent = {
            Text("Share Talksy with your contacts", fontSize = 13.sp)
        },
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        },
        modifier = Modifier.clickable(onClick = onInvite)
    )
}

@Composable
fun EmptyContactsState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.PersonSearch,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Search for users",
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Type at least 2 characters to search",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun EmptySearchState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "No users found",
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "Try a different search term",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun UserItem(
    user: User,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    ListItem(
        headlineContent = { 
            Text(
                text = user.displayName,
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (user.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Online",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        text = user.status.ifEmpty { user.email },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        },
        leadingContent = {
            Box {
                AsyncImage(
                    model = user.photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                
                // Online indicator
                if (user.isOnline) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        },
        trailingContent = trailing,
        modifier = Modifier.clickable(onClick = onClick)
    )
}
