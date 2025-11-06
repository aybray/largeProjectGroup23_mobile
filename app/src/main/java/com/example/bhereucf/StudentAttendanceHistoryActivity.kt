package com.example.bhereucf

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class StudentAttendanceHistoryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.student_attendance_history_layout)

        val classCode = intent.getStringExtra("CLASS_CODE")
        val section = intent.getStringExtra("SECTION")
        val className = intent.getStringExtra("CLASS_NAME")

        val classInfoText: TextView = findViewById(R.id.class_info_text)
        val backButton: ImageView = findViewById(R.id.back_button)
        val recyclerView: RecyclerView = findViewById(R.id.history_recycler_view)

        val classInfo = "$classCode-$section"
        classInfoText.text = classInfo

        backButton.setOnClickListener {
            finish()
        }

        // TODO: Fetch attendance history from API
        // For now, show empty state
        recyclerView.layoutManager = LinearLayoutManager(this)
        // recyclerView.adapter = AttendanceHistoryAdapter(historyList)
    }
}

