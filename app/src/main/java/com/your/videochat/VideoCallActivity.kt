package com.your.videochat

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import okhttp3.*
import org.json.JSONObject
import java.util.UUID
import android.util.Log
import android.util.Pair

class VideoCallActivity : AppCompatActivity() {

    private lateinit var localVideoView: SurfaceViewRenderer
    private lateinit var remoteVideoView: SurfaceViewRenderer
    private lateinit var micButton: ImageButton
    private lateinit var cameraButton: ImageButton
    private lateinit var endCallButton: ImageButton
    private lateinit var chatButton: ImageButton
    private lateinit var cameraSwitchButton: ImageButton
    private lateinit var chatLayout: CardView
    private lateinit var chatInput: EditText
    private lateinit var sendChatButton: ImageButton
    private lateinit var chatMessages: LinearLayout
    private lateinit var roomNameTextView: TextView
    private lateinit var callDurationTextView: TextView

    private var isMicEnabled = true
    private var isCameraEnabled = true
    private var isFrontCamera = false
    private var callStartTime: Long = 0
    private val callDurationHandler = Handler(Looper.getMainLooper())

    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var cameraCapturer: CameraVideoCapturer? = null
    private lateinit var eglBase: EglBase

    private var webSocket: WebSocket? = null
    // Оптимизированный HTTP клиент для максимальной скорости
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)  // Быстрое подключение
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)     // Быстрое чтение
        .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)    // Быстрая запись
        .retryOnConnectionFailure(true)                             // Автоповтор при ошибках
        .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)    // Проверка соединения
        .build()
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
        // Google STUN servers - самые быстрые
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        // Дополнительные STUN для надежности
        PeerConnection.IceServer.builder("stun:global.stun.twilio.com:3478").createIceServer(),
        // TURN серверы для NAT traversal (если доступны)
        // PeerConnection.IceServer.builder("turn:your-turn-server.com:3478").setUsername("user").setPassword("pass").createIceServer()
    )

    private val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)

        roomId = intent.getStringExtra("roomId") ?: ""
        
        if (roomId.isBlank()) {
            Log.e(TAG, "Room ID is null or empty")
            Toast.makeText(this, "Ошибка: ID комнаты не указан", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        // Определяем URL сервера
        val serverUrl = intent.getStringExtra("serverUrl") ?: SERVER_URL
        if (serverUrl.isBlank()) {
            Log.e(TAG, "Server URL is null or empty")
            Toast.makeText(this, "Ошибка: URL сервера не указан", Toast.LENGTH_LONG).show()
            finish()
            return
        }
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
        cameraSwitchButton = findViewById(R.id.cameraSwitchButton)
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
        cameraSwitchButton.setOnClickListener { switchCamera() }
        sendChatButton.setOnClickListener { sendChatMessage() }
    }

    private fun initializeWebRTC() {
        Log.d(TAG, "Initializing WebRTC...")
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                .createInitializationOptions()
        )

        eglBase = EglBase.create()

        // Оптимизированная аудио конфигурация для минимальной задержки
        val audioDeviceModule = JavaAudioDeviceModule.builder(applicationContext)
            .setAudioSource(android.media.MediaRecorder.AudioSource.CAMCORDER)  // Лучшее качество для видеозвонков
            .setUseLowLatency(true)  // Включаем low latency для скорости
            .setSampleRate(48000)  // Высокое качество
            .setAudioFormat(android.media.AudioFormat.ENCODING_PCM_16BIT)  // 16-bit для качества
            .createAudioDeviceModule()

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()

        localVideoView.init(eglBase.eglBaseContext, null)
        localVideoView.setZOrderMediaOverlay(true)
        remoteVideoView.init(eglBase.eglBaseContext, null)

        createPeerConnection()
        Log.d(TAG, "WebRTC initialized with studio quality audio")
    }

    private fun createPeerConnection() {
        Log.d(TAG, "Creating PeerConnection...")
        
        if (peerConnectionFactory == null) {
            Log.e(TAG, "PeerConnectionFactory is null")
            return
        }
        
        val config = PeerConnection.RTCConfiguration(iceServers)
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        
        // Оптимизированные настройки для максимальной скорости
        config.iceConnectionReceivingTimeout = 1000  // 1 секунда для быстрого переключения
        config.iceBackupCandidatePairPingInterval = 500  // 0.5 секунды для быстрой проверки
        config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        config.iceCandidatePoolSize = 20  // Увеличиваем пул кандидатов
        config.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE  // Оптимизация带宽
        config.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE  // Уменьшение трафика
        config.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED  // Отключаем TCP для скорости
        config.candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.LOW_COST  // Приоритет WiFi/4G
        
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
                } else if (mediaStreamTrack is AudioTrack) {
                    runOnUiThread {
                        Log.d(TAG, "Remote audio track found via onTrack: ${mediaStreamTrack.id()}")
                        Log.d(TAG, "Audio track enabled: ${mediaStreamTrack.enabled()}")
                        Log.d(TAG, "Audio track state: ${mediaStreamTrack.state()}")
                        
                        // Включаем аудио трек для воспроизведения
                        mediaStreamTrack.setEnabled(true)
                        Log.d(TAG, "Remote audio track enabled via onTrack - собеседник должен быть слышен!")
                    }
                }
            }

            override fun onAddStream(stream: MediaStream) {
                Log.d(TAG, "onAddStream received")
                Log.d(TAG, "Stream audio tracks count: ${stream.audioTracks.size}")
                Log.d(TAG, "Stream video tracks count: ${stream.videoTracks.size}")
                
                // Обработка видео треков
                stream.videoTracks.firstOrNull()?.let {
                    runOnUiThread {
                        remoteVideoTrack = it
                        it.addSink(remoteVideoView)
                        Log.d(TAG, "Remote video track added")
                        // Start call duration timer when call is connected
                        startCallDurationTimer()
                    }
                }
                
                // Обработка аудио треков - ВАЖНО!
                stream.audioTracks.forEach { audioTrack ->
                    runOnUiThread {
                        Log.d(TAG, "Remote audio track found: ${audioTrack.id()}")
                        Log.d(TAG, "Audio track enabled: ${audioTrack.enabled()}")
                        Log.d(TAG, "Audio track state: ${audioTrack.state()}")
                        
                        // Включаем аудио трек для воспроизведения
                        audioTrack.setEnabled(true)
                        Log.d(TAG, "Remote audio track enabled - собеседник должен быть слышен!")
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
        
        if (peerConnectionFactory == null) {
            Log.e(TAG, "PeerConnectionFactory is null when starting local video")
            return
        }
        
        val audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio", audioSource)

        val surfaceTextureHelper = SurfaceTextureHelper.create("SurfaceTextureHelper", eglBase.eglBaseContext)
        cameraCapturer = createCameraCapturer()
        
        if (cameraCapturer != null) {
            val videoSource = peerConnectionFactory?.createVideoSource(cameraCapturer!!.isScreencast)
            cameraCapturer!!.initialize(surfaceTextureHelper, applicationContext, videoSource?.capturerObserver)
            // Адаптивное качество видео в зависимости от устройства
            val optimalResolution = getOptimalVideoResolution()
            val optimalFps = getOptimalFps()
            cameraCapturer!!.startCapture(optimalResolution.first, optimalResolution.second, optimalFps)
            
            localVideoTrack = peerConnectionFactory?.createVideoTrack("video", videoSource)
            localVideoTrack?.addSink(localVideoView)

            // Используем addTrack вместо addStream (Unified Plan)
            localVideoTrack?.let { 
                val result = peerConnection?.addTrack(it, listOf("localStream"))
                Log.d(TAG, "Local video track added to peer connection: ${result != null}")
            }
            localAudioTrack?.let { 
                val result = peerConnection?.addTrack(it, listOf("localStream"))
                it.setEnabled(true)  // Включаем локальный аудио трек
                Log.d(TAG, "Local audio track added to peer connection: ${result != null}")
                Log.d(TAG, "Local audio track enabled: ${it.enabled()}")
                Log.d(TAG, "Local audio track state: ${it.state()}")
                Log.d(TAG, "Local audio track ready to send audio data")
            }
            Log.d(TAG, "Local video started")
        } else {
            Log.e(TAG, "Failed to create camera capturer")
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        return try {
            // Используем Camera2 для лучшей производительности
            val enumerator = Camera2Enumerator(applicationContext)
            val deviceNames = enumerator.deviceNames
            Log.d(TAG, "Available camera devices: $deviceNames")
            
            // Приоритетный порядок камер для оптимального качества
            val preferredCameras = if (isFrontCamera) {
                // Для фронтальной камеры ищем с поддержкой автофокуса
                deviceNames.filter { enumerator.isFrontFacing(it) }
                    .sortedByDescending { getCameraPriority(it, enumerator) }
            } else {
                // Для задней камеры ищем с наилучшим качеством
                deviceNames.filter { enumerator.isBackFacing(it) }
                    .sortedByDescending { getCameraPriority(it, enumerator) }
            }
            
            // Пробуем создать камеру с наивысшим приоритетом
            for (cameraName in preferredCameras) {
                try {
                    Log.d(TAG, "Creating camera capturer: $cameraName")
                    val capturer = enumerator.createCapturer(cameraName, null)
                    if (capturer != null) {
                        Log.d(TAG, "Camera created successfully: $cameraName")
                        return capturer
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create camera $cameraName: ${e.message}")
                }
            }
            
            // Fallback: пробуем любую доступную камеру
            for (cameraName in deviceNames) {
                try {
                    val capturer = enumerator.createCapturer(cameraName, null)
                    if (capturer != null) {
                        Log.w(TAG, "Using fallback camera: $cameraName")
                        return capturer
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Fallback camera $cameraName failed: ${e.message}")
                }
            }
            
            Log.e(TAG, "No camera could be created")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Critical error creating camera capturer: ${e.message}")
            null
        }
    }
    
    // Определение приоритета камеры на основе характеристик
    private fun getCameraPriority(cameraName: String, enumerator: Camera2Enumerator): Int {
        return try {
            var priority = 0
            
            // Проверяем поддержку камеры
            if (enumerator.deviceNames.contains(cameraName)) {
                priority += 10
            }
            
            // Дополнительные баллы за определенные характеристики камеры
            if (cameraName.contains("wide", ignoreCase = true)) priority += 5
            if (cameraName.contains("main", ignoreCase = true)) priority += 3
            if (cameraName.contains("ultra", ignoreCase = true)) priority += 7
            
            priority
        } catch (e: Exception) {
            Log.w(TAG, "Error getting camera priority for $cameraName: ${e.message}")
            0
        }
    }

    private fun connectToSignallingServer() {
        if (roomId.isBlank()) {
            Log.e(TAG, "Cannot connect: Room ID is empty")
            return
        }
        
        Log.d(TAG, "Connecting to signalling server: $SERVER_URL")
        val request = Request.Builder()
            .url(SERVER_URL)
            .addHeader("User-Agent", "VideoChat-Android/1.0")  // Идентификация клиента
            .addHeader("Connection", "keep-alive")             // Поддержание соединения
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully!")
                reconnectAttempts = 0  // Сброс счетчика переподключений
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
                attemptReconnect()
            }
        }

        webSocket = client.newWebSocket(request, listener)
    }

    private val heartbeatHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var heartbeatRunnable: Runnable? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5

    private fun startHeartbeat() {
        heartbeatRunnable?.let { heartbeatHandler.removeCallbacks(it) }
        heartbeatRunnable = object : Runnable {
            override fun run() {
                webSocket?.let { ws ->
                    // Проверяем состояние соединения
                    try {
                        ws.send("{\"type\":\"ping\"}")
                        Log.d(TAG, "Sent ping")
                    } catch (e: Exception) {
                        Log.w(TAG, "WebSocket not open, attempting reconnect")
                        attemptReconnect()
                    }
                }
                heartbeatHandler.postDelayed(this, 15000) // Каждые 15 секунд для экономии батареи
            }
        }
        heartbeatHandler.post(heartbeatRunnable!!)
    }

    private fun stopHeartbeat() {
        heartbeatRunnable?.let { heartbeatHandler.removeCallbacks(it) }
        heartbeatRunnable = null
    }

    // Автоматическое переподключение с экспоненциальной задержкой
    private fun attemptReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.e(TAG, "Max reconnect attempts reached")
            runOnUiThread {
                Toast.makeText(this, "Не удалось подключиться к серверу", Toast.LENGTH_LONG).show()
            }
            return
        }

        reconnectAttempts++
        val delay = (1000L * reconnectAttempts * reconnectAttempts).coerceAtMost(30000) // Экспоненциальная задержка до 30 сек
        
        Log.d(TAG, "Attempting reconnect #$reconnectAttempts in ${delay}ms")
        
        heartbeatHandler.postDelayed({
            try {
                connectToSignallingServer()
            } catch (e: Exception) {
                Log.e(TAG, "Reconnect failed: ${e.message}")
                attemptReconnect() // Пробуем снова
            }
        }, delay)
    }

    private fun joinRoom() {
        if (roomId.isBlank() || webSocket == null) {
            Log.e(TAG, "Cannot join room: roomId is blank or websocket is null")
            return
        }
        
        // Добавляем пароль комнаты для защиты
        val roomPassword = intent.getStringExtra("roomPassword") ?: ""
        
        val message = JSONObject().apply {
            put("type", "join")
            put("roomId", roomId)
            put("userId", userId)
            put("roomPassword", roomPassword)
        }
        Log.d(TAG, "Joining secure room: $message")
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
                "error" -> {
                    val errorMsg = json.getString("error")
                    Log.e(TAG, "Server error: $errorMsg")
                    runOnUiThread {
                        Toast.makeText(this, "Ошибка сервера: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message: ${e.message}")
        }
    }

    private fun handleOffer(json: JSONObject) {
        try {
            remoteUserId = json.getString("userId")
            val offerSdp = json.getString("offer")
            Log.d(TAG, "Received offer from: $remoteUserId")
            Log.d(TAG, "Incoming Offer SDP: $offerSdp")
            logAudioSdpDetails(offerSdp)
            
            if (peerConnection == null) {
                Log.e(TAG, "PeerConnection is null when handling offer")
                return
            }
            
            val sdp = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
            peerConnection?.setRemoteDescription(SimpleSdpObserver(), sdp)
            createAnswer()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling offer: ${e.message}")
        }
    }

    private fun handleAnswer(json: JSONObject) {
        try {
            val answerSdp = json.getString("answer")
            Log.d(TAG, "Received answer")
            Log.d(TAG, "Incoming Answer SDP: $answerSdp")
            logAudioSdpDetails(answerSdp)
            
            if (peerConnection == null) {
                Log.e(TAG, "PeerConnection is null when handling answer")
                return
            }
            
            val sdp = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
            peerConnection?.setRemoteDescription(SimpleSdpObserver(), sdp)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling answer: ${e.message}")
        }
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
            
            // Универсальные настройки для максимальной совместимости
            mandatory.add(MediaConstraints.KeyValuePair("audioBitrate", "128000"))  // 128 kbps - универсальный битрейт
            mandatory.add(MediaConstraints.KeyValuePair("audioSamplingRate", "48000"))  // 48 kHz - стандарт WebRTC
            mandatory.add(MediaConstraints.KeyValuePair("audioChannels", "2"))  // Стерео - по умолчанию
        }
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.d(TAG, "Offer created successfully")
                Log.d(TAG, "Offer SDP: ${sessionDescription.description}")
                logAudioSdpDetails(sessionDescription.description)
                
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
            
            // Универсальные настройки для максимальной совместимости
            mandatory.add(MediaConstraints.KeyValuePair("audioBitrate", "128000"))  // 128 kbps - универсальный битрейт
            mandatory.add(MediaConstraints.KeyValuePair("audioSamplingRate", "48000"))  // 48 kHz - стандарт WebRTC
            mandatory.add(MediaConstraints.KeyValuePair("audioChannels", "2"))  // Стерео - по умолчанию
        }
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.d(TAG, "Answer created successfully")
                Log.d(TAG, "Answer SDP: ${sessionDescription.description}")
                logAudioSdpDetails(sessionDescription.description)
                
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
        if (remoteUserId.isBlank()) {
            Log.e(TAG, "Cannot send offer: remoteUserId is empty")
            return
        }
        
        if (webSocket == null) {
            Log.e(TAG, "Cannot send offer: WebSocket is null")
            return
        }
        
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
        if (remoteUserId.isBlank()) {
            Log.e(TAG, "Cannot send answer: remoteUserId is empty")
            return
        }
        
        if (webSocket == null) {
            Log.e(TAG, "Cannot send answer: WebSocket is null")
            return
        }
        
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
        if (remoteUserId.isBlank()) {
            Log.e(TAG, "Cannot send ICE candidate: remoteUserId is empty")
            return
        }
        
        if (webSocket == null) {
            Log.e(TAG, "Cannot send ICE candidate: WebSocket is null")
            return
        }
        
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

    private fun switchCamera() {
        cameraCapturer?.let { capturer ->
            try {
                // Переключаем камеру
                isFrontCamera = !isFrontCamera
                capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                    override fun onCameraSwitchDone(isFrontCameraNew: Boolean) {
                        Log.d(TAG, "Camera switched successfully. Front camera: $isFrontCameraNew")
                        runOnUiThread {
                            // Показываем уведомление о переключении
                            val cameraType = if (isFrontCameraNew) "Передняя" else "Задняя"
                            Toast.makeText(this@VideoCallActivity, "$cameraType камера", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onCameraSwitchError(error: String) {
                        Log.e(TAG, "Camera switch error: $error")
                        runOnUiThread {
                            Toast.makeText(this@VideoCallActivity, "Ошибка переключения камеры", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error switching camera: ${e.message}")
                Toast.makeText(this, "Ошибка переключения камеры", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Log.e(TAG, "Camera capturer is null, cannot switch camera")
            Toast.makeText(this, "Камера недоступна", Toast.LENGTH_SHORT).show()
        }
    }

    // Определение оптимального разрешения видео в зависимости от устройства
    private fun getOptimalVideoResolution(): Pair<Int, Int> {
        return try {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            // Определяем мощность устройства
            val isLowEndDevice = isLowEndDevice()
            
            when {
                isLowEndDevice -> {
                    Log.d(TAG, "Using low resolution for low-end device")
                    Pair(640, 480)  // VGA
                }
                screenWidth >= 1440 -> {
                    Log.d(TAG, "Using high resolution for high-end device")
                    Pair(1920, 1080)  // Full HD
                }
                screenWidth >= 1080 -> {
                    Log.d(TAG, "Using medium resolution for mid-range device")
                    Pair(1280, 720)   // HD
                }
                else -> {
                    Log.d(TAG, "Using standard resolution")
                    Pair(960, 540)    // qHD
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error determining resolution: ${e.message}")
            Pair(1280, 720)  // Fallback to HD
        }
    }

    // Определение оптимального FPS в зависимости от устройства
    private fun getOptimalFps(): Int {
        return if (isLowEndDevice()) {
            15  // Низкий FPS для экономии ресурсов
        } else {
            30  // Стандартный FPS
        }
    }

    // Проверка на низкопроизводительное устройство
    private fun isLowEndDevice(): Boolean {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            
            // Считаем устройство низкопроизводительным если < 2GB RAM
            val isLowMemory = memoryInfo.totalMem < 2L * 1024 * 1024 * 1024
            
            // Проверяем количество ядер процессора
            val coreCount = Runtime.getRuntime().availableProcessors()
            val isLowCores = coreCount < 4
            
            Log.d(TAG, "Device memory: ${memoryInfo.totalMem / (1024 * 1024)}MB, cores: $coreCount")
            
            isLowMemory || isLowCores
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device performance: ${e.message}")
            false  // По умолчанию считаем устройство мощным
        }
    }

    private fun toggleChat() {
        chatLayout.visibility = if (chatLayout.visibility == View.GONE) View.VISIBLE else View.GONE
    }

    private fun sendChatMessage() {
        val message = chatInput.text.toString().trim()
        if (message.isNotEmpty()) {
            if (webSocket == null) {
                Log.e(TAG, "Cannot send chat message: WebSocket is null")
                Toast.makeText(this, "Нет подключения к серверу", Toast.LENGTH_SHORT).show()
                return
            }
            
            if (roomId.isBlank()) {
                Log.e(TAG, "Cannot send chat message: roomId is empty")
                return
            }
            
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
        
        // Останавливаем все таймеры и фоновые процессы
        stopHeartbeat()
        stopCallDurationTimer()
        
        // Корректно закрываем соединения
        try {
            webSocket?.close(1000, "Activity destroyed")
            webSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebSocket: ${e.message}")
        }
        
        try {
            peerConnection?.close()
            peerConnection = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing PeerConnection: ${e.message}")
        }
        
        try {
            cameraCapturer?.stopCapture()
            cameraCapturer?.dispose()
            cameraCapturer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing camera capturer: ${e.message}")
        }
        
        // Освобождаем видео ресурсы
        try {
            localVideoTrack?.removeSink(localVideoView)
            remoteVideoTrack?.removeSink(remoteVideoView)
            localVideoTrack?.dispose()
            remoteVideoTrack?.dispose()
            localVideoTrack = null
            remoteVideoTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing video tracks: ${e.message}")
        }
        
        // Освобождаем аудио ресурсы
        try {
            localAudioTrack?.dispose()
            localAudioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing audio track: ${e.message}")
        }
        
        // Освобождаем SurfaceViewRenderer
        try {
            localVideoView.release()
            remoteVideoView.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing video views: ${e.message}")
        }
        
        // Освобождаем EGL контекст
        try {
            eglBase.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing EGL base: ${e.message}")
        }
        
        // Очищаем PeerConnectionFactory
        try {
            peerConnectionFactory?.dispose()
            peerConnectionFactory = null
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing PeerConnectionFactory: ${e.message}")
        }
        
        Log.d(TAG, "All resources cleaned up successfully")
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

    private fun logAudioSdpDetails(sdp: String) {
        Log.d(TAG, "=== SDP Audio Details ===")
        val lines = sdp.split("\n")
        var inAudioSection = false
        
        for (line in lines) {
            if (line.startsWith("m=")) {
                inAudioSection = line.contains("audio")
                if (inAudioSection) {
                    Log.d(TAG, "Audio media line: $line")
                }
            } else if (inAudioSection && line.startsWith("a=")) {
                Log.d(TAG, "Audio attribute: $line")
                
                // Проверяем кодеки
                if (line.contains("rtpmap")) {
                    Log.d(TAG, "Found codec: $line")
                }
                
                // Проверяем битрейт
                if (line.contains("b=AS")) {
                    Log.d(TAG, "Found bitrate: $line")
                }
                
                // Проверяем FMTP для Opus
                if (line.contains("fmtp") && line.contains("opus")) {
                    Log.d(TAG, "Found Opus parameters: $line")
                }
            }
        }
        Log.d(TAG, "=== End SDP Audio Details ===")
    }

    class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sessionDescription: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(reason: String) {}
        override fun onSetFailure(reason: String) {}
    }
}
