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
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        SocketManager.countdownFlow.collect { value ->
            countdown = value
            if (value == 1) {
                onGameStart()
            }
        }
        SocketManager.gameStartFlow.collect {
            countdown = null
            onGameStart()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Card Clash Lobby", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        // Create Room
        OutlinedTextField(
            value = maxPlayers,
            onValueChange = { maxPlayers = it.filter { c -> c.isDigit() } },
            label = { Text("Max Players (2-6)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                isLoading = true
                error = ""
                coroutineScope.launch {
                    try {
                        val maxPlayersInt = maxPlayers.toIntOrNull() ?: 2
                        val response = RetrofitClient.apiService.createRoom(
                            CreateRoomRequest(maxPlayersInt)
                        )
                        if (response.isSuccessful) {
                            val roomResponse = response.body()
                            roomCode = roomResponse?.roomCode ?: ""
                            playerCount = roomResponse?.playerCount ?: 0
                            maxPlayers = roomResponse?.maxPlayers?.toString() ?: ""
                            SocketManager.joinRoom(roomCode)
                        } else {
                            error = response.errorBody()?.string() ?: "Failed to create room"
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "Network error"
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading && maxPlayers.toIntOrNull() in 2..6,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create Room")
        }

        // Join Room
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = roomCode,
            onValueChange = { roomCode = it.uppercase() },
            label = { Text("Room Code") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                isLoading = true
                error = ""
                coroutineScope.launch {
                    try {
                        val response = RetrofitClient.apiService.joinRoom(
                            JoinRoomRequest(roomCode)
                        )
                        if (response.isSuccessful) {
                            val roomResponse = response.body()
                            playerCount = roomResponse?.playerCount ?: 0
                            maxPlayers = roomResponse?.maxPlayers?.toString() ?: ""
                            SocketManager.joinRoom(roomCode)
                        } else {
                            error = response.errorBody()?.string() ?: "Failed to join room"
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "Network error"
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading && roomCode.length >= 6,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Join Room")
        }

        // Room Info
        if (roomCode.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Room Code: $roomCode", style = MaterialTheme.typography.bodyLarge)
            Text("Players: $playerCount/$maxPlayers", style = MaterialTheme.typography.bodyLarge)
        }
        countdown?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Game starts in $it...", style = MaterialTheme.typography.headlineSmall)
        }
        if (error.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        if (isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }
    }
}