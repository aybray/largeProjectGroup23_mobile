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
import kotlin.text.isNotEmpty

//import androidx.appcompat.app.AppCompatActivity

class Register_EnterCode : ComponentActivity() {

    lateinit var backButton : ImageButton
    lateinit var codeInput : EditText
    lateinit var confirmButton1 : Button

    lateinit var warning : EditText

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.entercodelayout)

        confirmButton1 = findViewById(R.id.confirm_btn1)
        confirmButton1.setOnClickListener {

            codeInput = findViewById(R.id.retypepw_input)
            val userCode = codeInput.text.toString()

            verifyCode(userCode, SharedRegisterData.email)
        }

        backButton = findViewById(R.id.back_btn)
        backButton.setOnClickListener {
            finish() // This will go back to the previous activity

        }

        val resendEmail: TextView = findViewById(R.id.resendEmail)
        resendEmail.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG)

        resendEmail.setOnClickListener {
            sendEmail(SharedRegisterData.email);
        }
    }

    private fun verifyCode(email: String, code: String) {

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
                    register();

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
                    Toast.makeText(this@Register_EnterCode, errorMessage, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<VerifyEmailCodeResponse>, t: Throwable) {
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
            templateChoice = "passwordReset",
        )

        val call = RetrofitClient.apiService.sendEmailCode(request)

        call.enqueue(object : Callback<SendEmailCodeResponse> {

            override fun onResponse(call: Call<SendEmailCodeResponse>, response: Response<SendEmailCodeResponse>) {

                Log.d("sendEmail", "Response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")

                if (response.isSuccessful) {

                    setResult(RESULT_OK)

                    Toast.makeText(this@Register_EnterCode, "Successfully sent email!", Toast.LENGTH_SHORT).show()

                    //finish()

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
                    Toast.makeText(this@Register_EnterCode, errorMessage, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<SendEmailCodeResponse>, t: Throwable) {
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

                    setResult(RESULT_OK)

                    Toast.makeText(this@Register_EnterCode, "Registration successful!", Toast.LENGTH_SHORT).show()

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
                    Toast.makeText(this@Register_EnterCode, errorMessage, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
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
                        Toast.makeText(
                            this@Register_EnterCode,
                            "Login successful, Welcome ${loginResponse.firstName} ${loginResponse.lastName}!",
                            Toast.LENGTH_LONG
                        ).show()

                        // Check for teacher role (backend returns lowercase "teacher")
                        if (loginResponse.role.equals("teacher", ignoreCase = true)) {
                            val intent = Intent(this@Register_EnterCode, TeacherClassListActivity::class.java)
                            intent.putExtra("USER_ID", loginResponse.id)
                            intent.putExtra("FIRST_NAME", loginResponse.firstName)
                            intent.putExtra("LAST_NAME", loginResponse.lastName)
                            startActivity(intent)
                            finish() // Close the login activity
                        } else {
                            // Student login - navigate to student class list
                            val intent = Intent(this@Register_EnterCode, StudentClassListActivity::class.java)
                            intent.putExtra("USER_ID", loginResponse.id)
                            intent.putExtra("FIRST_NAME", loginResponse.firstName)
                            intent.putExtra("LAST_NAME", loginResponse.lastName)
                            startActivity(intent)
                            finish() // Close the login activity
                        }
                    }
                    else {
                        // Login failed
                        Toast.makeText(
                            this@Register_EnterCode,
                            "Invalid username or password",
                            Toast.LENGTH_LONG).show()
                    }
                }
                else {
                    // Response not successful
                    Toast.makeText(
                        this@Register_EnterCode,
                        "Login failed. Please try again.",
                        Toast.LENGTH_LONG).show()
                }
            }
            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                // Network error or server not reachable
                Toast.makeText(
                    this@Register_EnterCode,
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