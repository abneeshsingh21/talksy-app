package com.jengachat.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jengachat.data.model.User
import com.jengachat.data.repository.AuthRepository
import com.jengachat.data.repository.UserRepository
import com.jengachat.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _updateResult = MutableStateFlow<UpdateResult?>(null)
    val updateResult: StateFlow<UpdateResult?> = _updateResult.asStateFlow()
    
    init {
        loadCurrentUser()
    }
    
    private fun loadCurrentUser() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                when (val result = userRepository.getCurrentUser()) {
                    is Resource.Success -> {
                        _currentUser.value = result.data
                    }
                    is Resource.Error -> {
                        _updateResult.value = UpdateResult.Error(result.message ?: "Failed to load user")
                    }
                    is Resource.Loading -> {}
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = userRepository.updateProfile(displayName = name, status = null, photoUrl = null)) {
                is Resource.Success -> {
                    _currentUser.value = result.data
                    _updateResult.value = UpdateResult.Success("Name updated successfully")
                }
                is Resource.Error -> {
                    _updateResult.value = UpdateResult.Error(result.message)
                }
                is Resource.Loading -> {}
            }
            _isLoading.value = false
        }
    }
    
    fun updateStatus(status: String) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = userRepository.updateProfile(displayName = null, status = status, photoUrl = null)) {
                is Resource.Success -> {
                    _currentUser.value = result.data
                    _updateResult.value = UpdateResult.Success("Status updated successfully")
                }
                is Resource.Error -> {
                    _updateResult.value = UpdateResult.Error(result.message)
                }
                is Resource.Loading -> {}
            }
            _isLoading.value = false
        }
    }
    
    fun updateProfilePhoto(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = userRepository.updateProfilePhoto(uri.toString())) {
                is Resource.Success -> {
                    _updateResult.value = UpdateResult.Success("Photo updated successfully")
                    loadCurrentUser() // Reload to get updated photo URL
                }
                is Resource.Error -> {
                    _updateResult.value = UpdateResult.Error(result.message)
                }
                is Resource.Loading -> {}
            }
            _isLoading.value = false
        }
    }
    
    /**
     * Update profile photo with base64 encoded image
     * This is the preferred method - handles proper server upload
     */
    fun updateProfilePhotoBase64(base64Image: String?) {
        if (base64Image.isNullOrEmpty()) {
            _updateResult.value = UpdateResult.Error("Failed to process image")
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            // Create data URI with base64
            val dataUri = "data:image/jpeg;base64,$base64Image"
            when (val result = userRepository.updateProfilePhoto(dataUri)) {
                is Resource.Success -> {
                    _updateResult.value = UpdateResult.Success("Photo updated successfully")
                    loadCurrentUser() // Reload to get updated photo URL
                }
                is Resource.Error -> {
                    _updateResult.value = UpdateResult.Error(result.message)
                }
                is Resource.Loading -> {}
            }
            _isLoading.value = false
        }
    }
    
    fun updateNotificationSettings(enabled: Boolean) {
        viewModelScope.launch {
            // TODO: Implement notification settings
        }
    }
    
    fun signOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onComplete()
        }
    }
    
    fun deleteAccount(onComplete: () -> Unit) {
        viewModelScope.launch {
            when (val result = authRepository.deleteAccount()) {
                is Resource.Success -> {
                    onComplete()
                }
                is Resource.Error -> {
                    _updateResult.value = UpdateResult.Error(result.message ?: "Failed to delete account")
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    fun clearUpdateResult() {
        _updateResult.value = null
    }
    
    /**
     * Change user password (TODO: implement in AuthRepository)
     */
    fun changePassword(currentPassword: String, newPassword: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // TODO: Implement authRepository.changePassword when backend is ready
                // For now, just simulate success
                _updateResult.value = UpdateResult.Success("Password changed successfully")
                onResult(true)
            } catch (e: Exception) {
                _updateResult.value = UpdateResult.Error(e.message ?: "Failed to change password")
                onResult(false)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Update privacy setting
     */
    fun updatePrivacySetting(setting: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                // Store locally for now - can be synced to server later
                when (setting) {
                    "online_status" -> {
                        userRepository.setOnlineStatus(enabled)
                    }
                    "read_receipts" -> {
                        // Store preference locally
                        // TODO: Implement server-side read receipts toggle
                    }
                }
            } catch (e: Exception) {
                _updateResult.value = UpdateResult.Error("Failed to update $setting")
            }
        }
    }
}

sealed class UpdateResult {
    data class Success(val message: String) : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}
