package com.example.bhereucf

data class RegisterRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val id: String,
    val role: String
)