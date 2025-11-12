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

class EnterCode : ComponentActivity() {

    lateinit var confirmButton1 : Button
    lateinit var codeInput : EditText

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.entercodelayout)

        confirmButton1 = findViewById(R.id.confirm_btn1)
        confirmButton1.setOnClickListener {
            codeInput = findViewById(R.id.retypepw_input)
            val userCode = codeInput.text.toString()

            verifyCode(SharedForgotPWData.email, userCode)
        }

        val resendEmail: TextView = findViewById(R.id.resendEmail)
        resendEmail.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG)

        resendEmail.setOnClickListener {
            sendEmail(SharedForgotPWData.email)
        }
    }

    private fun verifyCode(email: String, code: String) {

        // Log the values being sent for debugging
        Log.d("verifyCode", "=== VERIFY FORGOT PW CODE REQUEST ===")
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

                    val intent = Intent(this@EnterCode, ResetPassword::class.java)
                    startActivity(intent)

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
                    Toast.makeText(this@EnterCode, errorMessage, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<VerifyEmailCodeResponse>, t: Throwable) {
                Toast.makeText(this@EnterCode, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("verifyCode", "Network error", t)
            }
        })
    }

    private fun sendEmail(email: String) {
        // Trim all inputs to ensure consistency
        val trimmedEmail = email.trim()

        // Log the values being sent for debugging
        Log.d("sendEmail", "=== RESEND FORGOT PW EMAIL REQUEST ===")
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

                    Toast.makeText(this@EnterCode, "Successfully sent email!", Toast.LENGTH_SHORT).show()

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
                    Toast.makeText(this@EnterCode, errorMessage, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<SendEmailCodeResponse>, t: Throwable) {
                Toast.makeText(this@EnterCode, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("sendEmail", "Network error", t)
            }
        })
    }
}
