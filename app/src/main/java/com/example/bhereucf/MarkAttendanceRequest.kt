package com.example.bhereucf

import com.google.gson.annotations.SerializedName

data class MarkAttendanceRequest(
    @SerializedName("userId")
    val userId: String,
    @SerializedName("objectId")
    val objectId: String,
    @SerializedName("secret")
    val secret: String
)

