package com.example.bhereucf

data class FetchTeacherRecordsResponse(
    val error: String,
    val records: List<AttendanceRecord>? = null
)

data class AttendanceRecord(
    val _id: String? = null,
    val classId: String? = null,
    val instructorId: String? = null,
    val startTime: String,
    val active: Boolean? = null,
    val totalPings: Int,
    val pingsCollected: Map<String, Int>? = null
)

