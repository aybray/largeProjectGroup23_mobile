package com.example.bhereucf

import com.google.gson.annotations.SerializedName

data class SendEmailCodeRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("templateChoice")
    val templateChoice: String,
)

