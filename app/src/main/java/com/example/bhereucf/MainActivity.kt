package com.example.bhereucf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.widget.TextView
import android.content.Intent
import android.graphics.Paint
import android.widget.EditText
import android.widget.Button
import android.widget.Toast
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.mainlayout)

        // Get references to UI elements
        val usernameInput: EditText = findViewById(R.id.usernameInput)
        val passwordInput: EditText = findViewById(R.id.passwordInput)
        val loginButton: Button = findViewById(R.id.login_btn)
        val forgotPWLink: TextView = findViewById(R.id.forgotPassword)
        val registerLink: TextView = findViewById(R.id.registerHere)

        loginButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            // Input validation
            if(username.isEmpty()) {
                usernameInput.error = "Username is required"
                usernameInput.requestFocus()
                return@setOnClickListener
            }
            if(password.isEmpty()) {
                passwordInput.error = "Password is required"
                passwordInput.requestFocus()
                return@setOnClickListener
            }
            // Call the login function
            performLogin(username, password)
        }

        forgotPWLink.setOnClickListener {
            forgotPWLink.setTextColor(resources.getColor(R.color.ucf_metallic_gold, theme))
            val intent = Intent(this, ForgotPW_EnterEmail::class.java);
            startActivity(intent);
        }

        val registerLink: TextView = findViewById(R.id.registerHere)
        registerLink.setOnClickListener {
            registerLink.setTextColor(resources.getColor(R.color.ucf_metallic_gold, theme))
            val intent = Intent(this, Register_EnterInfo::class.java);
            startActivity(intent);
        }

        // Code from https://tutorial.eyehunts.com/android/android-text-underline-xml-layout-textview-kotlin-java/
        forgotPWLink.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG)
        registerLink.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG)
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
                            this@MainActivity,
                            "Login successful, Welcome ${loginResponse.firstName} ${loginResponse.lastName}!",
                            Toast.LENGTH_LONG
                        ).show()

                        // Check for teacher role (backend returns lowercase "teacher")
                        if (loginResponse.role.equals("teacher", ignoreCase = true)) {
                            val intent = Intent(this@MainActivity, TeacherClassListActivity::class.java)
                            intent.putExtra("USER_ID", loginResponse.id)
                            intent.putExtra("FIRST_NAME", loginResponse.firstName)
                            intent.putExtra("LAST_NAME", loginResponse.lastName)
                            startActivity(intent)
                            finish() // Close the login activity
                        } else {
                            // Student login - navigate to student class list
                            val intent = Intent(this@MainActivity, StudentClassListActivity::class.java)
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
                            this@MainActivity,
                            "Invalid username or password",
                            Toast.LENGTH_LONG).show()
                    }
                }
                else {
                        // Response not successful
                        Toast.makeText(
                            this@MainActivity,
                            "Login failed. Please try again.",
                            Toast.LENGTH_LONG).show()
                }
            }
            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                // Network error or server not reachable
                Toast.makeText(
                    this@MainActivity,
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

