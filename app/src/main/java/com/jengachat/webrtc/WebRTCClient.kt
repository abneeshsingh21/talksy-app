package com.jengachat.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebRTC client for handling peer-to-peer audio/video calls
 */
@Singleton
class WebRTCClient @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "WebRTCClient"
        
        // ICE servers for NAT traversal
        private val ICE_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
    }
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var localVideoSource: VideoSource? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    
    private var listener: WebRTCListener? = null
    
    interface WebRTCListener {
        fun onIceCandidate(candidate: IceCandidate)
        fun onLocalStream(stream: MediaStream)
        fun onRemoteStream(stream: MediaStream)
        fun onConnectionStateChange(state: PeerConnection.PeerConnectionState)
        fun onError(error: String)
    }
    
    fun setListener(listener: WebRTCListener) {
        this.listener = listener
    }
    
    fun initialize() {
        Log.d(TAG, "Initializing WebRTC")
        
        // Initialize PeerConnectionFactory
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        
        val encoderFactory = DefaultVideoEncoderFactory(
            EglBase.create().eglBaseContext,
            true,
            true
        )
        val decoderFactory = DefaultVideoDecoderFactory(EglBase.create().eglBaseContext)
        
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
        
        Log.d(TAG, "WebRTC initialized successfully")
    }
    
    fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(ICE_SERVERS).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        
        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                    Log.d(TAG, "Signaling state changed: $state")
                }
                
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "ICE connection state changed: $state")
                }
                
                override fun onIceConnectionReceivingChange(receiving: Boolean) {
                    Log.d(TAG, "ICE connection receiving: $receiving")
                }
                
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    Log.d(TAG, "ICE gathering state changed: $state")
                }
                
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let {
                        Log.d(TAG, "ICE candidate: ${it.sdp}")
                        listener?.onIceCandidate(it)
                    }
                }
                
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                    Log.d(TAG, "ICE candidates removed")
                }
                
                override fun onAddStream(stream: MediaStream?) {
                    stream?.let {
                        Log.d(TAG, "Remote stream added")
                        listener?.onRemoteStream(it)
                    }
                }
                
                override fun onRemoveStream(stream: MediaStream?) {
                    Log.d(TAG, "Remote stream removed")
                }
                
                override fun onDataChannel(channel: DataChannel?) {
                    Log.d(TAG, "Data channel: ${channel?.label()}")
                }
                
                override fun onRenegotiationNeeded() {
                    Log.d(TAG, "Renegotiation needed")
                }
                
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    Log.d(TAG, "Track added")
                }
                
                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    Log.d(TAG, "Connection state changed: $newState")
                    newState?.let { listener?.onConnectionStateChange(it) }
                }
            }
        )
        
        Log.d(TAG, "Peer connection created")
    }
    
    fun createAudioTrack(): AudioTrack? {
        val audioConstraints = MediaConstraints()
        val audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio_track", audioSource)
        return localAudioTrack
    }
    
    fun createVideoTrack(surfaceViewRenderer: SurfaceViewRenderer): VideoTrack? {
        videoCapturer = createCameraCapturer()
        
        videoCapturer?.let { capturer ->
            val eglBase = EglBase.create()
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            
            localVideoSource = peerConnectionFactory?.createVideoSource(capturer.isScreencast)
            capturer.initialize(surfaceTextureHelper, context, localVideoSource?.capturerObserver)
            capturer.startCapture(1280, 720, 30)
            
            localVideoTrack = peerConnectionFactory?.createVideoTrack("video_track", localVideoSource)
            localVideoTrack?.addSink(surfaceViewRenderer)
            
            return localVideoTrack
        }
        
        return null
    }
    
    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        
        // Try front camera first
        for (deviceName in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        
        // Fall back to back camera
        for (deviceName in enumerator.deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        
        return null
    }
    
    fun switchCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }
    
    fun createOffer(callback: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            callback(it)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {
                            listener?.onError("Failed to set local description: $p0")
                        }
                    }, it)
                }
            }
            
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                listener?.onError("Failed to create offer: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }
    
    fun createAnswer(callback: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints()
        
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            callback(it)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {
                            listener?.onError("Failed to set local description: $p0")
                        }
                    }, it)
                }
            }
            
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                listener?.onError("Failed to create answer: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }
    
    fun setRemoteDescription(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set successfully")
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(error: String?) {
                listener?.onError("Failed to set remote description: $error")
            }
        }, sdp)
    }
    
    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }
    
    fun enableAudio(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }
    
    fun enableVideo(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }
    
    fun close() {
        Log.d(TAG, "Closing WebRTC client")
        
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        videoCapturer = null
        
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        
        localVideoSource?.dispose()
        localVideoSource = null
        
        localAudioTrack?.dispose()
        localAudioTrack = null
        
        localVideoTrack?.dispose()
        localVideoTrack = null
        
        peerConnection?.close()
        peerConnection = null
        
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        
        listener = null
    }
}
