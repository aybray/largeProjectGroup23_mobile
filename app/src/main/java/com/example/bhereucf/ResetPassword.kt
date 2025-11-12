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

class ResetPassword : ComponentActivity() {

    lateinit var confirmButton : Button
    lateinit var passwordInput : EditText
    lateinit var retypeInput : EditText

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.resetpasswordlayout)

        confirmButton = findViewById(R.id.confirm_btn)
        confirmButton.setOnClickListener {

            passwordInput = findViewById(R.id.newpw_input)
            retypeInput = findViewById(R.id.retypepw_input)
            val newPW = passwordInput.text.toString().trim()
            val retypePW = retypeInput.text.toString().trim()

            // Validate password is not blank
            if (newPW.isEmpty()) {
                passwordInput.error = "Password cannot be blank"
                passwordInput.requestFocus()
                Toast.makeText(this@ResetPassword, "Password cannot be blank.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Validate password length (minimum 6 characters)
            if (newPW.length < 6) {
                passwordInput.error = "Password must be at least 6 characters"
                passwordInput.requestFocus()
                Toast.makeText(this@ResetPassword, "Password must be at least 6 characters", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Validate passwords match
            if (newPW != retypePW) {
                retypeInput.error = "Passwords do not match"
                retypeInput.requestFocus()
                Toast.makeText(this@ResetPassword, "Passwords do not match.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            resetPassword(SharedForgotPWData.email, newPW)
        }
    }

    private fun resetPassword(email: String, password: String) {

        // Log the values being sent for debugging
        Log.d("resetPassword", "=== UPDATE PW REQUEST ===")
        Log.d("resetPassword", "email: '$email' (length: ${email.length})")
        Log.d("resetPassword", "password: '$password'")
        Log.d("resetPassword", "=========================")

        val request = UpdatePasswordRequest(
            email = email,
            newPassword = password,
        )

        val call = RetrofitClient.apiService.updatePassword(request)

        call.enqueue(object : Callback<UpdatePasswordResponse> {

            override fun onResponse(call: Call<UpdatePasswordResponse>, response: Response<UpdatePasswordResponse>) {

                Log.d("resetPassword", "Response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")

                if (response.isSuccessful) {

                    setResult(RESULT_OK)

                    Toast.makeText(this@ResetPassword, "Password updated!", Toast.LENGTH_LONG).show()

                    val intent = Intent(this@ResetPassword, MainActivity::class.java)
                    startActivity(intent)

                } else {
                    // HTTP error response - try to read error body
                    val errorBody = response.errorBody()?.string()
                    Log.e("resetPassword", "HTTP error ${response.code()}, error body: $errorBody")

                    val errorMessage = when (response.code()) {
                        400 -> {
                            if (errorBody != null && errorBody.isNotEmpty()) {
                                "Invalid request: $errorBody"
                            } else {
                                "Invalid operation. Please try again."
                            }
                        }
                        404 -> {
                            if (errorBody != null && errorBody.isNotEmpty()) {
                                "Invalid permission: $errorBody"
                            } else {
                                "You do not have permission. Please try again."
                            }
                        }
                        else -> {
                            if (errorBody != null && errorBody.isNotEmpty()) {
                                "Failed to update password: $errorBody"
                            } else {
                                "Failed to update password. Please try again."
                            }
                        }
                    }
                    Toast.makeText(this@ResetPassword, errorMessage, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<UpdatePasswordResponse>, t: Throwable) {
                Toast.makeText(this@ResetPassword, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("resetPassword", "Network error", t)
            }
        })
    }
}
