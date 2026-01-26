package com.jengachat.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jengachat.util.PermissionManager
import com.jengachat.util.rememberMultiplePermissionsLauncher

/**
 * Permission Request Dialog shown on first app launch
 */
@Composable
fun PermissionRequestDialog(
    onDismiss: () -> Unit,
    onPermissionsResult: (Boolean) -> Unit
) {
    val context = LocalContext.current
    
    val permissionLauncher = rememberMultiplePermissionsLauncher { results ->
        val allGranted = results.values.all { it }
        PermissionManager.markPermissionRequestShown(context)
        onPermissionsResult(allGranted)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Enable Permissions",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Talksy needs some permissions to work properly",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Permission items
                PermissionItem(
                    icon = Icons.Default.Contacts,
                    title = "Contacts",
                    description = "Find friends who use Talksy"
                )
                PermissionItem(
                    icon = Icons.Default.Camera,
                    title = "Camera",
                    description = "For video calls and sharing photos"
                )
                PermissionItem(
                    icon = Icons.Default.Mic,
                    title = "Microphone",
                    description = "For voice and video calls"
                )
                PermissionItem(
                    icon = Icons.Default.Photo,
                    title = "Photos & Media",
                    description = "Send and receive media files"
                )
                PermissionItem(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    description = "Get notified of new messages"
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        permissionLauncher.launch(
                            PermissionManager.allPermissions.toTypedArray()
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onDismiss) {
                    Text("Skip for now")
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, fontWeight = FontWeight.Medium)
            Text(
                text = description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Simple permission request button for specific permission
 */
@Composable
fun RequestContactsPermissionButton(
    onPermissionResult: (Boolean) -> Unit
) {
    val context = LocalContext.current
    
    val permissionLauncher = rememberMultiplePermissionsLauncher { results ->
        val granted = results[android.Manifest.permission.READ_CONTACTS] == true
        onPermissionResult(granted)
    }

    Button(
        onClick = {
            permissionLauncher.launch(arrayOf(android.Manifest.permission.READ_CONTACTS))
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Default.Contacts, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Enable Contacts Access")
    }
}

/**
 * Open app settings screen
 */
@Composable
fun OpenSettingsButton() {
    val context = LocalContext.current
    
    OutlinedButton(
        onClick = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Default.Settings, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Open Settings")
    }
}
