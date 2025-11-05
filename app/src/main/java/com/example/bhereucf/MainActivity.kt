package com.example.bhereucf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
//import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.content.Intent
import android.graphics.Paint

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.mainlayout)

        val forgotPWLink: TextView = findViewById(R.id.forgotPassword)
        forgotPWLink.setOnClickListener {
            val intent = Intent(this, ForgotPW_EnterEmail::class.java);
            startActivity(intent);
        }

        val registerLink: TextView = findViewById(R.id.registerHere)
        registerLink.setOnClickListener {
            val intent = Intent(this, Register_EnterInfo::class.java);
            startActivity(intent);
        }

        // Code from https://tutorial.eyehunts.com/android/android-text-underline-xml-layout-textview-kotlin-java/
        forgotPWLink.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG)
        registerLink.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG)
    }
}

