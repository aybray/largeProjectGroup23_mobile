package com.example.bhereucf

data class CheckJwtResponse(
    val contents: JwtContents? = null,
    val error: String
)

data class JwtContents(
    val id: String,
    val firstName: String,
    val lastName: String,
    val role: String
)

