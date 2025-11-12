package com.example.bhereucf

import com.google.gson.annotations.SerializedName

data class CheckJwtRequest(
    @SerializedName("possibleJWT")
    val possibleJWT: String
)

