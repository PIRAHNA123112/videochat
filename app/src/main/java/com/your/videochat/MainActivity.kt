package com.your.videochat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

data class Room(val id: String, val usersCount: Int)

class MainActivity : AppCompatActivity() {

    private lateinit var serverUrlInput: EditText
    private lateinit var roomNameInput: EditText
    private lateinit var createRoomButton: Button
    private lateinit var emulatorButton: Button
    private lateinit var deviceButton: Button
    private lateinit var roomsRecyclerView: RecyclerView
    private lateinit var emptyRoomsText: TextView
    private lateinit var connectionStatus: TextView
    
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    private val rooms = mutableListOf<Room>()
    private lateinit var roomsAdapter: RoomsAdapter

    companion object {
        private const val TAG = "MainActivity"
        // Public server URL - change this to your deployed server
        private var SERVER_URL = "wss://videochat-aend.onrender.com"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        serverUrlInput = findViewById(R.id.serverUrlInput)
        roomNameInput = findViewById(R.id.roomNameInput)
        createRoomButton = findViewById(R.id.createRoomButton)
        emulatorButton = findViewById(R.id.emulatorButton)
        deviceButton = findViewById(R.id.deviceButton)
        roomsRecyclerView = findViewById(R.id.roomsRecyclerView)
        emptyRoomsText = findViewById(R.id.emptyRoomsText)
        connectionStatus = findViewById(R.id.connectionStatus)

        roomsAdapter = RoomsAdapter(rooms) { room ->
            joinRoom(room.id)
        }
        roomsRecyclerView.layoutManager = LinearLayoutManager(this)
        roomsRecyclerView.adapter = roomsAdapter

        createRoomButton.setOnClickListener {
            val roomName = roomNameInput.text.toString().trim()
            if (roomName.isNotEmpty()) {
                joinRoom(roomName)
            } else {
                Toast.makeText(this, "Введите название комнаты", Toast.LENGTH_SHORT).show()
            }
        }

        // Быстрые кнопки URL
        emulatorButton.setOnClickListener {
            serverUrlInput.setText("ws://10.0.2.2:3000")
            connectToServer()
        }

        deviceButton.setOnClickListener {
            serverUrlInput.setText("wss://your-app-name.onrender.com")
            connectToServer()
        }

        // Подключаемся к серверу при изменении URL
        serverUrlInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                connectToServer()
            }
        }

        // Устанавливаем публичный URL по умолчанию
        serverUrlInput.setText(SERVER_URL)
        connectToServer()
    }

    private fun connectToServer() {
        val url = serverUrlInput.text.toString().trim()
        if (url.isEmpty()) return

        SERVER_URL = url
        connectionStatus.text = "Подключение..."
        connectionStatus.setTextColor(0xFFFF6600.toInt())

        webSocket?.close(1000, "Reconnecting")

        val request = Request.Builder().url(SERVER_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                runOnUiThread {
                    connectionStatus.text = "Подключено"
                    connectionStatus.setTextColor(0xFF00AA00.toInt())
                    Log.d(TAG, "WebSocket connected")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val type = json.getString("type")

                    if (type == "rooms-list") {
                        val roomsArray = json.getJSONArray("rooms")
                        val newRooms = mutableListOf<Room>()
                        
                        for (i in 0 until roomsArray.length()) {
                            val roomJson = roomsArray.getJSONObject(i)
                            newRooms.add(Room(
                                roomJson.getString("id"),
                                roomJson.getInt("usersCount")
                            ))
                        }

                        runOnUiThread {
                            rooms.clear()
                            rooms.addAll(newRooms)
                            roomsAdapter.notifyDataSetChanged()
                            
                            if (rooms.isEmpty()) {
                                roomsRecyclerView.visibility = View.GONE
                                emptyRoomsText.visibility = View.VISIBLE
                            } else {
                                roomsRecyclerView.visibility = View.VISIBLE
                                emptyRoomsText.visibility = View.GONE
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                runOnUiThread {
                    connectionStatus.text = "Ошибка подключения: ${t.message}"
                    connectionStatus.setTextColor(0xFFFF0000.toInt())
                    Log.e(TAG, "WebSocket failure: ${t.message}")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                runOnUiThread {
                    connectionStatus.text = "Отключено"
                    connectionStatus.setTextColor(0xFFFF6600.toInt())
                }
            }
        })
    }

    private fun joinRoom(roomId: String) {
        val intent = Intent(this, VideoCallActivity::class.java)
        intent.putExtra("roomId", roomId)
        intent.putExtra("serverUrl", SERVER_URL)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket?.close(1000, "Activity destroyed")
    }
}

class RoomsAdapter(
    private val rooms: List<Room>,
    private val onJoinClick: (Room) -> Unit
) : RecyclerView.Adapter<RoomsAdapter.RoomViewHolder>() {

    class RoomViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val roomNameText: TextView = view.findViewById(R.id.roomNameText)
        val usersCountText: TextView = view.findViewById(R.id.usersCountText)
        val joinButton: Button = view.findViewById(R.id.joinRoomButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room, parent, false)
        return RoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        val room = rooms[position]
        holder.roomNameText.text = room.id
        val usersText = if (room.usersCount == 1) "1 участник" else "${room.usersCount} участников"
        holder.usersCountText.text = usersText
        holder.joinButton.setOnClickListener { onJoinClick(room) }
    }

    override fun getItemCount() = rooms.size
}