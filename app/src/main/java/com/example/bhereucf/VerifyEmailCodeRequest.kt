package com.example.bhereucf

data class VerifyEmailCodeRequest(
    val email: String,
    val verificationCode: String,
)