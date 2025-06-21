package com.praveen.cardclash

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.praveen.cardclash.network.SocketManager
import com.praveen.cardclash.ui.app.GameScreen
import com.praveen.cardclash.ui.app.LobbyScreen
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SocketManager.connect()
        setContent {
            CardClashTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var screenState by remember { mutableStateOf("lobby") }
                    var roomCode by remember { mutableStateOf("") }

                    Log.d("MainActivity", "Current screenState: $screenState, roomCode: $roomCode")

                    when (screenState) {
                        "lobby" -> LobbyScreen(
                            onGameStart = { code ->
                                if (code.isNotBlank()) {
                                    roomCode = code.uppercase()
                                    Log.d("MainActivity", "LobbyScreen triggered navigation with roomCode: $roomCode")
                                    screenState = "game"
                                } else {
                                    Log.e("MainActivity", "LobbyScreen provided empty roomCode")
                                }
                            }
                        )
                        "game" -> if (roomCode.isNotBlank()) {
                            Log.d("MainActivity", "Rendering GameScreen with roomCode: $roomCode")
                            GameScreen(
                                roomCode = roomCode,
                                onGameEnd = {
                                    Log.d("MainActivity", "Game ended, returning to LobbyScreen")
                                    screenState = "lobby"
                                    roomCode = ""
                                }
                            )
                        } else {
                            Log.e("MainActivity", "Cannot show GameScreen: roomCode is empty")
                            screenState = "lobby"
                            LobbyScreen(
                                onGameStart = { code ->
                                    if (code.isNotBlank()) {
                                        roomCode = code.uppercase()
                                        Log.d("MainActivity", "LobbyScreen triggered navigation with roomCode: $roomCode")
                                        screenState = "game"
                                    } else {
                                        Log.e("MainActivity", "LobbyScreen provided empty roomCode")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        SocketManager.disconnect()
        super.onDestroy()
    }
}
@Composable
fun CardClashTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        content()
    }
}