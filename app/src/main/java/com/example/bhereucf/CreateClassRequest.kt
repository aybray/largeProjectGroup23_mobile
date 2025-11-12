package com.example.bhereucf

import com.google.gson.annotations.SerializedName

data class CreateClassRequest(
    @SerializedName("name")
    val name: String,
    @SerializedName("classCode")
    val classCode: String,
    @SerializedName("section")
    val section: String,
    @SerializedName("duration")
    val duration: Int,
    // instructorId extracted from JWT token for backend compatibility
    // JWT token is also sent in Authorization header
    @SerializedName("instructorId")
    val instructorId: String,
    @SerializedName("daysOffered")
    val daysOffered: String,
    @SerializedName("startTime")
    val startTime: String,
    @SerializedName("endTime")
    val endTime: String
)

