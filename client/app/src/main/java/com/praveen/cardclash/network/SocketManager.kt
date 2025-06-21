package com.praveen.cardclash.network

//private const val SERVER_URL = "http://192.168.31.210:3000"
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.json.JSONObject

import java.util.UUID

object SocketManager {
    private var socket: Socket? = null
    private const val SERVER_URL = "http://192.168.31.210:3000"
    private var currentSocketId: String = ""
    private val instanceId: String = UUID.randomUUID().toString() // Unique per instance
    private val countdownChannel = Channel<Int>()
    private val gameStartChannel = Channel<Unit>()
    private val gameDataChannel = Channel<GameData>()
    private val roundResultChannel = Channel<RoundResult>()
    private val gameEndChannel = Channel<GameEnd>()
    private val statQuotedChannel = Channel<StatQuoted>()
    private val challengeInitiatedChannel = Channel<StatQuoted>()

    val countdownFlow = countdownChannel.receiveAsFlow()
    val gameStartFlow = gameStartChannel.receiveAsFlow()
    val gameDataFlow = gameDataChannel.receiveAsFlow()
    val roundResultFlow = roundResultChannel.receiveAsFlow()
    val gameEndFlow = gameEndChannel.receiveAsFlow()
    val statQuotedFlow = statQuotedChannel.receiveAsFlow()
    val challengeInitiatedFlow = challengeInitiatedChannel.receiveAsFlow()

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
    data class RoundResult(
        val winner: String,
        val stat: String,
        val submissions: Map<String, Submission>,
        val gameState: GameData
    )
    data class Submission(val stat: String, val value: Number)
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
                countdownChannel.trySend(value)
                Log.d("SocketManager", "Received countdown: $value, socketId=$currentSocketId, instanceId=$instanceId")
            }?.on("gameStart") {
                gameStartChannel.trySend(Unit)
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
                    gameDataChannel.trySend(gameData)
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
                    challengeInitiatedChannel.trySend(quoted)
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
                    statQuotedChannel.trySend(quoted)
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
                    roundResultChannel.trySend(RoundResult(
                        winner = data.optString("winner", ""),
                        stat = data.optString("stat", ""),
                        submissions = submissions,
                        gameState = GameData(
                            players = players,
                            currentTurn = gameState.getString("currentTurn"),
                            round = gameState.getInt("round"),
                            cards = cards,
                            scores = scores
                        )
                    ))
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
                    gameEndChannel.trySend(GameEnd(
                        winner = data.optString("winner", null),
                        scores = scores
                    ))
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