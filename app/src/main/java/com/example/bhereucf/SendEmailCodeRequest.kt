package com.example.bhereucf

data class SendEmailCodeRequest(
    val email: String,
    val templateChoice: String,
)