package com.example.bhereucf

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import kotlin.random.Random

//import androidx.appcompat.app.AppCompatActivity

class Register_EnterCode : ComponentActivity() {

    lateinit var backButton : Button
    lateinit var codeInput : EditText
    lateinit var confirmButton1 : Button

    lateinit var warning : EditText

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.entercodelayout)

        val code = Random.nextInt(100000, 999999);

        val verify = ForgotPWEmailVerify(code, 42075033);
        verify.sendEmail();

        confirmButton1 = findViewById(R.id.confirm_btn1)
        confirmButton1.setOnClickListener {

            val code = SharedData.code
            codeInput = findViewById(R.id.code_input)
            val userCode = codeInput.text.toString()

            if (userCode == code.toString())
            {
                // Register user
            }
            else
            {
                warning.visibility = View.VISIBLE
            }
        }

        backButton = findViewById(R.id.back_btn)
        backButton.setOnClickListener {
            finish() // This will go back to the previous activity

        }

        val resendEmail: TextView = findViewById(R.id.resendEmail)
        resendEmail.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG)

        resendEmail.setOnClickListener {
            val verify = ForgotPWEmailVerify(SharedData.code, 42075033);
            verify.sendEmail();
        }
    }
}