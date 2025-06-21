package com.praveen.cardclash.network


import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("create-room")
    suspend fun createRoom(@Body request: CreateRoomRequest): Response<RoomResponse>

    @POST("join-room")
    suspend fun joinRoom(@Body request: JoinRoomRequest): Response<RoomResponse>
}

data class CreateRoomRequest(val maxPlayers: Int)
data class JoinRoomRequest(val roomCode: String)
data class RoomResponse(val roomCode: String, val playerCount: Int, val maxPlayers: Int)