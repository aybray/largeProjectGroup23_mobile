package com.example.bhereucf

import com.google.gson.annotations.SerializedName

data class PrepareBroadcastRequest(
    // userId extracted from JWT token for backend compatibility
    // JWT token is also sent in Authorization header
    @SerializedName("userId")
    val userId: String,
    @SerializedName("objectId")
    val objectId: String
)

