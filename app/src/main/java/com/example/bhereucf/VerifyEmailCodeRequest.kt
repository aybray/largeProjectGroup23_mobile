package com.example.bhereucf

import com.google.gson.annotations.SerializedName

data class VerifyEmailCodeRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("verificationCode")
    val verificationCode: String,
)

