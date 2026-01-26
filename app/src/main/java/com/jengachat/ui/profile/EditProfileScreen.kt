package com.jengachat.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.jengachat.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBackClick: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val currentUser by viewModel.currentUser.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val updateResult by viewModel.updateResult.collectAsState()
    
    var displayName by remember(currentUser) { mutableStateOf(currentUser?.displayName ?: "") }
    var status by remember(currentUser) { mutableStateOf(currentUser?.status ?: "") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessingImage by remember { mutableStateOf(false) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            isProcessingImage = true
            // Convert to base64 with smaller size for profile photo
            scope.launch(Dispatchers.IO) {
                // Use 300px max width and 60% quality for smaller base64
                val base64 = ImageUtils.uriToBase64(context, it, maxWidth = 300, quality = 60)
                isProcessingImage = false
                viewModel.updateProfilePhotoBase64(base64)
            }
        }
    }
    
    // Show snackbar for update result
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(updateResult) {
        updateResult?.let { result ->
            when (result) {
                is UpdateResult.Success -> {
                    snackbarHostState.showSnackbar(result.message)
                }
                is UpdateResult.Error -> {
                    snackbarHostState.showSnackbar(result.message)
                }
            }
            viewModel.clearUpdateResult()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Profile Photo
            Box {
                AsyncImage(
                    model = selectedImageUri ?: currentUser?.photoUrl,
                    contentDescription = "Profile Photo",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentScale = ContentScale.Crop
                )
                
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(36.dp)
                        .clickable { imagePickerLauncher.launch("image/*") },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Change Photo",
                        modifier = Modifier.padding(8.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            
            TextButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                Text("Change Photo")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Display Name
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                trailingIcon = {
                    if (displayName != currentUser?.displayName && displayName.isNotBlank()) {
                        IconButton(
                            onClick = { viewModel.updateDisplayName(displayName) },
                            enabled = !isLoading
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Save")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Email (read-only)
            OutlinedTextField(
                value = currentUser?.email ?: "",
                onValueChange = { },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = false,
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Status
            OutlinedTextField(
                value = status,
                onValueChange = { status = it },
                label = { Text("Status") },
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                trailingIcon = {
                    if (status != currentUser?.status) {
                        IconButton(
                            onClick = { viewModel.updateStatus(status) },
                            enabled = !isLoading
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Save")
                        }
                    }
                },
                placeholder = { Text("What's on your mind?") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Status Suggestions
            Text(
                text = "Quick Status",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val statusSuggestions = listOf(
                "📱 Available",
                "🔇 Busy",
                "💤 Sleeping",
                "🏃 At the gym",
                "📚 Studying",
                "🎮 Gaming"
            )
            
            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                statusSuggestions.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { suggestion ->
                            SuggestionChip(
                                onClick = {
                                    status = suggestion
                                    viewModel.updateStatus(suggestion)
                                },
                                label = { Text(suggestion) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            if (isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
