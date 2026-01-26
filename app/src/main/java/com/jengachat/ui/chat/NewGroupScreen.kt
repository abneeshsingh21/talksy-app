package com.jengachat.ui.chat

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.jengachat.data.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGroupScreen(
    onBackClick: () -> Unit,
    onGroupCreated: (String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    var groupName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    val selectedUsers = remember { mutableStateListOf<User>() }
    val users by viewModel.users.collectAsState()
    var isCreating by remember { mutableStateOf(false) }
    
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            viewModel.searchUsers(searchQuery)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Group") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedUsers.isNotEmpty() && groupName.isNotBlank()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!isCreating) {
                            isCreating = true
                            viewModel.createGroupChat(
                                name = groupName,
                                memberIds = selectedUsers.map { it.id }
                            ) { chatId ->
                                onGroupCreated(chatId)
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Check, contentDescription = null) },
                    text = { Text("Create") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Group Name Input
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Group Name") },
                leadingIcon = { Icon(Icons.Default.Group, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            
            // Selected Members
            if (selectedUsers.isNotEmpty()) {
                Text(
                    text = "Selected (${selectedUsers.size})",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                LazyColumn(
                    modifier = Modifier.heightIn(max = 150.dp)
                ) {
                    items(selectedUsers, key = { it.id }) { user ->
                        ListItem(
                            headlineContent = { Text(user.displayName) },
                            leadingContent = {
                                AsyncImage(
                                    model = user.photoUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { selectedUsers.remove(user) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove")
                                }
                            }
                        )
                    }
                }
                
                Divider()
            }
            
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search users to add") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            
            Text(
                text = "Add Members",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // Available Users
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                val availableUsers = users.filter { user ->
                    selectedUsers.none { it.id == user.id }
                }
                
                items(availableUsers, key = { it.id }) { user ->
                    UserItem(
                        user = user,
                        onClick = { selectedUsers.add(user) },
                        trailing = {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                    )
                }
                
                if (availableUsers.isEmpty() && searchQuery.length >= 2) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No users found",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
