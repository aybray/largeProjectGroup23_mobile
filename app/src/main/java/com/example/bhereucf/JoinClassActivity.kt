package com.example.bhereucf

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class JoinClassActivity : ComponentActivity() {

    private lateinit var classCodeInput: EditText
    private lateinit var sectionInput: EditText
    private lateinit var joinButton: Button
    private var currentClasses: List<Class> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.join_class_layout)

        val backButton: ImageView = findViewById(R.id.back_button)
        backButton.setOnClickListener {
            finish()
        }

        classCodeInput = findViewById(R.id.class_code_input)
        sectionInput = findViewById(R.id.section_input)
        joinButton = findViewById(R.id.join_class_button)

        val userId = intent.getStringExtra("USER_ID")
        // Get current classes for duplicate checking
        @Suppress("UNCHECKED_CAST")
        currentClasses = intent.getParcelableArrayListExtra<Class>("CURRENT_CLASSES") ?: emptyList()

        joinButton.setOnClickListener {
            val classCode = classCodeInput.text.toString().trim()
            val section = sectionInput.text.toString().trim()

            // Basic input validation - just check for empty fields
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

            // Check for duplicate class before making API call
            val isDuplicate = currentClasses.any { 
                it.classCode.equals(classCode, ignoreCase = true) && 
                it.section.equals(section, ignoreCase = true) 
            }
            
            if (isDuplicate) {
                Toast.makeText(this, "You are already enrolled in this class.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (userId != null) {
                joinClass(userId, classCode, section)
            } else {
                Toast.makeText(this, "User ID not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun joinClass(userId: String, classCode: String, section: String) {
        // Trim all inputs to ensure consistency
        val trimmedUserId = userId.trim()
        val trimmedClassCode = classCode.trim()
        val trimmedSection = section.trim()
        
        // Log the values being sent for debugging
        Log.d("JoinClass", "=== JOIN CLASS REQUEST ===")
        Log.d("JoinClass", "userId (before trim): '$userId' (length: ${userId.length})")
        Log.d("JoinClass", "userId (after trim): '$trimmedUserId' (length: ${trimmedUserId.length})")
        Log.d("JoinClass", "classCode: '$trimmedClassCode', section: '$trimmedSection'")
        Log.d("JoinClass", "=========================")
        
        val request = JoinClassRequest(
            userId = trimmedUserId,
            classCode = trimmedClassCode,
            section = trimmedSection
        )

        val call = RetrofitClient.apiService.joinClass(request)
        joinButton.isEnabled = false
        joinButton.text = "Joining..."

        call.enqueue(object : Callback<JoinClassResponse> {
            override fun onResponse(call: Call<JoinClassResponse>, response: Response<JoinClassResponse>) {
                joinButton.isEnabled = true
                joinButton.text = "Join Class"

                Log.d("JoinClass", "Response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")
                
                if (response.isSuccessful && response.body() != null) {
                    val joinResponse = response.body()!!
                    Log.d("JoinClass", "Response body - success: ${joinResponse.success}, error: '${joinResponse.error}'")
                    
                    // Check for duplicate error from backend
                    val errorLower = joinResponse.error.lowercase()
                    val isDuplicateError = errorLower.contains("already") || 
                                         errorLower.contains("duplicate") ||
                                         errorLower.contains("already enrolled")
                    
                    // Consider success if: success is true OR (error is empty AND HTTP 200)
                    // But exclude duplicate errors
                    val isSuccess = (joinResponse.success || joinResponse.error.isEmpty()) && !isDuplicateError
                    
                    if (isSuccess) {
                        Toast.makeText(this@JoinClassActivity, "Successfully joined class!", Toast.LENGTH_SHORT).show()
                        // Refresh the class list by finishing and returning to previous activity
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        // Backend validation failed - class doesn't exist, duplicate, or other error
                        val errorMessage = when {
                            isDuplicateError -> "You are already enrolled in this class."
                            joinResponse.error.isNotEmpty() -> joinResponse.error
                            else -> "Class not found. Please verify the class code and section with your instructor."
                        }
                        Log.e("JoinClass", "Failed to join: $errorMessage")
                        Toast.makeText(this@JoinClassActivity, errorMessage, Toast.LENGTH_LONG).show()
                        // Don't clear inputs - keep them for debugging/retry
                    }
                } else {
                    // HTTP error response - try to read error body
                    val errorBody = response.errorBody()?.string()
                    Log.e("JoinClass", "HTTP error ${response.code()}, error body: $errorBody")
                    
                    val errorMessage = when (response.code()) {
                        404 -> "Class not found. Please verify the class code and section with your instructor."
                        400 -> {
                            if (errorBody != null && errorBody.isNotEmpty()) {
                                "Invalid request: $errorBody"
                            } else {
                                "Invalid request. Please check your input."
                            }
                        }
                        403 -> "You do not have permission to join this class."
                        else -> {
                            if (errorBody != null && errorBody.isNotEmpty()) {
                                "Failed to join class: $errorBody"
                            } else {
                                "Failed to join class. Please check the class code and section."
                            }
                        }
                    }
                    Toast.makeText(this@JoinClassActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<JoinClassResponse>, t: Throwable) {
                joinButton.isEnabled = true
                joinButton.text = "Join Class"
                Toast.makeText(this@JoinClassActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                t.printStackTrace()
            }
        })
    }
}

