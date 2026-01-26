package com.jengachat.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages audio routing for WebRTC calls
 */
@Singleton
class RTCAudioManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "RTCAudioManager"
    }
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    
    private var savedAudioMode: Int = AudioManager.MODE_NORMAL
    private var savedIsSpeakerOn: Boolean = false
    private var savedIsMicrophoneMute: Boolean = false
    
    enum class AudioDevice {
        SPEAKER,
        EARPIECE,
        WIRED_HEADSET,
        BLUETOOTH
    }
    
    private var selectedDevice: AudioDevice = AudioDevice.EARPIECE
    private var listener: AudioManagerListener? = null
    
    interface AudioManagerListener {
        fun onAudioDeviceChanged(device: AudioDevice)
        fun onAudioDevicesAvailable(devices: Set<AudioDevice>)
    }
    
    fun setListener(listener: AudioManagerListener) {
        this.listener = listener
    }
    
    fun start(isVideoCall: Boolean) {
        Log.d(TAG, "Starting audio manager for ${if (isVideoCall) "video" else "voice"} call")
        
        // Save current audio state
        savedAudioMode = audioManager.mode
        savedIsSpeakerOn = audioManager.isSpeakerphoneOn
        savedIsMicrophoneMute = audioManager.isMicrophoneMute
        
        // Request audio focus
        requestAudioFocus()
        
        // Set audio mode for voice communication
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        
        // For video calls, default to speaker; for voice calls, default to earpiece
        if (isVideoCall) {
            setSpeakerOn(true)
        } else {
            setSpeakerOn(false)
        }
        
        updateAvailableDevices()
    }
    
    fun stop() {
        Log.d(TAG, "Stopping audio manager")
        
        // Abandon audio focus
        abandonAudioFocus()
        
        // Restore previous audio state
        audioManager.mode = savedAudioMode
        audioManager.isSpeakerphoneOn = savedIsSpeakerOn
        audioManager.isMicrophoneMute = savedIsMicrophoneMute
    }
    
    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    handleAudioFocusChange(focusChange)
                }
                .build()
            
            audioFocusRequest?.let {
                audioManager.requestAudioFocus(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange -> handleAudioFocusChange(focusChange) },
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }
    
    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }
    
    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained")
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Audio focus lost")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost temporarily")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus lost (can duck)")
            }
        }
    }
    
    fun setSpeakerOn(on: Boolean) {
        Log.d(TAG, "Setting speaker: $on")
        audioManager.isSpeakerphoneOn = on
        selectedDevice = if (on) AudioDevice.SPEAKER else AudioDevice.EARPIECE
        listener?.onAudioDeviceChanged(selectedDevice)
    }
    
    fun isSpeakerOn(): Boolean {
        return audioManager.isSpeakerphoneOn
    }
    
    fun setMicrophoneMute(mute: Boolean) {
        Log.d(TAG, "Setting microphone mute: $mute")
        audioManager.isMicrophoneMute = mute
    }
    
    fun isMicrophoneMute(): Boolean {
        return audioManager.isMicrophoneMute
    }
    
    fun selectAudioDevice(device: AudioDevice) {
        Log.d(TAG, "Selecting audio device: $device")
        
        when (device) {
            AudioDevice.SPEAKER -> {
                audioManager.isSpeakerphoneOn = true
            }
            AudioDevice.EARPIECE -> {
                audioManager.isSpeakerphoneOn = false
            }
            AudioDevice.WIRED_HEADSET -> {
                audioManager.isSpeakerphoneOn = false
            }
            AudioDevice.BLUETOOTH -> {
                audioManager.isSpeakerphoneOn = false
                audioManager.startBluetoothSco()
            }
        }
        
        selectedDevice = device
        listener?.onAudioDeviceChanged(device)
    }
    
    fun getSelectedDevice(): AudioDevice {
        return selectedDevice
    }
    
    fun getAvailableDevices(): Set<AudioDevice> {
        val devices = mutableSetOf<AudioDevice>()
        
        // Speaker and earpiece are always available
        devices.add(AudioDevice.SPEAKER)
        devices.add(AudioDevice.EARPIECE)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            
            for (audioDevice in audioDevices) {
                when (audioDevice.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                        devices.add(AudioDevice.WIRED_HEADSET)
                    }
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                        devices.add(AudioDevice.BLUETOOTH)
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            if (audioManager.isWiredHeadsetOn) {
                devices.add(AudioDevice.WIRED_HEADSET)
            }
            @Suppress("DEPRECATION")
            if (audioManager.isBluetoothScoOn) {
                devices.add(AudioDevice.BLUETOOTH)
            }
        }
        
        return devices
    }
    
    private fun updateAvailableDevices() {
        val devices = getAvailableDevices()
        listener?.onAudioDevicesAvailable(devices)
        
        // Auto-select best device
        when {
            devices.contains(AudioDevice.BLUETOOTH) -> selectAudioDevice(AudioDevice.BLUETOOTH)
            devices.contains(AudioDevice.WIRED_HEADSET) -> selectAudioDevice(AudioDevice.WIRED_HEADSET)
            else -> {} // Keep current selection
        }
    }
}
