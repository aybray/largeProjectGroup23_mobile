package com.example.bhereucf

import com.google.gson.annotations.SerializedName

data class JoinClassRequest(
    @SerializedName("userId")
    val userId: String,
    @SerializedName("classCode")
    val classCode: String,
    @SerializedName("section")
    val section: String
)

