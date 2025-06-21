package com.praveen.cardclash

import android.os.Bundle
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.praveen.cardclash.network.SocketManager
import com.praveen.cardclash.ui.app.LobbyScreen
import com.praveen.cardclash.ui.theme.CardClashGameTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SocketManager.connect()
        setContent {
            CardClashTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LobbyScreen(onGameStart = {
                        // Placeholder for game screen
                    })
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

@Composable
fun WelcomeScreen() {
    Text(text = "Welcome to Card Clash!")
}