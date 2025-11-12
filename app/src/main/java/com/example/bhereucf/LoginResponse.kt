package com.example.bhereucf

data class LoginResponse(
    val id: String,
    val firstName: String,
    val lastName: String,
    val role: String,
    val error: String,
    val token: String? = null  // JWT token from backend
)

