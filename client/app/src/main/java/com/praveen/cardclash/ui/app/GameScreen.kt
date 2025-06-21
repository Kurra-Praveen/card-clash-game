package com.praveen.cardclash.ui.app

//@SuppressLint("DefaultLocale")

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.praveen.cardclash.network.RetrofitClient
import com.praveen.cardclash.network.SocketManager
import com.praveen.cardclash.network.StartGameRequest
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import kotlinx.coroutines.CoroutineScope


@Composable
fun GameScreen(
    roomCode: String,
    onGameEnd: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var gameData by remember { mutableStateOf<SocketManager.GameData?>(null) }
    var roundResult by remember { mutableStateOf<SocketManager.RoundResult?>(null) }
    var gameEnd by remember { mutableStateOf<SocketManager.GameEnd?>(null) }
    var challengeState by remember { mutableStateOf<SocketManager.StatQuoted?>(null) }
    var countdownTime by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var selectedCardIndex by remember { mutableStateOf(0) }
    var selectedStat by remember { mutableStateOf("runs") }
    var hasSubmitted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (roomCode.isBlank()) {
            error = "Room code is missing"
            Log.e("GameScreen", "Cannot call /start-game: roomCode is empty")
            isLoading = false
            return@LaunchedEffect
        }
        Log.d("GameScreen", "Socket ID: ${SocketManager.socketId}, isConnected: ${SocketManager.isConnected()}")
        try {
            val response = RetrofitClient.apiService.startGame(StartGameRequest(roomCode))
            if (response.isSuccessful) {
                Log.d("GameScreen", "/start-game successful for roomCode: $roomCode")
            } else {
                error = response.errorBody()?.string() ?: "Failed to start game"
                Log.e("GameScreen", "Failed to start game: $error")
                isLoading = false
            }
        } catch (e: Exception) {
            error = e.message ?: "Network error"
            Log.e("GameScreen", "Network error: $error")
            isLoading = false
        }

        SocketManager.gameDataFlow.collect { data ->
            gameData = data
            isLoading = false
            Log.d("GameScreen", "Received gameData: round=${data.round}, cards=${data.cards.size}, currentTurn=${data.currentTurn}, socketId=${SocketManager.socketId}")
            if (data.players.none { it.socketId == SocketManager.socketId }) {
                error = "Socket ID not found in game data. Please reconnect."
                Log.e("GameScreen", "Socket ID ${SocketManager.socketId} not in players: ${data.players.map { it.socketId }}")
            }
        }
        SocketManager.challengeInitiatedFlow.collect { quoted ->
            Log.d("GameScreen", "Received challengeInitiated: activePlayer=${quoted.activePlayer}, stat=${quoted.stat}, time=${quoted.timeRemaining}, isConfirmation=${quoted.isConfirmation}, socketId=${SocketManager.socketId}")
            if (!quoted.isConfirmation && quoted.activePlayer != SocketManager.socketId) {
                challengeState = quoted
                countdownTime = quoted.timeRemaining
                Log.d("GameScreen", "Set challengeState for opponent: activePlayer=${quoted.activePlayer}, stat=${quoted.stat}, time=$countdownTime")
            } else if (quoted.isConfirmation) {
                Log.d("GameScreen", "Ignored challengeInitiated: isConfirmation=true")
            } else {
                Log.d("GameScreen", "Ignored challengeInitiated: activePlayer is self")
            }
        }
        SocketManager.statQuotedFlow.collect { quoted ->
            Log.d("GameScreen", "Received statQuoted: activePlayer=${quoted.activePlayer}, stat=${quoted.stat}, time=${quoted.timeRemaining}, isConfirmation=${quoted.isConfirmation}, socketId=${SocketManager.socketId}")
            if (!quoted.isConfirmation && challengeState?.activePlayer == quoted.activePlayer && challengeState?.stat == quoted.stat && quoted.activePlayer != SocketManager.socketId) {
                countdownTime = quoted.timeRemaining
                Log.d("GameScreen", "Updated countdown: time=${quoted.timeRemaining}")
            }
        }
        SocketManager.roundResultFlow.collect { result ->
            roundResult = result
            gameData = result.gameState
            selectedCardIndex = 0
            hasSubmitted = false
            challengeState = null
            countdownTime = 0
            Log.d("GameScreen", "Received roundResult: winner=${result.winner}, stat=${result.stat}, socketId=${SocketManager.socketId}")
        }
        SocketManager.gameEndFlow.collect { end ->
            gameEnd = end
            Log.d("GameScreen", "Received gameEnd: winner=${end.winner}, socketId=${SocketManager.socketId}")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .wrapContentHeight(Alignment.Top),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Card Clash - Room: $roomCode", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator()
            Text("Loading game data...")
        } else if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                SocketManager.disconnect()
                SocketManager.connect()
            }) {
                Text("Reconnect")
            }
        } else if (gameEnd != null) {
            Text(
                when {
                    gameEnd!!.winner == SocketManager.socketId -> "You won the game!"
                    gameEnd!!.winner == null -> "Game ended in a draw"
                    else -> "Opponent won the game!"
                },
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Final Scores:")
            gameEnd!!.scores.forEach { (socketId, score) ->
                Text("${if (socketId == SocketManager.socketId) "You" else "Opponent"}: $score")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onGameEnd() }) {
                Text("Return to Lobby")
            }
        } else if (gameData == null) {
            Text("Initializing game...")
        } else {
            gameData?.let { data ->
                // Game Info
                Text("Round: ${data.round}", style = MaterialTheme.typography.bodyLarge)
                Text("Current Turn: ${if (data.currentTurn == SocketManager.socketId) "You" else "Opponent"}")
                Text("Your Cards: ${data.players.find { it.socketId == SocketManager.socketId }?.cardCount ?: 0}")
                Text("Your Score: ${data.scores[SocketManager.socketId] ?: 0}")
                Spacer(modifier = Modifier.height(16.dp))

                // Scores
                Text("Scores:", fontWeight = FontWeight.Bold)
                data.scores.forEach { (socketId, score) ->
                    Text("${if (socketId == SocketManager.socketId) "You" else "Opponent"}: $score")
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Active Player Quoted Stat Banner and Buttons
                challengeState?.let { quoted ->
                    Log.d("GameScreen", "Rendering challengeState: activePlayer=${quoted.activePlayer}, stat=${quoted.stat}, time=$countdownTime, isOpponent=${quoted.activePlayer != SocketManager.socketId}, hasSubmitted=$hasSubmitted")
                    if (countdownTime > 0 && quoted.activePlayer != SocketManager.socketId) {
                        QuotedStatBanner(quoted, countdownTime)
                        if (!hasSubmitted) {
                            OpponentButtons(
                                data = data,
                                quoted = quoted,
                                selectedCardIndex = selectedCardIndex,
                                hasSubmitted = hasSubmitted,
                                roomCode = roomCode,
                                coroutineScope = coroutineScope,
                                onChallengeSubmitted = { hasSubmitted = true },
                                onGaveUpSubmitted = { hasSubmitted = true }
                            )
                        }
                    }
                }

                // Card Selection
                Text("Your Cards:", fontWeight = FontWeight.Bold)
                LazyColumn(
                    modifier = Modifier
                        .height(200.dp)
                        .fillMaxWidth()
                ) {
                    itemsIndexed(data.cards) { index, card ->
                        val isSelectable = (data.currentTurn == SocketManager.socketId || (challengeState?.activePlayer != SocketManager.socketId && countdownTime > 0)) && !hasSubmitted
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .background(if (index == selectedCardIndex && isSelectable) Color.LightGray else Color.Transparent)
                                .clickable(enabled = isSelectable) { selectedCardIndex = index },
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Player: ${card.playerName}", style = MaterialTheme.typography.bodyLarge)
                                Text("Runs: ${card.runs} ${if (getStatStrength(card, "runs") > 0.8) "🔥" else ""}")
                                Text("Wickets: ${card.wickets} ${if (getStatStrength(card, "wickets") > 0.8) "🔥" else ""}")
                                Text("Batting Avg: ${String.format("%.2f", card.battingAverage)} ${if (getStatStrength(card, "battingAverage") > 0.8) "🔥" else ""}")
                                Text("Strike Rate: ${String.format("%.2f", card.strikeRate)} ${if (getStatStrength(card, "strikeRate") > 0.8) "🔥" else ""}")
                                Text("Matches: ${card.matchesPlayed} ${if (getStatStrength(card, "matchesPlayed") > 0.8) "🔥" else ""}")
                                Text("Centuries: ${card.centuries} ${if (getStatStrength(card, "centuries") > 0.8) "🔥" else ""}")
                                Text("Five Wicket Hauls: ${card.fiveWicketHauls} ${if (getStatStrength(card, "fiveWicketHauls") > 0.8) "🔥" else ""}")
                            }
                        }
                    }
                }

                // Stat Selection for Active Player
                if (data.currentTurn == SocketManager.socketId && challengeState == null && !hasSubmitted) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Select Stat to Quote:")
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(listOf(
                            "runs", "wickets", "battingAverage", "strikeRate",
                            "matchesPlayed", "centuries", "fiveWicketHauls"
                        )) { stat ->
                            Button(
                                onClick = { selectedStat = stat },
                                enabled = !hasSubmitted,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedStat == stat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier.width(120.dp)
                            ) {
                                Text(stat.replaceFirstChar { it.uppercase() })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            data.cards.getOrNull(selectedCardIndex)?.let { card ->
                                val value = when (selectedStat) {
                                    "runs" -> card.runs
                                    "wickets" -> card.wickets
                                    "battingAverage" -> card.battingAverage
                                    "strikeRate" -> card.strikeRate
                                    "matchesPlayed" -> card.matchesPlayed
                                    "centuries" -> card.centuries
                                    "fiveWicketHauls" -> card.fiveWicketHauls
                                    else -> 0
                                }
                                coroutineScope.launch {
                                    SocketManager.submitStat(roomCode, selectedCardIndex, selectedStat, value)
                                    hasSubmitted = true
                                    Log.d("GameScreen", "Stat submitted: cardIndex=$selectedCardIndex, stat=$selectedStat, value=$value, socketId=${SocketManager.socketId}")
                                }
                            }
                        },
                        enabled = !hasSubmitted && data.cards.isNotEmpty()
                    ) {
                        Text("Submit ${selectedStat.replaceFirstChar { it.uppercase() }}")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Round Result
                roundResult?.let { result ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Round ${data.round - 1} Result: ${if (result.winner == SocketManager.socketId) "You won!" else if (result.winner.isEmpty()) "No winner!" else "Opponent won!"}",
                        fontWeight = FontWeight.Bold
                    )
                    Text("Stat: ${result.stat.replaceFirstChar { it.uppercase() }}")
                    result.submissions.forEach { (socketId, submission) ->
                        Text("${if (socketId == SocketManager.socketId) "You" else "Opponent"}: ${submission.stat.replaceFirstChar { it.uppercase() }}=${submission.value}")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onGameEnd() },
            modifier = Modifier.padding(16.dp)
        ) {
            Text("End Game (Test)")
        }
    }
}

@Composable
fun QuotedStatBanner(quoted: SocketManager.StatQuoted, countdownTime: Int) {
    Log.d("GameScreen", "Rendering QuotedStatBanner: stat=${quoted.stat}, time=$countdownTime")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(Color.Yellow), // Debug visibility
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Active Player Quoted: ${quoted.stat.replaceFirstChar { it.uppercase() }}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "${countdownTime}s",
                color = Color.Red,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
fun OpponentButtons(
    data: SocketManager.GameData,
    quoted: SocketManager.StatQuoted,
    selectedCardIndex: Int,
    hasSubmitted: Boolean,
    roomCode: String,
    coroutineScope: CoroutineScope,
    onChallengeSubmitted: () -> Unit,
    onGaveUpSubmitted: () -> Unit
) {
    Log.d("GameScreen", "Rendering OpponentButtons: hasSubmitted=$hasSubmitted, cardCount=${data.cards.size}, socketId=${SocketManager.socketId}")
    Text("Respond to Challenge:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(
            onClick = {
                data.cards.getOrNull(selectedCardIndex)?.let { card ->
                    val value = when (quoted.stat) {
                        "runs" -> card.runs
                        "wickets" -> card.wickets
                        "battingAverage" -> card.battingAverage
                        "strikeRate" -> card.strikeRate
                        "matchesPlayed" -> card.matchesPlayed
                        "centuries" -> card.centuries
                        "fiveWicketHauls" -> card.fiveWicketHauls
                        else -> 0
                    }
                    coroutineScope.launch {
                        SocketManager.challenge(roomCode, selectedCardIndex, quoted.stat, value)
                        onChallengeSubmitted()
                        Log.d("GameScreen", "Challenge submitted: cardIndex=$selectedCardIndex, stat=${quoted.stat}, value=$value, socketId=${SocketManager.socketId}")
                    }
                }
            },
            enabled = !hasSubmitted && data.cards.isNotEmpty(),
            modifier = Modifier.width(120.dp)
        ) {
            Text("Challenge")
        }
        Button(
            onClick = {
                coroutineScope.launch {
                    SocketManager.gaveUp(roomCode)
                    onGaveUpSubmitted()
                    Log.d("GameScreen", "GaveUp submitted, socketId=${SocketManager.socketId}")
                }
            },
            enabled = !hasSubmitted,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.width(120.dp)
        ) {
            Text("Gave Up")
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

fun getStatStrength(card: SocketManager.Card, stat: String): Float {
    val maxValues = mapOf(
        "runs" to 10000f,
        "wickets" to 500f,
        "battingAverage" to 100f,
        "strikeRate" to 200f,
        "matchesPlayed" to 500f,
        "centuries" to 50f,
        "fiveWicketHauls" to 10f
    )
    val value = when (stat) {
        "runs" -> card.runs.toFloat()
        "wickets" -> card.wickets.toFloat()
        "battingAverage" -> card.battingAverage
        "strikeRate" -> card.strikeRate
        "matchesPlayed" -> card.matchesPlayed.toFloat()
        "centuries" -> card.centuries.toFloat()
        "fiveWicketHauls" -> card.fiveWicketHauls.toFloat()
        else -> 0f
    }
    return (value / maxValues[stat]!!).coerceIn(0f, 1f)
}