package com.example.bhereucf

data class NewSecretRequest(
    val userId: String,
    val objectId: String,
    val secret: String
)

