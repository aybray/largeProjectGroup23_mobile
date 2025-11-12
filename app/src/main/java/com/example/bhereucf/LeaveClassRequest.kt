package com.example.bhereucf

import com.google.gson.annotations.SerializedName

data class LeaveClassRequest(
    // userId extracted from JWT token for backend compatibility
    // JWT token is also sent in Authorization header
    @SerializedName("userId")
    val userId: String,
    @SerializedName("classId")
    val classId: String
)

