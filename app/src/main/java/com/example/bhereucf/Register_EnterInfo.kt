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
import kotlin.text.isNotEmpty


object SharedRegisterData {
    lateinit var email: String
    lateinit var password: String
    lateinit var firstName: String
    lateinit var lastName: String
    lateinit var id: String
    lateinit var role: String
}
class Register_EnterInfo : ComponentActivity() {

    lateinit var fullNameInput : EditText
    lateinit var usernameInput : EditText
    lateinit var passwordInput : EditText
    lateinit var nidInput : EditText
    lateinit var emailInput : EditText
    lateinit var createAccountButton : Button
    lateinit var studentRadio : RadioButton
    lateinit var teacherRadio : RadioButton
    lateinit var backButton : ImageButton


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.registerlayout)

        // Back button functionality
        backButton = findViewById(R.id.back_btn)
        backButton.setOnClickListener {
            finish() // This will go back to the previous activity

        }

        fullNameInput = findViewById(R.id.full_name_input)
        usernameInput = findViewById(R.id.enter_email)
        passwordInput = findViewById(R.id.password_input)
        nidInput = findViewById(R.id.nid_input)
        emailInput = findViewById(R.id.email_input)
        studentRadio = findViewById(R.id.radio_student)
        teacherRadio = findViewById(R.id.radio_teacher)

        createAccountButton = findViewById(R.id.create_btn)
        createAccountButton.setOnClickListener {
            val fullName = fullNameInput.text.toString()
            val name = fullName.split(" ", limit = 2)
            SharedRegisterData.firstName = name[0]
            if (name.size > 1) {
                SharedRegisterData.lastName = name[1]
            }
            val username = usernameInput.text.toString()
            val password = passwordInput.text.toString()
            SharedRegisterData.password = password
            val nid = nidInput.text.toString()
            SharedRegisterData.id = nid
            val email = emailInput.text.toString()
            SharedRegisterData.email = email
            val isStudent = studentRadio.isChecked
            val isTeacher = teacherRadio.isChecked
            val userType = if (isStudent) "Student" else if (isTeacher) "Teacher" else ""
            SharedRegisterData.role = userType
            // Use Logcat to see/confirm the text inputs
            Log.i("Test Account creation", "Full Name: $fullName")
            Log.i("Test Account creation", "Username: $username")
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
        SharedForgotPWData.email = trimmedEmail

        // Log the values being sent for debugging
        Log.d("sendEmail", "=== SEND REGISTRATION EMAIL REQUEST ===")
        Log.d("sendEmail", "email (before trim): '$email' (length: ${email.length})")
        Log.d("sendEmail", "email (after trim): '$trimmedEmail' (length: ${trimmedEmail.length})")
        Log.d("sendEmail", "=========================")

        val request = SendEmailCodeRequest(
            email = trimmedEmail,
            templateChoice = "register",
        )

        val call = RetrofitClient.apiService.sendEmailCode(request)

        call.enqueue(object : Callback<SendEmailCodeResponse> {

            override fun onResponse(call: Call<SendEmailCodeResponse>, response: Response<SendEmailCodeResponse>) {

                Log.d("sendEmail", "Response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")

                if (response.isSuccessful) {

                    Toast.makeText(this@Register_EnterInfo, "Successfully sent email!", Toast.LENGTH_SHORT).show()

                    setResult(RESULT_OK)

                    val intent = Intent(this@Register_EnterInfo, Register_EnterCode::class.java)
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
                        else -> {
                            if (errorBody != null && errorBody.isNotEmpty()) {
                                "Failed to send email: $errorBody"
                            } else {
                                "Failed to send email. Please try another email."
                            }
                        }
                    }
                    Toast.makeText(this@Register_EnterInfo, errorMessage, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<SendEmailCodeResponse>, t: Throwable) {
            }
        })
    }
}