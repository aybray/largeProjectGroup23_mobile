package com.example.bhereucf

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

class StudentAttendanceConfirmationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.student_attendance_confirmation_layout)

        val classCode = intent.getStringExtra("CLASS_CODE")
        val section = intent.getStringExtra("SECTION")
        val className = intent.getStringExtra("CLASS_NAME")

        val classInfoText: TextView = findViewById(R.id.class_info_text)
        val confirmationText: TextView = findViewById(R.id.confirmation_text)
        val okButton: Button = findViewById(R.id.ok_button)

        val classInfo = "$classCode-$section"
        classInfoText.text = classInfo
        confirmationText.text = "You have been marked present!"

        okButton.setOnClickListener {
            finish()
        }
    }
}

