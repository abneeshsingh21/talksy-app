package com.jengachat.ui.call

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jengachat.data.model.Call
import com.jengachat.data.model.CallEndReason
import com.jengachat.data.model.CallStatus
import com.jengachat.data.model.CallType
import com.jengachat.data.repository.CallRepository
import com.jengachat.data.repository.UserRepository
import com.jengachat.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    private val callRepository: CallRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    
    private val _currentCall = MutableStateFlow<Call?>(null)
    val currentCall: StateFlow<Call?> = _currentCall.asStateFlow()
    
    private val _callHistory = MutableStateFlow<List<Call>>(emptyList())
    val callHistory: StateFlow<List<Call>> = _callHistory.asStateFlow()
    
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()
    
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()
    
    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()
    
    private val _isVideoEnabled = MutableStateFlow(true)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled.asStateFlow()
    
    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()
    
    init {
        loadCallHistory()
    }
    
    private fun loadCallHistory() {
        viewModelScope.launch {
            when (val result = callRepository.getCallHistory()) {
                is Resource.Success -> {
                    _callHistory.value = result.data ?: emptyList()
                }
                is Resource.Error -> {
                    // Handle error
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    fun initiateCall(
        receiverId: String,
        callType: CallType,
        onCallCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            _callState.value = CallState.Initiating
            
            when (val result = callRepository.initiateCall(
                participantIds = listOf(receiverId),
                callType = callType,
                chatId = null,
                isGroupCall = false
            )) {
                is Resource.Success -> {
                    result.data?.let { call ->
                        _callState.value = CallState.Ringing
                        onCallCreated(call.id)
                        observeCall(call.id)
                    }
                }
                is Resource.Error -> {
                    _callState.value = CallState.Error(result.message ?: "Failed to initiate call")
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    fun initiateGroupCall(
        chatId: String,
        participantIds: List<String>,
        callType: CallType,
        onCallCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            _callState.value = CallState.Initiating
            
            when (val result = callRepository.initiateCall(
                participantIds = participantIds,
                callType = callType,
                chatId = chatId,
                isGroupCall = true
            )) {
                is Resource.Success -> {
                    result.data?.let { call ->
                        _callState.value = CallState.Ringing
                        onCallCreated(call.id)
                        observeCall(call.id)
                    }
                }
                is Resource.Error -> {
                    _callState.value = CallState.Error(result.message ?: "Failed to initiate group call")
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    fun answerCall(callId: String) {
        viewModelScope.launch {
            _callState.value = CallState.Connecting
            callRepository.joinCall(callId)
            observeCall(callId)
        }
    }
    
    fun rejectCall(callId: String) {
        viewModelScope.launch {
            callRepository.endCall(callId, CallEndReason.DECLINED)
            _callState.value = CallState.Ended
            _currentCall.value = null
        }
    }
    
    fun endCall() {
        viewModelScope.launch {
            _currentCall.value?.let { call ->
                callRepository.endCall(call.id, CallEndReason.COMPLETED)
            }
            _callState.value = CallState.Ended
            _currentCall.value = null
        }
    }
    
    private fun observeCall(callId: String) {
        viewModelScope.launch {
            callRepository.observeCall(callId).collect { call ->
                _currentCall.value = call
                
                when (call?.status) {
                    CallStatus.INITIATING -> _callState.value = CallState.Initiating
                    CallStatus.RINGING -> _callState.value = CallState.Ringing
                    CallStatus.CONNECTING -> _callState.value = CallState.Connecting
                    CallStatus.ONGOING -> _callState.value = CallState.Connected
                    CallStatus.ON_HOLD -> _callState.value = CallState.OnHold
                    CallStatus.RECONNECTING -> _callState.value = CallState.Reconnecting
                    CallStatus.ENDED -> _callState.value = CallState.Ended
                    null -> {}
                }
            }
        }
    }
    
    fun toggleMute() {
        viewModelScope.launch {
            _isMuted.value = !_isMuted.value
            _currentCall.value?.let { call ->
                callRepository.toggleMute(call.id, _isMuted.value)
            }
        }
    }
    
    fun toggleSpeaker() {
        _isSpeakerOn.value = !_isSpeakerOn.value
    }
    
    fun toggleVideo() {
        viewModelScope.launch {
            _isVideoEnabled.value = !_isVideoEnabled.value
            _currentCall.value?.let { call ->
                callRepository.toggleVideo(call.id, _isVideoEnabled.value)
            }
        }
    }
    
    fun switchCamera() {
        _isFrontCamera.value = !_isFrontCamera.value
    }
    
    fun deleteCallFromHistory(callId: String) {
        viewModelScope.launch {
            // Remove from local list
            _callHistory.value = _callHistory.value.filter { it.id != callId }
        }
    }
    
    fun clearCallHistory() {
        viewModelScope.launch {
            _callHistory.value = emptyList()
        }
    }
}

sealed class CallState {
    object Idle : CallState()
    object Initiating : CallState()
    object Ringing : CallState()
    object Connecting : CallState()
    object Connected : CallState()
    object OnHold : CallState()
    object Reconnecting : CallState()
    object Ended : CallState()
    data class Error(val message: String) : CallState()
}
