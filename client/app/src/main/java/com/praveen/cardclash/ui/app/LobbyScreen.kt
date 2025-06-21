package com.praveen.cardclash.ui.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.praveen.cardclash.network.CreateRoomRequest
import com.praveen.cardclash.network.JoinRoomRequest
import com.praveen.cardclash.network.RetrofitClient
import com.praveen.cardclash.network.SocketManager
import kotlinx.coroutines.launch

@Composable
fun LobbyScreen(
    onGameStart: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var roomCode by remember { mutableStateOf("") }
    var maxPlayers by remember { mutableStateOf("") }
    var playerCount by remember { mutableStateOf(0) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        SocketManager.countdownFlow.collect { value ->
            countdown = value
            if (value == 1) {
                onGameStart()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Card Clash Lobby", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // Create Room
        OutlinedTextField(
            value = maxPlayers,
            onValueChange = { maxPlayers = it },
            label = { Text("Max Players (2-6)") }
        )
        Button(
            onClick = {
                coroutineScope.launch {
                    try {
                        val response = RetrofitClient.apiService.createRoom(
                            CreateRoomRequest(maxPlayers.toIntOrNull() ?: 2)
                        )
                        if (response.isSuccessful) {
                            roomCode = response.body()?.roomCode ?: ""
                            playerCount = response.body()?.playerCount ?: 0
                            SocketManager.joinRoom(roomCode)
                        } else {
                            error = "Failed to create room"
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "Error"
                    }
                }
            },
            enabled = maxPlayers.toIntOrNull() in 2..6
        ) {
            Text("Create Room")
        }

        // Join Room
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = roomCode,
            onValueChange = { roomCode = it },
            label = { Text("Room Code") }
        )
        Button(
            onClick = {
                coroutineScope.launch {
                    try {
                        val response = RetrofitClient.apiService.joinRoom(
                            JoinRoomRequest(roomCode)
                        )
                        if (response.isSuccessful) {
                            playerCount = response.body()?.playerCount ?: 0
                            SocketManager.joinRoom(roomCode)
                        } else {
                            error = "Failed to join room"
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "Error"
                    }
                }
            }
        ) {
            Text("Join Room")
        }

        // Display Room Info
        if (roomCode.isNotEmpty()) {
            Text("Room Code: $roomCode")
            Text("Players: $playerCount/${maxPlayers.toIntOrNull() ?: 2}")
        }
        countdown?.let { Text("Game starts in $it...") }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
    }
}