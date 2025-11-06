package com.example.bhereucf

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("/api/login")
    fun login(@Body request: LoginRequest): Call<LoginResponse>

    @POST("/api/register")
    fun register(@Body request: RegisterRequest): Call<RegisterResponse>

    @POST("/api/fetchclasses")
    fun fetchClasses(@Body request: FetchClassesRequest): Call<FetchClassesResponse>

    @POST("/api/preparebroadcast")
    fun prepareBroadcast(@Body request: PrepareBroadcastRequest): Call<PrepareBroadcastResponse>

    @POST("/api/newsecret")
    fun newSecret(@Body request: NewSecretRequest): Call<NewSecretResponse>

    @POST("/api/endbroadcast")
    fun endBroadcast(@Body request: EndBroadcastRequest): Call<Unit>

    @POST("/api/joinclass")
    fun joinClass(@Body request: JoinClassRequest): Call<JoinClassResponse>

    @POST("/api/leaveclass")
    fun leaveClass(@Body request: LeaveClassRequest): Call<LeaveClassResponse>

    @POST("/api/markmehere")
    fun markAttendance(@Body request: MarkAttendanceRequest): Call<MarkAttendanceResponse>
}

