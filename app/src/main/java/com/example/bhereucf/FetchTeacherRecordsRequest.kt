package com.example.bhereucf

import com.google.gson.annotations.SerializedName

data class FetchTeacherRecordsRequest(
    @SerializedName("objectId")
    val objectId: String
)

