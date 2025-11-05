package com.example.bhereucf

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

class RegisterPage : ComponentActivity() {

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
        usernameInput = findViewById(R.id.username_input)
        passwordInput = findViewById(R.id.password_input)
        nidInput = findViewById(R.id.nid_input)
        emailInput = findViewById(R.id.email_input)
        studentRadio = findViewById(R.id.radio_student)
        teacherRadio = findViewById(R.id.radio_teacher)

        createAccountButton = findViewById(R.id.create_btn)
        createAccountButton.setOnClickListener {
            val fullName = fullNameInput.text.toString()
            val username = usernameInput.text.toString()
            val password = passwordInput.text.toString()
            val nid = nidInput.text.toString()
            val email = emailInput.text.toString()
            val isStudent = studentRadio.isChecked
            val isTeacher = teacherRadio.isChecked
            val userType = if (isStudent) "Student" else if (isTeacher) "Teacher" else ""
            // Use Logcat to see/confirm the text inputs
            Log.i("Test Account creation", "Full Name: $fullName")
            Log.i("Test Account creation", "Username: $username")
            Log.i("Test Account creation", "Password: $password")
            Log.i("Test Account creation", "NID: $nid")
            Log.i("Test Account creation", "Email: $email")
            Log.i("Test Account creation", "User Type: $userType")

            // Navigate to Email Verify
            val intent = Intent(this, EmailVerify::class.java)
            startActivity(intent)
        }



    }
}