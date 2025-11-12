package com.example.bhereucf

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AddClassActivity : ComponentActivity() {

    private lateinit var classNameInput: EditText
    private lateinit var classCodeInput: EditText
    private lateinit var sectionInput: EditText
    private lateinit var startTimeInput: EditText
    private lateinit var endTimeInput: EditText
    private lateinit var dayMonday: CheckBox
    private lateinit var dayTuesday: CheckBox
    private lateinit var dayWednesday: CheckBox
    private lateinit var dayThursday: CheckBox
    private lateinit var dayFriday: CheckBox
    private lateinit var createButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.add_class_layout)

        classNameInput = findViewById(R.id.class_name_input)
        classCodeInput = findViewById(R.id.class_code_input)
        sectionInput = findViewById(R.id.section_input)
        startTimeInput = findViewById(R.id.start_time_input)
        endTimeInput = findViewById(R.id.end_time_input)
        dayMonday = findViewById(R.id.day_monday)
        dayTuesday = findViewById(R.id.day_tuesday)
        dayWednesday = findViewById(R.id.day_wednesday)
        dayThursday = findViewById(R.id.day_thursday)
        dayFriday = findViewById(R.id.day_friday)
        createButton = findViewById(R.id.create_class_button)

        // userId is now in JWT token - not passed via Intent

        createButton.setOnClickListener {
            val className = classNameInput.text.toString().trim()
            val classCode = classCodeInput.text.toString().trim()
            val section = sectionInput.text.toString().trim()
            val startTime = startTimeInput.text.toString().trim()
            val endTime = endTimeInput.text.toString().trim()

            // Validate inputs
            if (className.isEmpty()) {
                classNameInput.error = "Class name is required"
                classNameInput.requestFocus()
                return@setOnClickListener
            }

            if (classCode.isEmpty()) {
                classCodeInput.error = "Class code is required"
                classCodeInput.requestFocus()
                return@setOnClickListener
            }

            if (section.isEmpty()) {
                sectionInput.error = "Section is required"
                sectionInput.requestFocus()
                return@setOnClickListener
            }

            // Build days offered string
            val selectedDays = mutableListOf<String>()
            if (dayMonday.isChecked) selectedDays.add("M")
            if (dayTuesday.isChecked) selectedDays.add("T")
            if (dayWednesday.isChecked) selectedDays.add("W")
            if (dayThursday.isChecked) selectedDays.add("Th")
            if (dayFriday.isChecked) selectedDays.add("F")

            if (selectedDays.isEmpty()) {
                Toast.makeText(this, "Please select at least one day", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val daysOffered = selectedDays.joinToString("")

            if (startTime.isEmpty()) {
                startTimeInput.error = "Start time is required"
                startTimeInput.requestFocus()
                return@setOnClickListener
            }

            if (endTime.isEmpty()) {
                endTimeInput.error = "End time is required"
                endTimeInput.requestFocus()
                return@setOnClickListener
            }

            // Calculate duration in minutes
            val duration = calculateDuration(startTime, endTime)
            if (duration <= 0) {
                Toast.makeText(this, "End time must be after start time", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Extract instructorId from JWT token only
            val instructorId = JwtTokenManager.getUserIdFromToken(this)
            if (instructorId != null) {
                createClass(instructorId, className, classCode, section, daysOffered, startTime, endTime, duration)
            } else {
                Toast.makeText(this, "Error: Unable to get user ID from token", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun calculateDuration(startTime: String, endTime: String): Int {
        try {
            val (startHours, startMinutes) = startTime.split(":").map { it.toInt() }
            val (endHours, endMinutes) = endTime.split(":").map { it.toInt() }

            val startTotalMinutes = startHours * 60 + startMinutes
            var endTotalMinutes = endHours * 60 + endMinutes

            // Handle case where end time is next day (e.g., 23:00 to 01:00)
            if (endTotalMinutes < startTotalMinutes) {
                endTotalMinutes += 24 * 60
            }

            return endTotalMinutes - startTotalMinutes
        } catch (e: Exception) {
            Log.e("AddClass", "Error calculating duration: ${e.message}")
            return 0
        }
    }

    private fun createClass(
        userId: String,
        className: String,
        classCode: String,
        section: String,
        daysOffered: String,
        startTime: String,
        endTime: String,
        duration: Int
    ) {
        val trimmedUserId = userId.trim()
        val trimmedClassName = className.trim()
        val trimmedClassCode = classCode.trim()
        val trimmedSection = section.trim()
        val trimmedDaysOffered = daysOffered.trim()
        val trimmedStartTime = startTime.trim()
        val trimmedEndTime = endTime.trim()

        Log.d("CreateClass", "=== CREATE CLASS REQUEST ===")
        Log.d("CreateClass", "userId: '$trimmedUserId'")
        Log.d("CreateClass", "name: '$trimmedClassName'")
        Log.d("CreateClass", "classCode: '$trimmedClassCode'")
        Log.d("CreateClass", "section: '$trimmedSection'")
        Log.d("CreateClass", "daysOffered: '$trimmedDaysOffered'")
        Log.d("CreateClass", "startTime: '$trimmedStartTime'")
        Log.d("CreateClass", "endTime: '$trimmedEndTime'")
        Log.d("CreateClass", "duration: $duration")
        Log.d("CreateClass", "=========================")

        // Extract instructorId from JWT token only
        val instructorId = JwtTokenManager.getUserIdFromToken(this)
        if (instructorId == null) {
            Toast.makeText(this, "Error: Unable to get user ID from token", Toast.LENGTH_LONG).show()
            Log.e("CreateClass", "Failed to extract instructorId from JWT token")
            return
        }

        val request = CreateClassRequest(
            name = trimmedClassName,
            classCode = trimmedClassCode,
            section = trimmedSection,
            duration = duration,
            instructorId = instructorId,
            daysOffered = trimmedDaysOffered,
            startTime = trimmedStartTime,
            endTime = trimmedEndTime
        )

        val call = RetrofitClient.apiService.createClass(request)

        call.enqueue(object : Callback<CreateClassResponse> {
            override fun onResponse(
                call: Call<CreateClassResponse>,
                response: Response<CreateClassResponse>
            ) {
                Log.d("CreateClass", "Response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")

                if (response.isSuccessful) {
                    val createResponse = response.body()
                    if (createResponse != null && createResponse.error.isEmpty()) {
                        Toast.makeText(
                            this@AddClassActivity,
                            "Class created successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        val errorMsg = createResponse?.error ?: "Unknown error"
                        Log.e("CreateClass", "Error in response: $errorMsg")
                        Toast.makeText(
                            this@AddClassActivity,
                            "Failed to create class: $errorMsg",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("CreateClass", "HTTP ${response.code()} - Error body: $errorBody")
                    
                    // Try to parse error message from JSON
                    val errorMsg = try {
                        if (errorBody != null && errorBody.contains("error")) {
                            val json = org.json.JSONObject(errorBody)
                            json.getString("error")
                        } else {
                            "Failed to create class (HTTP ${response.code()})"
                        }
                    } catch (e: Exception) {
                        "Failed to create class (HTTP ${response.code()})"
                    }
                    
                    Toast.makeText(
                        this@AddClassActivity,
                        errorMsg,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onFailure(call: Call<CreateClassResponse>, t: Throwable) {
                Log.e("CreateClass", "Network error: ${t.message}", t)
                Toast.makeText(
                    this@AddClassActivity,
                    "Network error: ${t.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }
}

