package com.example.bhereucf

import com.google.gson.annotations.SerializedName

data class UpdatePasswordRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("newPassword")
    val newPassword: String
)

