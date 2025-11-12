package com.example.bhereucf

import com.google.gson.annotations.SerializedName

data class PingRequest(
    // userId removed - comes from JWT token in Authorization header
    @SerializedName("objectId")
    val objectId: String
)

