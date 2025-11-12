package com.example.bhereucf

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ForgotPassword : ComponentActivity() {

    lateinit var sendButton : Button
    lateinit var emailInput : EditText

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.forgotpwlayout)

        sendButton = findViewById(R.id.send_btn)
        sendButton.setOnClickListener {
            emailInput = findViewById(R.id.enter_email)
            val email = emailInput.text.toString()
            this.sendEmail(email)
        }
    }

    private fun sendEmail(email: String) {
        // Trim all inputs to ensure consistency
        val trimmedEmail = email.trim()
        SharedForgotPWData.email = trimmedEmail

        // Log the values being sent for debugging
        Log.d("sendEmail", "=== SEND FORGOT PW EMAIL REQUEST ===")
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

                    Toast.makeText(this@ForgotPassword, "Successfully sent email!", Toast.LENGTH_SHORT).show()

                    setResult(RESULT_OK)
                    val intent = Intent(this@ForgotPassword, EnterCode::class.java)
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
                    Toast.makeText(this@ForgotPassword, errorMessage, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<SendEmailCodeResponse>, t: Throwable) {
                Toast.makeText(this@ForgotPassword, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("sendEmail", "Network error", t)
            }
        })
    }
}
