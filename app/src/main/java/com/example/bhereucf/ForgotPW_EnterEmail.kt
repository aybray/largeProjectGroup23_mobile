package com.example.bhereucf

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import kotlin.random.Random

//import androidx.appcompat.app.AppCompatActivity
object SharedData {
    var code: Int = Random.nextInt(100000, 999999);
}
class ForgotPW_EnterEmail : ComponentActivity() {

    lateinit var backButton : ImageButton
    lateinit var sendButton : Button

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.forgotpwlayout)

        backButton = findViewById(R.id.back_btn)
        backButton.setOnClickListener {
            finish() // This will go back to the previous activity

        }

        sendButton = findViewById(R.id.send_btn)
        sendButton.setOnClickListener {
            val verify = ForgotPWEmailVerify(SharedData.code, 42061052);
            verify.sendEmail();

            val intent = Intent(this, ForgotPW_EnterCode::class.java);
            startActivity(intent);
        }
    }
}
