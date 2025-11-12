package com.example.bhereucf

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RegisterPage : ComponentActivity() {

    lateinit var fullNameInput : EditText
    lateinit var passwordInput : EditText
    lateinit var nidInput : EditText
    lateinit var emailInput : EditText
    lateinit var createAccountButton : Button
    lateinit var studentRadio : RadioButton
    lateinit var teacherRadio : RadioButton

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.registerlayout)

        fullNameInput = findViewById(R.id.full_name_input)
        emailInput = findViewById(R.id.enter_email)
        passwordInput = findViewById(R.id.password_input)
        nidInput = findViewById(R.id.nid_input)
        studentRadio = findViewById(R.id.radio_student)
        teacherRadio = findViewById(R.id.radio_teacher)

        createAccountButton = findViewById(R.id.create_btn)
        createAccountButton.setOnClickListener {
            val fullName = fullNameInput.text.toString()
            val name = fullName.split(" ", limit = 2)
            SharedRegisterData.firstName = name[0].trim()
            if (name.size > 1) {
                SharedRegisterData.lastName = name[1].trim()
            } else {
                SharedRegisterData.lastName = ""
            }
            val password = passwordInput.text.toString().trim()
            
            // Validate password length (minimum 6 characters)
            if (password.length < 6) {
                passwordInput.error = "Password must be at least 6 characters"
                passwordInput.requestFocus()
                return@setOnClickListener
            }
            
            SharedRegisterData.password = password
            val nid = nidInput.text.toString()
            SharedRegisterData.id = nid.trim()
            val email = emailInput.text.toString()
            SharedRegisterData.email = email.trim()
            val isStudent = studentRadio.isChecked
            val isTeacher = teacherRadio.isChecked
            val userType = if (isStudent) "Student" else if (isTeacher) "Teacher" else ""
            SharedRegisterData.role = userType.trim()
            // Use Logcat to see/confirm the text inputs
            Log.i("Test Account creation", "Full Name: $fullName")
            Log.i("Test Account creation", "Password: $password")
            Log.i("Test Account creation", "NID: $nid")
            Log.i("Test Account creation", "Email: $email")
            Log.i("Test Account creation", "User Type: $userType")

            sendEmail(email)
        }
    }

    private fun sendEmail(email: String) {
        // Trim all inputs to ensure consistency
        val trimmedEmail = email.trim()

        // Log the values being sent for debugging
        Log.d("sendEmail", "=== SEND REGISTRATION EMAIL REQUEST ===")
        Log.d("sendEmail", "email (before trim): '$email' (length: ${email.length})")
        Log.d("sendEmail", "email (after trim): '$trimmedEmail' (length: ${trimmedEmail.length})")
        Log.d("sendEmail", "=========================")

        val request = SendEmailCodeRequest(
            email = trimmedEmail,
            templateChoice = "registration",
        )

        val call = RetrofitClient.apiService.sendEmailCode(request)

        call.enqueue(object : Callback<SendEmailCodeResponse> {

            override fun onResponse(call: Call<SendEmailCodeResponse>, response: Response<SendEmailCodeResponse>) {

                Log.d("sendEmail", "Response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")

                if (response.isSuccessful) {

                    Toast.makeText(this@RegisterPage, "Successfully sent email!", Toast.LENGTH_SHORT).show()

                    setResult(RESULT_OK)

                    val intent = Intent(this@RegisterPage, EmailVerify::class.java)
                    startActivity(intent)

                } else {
                    // HTTP error response - try to read error body
                    val errorBody = response.errorBody()?.string()
                    Log.e("sendEmail", "HTTP error ${response.code()}, error body: $errorBody")

                    val errorMessage = when (response.code()) {
                        400 -> {
                            if (errorBody != null && errorBody.isNotEmpty()) {
                                "Invalid request: $errorBody"
                            } else {
                                "Invalid request. Please try another email."
                            }
                        }
                        502 -> {
                            "Server error (502). The email service may be temporarily unavailable. Please try again later."
                        }
                        503 -> {
                            "Service unavailable. Please try again later."
                        }
                        else -> {
                            if (errorBody != null && errorBody.isNotEmpty()) {
                                "Failed to send email: $errorBody"
                            } else {
                                "Failed to send email (HTTP ${response.code()}). Please try again."
                            }
                        }
                    }
                    Toast.makeText(this@RegisterPage, errorMessage, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<SendEmailCodeResponse>, t: Throwable) {
                Toast.makeText(this@RegisterPage, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("sendEmail", "Network error", t)
            }
        })
    }
}
