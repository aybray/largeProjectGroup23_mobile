package com.example.bhereucf

data class PrepareBroadcastResponse(
    val secret: String,
    val broadcastName: String,
    val error: String = ""
)

