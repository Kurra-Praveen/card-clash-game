package com.praveen.cardclash.ui.mockups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// --- Mock Data Classes ---
data class MockPlayer(val name: String, val score: Int, val cardsLeft: Int, val isActive: Boolean)
data class MockCard(
    val playerName: String,
    val runs: Int,
    val wickets: Int,
    val battingAverage: Float,
    val strikeRate: Float,
    val matchesPlayed: Int,
    val centuries: Int,
    val fiveWicketHauls: Int
)

// --- Modernized Game Info Bar (Reusable) ---
@Composable
fun GameInfoBar(round: Int, currentTurn: String, yourCards: Int, yourScore: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = Brush.horizontalGradient(
                listOf(
                    Color(0xFF43CEA2), // Cricket green
                    Color(0xFF185A9D)
                )
            ).let { Color.Transparent },
            contentColor = Color.White
        )
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF43CEA2),
                            Color(0xFF185A9D)
                        )
                    )
                )
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = "Round", tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("Round $round", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, contentDescription = "Turn", tint = Color.Yellow)
                    Spacer(Modifier.width(4.dp))
                    Surface(
                        color = if (currentTurn == "You") Color(0xFF4CAF50) else Color(0xFFF44336),
                        shape = MaterialTheme.shapes.small,
                        shadowElevation = 2.dp
                    ) {
                        Text(
                            text = if (currentTurn == "You") "Your Turn" else "Opponent",
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.List, contentDescription = "Cards", tint = Color.White)
                    Spacer(Modifier.width(2.dp))
                    Text("$yourCards", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Filled.Star, contentDescription = "Score", tint = Color(0xFFFFD600))
                    Spacer(Modifier.width(2.dp))
                    Text("$yourScore", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- Card List (Reusable) ---
@Composable
fun CardList(cards: List<MockCard>, selectedIndex: Int?, onSelect: (Int) -> Unit, isSelectable: Boolean) {
    LazyColumn(
        modifier = Modifier
            .height(200.dp)
            .fillMaxWidth()
    ) {
        itemsIndexed (cards) { index, card ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(if (selectedIndex == index && isSelectable) Color.LightGray else Color.Transparent)
                    .clickable(enabled = isSelectable) { onSelect(index) },
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Player: ${card.playerName}", style = MaterialTheme.typography.bodyLarge)
                    Text("Runs: ${card.runs}")
                    Text("Wickets: ${card.wickets}")
                    Text("Avg: ${card.battingAverage}")
                    Text("SR: ${card.strikeRate}")
                    Text("Matches: ${card.matchesPlayed}")
                    Text("100s: ${card.centuries}")
                    Text("5W: ${card.fiveWicketHauls}")
                }
            }
        }
    }
}

// --- Stat Selection Bar (Active Player, Modernized) ---
@Composable
fun StatSelectionBar(selectedStat: String?, onStatSelected: (String) -> Unit, enabled: Boolean) {
    val statLabels = listOf(
        "Runs", "Wickets", "Batting Avg", "Strike Rate",
        "Matches", "Centuries", "5W Hauls"
    )
    val statKeys = listOf(
        "runs", "wickets", "battingAverage", "strikeRate",
        "matchesPlayed", "centuries", "fiveWicketHauls"
    )
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(statLabels) { idx, label ->
            val key = statKeys[idx]
            ElevatedButton(
                onClick = { onStatSelected(key) },
                enabled = enabled,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = if (selectedStat == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (selectedStat == key) Color.White else MaterialTheme.colorScheme.onSurface
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .widthIn(min = 90.dp, max = 120.dp)
                    .height(44.dp)
            ) {
                Text(
                    label,
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }
}

// --- Submit Stat Button (Active Player) ---
@Composable
fun SubmitStatButton(selectedStat: String?, enabled: Boolean, onSubmit: () -> Unit) {
    Button(
        onClick = onSubmit,
        enabled = enabled && selectedStat != null,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text("Submit ${selectedStat?.replaceFirstChar { it.uppercase() } ?: ""}")
    }
}

// --- Modern Circular Timer (Reusable) ---
@Composable
fun ModernCircularTimer(timer: Int, total: Int, modifier: Modifier = Modifier) {
    val progress = timer / total.toFloat()
    val color = when {
        progress > 0.5f -> Color(0xFF4CAF50) // Green
        progress > 0.2f -> Color(0xFFFFC107) // Yellow
        else -> Color(0xFFF44336) // Red
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(48.dp)
    ) {
        CircularProgressIndicator(
            progress = progress,
            color = color,
            strokeWidth = 6.dp,
            modifier = Modifier.fillMaxSize()
        )
        Text(
            "$timer",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

// --- Challenge Banner (Opponent, Modernized Timer) ---
@Composable
fun ChallengeBanner(stat: String, timer: Int, totalTime: Int = 20) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
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
                "Active Player Quoted: ${stat.replaceFirstChar { it.uppercase() }}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
            ModernCircularTimer(timer = timer, total = totalTime)
        }
    }
}

// --- Challenge Response Buttons (Opponent) ---
@Composable
fun ChallengeResponseButtons(onChallenge: () -> Unit, onGiveUp: () -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(
            onClick = onChallenge,
            enabled = enabled,
            modifier = Modifier.width(120.dp)
        ) {
            Text("Challenge")
        }
        Button(
            onClick = onGiveUp,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.width(120.dp)
        ) {
            Text("Gave Up")
        }
    }
}

// --- Stylish Top Card Display (Reusable) ---
@Composable
fun StylishTopCard(
    card: MockCard?,
    isSelectable: Boolean,
    isSelected: Boolean,
    onClick: (() -> Unit)? = null
) {
    if (card == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color.LightGray, shape = MaterialTheme.shapes.extraLarge)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No Cards Left", style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(16.dp)
            .then(
                if (isSelectable && onClick != null) Modifier.clickable { onClick() } else Modifier
            ),
        elevation = CardDefaults.cardElevation(12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = Brush.linearGradient(
                listOf(
                    Color(0xFF43CEA2), // Cricket green
                    Color(0xFF185A9D)  // Blue accent
                )
            ).let { BrushColor -> Color.Transparent }, // For Compose 1.6+, use Brush as background
            contentColor = Color.White
        )) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF43CEA2),
                            Color(0xFF185A9D)
                        )
                    )
                )
        ) {
            // Glossy overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally // Center all text horizontally
            ) {
                Text(
                    card.playerName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Runs", style = MaterialTheme.typography.labelMedium)
                        Text("${card.runs}", fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Wickets", style = MaterialTheme.typography.labelMedium)
                        Text("${card.wickets}", fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Avg", style = MaterialTheme.typography.labelMedium)
                        Text("${card.battingAverage}", fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SR", style = MaterialTheme.typography.labelMedium)
                        Text("${card.strikeRate}", fontWeight = FontWeight.Bold)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Matches", style = MaterialTheme.typography.labelSmall)
                        Text("${card.matchesPlayed}")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("100s", style = MaterialTheme.typography.labelSmall)
                        Text("${card.centuries}")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("5W", style = MaterialTheme.typography.labelSmall)
                        Text("${card.fiveWicketHauls}")
                    }
                }
            }
        }
    }
}

// --- Refactored Active Player Screen (Top Card Only) ---
@Composable
fun RefactoredActivePlayerScreen(
    round: Int,
    yourCards: List<MockCard>,
    selectedCardIndex: Int?,
    onSelectCard: (Int) -> Unit,
    selectedStat: String?,
    onStatSelected: (String) -> Unit,
    onSubmitStat: () -> Unit,
    yourScore: Int,
    hasSubmitted: Boolean // NEW PARAM
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            GameInfoBar(round = round, currentTurn = "You", yourCards = yourCards.size, yourScore = yourScore)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StylishTopCard(
                card = yourCards.firstOrNull(),
                isSelectable = !hasSubmitted,
                isSelected = true,
                onClick = { if (!hasSubmitted) onSelectCard(0) }
            )
            StatSelectionBar(selectedStat = selectedStat, onStatSelected = onStatSelected, enabled = !hasSubmitted)
            if (!hasSubmitted) {
                SubmitStatButton(selectedStat = selectedStat, enabled = true, onSubmit = onSubmitStat)
            } else {
                Button(onClick = {}, enabled = false, modifier = Modifier.padding(vertical = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                    Text("Submitted")
                }
                Text("Waiting for other players...", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

// --- Refactored Opponent Screen (Top Card Only) ---
@Composable
fun RefactoredOpponentScreen(
    round: Int,
    yourCards: List<MockCard>,
    selectedCardIndex: Int?,
    onSelectCard: (Int) -> Unit,
    challengeStat: String?,
    timer: Int?,
    hasSubmitted: Boolean,
    onChallenge: () -> Unit,
    onGiveUp: () -> Unit,
    yourScore: Int
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            GameInfoBar(round = round, currentTurn = "Opponent", yourCards = yourCards.size, yourScore = yourScore)
            if (challengeStat != null && timer != null) {
                ChallengeBanner(stat = challengeStat, timer = timer)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StylishTopCard(
                card = yourCards.firstOrNull(),
                isSelectable = !hasSubmitted,
                isSelected = true,
                onClick = { onSelectCard(0) }
            )
            if (challengeStat != null && timer != null && !hasSubmitted) {
                ChallengeResponseButtons(onChallenge = onChallenge, onGiveUp = onGiveUp, enabled = true)
            }
        }
    }
}

// --- TablePlayer Data Class (for Table Layout) ---
data class TablePlayer(
    val name: String,
    val isLocal: Boolean,
    val card: MockCard?,
    val cardsLeft: Int,
    val revealed: Boolean
)

// --- GameTableLayout Composable (Stub/Minimal) ---
@Composable
fun GameTableLayout(
    players: List<TablePlayer>,
    onLocalCardClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Minimal placeholder for compilation; replace with real layout as needed
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        players.forEach { player ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StylishTopCard(
                    card = if (player.revealed) player.card else null,
                    isSelectable = false,
                    isSelected = false
                )
                Text(player.name, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// --- Resolution Screen (Results) ---
@Composable
fun ResolutionScreen(
    round: Int,
    players: List<TablePlayer>,
    winnerName: String?,
    yourScore: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // --- Game Table Layout with all cards revealed ---
        GameTableLayout(
            players = players.map { it.copy(revealed = true) },
            onLocalCardClick = null,
            modifier = Modifier.fillMaxSize()
        )
        // --- Top Info Bar ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GameInfoBar(
                round = round,
                currentTurn = "Results",
                yourCards = players.lastOrNull()?.cardsLeft ?: 0,
                yourScore = yourScore
            )
            if (winnerName != null) {
                Card(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        text = "$winnerName wins the round!",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

// --- Modernized Resolution Screen (Results) ---
@Composable
fun ModernResolutionScreen(
    round: Int,
    players: List<TablePlayer>,
    winnerName: String?,
    stat: String?,
    statValues: Map<String, Any?>,
    yourScore: Int,
    countdown: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Top overlays: info bar and winner banner
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GameInfoBar(
                round = round,
                currentTurn = "Results",
                yourCards = players.lastOrNull()?.cardsLeft ?: 0,
                yourScore = yourScore
            )
            if (winnerName != null) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFD600))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$winnerName wins the round!",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (stat != null) {
                Text(
                    text = "Stat: ${stat.replaceFirstChar { it.uppercase() }}",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            // All player cards, winner highlighted (vertical stack)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                players.forEach { player ->
                    val isWinner = player.name == winnerName
                    val cardBg = if (isWinner) {
                        Brush.linearGradient(
                            listOf(
                                Color(0xFFFFC107),
                                Color(0xFFFF4081),
                                Color(0xFF00E676)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(
                                Color(0xFFBDBDBD),
                                Color(0xFFE0E0E0)
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(180.dp)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        StylishTopCardCustomBg(
                            card = player.card,
                            isSelectable = false,
                            isSelected = isWinner,
                            backgroundBrush = cardBg
                        )
                    }
                }
            }
        }
        // Circular countdown timer (bottom right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            ModernCircularTimer(timer = countdown, total = 5)
        }
    }
}

// --- StylishTopCard with custom background ---
@Composable
fun StylishTopCardCustomBg(
    card: MockCard?,
    isSelectable: Boolean,
    isSelected: Boolean,
    backgroundBrush: Brush,
    onClick: (() -> Unit)? = null
) {
    if (card == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color.LightGray, shape = MaterialTheme.shapes.extraLarge)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No Cards Left", style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(16.dp)
            .then(
                if (isSelectable && onClick != null) Modifier.clickable { onClick() } else Modifier
            ),
        elevation = CardDefaults.cardElevation(if (isSelected) 16.dp else 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = Color.White
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush, shape = MaterialTheme.shapes.extraLarge)
        ) {
            // Glossy overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    card.playerName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Runs", style = MaterialTheme.typography.labelMedium)
                        Text("${card.runs}", fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Wickets", style = MaterialTheme.typography.labelMedium)
                        Text("${card.wickets}", fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Avg", style = MaterialTheme.typography.labelMedium)
                        Text("${card.battingAverage}", fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SR", style = MaterialTheme.typography.labelMedium)
                        Text("${card.strikeRate}", fontWeight = FontWeight.Bold)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Matches", style = MaterialTheme.typography.labelSmall)
                        Text("${card.matchesPlayed}")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("100s", style = MaterialTheme.typography.labelSmall)
                        Text("${card.centuries}")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("5W", style = MaterialTheme.typography.labelSmall)
                        Text("${card.fiveWicketHauls}")
                    }
                }
            }
        }
    }
}

// --- Previews for Main Screens ---
@Preview(showBackground = true)
@Composable
fun RefactoredActivePlayerScreenPreview() {
    val cards = listOf(
        MockCard("Sachin", 10000, 200, 55.5f, 90.0f, 400, 51, 5),
        MockCard("Dravid", 9000, 50, 52.3f, 75.0f, 350, 36, 0)
    )
    Column {
        Text("Active Player - Not Submitted")
        RefactoredActivePlayerScreen(
            round = 3,
            yourCards = cards,
            selectedCardIndex = 0,
            onSelectCard = {},
            selectedStat = "runs",
            onStatSelected = {},
            onSubmitStat = {},
            yourScore = 15,
            hasSubmitted = false
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text("Active Player - Submitted")
        RefactoredActivePlayerScreen(
            round = 3,
            yourCards = cards,
            selectedCardIndex = 0,
            onSelectCard = {},
            selectedStat = "runs",
            onStatSelected = {},
            onSubmitStat = {},
            yourScore = 15,
            hasSubmitted = true
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RefactoredOpponentScreenPreview() {
    val cards = listOf(
        MockCard("Sachin", 10000, 200, 55.5f, 90.0f, 400, 51, 5),
        MockCard("Dravid", 9000, 50, 52.3f, 75.0f, 350, 36, 0)
    )
    RefactoredOpponentScreen(
        round = 3,
        yourCards = cards,
        selectedCardIndex = 0,
        onSelectCard = {},
        challengeStat = "runs",
        timer = 10,
        hasSubmitted = false,
        onChallenge = {},
        onGiveUp = {},
        yourScore = 15
    )
}

@Preview(showBackground = true)
@Composable
fun ResolutionScreenPreview() {
    val players = listOf(
        TablePlayer("Player2", false, MockCard("Player2", 8000, 100, 45.0f, 80.0f, 300, 20, 2), 3, true),
        TablePlayer("Player3", false, MockCard("Player3", 9000, 120, 50.0f, 85.0f, 320, 18, 3), 2, true),
        TablePlayer("Player4", false, MockCard("Player4", 7000, 90, 40.0f, 70.0f, 250, 10, 1), 1, true),
        TablePlayer("You", true, MockCard("You", 10000, 200, 55.5f, 90.0f, 400, 51, 5), 5, true)
    )
    ResolutionScreen(
        round = 3,
        players = players,
        winnerName = "You",
        yourScore = 15
    )
}

@Preview(showBackground = true)
@Composable
fun ModernResolutionScreenPreview() {
    val players = listOf(
        TablePlayer("Player2", false, MockCard("Player2", 8000, 100, 45.0f, 80.0f, 300, 20, 2), 3, true),
        TablePlayer("Player3", false, MockCard("Player3", 9000, 120, 50.0f, 85.0f, 320, 18, 3), 2, true),
        TablePlayer("Player4", false, MockCard("Player4", 7000, 90, 40.0f, 70.0f, 250, 10, 1), 1, true),
        TablePlayer("You", true, MockCard("You", 10000, 200, 55.5f, 90.0f, 400, 51, 5), 5, true)
    )
    val statValues = mapOf(
        "Player2" to 8000,
        "Player3" to 9000,
        "Player4" to 7000,
        "You" to 10000
    )
    ModernResolutionScreen(
        round = 3,
        players = players,
        winnerName = "You",
        stat = "runs",
        statValues = statValues,
        yourScore = 15,
        countdown = 3
    )
}
