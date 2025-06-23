package com.praveen.cardclash.ui.app

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.praveen.cardclash.network.CreateRoomRequest
import com.praveen.cardclash.network.JoinRoomRequest
import com.praveen.cardclash.network.RetrofitClient
import com.praveen.cardclash.network.RoomResponse
import com.praveen.cardclash.network.SocketManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LobbyScreen(
    onGameStart: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var roomCode by remember { mutableStateOf("") }
    var maxPlayers by remember { mutableStateOf("2") }
    var playerCount by remember { mutableStateOf(0) }
    var maxPlayersInt by remember { mutableStateOf(2) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isRoomJoined by remember { mutableStateOf(false) }

    // Use a stable scope for collecting flows
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            SocketManager.countdownFlow.collectLatest { value ->
                countdown = value
                Log.d("LobbyScreen", "Received countdown: $value")
            }
        }
        coroutineScope.launch {
            SocketManager.gameStartFlow.collectLatest {
                Log.d("LobbyScreen", "Game start received, isRoomJoined: $isRoomJoined, roomCode: $roomCode")
                if (isRoomJoined && roomCode.isNotBlank()) {
                    Log.d("LobbyScreen", "Navigating with roomCode: $roomCode")
                    onGameStart(roomCode)
                } else {
                    Log.e("LobbyScreen", "Cannot navigate: isRoomJoined=$isRoomJoined, roomCode=$roomCode")
                    error = "Room not properly initialized"
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .fillMaxWidth(0.95f),
            elevation = CardDefaults.cardElevation(12.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Game Logo/Icon
                Text("🃏", fontSize = MaterialTheme.typography.displayLarge.fontSize)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Card Clash Lobby", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))

                if (isLoading) {
                    CircularProgressIndicator()
                } else if (error.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("❌", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                } else if (countdown != null) {
                    Text("Game starts in $countdown...", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                } else if (isRoomJoined) {
                    Text("Waiting for players in room:", style = MaterialTheme.typography.bodyLarge)
                    Text(roomCode, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Players:", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("$playerCount/$maxPlayersInt", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = playerCount / maxPlayersInt.toFloat(),
                        modifier = Modifier.fillMaxWidth(0.7f)
                    )
                } else {
                    OutlinedTextField(
                        value = roomCode,
                        onValueChange = { roomCode = it.uppercase() },
                        label = { Text("Room Code") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = maxPlayers,
                        onValueChange = { maxPlayers = it },
                        label = { Text("Max Players (2-6)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                val max = maxPlayers.toIntOrNull()
                                if (max == null || max < 2 || max > 6) {
                                    error = "Max players must be 2-6"
                                    Log.e("LobbyScreen", "Invalid maxPlayers: $maxPlayers")
                                    return@Button
                                }
                                coroutineScope.launch {
                                    isLoading = true
                                    try {
                                        val response = RetrofitClient.apiService.createRoom(
                                            CreateRoomRequest(max)
                                        )
                                        Log.d("LobbyScreen", "Create room response: ${response.code()}")
                                        if (response.isSuccessful) {
                                            response.body()?.let { room: RoomResponse ->
                                                roomCode = room.roomCode
                                                playerCount = room.playerCount
                                                maxPlayersInt = room.maxPlayers
                                                SocketManager.joinRoom(roomCode)
                                                isRoomJoined = true
                                                Log.d("LobbyScreen", "Created room: $roomCode, playerCount: $playerCount, maxPlayers: $maxPlayersInt")
                                            } ?: run {
                                                error = "Invalid response from server"
                                                Log.e("LobbyScreen", "Create room failed: Empty response body")
                                            }
                                        } else {
                                            error = response.errorBody()?.string() ?: "Failed to create room"
                                            Log.e("LobbyScreen", "Create room failed: $error")
                                        }
                                    } catch (e: Exception) {
                                        error = e.message ?: "Network error"
                                        Log.e("LobbyScreen", "Network error: $error")
                                    }
                                    isLoading = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.filledTonalButtonColors()
                        ) {
                            Text("Create Room")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = {
                                if (roomCode.isBlank()) {
                                    error = "Enter a room code"
                                    Log.e("LobbyScreen", "Empty roomCode")
                                    return@Button
                                }
                                coroutineScope.launch {
                                    isLoading = true
                                    try {
                                        val response = RetrofitClient.apiService.joinRoom(
                                            JoinRoomRequest(roomCode)
                                        )
                                        Log.d("LobbyScreen", "Join room response: ${response.code()}")
                                        if (response.isSuccessful) {
                                            response.body()?.let { room: RoomResponse ->
                                                roomCode = room.roomCode
                                                playerCount = room.playerCount
                                                maxPlayersInt = room.maxPlayers
                                                SocketManager.joinRoom(roomCode)
                                                isRoomJoined = true
                                                Log.d("LobbyScreen", "Joined room: $roomCode, playerCount: $playerCount, maxPlayers: $maxPlayersInt")
                                            } ?: run {
                                                error = "Invalid response from server"
                                                Log.e("LobbyScreen", "Join room failed: Empty response body")
                                            }
                                        } else {
                                            error = response.errorBody()?.string() ?: "Failed to join room"
                                            Log.e("LobbyScreen", "Join room failed: $error")
                                        }
                                    } catch (e: Exception) {
                                        error = e.message ?: "Network error"
                                        Log.e("LobbyScreen", "Network error: $error")
                                    }
                                    isLoading = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.filledTonalButtonColors()
                        ) {
                            Text("Join Room")
                        }
                    }
                }
            }
        }
    }
}