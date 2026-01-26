package com.jengachat.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & Support") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Contact Section
            Text(
                text = "Contact Us",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    ContactItem(
                        icon = Icons.Outlined.Email,
                        title = "Email Support",
                        subtitle = "abneeshsingh21@gmail.com",
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:abneeshsingh21@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "Talksy Support")
                            }
                            context.startActivity(intent)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ContactItem(
                        icon = Icons.Outlined.BugReport,
                        title = "Report a Bug",
                        subtitle = "Help us improve Talksy",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, 
                                Uri.parse("https://github.com/abneeshsingh21/jenga-chat-/issues"))
                            context.startActivity(intent)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ContactItem(
                        icon = Icons.Outlined.GitHub,
                        title = "GitHub",
                        subtitle = "View source code",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, 
                                Uri.parse("https://github.com/abneeshsingh21/jenga-chat-"))
                            context.startActivity(intent)
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // FAQ Section
            Text(
                text = "Frequently Asked Questions",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    FaqItem(
                        question = "Is Talksy really end-to-end encrypted?",
                        answer = "Yes! Talksy uses state-of-the-art Signal Protocol for end-to-end encryption. Your messages, calls, and media are encrypted on your device and can only be read by the intended recipients."
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    FaqItem(
                        question = "Can Talksy read my messages?",
                        answer = "No. Talksy cannot read your messages. The encryption keys are stored only on your device, not on our servers. We have zero access to your message content."
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    FaqItem(
                        question = "How do voice and video calls work?",
                        answer = "Voice and video calls use WebRTC with end-to-end encryption. The call data is encrypted on your device and travels directly to the other party when possible."
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    FaqItem(
                        question = "Is Talksy free?",
                        answer = "Yes! Talksy is 100% free and open source. There are no ads, no subscriptions, and no hidden costs. We believe privacy should be accessible to everyone."
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    FaqItem(
                        question = "How do I find my friends?",
                        answer = "You can search for friends by their username or email address. You can also sync your contacts to find friends who are already on Talksy."
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ContactItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Outlined.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun FaqItem(
    question: String,
    answer: String
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = question,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = answer,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}
