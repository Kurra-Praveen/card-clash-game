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
import com.praveen.cardclash.ui.mockups.RefactoredActivePlayerScreen
import com.praveen.cardclash.ui.mockups.RefactoredOpponentScreen
import com.praveen.cardclash.ui.mockups.MockCard
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
    var showResolutionScreen by remember { mutableStateOf(false) }
    var currentRoundResult by remember { mutableStateOf<SocketManager.RoundResult?>(null) }
    var resolutionCountdown by remember { mutableStateOf(5) }

    LaunchedEffect(roomCode) {
        Log.d("GameScreen", "[COLLECTOR] LaunchedEffect(roomCode=$roomCode) started, socketId=${SocketManager.socketId}")
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

        Log.d("GameScreen", "[COLLECTOR] Collecting all flows concurrently...")
        launch {
            Log.d("GameScreen", "[COLLECTOR] gameDataFlow collector STARTED, socketId=${SocketManager.socketId}")
            SocketManager.gameDataFlow.collect { data ->
                Log.d("GameScreen", "[COLLECTOR] gameDataFlow received: $data, socketId=${SocketManager.socketId}")
                gameData = data
                isLoading = false
                Log.d("GameScreen", "Received gameData: round=${data.round}, cards=${data.cards.size}, currentTurn=${data.currentTurn}, socketId=${SocketManager.socketId}")
                if (data.players.none { it.socketId == SocketManager.socketId }) {
                    error = "Socket ID not found in game data. Please reconnect."
                    Log.e("GameScreen", "Socket ID ${SocketManager.socketId} not in players: ${data.players.map { it.socketId }}")
                }
            }
        }
        launch {
            Log.d("GameScreen", "[COLLECTOR] challengeInitiatedFlow collector STARTED, socketId=${SocketManager.socketId}")
            SocketManager.challengeInitiatedFlow.collect { quoted ->
                Log.d("GameScreen", "[COLLECTOR] challengeInitiatedFlow received: $quoted, socketId=${SocketManager.socketId}")
                Log.d("GameScreen", "[UI] challengeInitiated: activePlayer=${quoted.activePlayer}, stat=${quoted.stat}, time=${quoted.timeRemaining}, isConfirmation=${quoted.isConfirmation}, socketId=${SocketManager.socketId}")
                if (!quoted.isConfirmation && quoted.activePlayer != SocketManager.socketId) {
                    challengeState = quoted
                    countdownTime = quoted.timeRemaining
                    hasSubmitted = false
                    Log.d("GameScreen", "[UI] Set challengeState for opponent: $challengeState, countdown=$countdownTime")
                }
            }
        }
        launch {
            Log.d("GameScreen", "[COLLECTOR] statQuotedFlow collector STARTED, socketId=${SocketManager.socketId}")
            SocketManager.statQuotedFlow.collect { quoted ->
                Log.d("GameScreen", "[COLLECTOR] statQuotedFlow received: $quoted, socketId=${SocketManager.socketId}")
                Log.d("GameScreen", "[UI] statQuoted: activePlayer=${quoted.activePlayer}, stat=${quoted.stat}, time=${quoted.timeRemaining}, isConfirmation=${quoted.isConfirmation}, socketId=${SocketManager.socketId}")
                if (!quoted.isConfirmation && challengeState != null && quoted.activePlayer == challengeState?.activePlayer && quoted.stat == challengeState?.stat && quoted.activePlayer != SocketManager.socketId) {
                    countdownTime = quoted.timeRemaining
                    Log.d("GameScreen", "[UI] Updated countdown: $countdownTime")
                }
            }
        }
        launch {
            Log.d("GameScreen", "[COLLECTOR] roundResultFlow collector STARTED, socketId=${SocketManager.socketId}")
            SocketManager.roundResultFlow.collect { result ->
                Log.d("GameScreen", "[COLLECTOR] roundResultFlow received: $result, socketId=${SocketManager.socketId}")
                roundResult = result
                gameData = result.gameState
                selectedCardIndex = 0
                hasSubmitted = false
                challengeState = null
                countdownTime = 0
                showResolutionScreen = true
                currentRoundResult = result
                resolutionCountdown = 5
                // Start countdown
                launch {
                    while (resolutionCountdown > 0) {
                        kotlinx.coroutines.delay(1000)
                        resolutionCountdown--
                    }
                    showResolutionScreen = false
                    currentRoundResult = null
                }
                Log.d("GameScreen", "[UI] Received roundResult: winner=${result.winner}, stat=${result.stat}, socketId=${SocketManager.socketId}")
            }
        }
        launch {
            Log.d("GameScreen", "[COLLECTOR] gameEndFlow collector STARTED, socketId=${SocketManager.socketId}")
            SocketManager.gameEndFlow.collect { end ->
                Log.d("GameScreen", "[COLLECTOR] gameEndFlow received: $end, socketId=${SocketManager.socketId}")
                gameEnd = end
                Log.d("GameScreen", "Received gameEnd: winner=${end.winner}, socketId=${SocketManager.socketId}")
            }
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

        if (showResolutionScreen && currentRoundResult != null) {
            ResolutionScreen(
                roundResult = currentRoundResult!!,
                mySocketId = SocketManager.socketId,
                onContinue = {}, // No manual continue, auto after countdown
                countdown = resolutionCountdown
            )
        } else if (isLoading) {
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
            val isActivePlayer = gameData!!.currentTurn == SocketManager.socketId
            val isChallenged = challengeState?.let { it.activePlayer != SocketManager.socketId && countdownTime > 0 && !hasSubmitted } == true

            // Convert real cards to MockCard for UI
            val myCards = gameData!!.cards.map {
                MockCard(
                    playerName = it.playerName,
                    runs = it.runs,
                    wickets = it.wickets,
                    battingAverage = it.battingAverage,
                    strikeRate = it.strikeRate,
                    matchesPlayed = it.matchesPlayed,
                    centuries = it.centuries,
                    fiveWicketHauls = it.fiveWicketHauls
                )
            }
            val myScore = gameData!!.scores[SocketManager.socketId] ?: 0
            val round = gameData!!.round

            when {
                isActivePlayer -> {
                    RefactoredActivePlayerScreen(
                        round = round,
                        yourCards = myCards,
                        selectedCardIndex = selectedCardIndex,
                        onSelectCard = { selectedCardIndex = it },
                        selectedStat = selectedStat,
                        onStatSelected = { selectedStat = it },
                        onSubmitStat = {
                            val card = gameData!!.cards.getOrNull(selectedCardIndex)
                            val value = when (selectedStat) {
                                "runs" -> card?.runs ?: 0
                                "wickets" -> card?.wickets ?: 0
                                "battingAverage" -> card?.battingAverage ?: 0
                                "strikeRate" -> card?.strikeRate ?: 0
                                "matchesPlayed" -> card?.matchesPlayed ?: 0
                                "centuries" -> card?.centuries ?: 0
                                "fiveWicketHauls" -> card?.fiveWicketHauls ?: 0
                                else -> 0
                            }
                            coroutineScope.launch {
                                SocketManager.submitStat(roomCode, selectedCardIndex, selectedStat, value)
                                hasSubmitted = true
                            }
                        },
                        yourScore = myScore
                    )
                }
                else -> {
                    RefactoredOpponentScreen(
                        round = round,
                        yourCards = myCards,
                        selectedCardIndex = selectedCardIndex,
                        onSelectCard = { selectedCardIndex = it },
                        challengeStat = challengeState?.stat,
                        timer = if (isChallenged) countdownTime else null,
                        hasSubmitted = hasSubmitted,
                        onChallenge = {
                            val card = gameData!!.cards.getOrNull(selectedCardIndex)
                            val value = when (challengeState?.stat) {
                                "runs" -> card?.runs ?: 0
                                "wickets" -> card?.wickets ?: 0
                                "battingAverage" -> card?.battingAverage ?: 0
                                "strikeRate" -> card?.strikeRate ?: 0
                                "matchesPlayed" -> card?.matchesPlayed ?: 0
                                "centuries" -> card?.centuries ?: 0
                                "fiveWicketHauls" -> card?.fiveWicketHauls ?: 0
                                else -> 0
                            }
                            coroutineScope.launch {
                                SocketManager.challenge(roomCode, selectedCardIndex, challengeState?.stat ?: "", value)
                                hasSubmitted = true
                            }
                        },
                        onGiveUp = {
                            coroutineScope.launch {
                                SocketManager.gaveUp(roomCode)
                                hasSubmitted = true
                            }
                        },
                        yourScore = myScore
                    )
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
fun ActivePlayerScreen(
    gameData: SocketManager.GameData,
    challengeState: SocketManager.StatQuoted?,
    hasSubmitted: Boolean,
    selectedCardIndex: Int,
    setSelectedCardIndex: (Int) -> Unit,
    selectedStat: String,
    setSelectedStat: (String) -> Unit,
    onSubmitStat: (String, Number) -> Unit
) {
    // Game Info
    Text("Round: ${gameData.round}", style = MaterialTheme.typography.bodyLarge)
    Text("Current Turn: You")
    Text("Your Cards: ${gameData.players.find { it.socketId == SocketManager.socketId }?.cardCount ?: 0}")
    Text("Your Score: ${gameData.scores[SocketManager.socketId] ?: 0}")
    Spacer(modifier = Modifier.height(16.dp))

    // Card Selection
    Text("Your Cards:", fontWeight = FontWeight.Bold)
    LazyColumn(
        modifier = Modifier
            .height(200.dp)
            .fillMaxWidth()
    ) {
        itemsIndexed(gameData.cards) { index, card ->
            val isSelectable = !hasSubmitted
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(if (index == selectedCardIndex && isSelectable) Color.LightGray else Color.Transparent)
                    .clickable(enabled = isSelectable) { setSelectedCardIndex(index) },
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

    // Stat Selection
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
                onClick = { setSelectedStat(stat) },
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
            gameData.cards.getOrNull(selectedCardIndex)?.let { card ->
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
                onSubmitStat(selectedStat, value)
            }
        },
        enabled = !hasSubmitted && gameData.cards.isNotEmpty()
    ) {
        Text("Submit ${selectedStat.replaceFirstChar { it.uppercase() }}")
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
fun OpponentScreen(
    gameData: SocketManager.GameData,
    challengeState: SocketManager.StatQuoted?,
    countdownTime: Int,
    hasSubmitted: Boolean,
    selectedCardIndex: Int,
    setSelectedCardIndex: (Int) -> Unit,
    onChallenge: (String, Number) -> Unit,
    onGaveUp: () -> Unit
) {
    // Game Info
    Log.d("OpponentScreen", "RENDER OpponentScreen: challengeState=$challengeState, countdownTime=$countdownTime, hasSubmitted=$hasSubmitted, activePlayer=${challengeState?.activePlayer}, mySocketId=${SocketManager.socketId}")
    Text("Round: ${gameData.round}", style = MaterialTheme.typography.bodyLarge)
    Text("Current Turn: Opponent")
    Text("Your Cards: ${gameData.players.find { it.socketId == SocketManager.socketId }?.cardCount ?: 0}")
    Text("Your Score: ${gameData.scores[SocketManager.socketId] ?: 0}")
    Spacer(modifier = Modifier.height(16.dp))

    // Challenge Banner and Buttons
    val shouldShowBanner = challengeState != null && challengeState.activePlayer != SocketManager.socketId
    Log.d("OpponentScreen", "shouldShowBanner=$shouldShowBanner, challengeState=$challengeState, activePlayer=${challengeState?.activePlayer}, mySocketId=${SocketManager.socketId}")
    if (shouldShowBanner) {
        Log.d("OpponentScreen", "SHOWING BANNER: stat=${challengeState?.stat}, time=$countdownTime, hasSubmitted=$hasSubmitted")
        QuotedStatBanner(challengeState!!, countdownTime)
        if (!hasSubmitted) {
            Log.d("OpponentScreen", "SHOWING BUTTONS: hasSubmitted=$hasSubmitted, selectedCardIndex=$selectedCardIndex")
            OpponentButtons(
                data = gameData,
                quoted = challengeState!!,
                selectedCardIndex = selectedCardIndex,
                hasSubmitted = hasSubmitted,
                roomCode = "", // Not needed, handled in parent
                coroutineScope = rememberCoroutineScope(), // Not used, handled in parent
                onChallengeSubmitted = { onChallenge(challengeState.stat, 0) }, // Value set below
                onGaveUpSubmitted = { onGaveUp() }
            )
        } else {
            Log.d("OpponentScreen", "BUTTONS HIDDEN: hasSubmitted=$hasSubmitted")
        }
    } else {
        Log.d("OpponentScreen", "BANNER NOT SHOWN: shouldShowBanner=$shouldShowBanner")
    }

    // Card Selection
    Text("Your Cards:", fontWeight = FontWeight.Bold)
    LazyColumn(
        modifier = Modifier
            .height(200.dp)
            .fillMaxWidth()
    ) {
        itemsIndexed(gameData.cards) { index, card ->
            val isSelectable = (challengeState != null && challengeState.activePlayer != SocketManager.socketId && !hasSubmitted)
            Log.d("OpponentScreen", "Card index=$index, isSelectable=$isSelectable, selectedCardIndex=$selectedCardIndex")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(if (index == selectedCardIndex && isSelectable) Color.LightGray else Color.Transparent)
                    .clickable(enabled = isSelectable) { setSelectedCardIndex(index) },
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

    // Challenge/Respond Buttons
    if (challengeState != null && challengeState.activePlayer != SocketManager.socketId && countdownTime > 0 && !hasSubmitted) {
        val card = gameData.cards.getOrNull(selectedCardIndex)
        val value = when (challengeState.stat) {
            "runs" -> card?.runs ?: 0
            "wickets" -> card?.wickets ?: 0
            "battingAverage" -> card?.battingAverage ?: 0
            "strikeRate" -> card?.strikeRate ?: 0
            "matchesPlayed" -> card?.matchesPlayed ?: 0
            "centuries" -> card?.centuries ?: 0
            "fiveWicketHauls" -> card?.fiveWicketHauls ?: 0
            else -> 0
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { onChallenge(challengeState.stat, value) },
                enabled = !hasSubmitted && gameData.cards.isNotEmpty(),
                modifier = Modifier.width(120.dp)
            ) {
                Text("Challenge")
            }
            Button(
                onClick = { onGaveUp() },
                enabled = !hasSubmitted,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.width(120.dp)
            ) {
                Text("Gave Up")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
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

@Composable
fun ResolutionScreen(
    roundResult: SocketManager.RoundResult,
    mySocketId: String,
    onContinue: () -> Unit,
    countdown: Int
) {
    val winner = roundResult.winner
    val stat = roundResult.stat
    val submissions = roundResult.submissions
    val gameState = roundResult.gameState
    val scores = roundResult.scores
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Round Resolution", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            when {
                winner == null -> "Round Draw!"
                winner == mySocketId -> "You won the round!"
                else -> "Opponent won the round!"
            },
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Stat: ${stat?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Submissions:", fontWeight = FontWeight.Bold)
        submissions.forEach { (sid, sub) ->
            val playerLabel = if (sid == mySocketId) "You" else "Opponent"
            Text("$playerLabel: ${sub.stat} = ${sub.value}")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("All Player Top Cards:", fontWeight = FontWeight.Bold)
        val revealedCards = roundResult.revealedCards as? Map<String, SocketManager.Card> ?: emptyMap()
        Column(modifier = Modifier.fillMaxWidth()) {
            revealedCards.forEach { (sid, card) ->
                val playerLabel = if (sid == mySocketId) "You" else "Opponent"
                Card(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth()
                        .background(
                            if (winner == sid) Color(0xFFD4EDDA) else Color.White
                        ),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text("$playerLabel", fontWeight = FontWeight.Bold)
                        Text("Player: ${card.playerName}")
                        Text("Runs: ${card.runs}")
                        Text("Wickets: ${card.wickets}")
                        Text("Batting Avg: ${String.format("%.2f", card.battingAverage)}")
                        Text("Strike Rate: ${String.format("%.2f", card.strikeRate)}")
                        Text("Matches: ${card.matchesPlayed}")
                        Text("Centuries: ${card.centuries}")
                        Text("Five Wicket Hauls: ${card.fiveWicketHauls}")
                        if (winner == sid) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Winner!", color = Color(0xFF155724), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Scores:", fontWeight = FontWeight.Bold)
        scores.forEach { (sid, score) ->
            val playerLabel = if (sid == mySocketId) "You" else "Opponent"
            Text("$playerLabel: $score")
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Continuing in $countdown seconds...", fontWeight = FontWeight.Bold)
    }
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