package com.example.bhereucf

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Class(
    val _id: String,
    val name: String,
    val classCode: String,
    val section: String,
    val daysOffered: String,
    val startTime: String,
    val endTime: String,
    val instructorName: String,
    val deviceName: String?
) : Parcelable

