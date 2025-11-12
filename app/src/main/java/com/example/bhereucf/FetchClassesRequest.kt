package com.example.bhereucf

import com.google.gson.annotations.SerializedName

// Request includes userId (extracted from JWT) for backend compatibility
// JWT token is also sent in Authorization header
data class FetchClassesRequest(
    @SerializedName("userId")
    val userId: String
)

