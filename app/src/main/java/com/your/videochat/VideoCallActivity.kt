package com.your.videochat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.webrtc.*
import okhttp3.*
import org.json.JSONObject
import java.util.UUID
import android.util.Log

class VideoCallActivity : AppCompatActivity() {

    private lateinit var localVideoView: SurfaceViewRenderer
    private lateinit var remoteVideoView: SurfaceViewRenderer
    private lateinit var micButton: ImageButton
    private lateinit var cameraButton: ImageButton
    private lateinit var endCallButton: ImageButton
    private lateinit var chatButton: ImageButton
    private lateinit var chatLayout: CardView
    private lateinit var chatInput: EditText
    private lateinit var sendChatButton: ImageButton
    private lateinit var chatMessages: LinearLayout
    private lateinit var roomNameTextView: TextView
    private lateinit var callDurationTextView: TextView

    private var isMicEnabled = true
    private var isCameraEnabled = true
    private var callStartTime: Long = 0
    private val callDurationHandler = Handler(Looper.getMainLooper())

    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private lateinit var eglBase: EglBase

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    private var userId: String = UUID.randomUUID().toString()
    private var roomId: String = ""
    private var remoteUserId: String = ""
    private var peerConnectionFactory: PeerConnectionFactory? = null

    companion object {
        private const val TAG = "VideoCall"
        // Public server URL - change this to your deployed server
        private var SERVER_URL = "wss://videochat-aend.onrender.com"
    }

    private val iceServers = listOf(
        // Google STUN servers
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        // Twilio STUN servers (work well globally)
        PeerConnection.IceServer.builder("stun:global.stun.twilio.com:3478").createIceServer(),
        // Additional STUN servers for better connectivity in Russia
        PeerConnection.IceServer.builder("stun:stun.services.mozilla.com").createIceServer()
    )

    private val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)

        roomId = intent.getStringExtra("roomId") ?: ""
        
        // Определяем URL сервера
        val serverUrl = intent.getStringExtra("serverUrl") ?: SERVER_URL
        SERVER_URL = serverUrl
        Log.d(TAG, "Room ID: $roomId, User ID: $userId, Server: $SERVER_URL")
        
        initViews()
        initializeWebRTC()
        requestPermissions()
        // Подключаемся к серверу после получения разрешений
    }

    private fun initViews() {
        localVideoView = findViewById(R.id.localVideoView)
        remoteVideoView = findViewById(R.id.remoteVideoView)
        micButton = findViewById(R.id.micButton)
        cameraButton = findViewById(R.id.cameraButton)
        endCallButton = findViewById(R.id.endCallButton)
        chatButton = findViewById(R.id.chatButton)
        chatLayout = findViewById(R.id.chatLayout)
        chatInput = findViewById(R.id.chatInput)
        sendChatButton = findViewById(R.id.sendChatButton)
        chatMessages = findViewById(R.id.chatMessages)
        roomNameTextView = findViewById(R.id.roomNameTextView)
        callDurationTextView = findViewById(R.id.callDurationTextView)

        roomNameTextView.text = roomId

        micButton.setOnClickListener { toggleMic() }
        cameraButton.setOnClickListener { toggleCamera() }
        endCallButton.setOnClickListener { endCall() }
        chatButton.setOnClickListener { toggleChat() }
        sendChatButton.setOnClickListener { sendChatMessage() }
    }

    private fun initializeWebRTC() {
        Log.d(TAG, "Initializing WebRTC...")
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                .createInitializationOptions()
        )

        eglBase = EglBase.create()

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()

        localVideoView.init(eglBase.eglBaseContext, null)
        localVideoView.setZOrderMediaOverlay(true)
        remoteVideoView.init(eglBase.eglBaseContext, null)

        createPeerConnection()
        Log.d(TAG, "WebRTC initialized")
    }

    private fun createPeerConnection() {
        Log.d(TAG, "Creating PeerConnection...")
        val config = PeerConnection.RTCConfiguration(iceServers)
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        
        peerConnection = peerConnectionFactory?.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "ICE Candidate: ${candidate.sdp}")
                sendIceCandidate(candidate)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}

            override fun onTrack(transceiver: RtpTransceiver) {
                Log.d(TAG, "onTrack received")
                val mediaStreamTrack = transceiver.receiver.track()
                if (mediaStreamTrack is VideoTrack) {
                    runOnUiThread {
                        remoteVideoTrack = mediaStreamTrack
                        mediaStreamTrack.addSink(remoteVideoView)
                        Log.d(TAG, "Remote video track added")
                        // Start call duration timer when call is connected
                        startCallDurationTimer()
                    }
                }
            }

            override fun onAddStream(stream: MediaStream) {
                Log.d(TAG, "onAddStream received")
                stream.videoTracks.firstOrNull()?.let {
                    runOnUiThread {
                        remoteVideoTrack = it
                        it.addSink(remoteVideoView)
                        // Start call duration timer when call is connected
                        startCallDurationTimer()
                    }
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState) {
                Log.d(TAG, "Signaling state: $state")
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "ICE connection state: $state")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                Log.d(TAG, "ICE gathering state: $state")
            }
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: DataChannel) {}
            override fun onRenegotiationNeeded() {
                Log.d(TAG, "Renegotiation needed")
            }
        })
        Log.d(TAG, "PeerConnection created")
    }

    private fun startLocalVideo() {
        Log.d(TAG, "Starting local video...")
        val audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio", audioSource)

        val surfaceTextureHelper = SurfaceTextureHelper.create("SurfaceTextureHelper", eglBase.eglBaseContext)
        val capturer = createCameraCapturer()
        
        if (capturer != null) {
            val videoSource = peerConnectionFactory?.createVideoSource(capturer.isScreencast)
            capturer.initialize(surfaceTextureHelper, applicationContext, videoSource?.capturerObserver)
            capturer.startCapture(1280, 720, 30)
            
            localVideoTrack = peerConnectionFactory?.createVideoTrack("video", videoSource)
            localVideoTrack?.addSink(localVideoView)

            // Используем addTrack вместо addStream (Unified Plan)
            localVideoTrack?.let { peerConnection?.addTrack(it, listOf("localStream")) }
            localAudioTrack?.let { peerConnection?.addTrack(it, listOf("localStream")) }
            Log.d(TAG, "Local video started")
        } else {
            Log.e(TAG, "Failed to create camera capturer")
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        return try {
            val enumerator = Camera2Enumerator(applicationContext)
            val deviceNames = enumerator.deviceNames
            Log.d(TAG, "Camera devices: $deviceNames")
            
            // Сначала пробуем заднюю камеру
            var capturer: CameraVideoCapturer? = null
            for (name in deviceNames) {
                if (enumerator.isBackFacing(name)) {
                    Log.d(TAG, "Trying back camera: $name")
                    capturer = enumerator.createCapturer(name, null)
                    if (capturer != null) {
                        Log.d(TAG, "Back camera created successfully")
                        return capturer
                    }
                }
            }
            
            // Если задней нет, пробуем переднюю
            for (name in deviceNames) {
                if (enumerator.isFrontFacing(name)) {
                    Log.d(TAG, "Trying front camera: $name")
                    capturer = enumerator.createCapturer(name, null)
                    if (capturer != null) {
                        Log.d(TAG, "Front camera created successfully")
                        return capturer
                    }
                }
            }
            
            Log.e(TAG, "No camera found")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error creating camera capturer: ${e.message}")
            null
        }
    }

    private fun connectToSignallingServer() {
        Log.d(TAG, "Connecting to signalling server: $SERVER_URL")
        val request = Request.Builder()
            .url(SERVER_URL)
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected!")
                joinRoom()
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Игнорируем pong сообщения
                if (text.contains("\"type\":\"pong\"")) {
                    return
                }
                Log.d(TAG, "Received message: $text")
                handleSignallingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code - $reason")
                webSocket.close(code, reason)
                stopHeartbeat()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code - $reason")
                stopHeartbeat()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                t.printStackTrace()
                stopHeartbeat()
            }
        }

        webSocket = client.newWebSocket(request, listener)
    }

    private val heartbeatHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var heartbeatRunnable: Runnable? = null

    private fun startHeartbeat() {
        heartbeatRunnable?.let { heartbeatHandler.removeCallbacks(it) }
        heartbeatRunnable = object : Runnable {
            override fun run() {
                webSocket?.let { ws ->
                    ws.send("{\"type\":\"ping\"}")
                    Log.d(TAG, "Sent ping")
                }
                heartbeatHandler.postDelayed(this, 25000) // Каждые 25 секунд
            }
        }
        heartbeatHandler.post(heartbeatRunnable!!)
    }

    private fun stopHeartbeat() {
        heartbeatRunnable?.let { heartbeatHandler.removeCallbacks(it) }
        heartbeatRunnable = null
    }

    private fun joinRoom() {
        val message = JSONObject().apply {
            put("type", "join")
            put("roomId", roomId)
            put("userId", userId)
        }
        Log.d(TAG, "Joining room: $message")
        webSocket?.send(message.toString())
    }

    private fun handleSignallingMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.getString("type")
            Log.d(TAG, "Handling message type: $type")

            when (type) {
                "offer" -> handleOffer(json)
                "answer" -> handleAnswer(json)
                "ice-candidate" -> handleIceCandidate(json)
                "user-joined" -> handleUserJoined(json)
                "user-left" -> {
                    remoteUserId = ""
                    Log.d(TAG, "User left the room")
                }
                "chat-message" -> {
                    val senderId = json.getString("userId")
                    val msg = json.getString("message")
                    val timestamp = json.getLong("timestamp")
                    handleChatMessage(senderId, msg, timestamp)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message: ${e.message}")
        }
    }

    private fun handleOffer(json: JSONObject) {
        remoteUserId = json.getString("userId")
        val offerSdp = json.getString("offer")
        Log.d(TAG, "Received offer from: $remoteUserId")
        val sdp = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        peerConnection?.setRemoteDescription(SimpleSdpObserver(), sdp)
        createAnswer()
    }

    private fun handleAnswer(json: JSONObject) {
        val answerSdp = json.getString("answer")
        Log.d(TAG, "Received answer")
        val sdp = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        peerConnection?.setRemoteDescription(SimpleSdpObserver(), sdp)
    }

    private fun handleIceCandidate(json: JSONObject) {
        val candidate = IceCandidate(
            json.getString("sdpMid"),
            json.getInt("sdpMLineIndex"),
            json.getString("candidate")
        )
        Log.d(TAG, "Adding ICE candidate")
        peerConnection?.addIceCandidate(candidate)
    }

    private fun handleUserJoined(json: JSONObject) {
        val newUserId = json.getString("userId")
        Log.d(TAG, "User joined: $newUserId")
        
        // Первый пользователь в комнате создаёт offer
        if (remoteUserId.isEmpty()) {
            remoteUserId = newUserId
            createOffer()
        }
    }

    private fun createOffer() {
        Log.d(TAG, "Creating offer...")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.d(TAG, "Offer created successfully")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.d(TAG, "Local description set, sending offer")
                        sendOffer(sessionDescription)
                    }
                    override fun onCreateFailure(s: String) {}
                    override fun onSetFailure(s: String) {
                        Log.e(TAG, "Failed to set local description: $s")
                    }
                }, sessionDescription)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(s: String) {
                Log.e(TAG, "Failed to create offer: $s")
            }
            override fun onSetFailure(s: String) {}
        }, constraints)
    }

    private fun createAnswer() {
        Log.d(TAG, "Creating answer...")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.d(TAG, "Answer created successfully")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.d(TAG, "Local description set, sending answer")
                        sendAnswer(sessionDescription)
                    }
                    override fun onCreateFailure(s: String) {}
                    override fun onSetFailure(s: String) {
                        Log.e(TAG, "Failed to set local description: $s")
                    }
                }, sessionDescription)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(s: String) {
                Log.e(TAG, "Failed to create answer: $s")
            }
            override fun onSetFailure(s: String) {}
        }, constraints)
    }

    private fun sendOffer(sessionDescription: SessionDescription) {
        val message = JSONObject().apply {
            put("type", "offer")
            put("roomId", roomId)
            put("userId", userId)
            put("targetUserId", remoteUserId)
            put("offer", sessionDescription.description)
        }
        Log.d(TAG, "Sending offer to: $remoteUserId")
        webSocket?.send(message.toString())
    }

    private fun sendAnswer(sessionDescription: SessionDescription) {
        val message = JSONObject().apply {
            put("type", "answer")
            put("roomId", roomId)
            put("userId", userId)
            put("targetUserId", remoteUserId)
            put("answer", sessionDescription.description)
        }
        Log.d(TAG, "Sending answer to: $remoteUserId")
        webSocket?.send(message.toString())
    }

    private fun sendIceCandidate(candidate: IceCandidate) {
        val message = JSONObject().apply {
            put("type", "ice-candidate")
            put("roomId", roomId)
            put("userId", userId)
            put("targetUserId", remoteUserId)
            put("candidate", candidate.sdp)
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
        }
        Log.d(TAG, "Sending ICE candidate")
        webSocket?.send(message.toString())
    }

    private fun toggleMic() {
        localAudioTrack?.let { track ->
            isMicEnabled = !isMicEnabled
            track.setEnabled(isMicEnabled)
            micButton.setImageResource(if (isMicEnabled) R.drawable.ic_mic else R.drawable.ic_mic_off)
        }
    }

    private fun toggleCamera() {
        localVideoTrack?.let { track ->
            isCameraEnabled = !isCameraEnabled
            track.setEnabled(isCameraEnabled)
            cameraButton.setImageResource(if (isCameraEnabled) R.drawable.ic_videocam else R.drawable.ic_videocam_off)
        }
    }

    private fun toggleChat() {
        chatLayout.visibility = if (chatLayout.visibility == View.GONE) View.VISIBLE else View.GONE
    }

    private fun sendChatMessage() {
        val message = chatInput.text.toString().trim()
        if (message.isNotEmpty()) {
            val jsonMessage = JSONObject().apply {
                put("type", "chat-message")
                put("roomId", roomId)
                put("userId", userId)
                put("message", message)
            }
            webSocket?.send(jsonMessage.toString())
            chatInput.text.clear()
        }
    }

    private fun handleChatMessage(userId: String, message: String, timestamp: Long) {
        runOnUiThread {
            addChatMessage(userId, message, timestamp)
        }
    }

    private fun addChatMessage(senderId: String, message: String, timestamp: Long) {
        val messageView = layoutInflater.inflate(R.layout.item_chat_message, chatMessages, false)

        val senderText = messageView.findViewById<TextView>(R.id.senderText)
        val messageText = messageView.findViewById<TextView>(R.id.messageText)
        val timeText = messageView.findViewById<TextView>(R.id.timeText)

        senderText.text = if (senderId == userId) "Вы" else senderId.takeLast(4)
        messageText.text = message

        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        timeText.text = sdf.format(java.util.Date(timestamp))

        chatMessages.addView(messageView)

        // Прокрутка к последнему сообщению
        val scrollView = chatMessages.parent as? android.widget.ScrollView
        scrollView?.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun endCall() {
        Log.d(TAG, "Ending call")
        stopCallDurationTimer()
        peerConnection?.close()
        webSocket?.close(1000, "Call ended")
        finish()
    }

    private fun requestPermissions() {
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), 100)
        } else {
            // Разрешения уже есть - запускаем сразу
            Log.d(TAG, "Permissions already granted")
            startLocalVideo()
            connectToSignallingServer()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startLocalVideo()
                connectToSignallingServer()
            } else {
                Log.e(TAG, "Permissions not granted")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopHeartbeat()
        stopCallDurationTimer()
        localVideoView.release()
        remoteVideoView.release()
        eglBase.release()
    }

    // Call Duration Timer
    private val callDurationRunnable = object : Runnable {
        override fun run() {
            val elapsedSeconds = (System.currentTimeMillis() - callStartTime) / 1000
            val minutes = elapsedSeconds / 60
            val seconds = elapsedSeconds % 60
            callDurationTextView.text = String.format("%02d:%02d", minutes, seconds)
            callDurationHandler.postDelayed(this, 1000)
        }
    }

    private fun startCallDurationTimer() {
        callStartTime = System.currentTimeMillis()
        callDurationHandler.post(callDurationRunnable)
    }

    private fun stopCallDurationTimer() {
        callDurationHandler.removeCallbacks(callDurationRunnable)
    }

    class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sessionDescription: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(reason: String) {}
        override fun onSetFailure(reason: String) {}
    }
}
