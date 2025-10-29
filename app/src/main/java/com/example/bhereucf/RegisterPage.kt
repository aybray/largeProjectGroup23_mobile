package com.example.bhereucf

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RegisterPage : ComponentActivity() {

    lateinit var firstNameInput : EditText
    lateinit var lastNameInput : EditText
    lateinit var passwordInput : EditText
    lateinit var nidInput : EditText
    lateinit var emailInput : EditText
    lateinit var createAccountButton : Button
    lateinit var studentRadio : RadioButton
    lateinit var teacherRadio : RadioButton
    lateinit var backButton : Button

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.registerlayout)
        // Initialize UI elements
        backButton = findViewById(R.id.back_btn)
        firstNameInput = findViewById(R.id.first_name_input)
        lastNameInput = findViewById(R.id.last_name_input)
        passwordInput = findViewById(R.id.password_input)
        nidInput = findViewById(R.id.nid_input)
        emailInput = findViewById(R.id.email_input)
        studentRadio = findViewById(R.id.radio_student)
        teacherRadio = findViewById(R.id.radio_teacher)
        createAccountButton = findViewById(R.id.create_btn)

        // Back button functionality
        backButton.setOnClickListener {
            finish()
        }

        // Create account button functionality
        createAccountButton.setOnClickListener {
            val firstName = firstNameInput.text.toString().trim()
            val lastName = lastNameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val nid = nidInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val isStudent = studentRadio.isChecked
            val isTeacher = teacherRadio.isChecked
            val role = if (isStudent) "Student" else if (isTeacher) "Teacher" else ""

            // Validate input
            if (firstName.isEmpty()) {
                firstNameInput.error = "First name is required"
                firstNameInput.requestFocus()
                return@setOnClickListener
            }
            if (lastName.isEmpty()) {
                lastNameInput.error = "Last name is required"
                lastNameInput.requestFocus()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                passwordInput.error = "Password is required"
                passwordInput.requestFocus()
                return@setOnClickListener
            }
            if (password.length < 8) {
                passwordInput.error = "Password must be at least 8 characters long"
                passwordInput.requestFocus()
                return@setOnClickListener
            }
            if (nid.isEmpty()) {
                nidInput.error = "NID is required"
                nidInput.requestFocus()
                return@setOnClickListener
            }
            if (email.isEmpty()) {
                emailInput.error = "Email is required"
                return@setOnClickListener
            }
            if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailInput.error = "Invalid email format"
                emailInput.requestFocus()
                return@setOnClickListener
            }
            if (role.isEmpty()) {
                Toast.makeText(this, "Please select a role (Student or Teacher)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // All validation passed, call API
            performRegister(email, password, firstName, lastName, nid, role)
        }
    }
    private fun performRegister(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        nid: String,
        role: String
    )
    {
        // Register request
        val registerRequest = RegisterRequest(
            email = email,
            password = password,
            firstName = firstName,
            lastName = lastName,
            id = nid,
            role = role
        )

        // Make API call
        val call = RetrofitClient.apiService.register(registerRequest)

        call.enqueue(object : Callback<RegisterResponse> {
            override fun onResponse(call: Call<RegisterResponse>, response: Response<RegisterResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val registerResponse = response.body()!!
                    if (registerResponse.error.isEmpty()) {
                        // Registration successful
                        Toast.makeText(
                            this@RegisterPage,
                            "Registration successful",
                            Toast.LENGTH_LONG).show()

                        // Navigate to email verification page
                        val intent = Intent(this@RegisterPage, EmailVerify::class.java)
                        startActivity(intent)
                        finish() // Close register page
                    }
                    else {
                        // Registration failed
                        Toast.makeText(
                            this@RegisterPage,
                            "Registration failed: ${registerResponse.error}",
                            Toast.LENGTH_LONG).show()
                    }
                }
                else {
                    // Response not successful
                    Toast.makeText(
                        this@RegisterPage,
                        "Response not successful, please try again.",
                        Toast.LENGTH_LONG).show()
                }
            }
            override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                // Network error
                Toast.makeText(
                    this@RegisterPage,
                    "Network error: ${t.message}",
                    Toast.LENGTH_LONG).show()
            }
        })

    }

}
