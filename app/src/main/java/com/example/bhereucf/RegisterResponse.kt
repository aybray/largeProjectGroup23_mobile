package com.example.bhereucf

data class RegisterResponse(
    val error: String,
    val token: String? = null  // JWT token from backend
)

