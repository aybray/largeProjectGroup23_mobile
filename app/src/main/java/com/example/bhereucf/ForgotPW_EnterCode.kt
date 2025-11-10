package com.example.bhereucf

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.example.bhereucf.ForgotPW_EnterEmail
import kotlin.random.Random

//import androidx.appcompat.app.AppCompatActivity

class ForgotPW_EnterCode : ComponentActivity() {

    lateinit var backButton : Button
    lateinit var confirmButton1 : Button
    lateinit var warning : EditText
    lateinit var codeInput : EditText

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.entercodelayout)

        warning = findViewById(R.id.warning)
        warning.visibility = View.GONE

        backButton = findViewById(R.id.back_btn)
        backButton.setOnClickListener {
            finish() // This will go back to the previous activity

        }

        confirmButton1 = findViewById(R.id.confirm_btn1)
        confirmButton1.setOnClickListener {

            val code = SharedData.code
            codeInput = findViewById(R.id.code_input)
            val userCode = codeInput.text.toString()

            if (userCode == code.toString())
            {
                val intent = Intent(this, ForgotPW_EnterNewPW::class.java);
                startActivity(intent);
            }
            else
            {
                warning.visibility = View.VISIBLE
            }
        }

        val resendEmail: TextView = findViewById(R.id.resendEmail)
        resendEmail.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG)

        resendEmail.setOnClickListener {
            val verify = ForgotPWEmailVerify(SharedData.code, 42061052);
            verify.sendEmail();
        }
    }
}