package com.example.bhereucf

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
//import androidx.appcompat.app.AppCompatActivity

class ForgotPW_EnterNewPW : ComponentActivity() {

    lateinit var backButton : ImageButton

    lateinit var confirmButton : Button

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
            // UPDATE PASSWORD API HERE
        }
    }
}