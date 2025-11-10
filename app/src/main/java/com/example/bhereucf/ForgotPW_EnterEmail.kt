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
import kotlin.random.Random
import kotlin.text.isNotEmpty

//import androidx.appcompat.app.AppCompatActivity
object SharedData {
    var code: Int = Random.nextInt(100000, 999999);
}
class ForgotPW_EnterEmail : ComponentActivity() {

    lateinit var backButton : ImageButton
    lateinit var sendButton : Button
    lateinit var emailInput : EditText

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.forgotpwlayout)

        emailInput = findViewById(R.id.enter_email)
        val email = emailInput.text.toString()

        backButton = findViewById(R.id.back_btn)
        backButton.setOnClickListener {
            finish() // This will go back to the previous activity

        }

        sendButton = findViewById(R.id.send_btn)
        sendButton.setOnClickListener {
            this.sendEmail(email);

            val intent = Intent(this, ForgotPW_EnterCode::class.java);
            startActivity(intent);
        }
    }

    private fun sendEmail(email: String) {
        // Trim all inputs to ensure consistency
        val trimmedEmail = email.trim()

        // Log the values being sent for debugging
        //Log.d("JoinClass", "=== JOIN CLASS REQUEST ===")
        //Log.d("JoinClass", "userId (before trim): '$userId' (length: ${userId.length})")
        //Log.d("JoinClass", "userId (after trim): '$trimmedUserId' (length: ${trimmedUserId.length})")
        //Log.d("JoinClass", "classCode: '$trimmedClassCode', section: '$trimmedSection'")
        //Log.d("JoinClass", "=========================")

        val request = SendEmailCodeRequest(
            email = trimmedEmail,
            templateChoice = "passwordReset",
        )

        val call = RetrofitClient.apiService.sendEmailCode(request)
        //joinButton.isEnabled = false
        //joinButton.text = "Joining..."

        call.enqueue(object : Callback<SendEmailCodeResponse> {

            override fun onResponse(call: Call<SendEmailCodeResponse>, response: Response<SendEmailCodeResponse>) {
                //joinButton.isEnabled = true
                //joinButton.text = "Join Class"

                //Log.d("JoinClass", "Response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")

                if (response.isSuccessful && response.body() != null) {
                    val joinResponse = response.body()!!
                    //Log.d("JoinClass", "Response body - success: ${joinResponse.success}, error: '${joinResponse.error}'")

                    // Consider success if: success is true OR (error is empty AND HTTP 200)
                    // But exclude duplicate errors
                    //val isSuccess = (joinResponse.success || joinResponse.error.isEmpty())

                    //if (isSuccess) {
                        //Toast.makeText(this@JoinClassActivity, "Successfully joined class!", Toast.LENGTH_SHORT).show()
                        // Refresh the class list by finishing and returning to previous activity
                        setResult(RESULT_OK)
                        finish()
                    //} else {
                        // Backend validation failed - class doesn't exist, duplicate, or other error
                        // Don't clear inputs - keep them for debugging/retry
                    //}
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
                    //Toast.makeText(this@JoinClassActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<SendEmailCodeResponse>, t: Throwable) {
            }
        })
    }
}
