package com.example.bhereucf

import com.google.gson.annotations.SerializedName

data class JoinClassRequest(
    // userId extracted from JWT token for backend compatibility
    // JWT token is also sent in Authorization header
    @SerializedName("userId")
    val userId: String,
    @SerializedName("classCode")
    val classCode: String,
    @SerializedName("section")
    val section: String
)

