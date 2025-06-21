package com.praveen.cardclash.network

//private const val SERVER_URL = "http://192.168.31.210:3000"
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

object SocketManager {
    private lateinit var socket: Socket
    private const val SERVER_URL = "http://192.168.31.210:3000" // Emulator: http://10.0.2.2:3000
    private val countdownChannel = Channel<Int>()
    private val gameStartChannel = Channel<Unit>()

    val countdownFlow = countdownChannel.receiveAsFlow()
    val gameStartFlow = gameStartChannel.receiveAsFlow()

    fun connect() {
        try {
            val opts = IO.Options().apply {
                transports = arrayOf("websocket")
                forceNew = true
                reconnection = true
                reconnectionAttempts = 5
                reconnectionDelay = 1000
            }
            socket = IO.socket(SERVER_URL, opts)
            socket.on(Socket.EVENT_CONNECT) {
                Log.d("SocketManager", "Connected to server")
            }.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e("SocketManager", "Connection failed: ${args.joinToString()}")
            }.on(Socket.EVENT_DISCONNECT) { args ->
                Log.d("SocketManager", "Disconnected: ${args.joinToString()}")
            }.on("countdown") { args ->
                val value = args[0] as Int
                countdownChannel.trySend(value)
                Log.d("SocketManager", "Received countdown: $value")
            }.on("gameStart") {
                gameStartChannel.trySend(Unit)
                Log.d("SocketManager", "Received gameStart")
            }
            socket.connect()
            Log.d("SocketManager", "Attempting to connect to $SERVER_URL")
        } catch (e: Exception) {
            Log.e("SocketManager", "Socket init failed: ${e.message}")
        }
    }

    fun joinRoom(roomCode: String) {
        if (::socket.isInitialized && socket.connected()) {
            socket.emit("joinRoom", roomCode)
            Log.d("SocketManager", "Joined room: $roomCode")
        } else {
            Log.e("SocketManager", "Cannot join room: Socket not connected")
        }
    }

    fun disconnect() {
        if (::socket.isInitialized) {
            socket.disconnect()
        }
    }

    fun isConnected(): Boolean {
        return ::socket.isInitialized && socket.connected()
    }
}