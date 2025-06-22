package com.praveen.cardclash.network

//private const val SERVER_URL = "http://192.168.31.210:3000"
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject

import java.util.UUID

object SocketManager {
    private var socket: Socket? = null
    private const val SERVER_URL = "http://192.168.31.210:3000"
    private var currentSocketId: String = ""
    private val instanceId: String = UUID.randomUUID().toString() // Unique per instance
    // Replace Channel with MutableSharedFlow (replay = 1) for all event flows
    private val countdownFlowInternal = MutableSharedFlow<Int>(replay = 1)
    private val gameStartFlowInternal = MutableSharedFlow<Unit>(replay = 1)
    private val gameDataFlowInternal = MutableSharedFlow<GameData>(replay = 1)
    private val roundResultFlowInternal = MutableSharedFlow<RoundResult>(replay = 1)
    private val gameEndFlowInternal = MutableSharedFlow<GameEnd>(replay = 1)
    private val statQuotedFlowInternal = MutableSharedFlow<StatQuoted>(replay = 1)
    private val challengeInitiatedFlowInternal = MutableSharedFlow<StatQuoted>(replay = 1)

    val countdownFlow = countdownFlowInternal.asSharedFlow().also {
        Log.d("SocketManager", "[FLOW] countdownFlow created (asSharedFlow)")
    }
    val gameStartFlow = gameStartFlowInternal.asSharedFlow().also {
        Log.d("SocketManager", "[FLOW] gameStartFlow created (asSharedFlow)")
    }
    val gameDataFlow = gameDataFlowInternal.asSharedFlow().also {
        Log.d("SocketManager", "[FLOW] gameDataFlow created (asSharedFlow)")
    }
    val roundResultFlow = roundResultFlowInternal.asSharedFlow().also {
        Log.d("SocketManager", "[FLOW] roundResultFlow created (asSharedFlow)")
    }
    val gameEndFlow = gameEndFlowInternal.asSharedFlow().also {
        Log.d("SocketManager", "[FLOW] gameEndFlow created (asSharedFlow)")
    }
    val statQuotedFlow = statQuotedFlowInternal.asSharedFlow().also {
        Log.d("SocketManager", "[FLOW] statQuotedFlow created (asSharedFlow)")
    }
    val challengeInitiatedFlow = challengeInitiatedFlowInternal.asSharedFlow().also {
        Log.d("SocketManager", "[FLOW] challengeInitiatedFlow created (asSharedFlow)")
    }

    val socketId: String
        get() = currentSocketId

    data class GameData(
        val players: List<Player>,
        val currentTurn: String,
        val round: Int,
        val cards: List<Card>,
        val scores: Map<String, Int>
    )
    data class Player(val socketId: String, val cardCount: Int)
    data class Card(
        val playerName: String,
        val runs: Int,
        val wickets: Int,
        val battingAverage: Float,
        val strikeRate: Float,
        val matchesPlayed: Int,
        val centuries: Int,
        val fiveWicketHauls: Int
    )
    data class Submission(val stat: String, val value: Number)
    data class RoundResult(
        val winner: String?,
        val stat: String?,
        val submissions: Map<String, Submission>,
        val scores: Map<String, Int>,
        val gameState: GameData
    )
    data class GameEnd(val winner: String?, val scores: Map<String, Int>)
    data class StatQuoted(val activePlayer: String, val stat: String, val timeRemaining: Int, val isConfirmation: Boolean)

    fun connect() {
        try {
            disconnect() // Ensure clean state
            val opts = IO.Options().apply {
                transports = arrayOf("websocket")
                forceNew = true
                reconnection = true
                reconnectionAttempts = 5
                reconnectionDelay = 1000
                query = "instanceId=$instanceId"
            }
            socket = IO.socket(SERVER_URL, opts)
            socket?.on(Socket.EVENT_CONNECT) {
                currentSocketId = socket?.id() ?: ""
                Log.d("SocketManager", "Connected to server, socketId=$currentSocketId, instanceId=$instanceId")
            }?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e("SocketManager", "Connection failed: ${args.joinToString()}, instanceId=$instanceId")
            }?.on(Socket.EVENT_DISCONNECT) { args ->
                currentSocketId = ""
                Log.d("SocketManager", "Disconnected: ${args.joinToString()}, instanceId=$instanceId")
            }?.on("countdown") { args ->
                val value = args[0] as Int
                val emitted = countdownFlowInternal.tryEmit(value)
                Log.d("SocketManager", "[FLOW-EMIT] countdownFlowInternal.tryEmit: $emitted, value=$value")
                Log.d("SocketManager", "Received countdown: $value, socketId=$currentSocketId, instanceId=$instanceId")
            }?.on("gameStart") {
                val emitted = gameStartFlowInternal.tryEmit(Unit)
                Log.d("SocketManager", "[FLOW-EMIT] gameStartFlowInternal.tryEmit: $emitted, value=Unit")
                Log.d("SocketManager", "Received gameStart, socketId=$currentSocketId, instanceId=$instanceId")
            }?.on("gameData") { args ->
                try {
                    val data = args[0] as JSONObject
                    val playersArray = data.getJSONArray("players")
                    val players = mutableListOf<Player>()
                    for (i in 0 until playersArray.length()) {
                        val p = playersArray.getJSONObject(i)
                        players.add(Player(
                            socketId = p.getString("socketId"),
                            cardCount = p.getInt("cardCount")
                        ))
                    }
                    val cardsArray = data.getJSONArray("cards")
                    val cards = mutableListOf<Card>()
                    for (i in 0 until cardsArray.length()) {
                        val c = cardsArray.getJSONObject(i)
                        cards.add(Card(
                            playerName = c.getString("playerName"),
                            runs = c.getInt("runs"),
                            wickets = c.getInt("wickets"),
                            battingAverage = c.getDouble("battingAverage").toFloat(),
                            strikeRate = c.getDouble("strikeRate").toFloat(),
                            matchesPlayed = c.getInt("matchesPlayed"),
                            centuries = c.getInt("centuries"),
                            fiveWicketHauls = c.getInt("fiveWicketHauls")
                        ))
                    }
                    val scoresObj = data.getJSONObject("scores")
                    val scores = mutableMapOf<String, Int>()
                    scoresObj.keys().forEach { key ->
                        if (key is String) {
                            scores[key] = scoresObj.getInt(key)
                        }
                    }
                    val gameData = GameData(
                        players = players,
                        currentTurn = data.getString("currentTurn"),
                        round = data.getInt("round"),
                        cards = cards,
                        scores = scores
                    )
                    val emitted = gameDataFlowInternal.tryEmit(gameData)
                    Log.d("SocketManager", "[FLOW-EMIT] gameDataFlowInternal.tryEmit: $emitted, value=$gameData")
                    Log.d("SocketManager", "Received gameData: round=${gameData.round}, cards=${gameData.cards.size}, socketId=$currentSocketId, instanceId=$instanceId")
                } catch (e: Exception) {
                    Log.e("SocketManager", "Failed to parse gameData: ${e.message}, instanceId=$instanceId")
                }
            }?.on("challengeInitiated") { args ->
                try {
                    val data = args[0] as JSONObject
                    val quoted = StatQuoted(
                        activePlayer = data.getString("activePlayer"),
                        stat = data.getString("stat"),
                        timeRemaining = data.getInt("timeRemaining"),
                        isConfirmation = data.optBoolean("isConfirmation", false)
                    )
                    val emitted = challengeInitiatedFlowInternal.tryEmit(quoted)
                    Log.d("SocketManager", "[FLOW-EMIT] challengeInitiatedFlowInternal.tryEmit: $emitted, value=$quoted")
                    Log.d("SocketManager", "Received challengeInitiated: activePlayer=${quoted.activePlayer}, stat=${quoted.stat}, time=${quoted.timeRemaining}, isConfirmation=${quoted.isConfirmation}, socketId=$currentSocketId, instanceId=$instanceId")
                } catch (e: Exception) {
                    Log.e("SocketManager", "Failed to parse challengeInitiated: ${e.message}, instanceId=$instanceId")
                }
            }?.on("statQuoted") { args ->
                try {
                    val data = args[0] as JSONObject
                    val quoted = StatQuoted(
                        activePlayer = data.getString("activePlayer"),
                        stat = data.getString("stat"),
                        timeRemaining = data.getInt("timeRemaining"),
                        isConfirmation = data.optBoolean("isConfirmation", false)
                    )
                    val emitted = statQuotedFlowInternal.tryEmit(quoted)
                    Log.d("SocketManager", "[FLOW-EMIT] statQuotedFlowInternal.tryEmit: $emitted, value=$quoted")
                    Log.d("SocketManager", "Received statQuoted: activePlayer=${quoted.activePlayer}, stat=${quoted.stat}, time=${quoted.timeRemaining}, isConfirmation=${quoted.isConfirmation}, socketId=$currentSocketId, instanceId=$instanceId")
                } catch (e: Exception) {
                    Log.e("SocketManager", "Failed to parse statQuoted: ${e.message}, instanceId=$instanceId")
                }
            }?.on("roundResult") { args ->
                try {
                    val data = args[0] as JSONObject
                    val gameState = data.getJSONObject("gameState")
                    val playersArray = gameState.getJSONArray("players")
                    val players = mutableListOf<Player>()
                    for (i in 0 until playersArray.length()) {
                        val p = playersArray.getJSONObject(i)
                        players.add(Player(
                            socketId = p.getString("socketId"),
                            cardCount = p.getInt("cardCount")
                        ))
                    }
                    val cardsArray = gameState.getJSONArray("cards")
                    val cards = mutableListOf<Card>()
                    for (i in 0 until cardsArray.length()) {
                        val c = cardsArray.getJSONObject(i)
                        cards.add(Card(
                            playerName = c.getString("playerName"),
                            runs = c.getInt("runs"),
                            wickets = c.getInt("wickets"),
                            battingAverage = c.getDouble("battingAverage").toFloat(),
                            strikeRate = c.getDouble("strikeRate").toFloat(),
                            matchesPlayed = c.getInt("matchesPlayed"),
                            centuries = c.getInt("centuries"),
                            fiveWicketHauls = c.getInt("fiveWicketHauls")
                        ))
                    }
                    val submissionsObj = data.getJSONObject("submissions")
                    val submissions = mutableMapOf<String, Submission>()
                    submissionsObj.keys().forEach { key ->
                        if (key is String) {
                            val s = submissionsObj.getJSONObject(key)
                            submissions[key] = Submission(
                                stat = s.optString("stat", ""),
                                value = s.get("value") as Number
                            )
                        }
                    }
                    val scoresObj = data.getJSONObject("scores")
                    val scores = mutableMapOf<String, Int>()
                    scoresObj.keys().forEach { key ->
                        if (key is String) {
                            scores[key] = scoresObj.getInt(key)
                        }
                    }
                    val result = RoundResult(
                        winner = data.optString("winner", ""),
                        stat = data.optString("stat", ""),
                        submissions = submissions,
                        scores = scores,
                        gameState = GameData(
                            players = players,
                            currentTurn = gameState.getString("currentTurn"),
                            round = gameState.getInt("round"),
                            cards = cards,
                            scores = scores
                        )
                    )
                    val emitted = roundResultFlowInternal.tryEmit(result)
                    Log.d("SocketManager", "[FLOW-EMIT] roundResultFlowInternal.tryEmit: $emitted, value=$result")
                    Log.d("SocketManager", "Received roundResult, socketId=$currentSocketId, instanceId=$instanceId")
                } catch (e: Exception) {
                    Log.e("SocketManager", "Failed to parse roundResult: ${e.message}, instanceId=$instanceId")
                }
            }?.on("gameEnd") { args ->
                try {
                    val data = args[0] as JSONObject
                    val scoresObj = data.getJSONObject("scores")
                    val scores = mutableMapOf<String, Int>()
                    scoresObj.keys().forEach { key ->
                        if (key is String) {
                            scores[key] = scoresObj.getInt(key)
                        }
                    }
                    val end = GameEnd(
                        winner = data.optString("winner", null),
                        scores = scores
                    )
                    val emitted = gameEndFlowInternal.tryEmit(end)
                    Log.d("SocketManager", "[FLOW-EMIT] gameEndFlowInternal.tryEmit: $emitted, value=$end")
                    Log.d("SocketManager", "Received gameEnd, socketId=$currentSocketId, instanceId=$instanceId")
                } catch (e: Exception) {
                    Log.e("SocketManager", "Failed to parse gameEnd: ${e.message}, instanceId=$instanceId")
                }
            }
            socket?.connect()
            Log.d("SocketManager", "Attempting to connect to $SERVER_URL, instanceId=$instanceId")
        } catch (e: Exception) {
            Log.e("SocketManager", "Socket init failed: ${e.message}, instanceId=$instanceId")
        }
    }

    fun joinRoom(roomCode: String) {
        if (socket?.connected() == true) {
            socket?.emit("joinRoom", roomCode.uppercase())
            Log.d("SocketManager", "Joined room: $roomCode, socketId=$currentSocketId, instanceId=$instanceId")
        } else {
            Log.e("SocketManager", "Cannot join room: Socket not connected, instanceId=$instanceId")
            connect()
            socket?.emit("joinRoom", roomCode.uppercase())
            Log.d("SocketManager", "Retried join room: $roomCode, socketId=$currentSocketId, instanceId=$instanceId")
        }
    }

    fun submitStat(roomCode: String, cardIndex: Int, stat: String, value: Number) {
        if (socket?.connected() == true) {
            socket?.emit("submitStat", JSONObject().apply {
                put("roomCode", roomCode.uppercase())
                put("cardIndex", cardIndex)
                put("stat", stat)
                put("value", value)
            })
            Log.d("SocketManager", "Submitted stat: cardIndex=$cardIndex, $stat=$value for room $roomCode, socketId=$currentSocketId, instanceId=$instanceId")
        } else {
            Log.e("SocketManager", "Cannot submit stat: Socket not connected, instanceId=$instanceId")
        }
    }

    fun challenge(roomCode: String, cardIndex: Int, stat: String, value: Number) {
        if (socket?.connected() == true) {
            socket?.emit("challenge", JSONObject().apply {
                put("roomCode", roomCode.uppercase())
                put("cardIndex", cardIndex)
                put("stat", stat)
                put("value", value)
            })
            Log.d("SocketManager", "Submitted challenge: cardIndex=$cardIndex, $stat=$value for room $roomCode, socketId=$currentSocketId, instanceId=$instanceId")
        } else {
            Log.e("SocketManager", "Cannot submit challenge: Socket not connected, instanceId=$instanceId")
        }
    }

    fun gaveUp(roomCode: String) {
        if (socket?.connected() == true) {
            socket?.emit("gaveUp", JSONObject().apply {
                put("roomCode", roomCode.uppercase())
            })
            Log.d("SocketManager", "Submitted gaveUp for room $roomCode, socketId=$currentSocketId, instanceId=$instanceId")
        } else {
            Log.e("SocketManager", "Cannot submit gaveUp: Socket not connected, instanceId=$instanceId")
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
        currentSocketId = ""
        Log.d("SocketManager", "Disconnected socket, instanceId=$instanceId")
    }

    fun isConnected(): Boolean {
        return socket?.connected() == true
    }
}