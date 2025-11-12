package com.example.bhereucf

data class FetchStudentRecordsResponse(
    val error: String,
    val records: List<StudentAttendanceRecord>? = null
)

data class StudentAttendanceRecord(
    val studentPings: Int,
    val totalPings: Int,
    val startTime: String
)

