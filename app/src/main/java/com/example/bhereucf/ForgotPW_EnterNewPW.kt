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
import kotlin.text.isNotEmpty

//import androidx.appcompat.app.AppCompatActivity

class ForgotPW_EnterNewPW : ComponentActivity() {

    lateinit var backButton : ImageButton

    lateinit var confirmButton : Button

    lateinit var passwordInput : EditText

    lateinit var retypeInput : EditText

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.resetpasswordlayout)

        backButton = findViewById(R.id.back_btn)
        backButton.setOnClickListener {
            finish() // This will go back to the previous activity

        }

        confirmButton = findViewById(R.id.confirm_btn)
        confirmButton.setOnClickListener {

            passwordInput = findViewById(R.id.newpw_input)
            retypeInput = findViewById(R.id.retypepw_input)
            val newPW = passwordInput.text.toString().trim()
            val retypePW = retypeInput.text.toString().trim()

            if (newPW == retypePW) {
                if (newPW == "")
                {
                    Toast.makeText(this@ForgotPW_EnterNewPW, "Password cannot be blank.", Toast.LENGTH_LONG).show()
                } else {
                    resetPassword(SharedForgotPWData.email, newPW);
                }
            } else {
                Toast.makeText(this@ForgotPW_EnterNewPW, "Passwords do not match.", Toast.LENGTH_LONG).show()
            }
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

                Log.d("verifyCode", "Response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")

                if (response.isSuccessful) {

                    setResult(RESULT_OK)

                    Toast.makeText(this@ForgotPW_EnterNewPW, "Password updated!", Toast.LENGTH_LONG).show()

                    val intent = Intent(this@ForgotPW_EnterNewPW, MainActivity::class.java)
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
                                "Invalid code: $errorBody"
                            } else {
                                "Invalid code. Please try again."
                            }
                        }
                    }
                    Toast.makeText(this@ForgotPW_EnterNewPW, errorMessage, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<UpdatePasswordResponse?>, t: Throwable) {
            }
        })
    }
}