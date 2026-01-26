package com.jengachat.ui.profile

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jengachat.util.PermissionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    
    // Settings state
    var notificationsEnabled by remember { mutableStateOf(true) }
    var soundEnabled by remember { mutableStateOf(true) }
    var vibrationEnabled by remember { mutableStateOf(true) }
    var showOnlineStatus by remember { mutableStateOf(true) }
    var readReceipts by remember { mutableStateOf(true) }
    var darkMode by remember { mutableStateOf(false) }
    var autoDownloadMedia by remember { mutableStateOf(true) }
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    
    // Delete account dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { 
                Text("Delete Account", fontWeight = FontWeight.Bold) 
            },
            text = { 
                Text("This will permanently delete your account and all your data. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAccount {
                            showDeleteDialog = false
                            Toast.makeText(context, "Account deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
    
    // Change password dialog
    if (showChangePasswordDialog) {
        var currentPassword by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { 
                Text("Change Password", fontWeight = FontWeight.Bold) 
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Current Password") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPassword == confirmPassword && newPassword.length >= 6) {
                            viewModel.changePassword(currentPassword, newPassword) { success ->
                                if (success) {
                                    Toast.makeText(context, "Password changed successfully", Toast.LENGTH_SHORT).show()
                                    showChangePasswordDialog = false
                                } else {
                                    Toast.makeText(context, "Failed to change password", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "Passwords don't match or too short", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Change")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
    
    // Clear data dialog
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { 
                Text("Clear Chat Data", fontWeight = FontWeight.Bold) 
            },
            text = { 
                Text("This will clear all cached media and data from your device. Your messages will remain on the server.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Clear app cache
                        try {
                            context.cacheDir.deleteRecursively()
                            Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to clear cache", Toast.LENGTH_SHORT).show()
                        }
                        showClearDataDialog = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Settings", fontWeight = FontWeight.Bold) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Notifications Section
            item {
                SettingsSectionCard(title = "Notifications") {
                    PremiumSettingsToggle(
                        icon = Icons.Outlined.Notifications,
                        title = "Push Notifications",
                        subtitle = "Receive message notifications",
                        checked = notificationsEnabled,
                        onCheckedChange = { 
                            notificationsEnabled = it
                            viewModel.updateNotificationSettings(it)
                            Toast.makeText(context, 
                                if (it) "Notifications enabled" else "Notifications disabled", 
                                Toast.LENGTH_SHORT).show()
                        }
                    )
                    
                    PremiumSettingsToggle(
                        icon = Icons.Outlined.VolumeUp,
                        title = "Sound",
                        subtitle = "Play notification sounds",
                        checked = soundEnabled,
                        onCheckedChange = { soundEnabled = it },
                        enabled = notificationsEnabled
                    )
                    
                    PremiumSettingsToggle(
                        icon = Icons.Outlined.Vibration,
                        title = "Vibration",
                        subtitle = "Vibrate for notifications",
                        checked = vibrationEnabled,
                        onCheckedChange = { vibrationEnabled = it },
                        enabled = notificationsEnabled
                    )
                    
                    PremiumSettingsItem(
                        icon = Icons.Outlined.AppSettingsAlt,
                        title = "System Settings",
                        subtitle = "Manage in device settings",
                        onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
            
            // Privacy Section
            item {
                SettingsSectionCard(title = "Privacy") {
                    PremiumSettingsToggle(
                        icon = Icons.Outlined.Circle,
                        title = "Online Status",
                        subtitle = "Show when you're online",
                        checked = showOnlineStatus,
                        onCheckedChange = { 
                            showOnlineStatus = it
                            viewModel.updatePrivacySetting("online_status", it)
                        }
                    )
                    
                    PremiumSettingsToggle(
                        icon = Icons.Outlined.DoneAll,
                        title = "Read Receipts",
                        subtitle = "Show when you've read messages",
                        checked = readReceipts,
                        onCheckedChange = { 
                            readReceipts = it
                            viewModel.updatePrivacySetting("read_receipts", it)
                        }
                    )
                    
                    PremiumSettingsItem(
                        icon = Icons.Outlined.Block,
                        title = "Blocked Contacts",
                        subtitle = "Manage blocked users",
                        onClick = {
                            Toast.makeText(context, "No blocked contacts yet", Toast.LENGTH_SHORT).show()
                        }
                    )
                    
                    PremiumSettingsItem(
                        icon = Icons.Outlined.Security,
                        title = "Two-Factor Auth",
                        subtitle = "Extra security for your account",
                        onClick = {
                            Toast.makeText(context, "2FA can be enabled from your account settings", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            
            // Appearance Section
            item {
                SettingsSectionCard(title = "Appearance") {
                    PremiumSettingsToggle(
                        icon = Icons.Outlined.DarkMode,
                        title = "Dark Mode",
                        subtitle = "Use dark theme",
                        checked = darkMode,
                        onCheckedChange = { 
                            darkMode = it
                            Toast.makeText(context, 
                                if (it) "Dark mode enabled (restart app)" else "Light mode enabled (restart app)", 
                                Toast.LENGTH_SHORT).show()
                        }
                    )
                    
                    PremiumSettingsItem(
                        icon = Icons.Outlined.Wallpaper,
                        title = "Chat Wallpaper",
                        subtitle = "Customize chat background",
                        onClick = {
                            Toast.makeText(context, "Wallpaper: Go to any chat → tap header → Change wallpaper", Toast.LENGTH_LONG).show()
                        }
                    )
                    
                    PremiumSettingsItem(
                        icon = Icons.Outlined.TextFields,
                        title = "Font Size",
                        subtitle = "Adjust message text size",
                        onClick = {
                            Toast.makeText(context, "Font size: Use system font settings", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            
            // Storage Section
            item {
                SettingsSectionCard(title = "Storage & Data") {
                    PremiumSettingsToggle(
                        icon = Icons.Outlined.Download,
                        title = "Auto-Download Media",
                        subtitle = "Download images and videos automatically",
                        checked = autoDownloadMedia,
                        onCheckedChange = { autoDownloadMedia = it }
                    )
                    
                    PremiumSettingsItem(
                        icon = Icons.Outlined.Storage,
                        title = "Storage Usage",
                        subtitle = "View and manage storage",
                        onClick = {
                            Toast.makeText(context, "Storage: ${context.cacheDir.totalSpace / 1024 / 1024} MB used", Toast.LENGTH_SHORT).show()
                        }
                    )
                    
                    PremiumSettingsItem(
                        icon = Icons.Outlined.DeleteSweep,
                        title = "Clear Cache",
                        subtitle = "Free up space",
                        onClick = { showClearDataDialog = true }
                    )
                }
            }
            
            // Account Section
            item {
                SettingsSectionCard(title = "Account") {
                    PremiumSettingsItem(
                        icon = Icons.Outlined.Lock,
                        title = "Change Password",
                        subtitle = "Update your password",
                        onClick = { showChangePasswordDialog = true }
                    )
                    
                    PremiumSettingsItem(
                        icon = Icons.Outlined.Email,
                        title = "Change Email",
                        subtitle = "Update your email address",
                        onClick = {
                            Toast.makeText(context, "Contact singhabneesh250@gmail.com to change email", Toast.LENGTH_LONG).show()
                        }
                    )
                    
                    PremiumSettingsItem(
                        icon = Icons.Outlined.CloudDownload,
                        title = "Export Data",
                        subtitle = "Download a copy of your data",
                        onClick = {
                            Toast.makeText(context, "Contact singhabneesh250@gmail.com for data export", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
            
            // Danger Zone
            item {
                Spacer(modifier = Modifier.height(8.dp))
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { showDeleteDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Delete Account",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Permanently delete your account",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
            
            // App info footer
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // About Section
                    Text(
                        text = "Talksy v1.0.0",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "End-to-End Encrypted Messaging",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Developer Info
                    Text(
                        text = "Developed by Abneesh Singh",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Help & Support
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "singhabneesh250@gmail.com",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Copyright
                    Text(
                        text = "© 2026 Abneesh Singh. All Rights Reserved.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Made with ❤️ in India",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
        
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                content = content
            )
        }
    }
}

@Composable
private fun PremiumSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(10.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.width(14.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun PremiumSettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (enabled) 
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            else 
                MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(10.dp),
                tint = if (enabled) 
                    MaterialTheme.colorScheme.primary
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
        
        Spacer(modifier = Modifier.width(14.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = if (enabled) 
                    MaterialTheme.colorScheme.onSurface
                else 
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = if (enabled) 
                    MaterialTheme.colorScheme.onSurfaceVariant
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

// Keep legacy components for backwards compatibility
@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    PremiumSettingsItem(icon, title, subtitle, onClick)
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    PremiumSettingsToggle(icon, title, subtitle, checked, onCheckedChange, enabled)
}
