package com.example.bhereucf

data class UpdatePasswordRequest(
    val email: String,
    val newPassword: String
)