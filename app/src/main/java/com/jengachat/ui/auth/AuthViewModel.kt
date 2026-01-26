package com.jengachat.ui.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jengachat.data.repository.AuthRepository
import com.jengachat.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    val isLoggedIn: Boolean get() = authRepository.isLoggedIn
    
    // Observe auth state changes
    val authStateFlow: Flow<Boolean> = authRepository.getAuthStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    fun login(email: String, password: String) {
        if (!validateEmail(email)) {
            _errorMessage.value = "Please enter a valid email"
            return
        }
        if (password.length < 6) {
            _errorMessage.value = "Password must be at least 6 characters"
            return
        }
        
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val result = authRepository.signInWithEmail(email, password)) {
                is Resource.Success -> {
                    _authState.value = AuthState.Success
                }
                is Resource.Error -> {
                    _authState.value = AuthState.Error(result.message ?: "Login failed")
                    _errorMessage.value = result.message
                }
                is Resource.Loading -> {
                    _authState.value = AuthState.Loading
                }
            }
        }
    }
    
    fun signUp(name: String, email: String, password: String, confirmPassword: String) {
        if (name.isBlank()) {
            _errorMessage.value = "Please enter your name"
            return
        }
        if (!validateEmail(email)) {
            _errorMessage.value = "Please enter a valid email"
            return
        }
        if (password.length < 6) {
            _errorMessage.value = "Password must be at least 6 characters"
            return
        }
        if (password != confirmPassword) {
            _errorMessage.value = "Passwords do not match"
            return
        }
        
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val result = authRepository.signUpWithEmail(email, password, name)) {
                is Resource.Success -> {
                    _authState.value = AuthState.Success
                }
                is Resource.Error -> {
                    _authState.value = AuthState.Error(result.message ?: "Sign up failed")
                    _errorMessage.value = result.message
                }
                is Resource.Loading -> {
                    _authState.value = AuthState.Loading
                }
            }
        }
    }
    
    fun changePassword(currentPassword: String, newPassword: String, confirmPassword: String) {
        if (currentPassword.length < 6) {
            _errorMessage.value = "Current password must be at least 6 characters"
            return
        }
        if (newPassword.length < 6) {
            _errorMessage.value = "New password must be at least 6 characters"
            return
        }
        if (newPassword != confirmPassword) {
            _errorMessage.value = "Passwords do not match"
            return
        }
        
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val result = authRepository.updatePassword(currentPassword, newPassword)) {
                is Resource.Success -> {
                    _authState.value = AuthState.PasswordChanged
                }
                is Resource.Error -> {
                    _authState.value = AuthState.Error(result.message ?: "Failed to change password")
                    _errorMessage.value = result.message
                }
                is Resource.Loading -> {
                    _authState.value = AuthState.Loading
                }
            }
        }
    }
    
    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _authState.value = AuthState.Idle
        }
    }
    
    fun deleteAccount() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val result = authRepository.deleteAccount()) {
                is Resource.Success -> {
                    _authState.value = AuthState.AccountDeleted
                }
                is Resource.Error -> {
                    _authState.value = AuthState.Error(result.message ?: "Failed to delete account")
                    _errorMessage.value = result.message
                }
                is Resource.Loading -> {
                    _authState.value = AuthState.Loading
                }
            }
        }
    }
    
    fun resetPassword(email: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val result = authRepository.resetPassword(email)) {
                is Resource.Success -> {
                    _authState.value = AuthState.PasswordResetSent
                }
                is Resource.Error -> {
                    _authState.value = AuthState.Error(result.message ?: "Failed to send reset email")
                    _errorMessage.value = result.message
                }
                is Resource.Loading -> {
                    _authState.value = AuthState.Loading
                }
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    fun resetState() {
        _authState.value = AuthState.Idle
    }
    
    private fun validateEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    object PasswordChanged : AuthState()
    object PasswordResetSent : AuthState()
    object AccountDeleted : AuthState()
    data class Error(val message: String) : AuthState()
}
