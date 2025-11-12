package com.example.bhereucf

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class EmailVerify : ComponentActivity() {

    lateinit var codeInput : EditText
    lateinit var confirmButton1 : Button

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.emailverifylayout)

        confirmButton1 = findViewById(R.id.confirm_btn1)
        confirmButton1.setOnClickListener {

            codeInput = findViewById(R.id.retypepw_input)
            val userCode = codeInput.text.toString()

            verifyCode(userCode, SharedRegisterData.email)
        }

        val resendEmail: TextView = findViewById(R.id.resendEmail)
        resendEmail.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG)

        resendEmail.setOnClickListener {
            sendEmail(SharedRegisterData.email)
        }
    }

    private fun verifyCode(code: String, email: String) {

        // Log the values being sent for debugging
        Log.d("verifyCode", "=== VERIFY REGISTRATION CODE REQUEST ===")
        Log.d("verifyCode", "email: '$email' (length: ${email.length})")
        Log.d("verifyCode", "code: '$code'")
        Log.d("verifyCode", "=========================")

        val request = VerifyEmailCodeRequest(
            email = email,
            verificationCode = code,
        )

        val call = RetrofitClient.apiService.verifyEmailCode(request)

        call.enqueue(object : Callback<VerifyEmailCodeResponse> {

            override fun onResponse(call: Call<VerifyEmailCodeResponse>, response: Response<VerifyEmailCodeResponse>) {

                Log.d("verifyCode", "Response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")

                if (response.isSuccessful) {

                    setResult(RESULT_OK)

                    // --- REGISTER AND LOGIN HERE ---
                    register()

                } else {
                    // HTTP error response - try to read error body
                    val errorBody = response.errorBody()?.string()
                    Log.e("verifyCode", "HTTP error ${response.code()}, error body: $errorBody")

                    val errorMessage = when (response.code()) {
                        400 -> {
                            if (errorBody != null && errorBody.isNotEmpty()) {
                                "Invalid request: $errorBody"
                            } else {
                                "Invalid code. Please try again."
                            }
                        }
                        else -> {
                            if (errorBody != null && errorBody.isNotEmpty()) {
                                "Invalid code: $errorBody"
                            } else {
                                "Invalid code. Please try again."
                            }
                        }
                    }
                    Toast.makeText(this@EmailVerify, errorMessage, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<VerifyEmailCodeResponse>, t: Throwable) {
                Toast.makeText(this@EmailVerify, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("verifyCode", "Network error", t)
            }
        })
    }

    private fun sendEmail(email: String) {
        // Trim all inputs to ensure consistency
        val trimmedEmail = email.trim()

        // Log the values being sent for debugging
        Log.d("sendEmail", "=== RESEND REGISTRATION EMAIL REQUEST ===")
        Log.d("sendEmail", "email (before trim): '$email' (length: ${email.length})")
        Log.d("sendEmail", "email (after trim): '$trimmedEmail' (length: ${trimmedEmail.length})")
        Log.d("sendEmail", "=========================")

        val request = SendEmailCodeRequest(
            email = trimmedEmail,
            templateChoice = "registration",
        )

        // Log the full request details
        Log.d("sendEmail", "Request details - email: '$trimmedEmail', templateChoice: 'registration'")
        Log.d("sendEmail", "Base URL: https://lp.ilovenarwhals.xyz/api/sendEmailCode")

        val call = RetrofitClient.apiService.sendEmailCode(request)

        call.enqueue(object : Callback<SendEmailCodeResponse> {

            override fun onResponse(call: Call<SendEmailCodeResponse>, response: Response<SendEmailCodeResponse>) {

                Log.d("sendEmail", "Response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")

                if (response.isSuccessful) {
                    Log.d("sendEmail", "Success - response body: ${response.body()}")
                    setResult(RESULT_OK)
                    Toast.makeText(this@EmailVerify, "Successfully sent email!", Toast.LENGTH_SHORT).show()
                } else {
                    // HTTP error response - try to read error body
                    val errorBody = try {
                        response.errorBody()?.string()
                    } catch (e: Exception) {
                        Log.e("sendEmail", "Error reading error body", e)
                        null
                    }
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
                    Toast.makeText(this@EmailVerify, errorMessage, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<SendEmailCodeResponse>, t: Throwable) {
                Toast.makeText(this@EmailVerify, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("sendEmail", "Network error", t)
            }
        })
    }

    private fun register() {

        // Log the values being sent for debugging
        Log.d("register", "=== REGISTRATION REQUEST ===")
        Log.d("register", "=========================")

        val request = RegisterRequest(
            email = SharedRegisterData.email,
            password = SharedRegisterData.password,
            firstName = SharedRegisterData.firstName,
            lastName = SharedRegisterData.lastName,
            id = SharedRegisterData.id,
            role = SharedRegisterData.role,
        )

        val call = RetrofitClient.apiService.register(request)

        call.enqueue(object : Callback<RegisterResponse> {

            override fun onResponse(call: Call<RegisterResponse>, response: Response<RegisterResponse>) {

                Log.d("register", "Response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")

                if (response.isSuccessful) {
                    val registerResponse = response.body()
                    
                    // Store JWT token if provided from registration
                    registerResponse?.token?.let { token ->
                        JwtTokenManager.saveToken(this@EmailVerify, token)
                    }

                    setResult(RESULT_OK)

                    Toast.makeText(this@EmailVerify, "Registration successful!", Toast.LENGTH_SHORT).show()

                    // Login for first time
                    performLogin(SharedRegisterData.email, SharedRegisterData.password)

                } else {
                    // HTTP error response - try to read error body
                    val errorBody = response.errorBody()?.string()
                    Log.e("register", "HTTP error ${response.code()}, error body: $errorBody")

                    val errorMessage = when (response.code()) {
                        400 -> {
                            if (errorBody != null && errorBody.isNotEmpty()) {
                                "Invalid information: $errorBody"
                            } else {
                                "Invalid request. Please try another NID or email."
                            }
                        }
                        else -> {
                            if (errorBody != null && errorBody.isNotEmpty()) {
                                "Failed to register: $errorBody"
                            } else {
                                "Failed to register. Please try another email."
                            }
                        }
                    }
                    Toast.makeText(this@EmailVerify, errorMessage, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                Toast.makeText(this@EmailVerify, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("register", "Network error", t)
            }
        })
    }

    private fun performLogin(username: String, password: String) {
        // Create a LoginRequest object with the provided username and password
        val loginRequest = LoginRequest(login = username, password = password)

        // Make the API call to the login endpoint
        val call = RetrofitClient.apiService.login(loginRequest)

        call.enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val loginResponse = response.body()!!

                    // Check if login was successful
                    if (loginResponse.id.isNotEmpty() && loginResponse.id != "-1") {
                        // Store JWT token if provided
                        loginResponse.token?.let { token ->
                            JwtTokenManager.saveToken(this@EmailVerify, token)
                        }
                        
                        Toast.makeText(
                            this@EmailVerify,
                            "Login successful, Welcome ${loginResponse.firstName} ${loginResponse.lastName}!",
                            Toast.LENGTH_LONG
                        ).show()

                        // Check for teacher role (backend returns lowercase "teacher")
                        // firstName, lastName, and role are now in JWT token - not passed via Intent
                        if (loginResponse.role.equals("teacher", ignoreCase = true)) {
                            val intent = Intent(this@EmailVerify, TeacherClassListActivity::class.java)
                            intent.putExtra("USER_ID", loginResponse.id)  // Still needed for internal app logic
                            startActivity(intent)
                            finish() // Close the login activity
                        } else {
                            // Student login - navigate to student class list
                            val intent = Intent(this@EmailVerify, StudentClassListActivity::class.java)
                            intent.putExtra("USER_ID", loginResponse.id)  // Still needed for internal app logic
                            startActivity(intent)
                            finish() // Close the login activity
                        }
                    }
                    else {
                        // Login failed
                        Toast.makeText(
                            this@EmailVerify,
                            "Invalid username or password",
                            Toast.LENGTH_LONG).show()
                    }
                }
                else {
                    // Response not successful
                    Toast.makeText(
                        this@EmailVerify,
                        "Login failed. Please try again.",
                        Toast.LENGTH_LONG).show()
                }
            }
            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                // Network error or server not reachable
                Toast.makeText(
                    this@EmailVerify,
                    "Network error: ${t.message}",
                    Toast.LENGTH_LONG).show()
                // Detailed logging for debugging
                println("=== LOGIN ERROR ===")
                println("Error: ${t.message}")
                println("Error type: ${t.javaClass.simpleName}")
                t.printStackTrace()  // This prints the full stack trace
                println("==================")
            }
        })
    }
}
