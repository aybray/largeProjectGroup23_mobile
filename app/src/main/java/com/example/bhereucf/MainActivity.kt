package com.example.bhereucf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
//import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.bhereucf.ui.theme.BHereUCFTheme
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
            forgotPWLink.setTextColor(resources.getColor(R.color.ucf_metallic_gold, theme))
            val intent = Intent(this, ForgotPassword::class.java);
            startActivity(intent);
        }

        val registerLink: TextView = findViewById(R.id.registerHere)
        registerLink.setOnClickListener {
            registerLink.setTextColor(resources.getColor(R.color.ucf_metallic_gold, theme))
            val intent = Intent(this, RegisterPage::class.java);
            startActivity(intent);
        }

        // Code from https://tutorial.eyehunts.com/android/android-text-underline-xml-layout-textview-kotlin-java/
        forgotPWLink.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG)
        registerLink.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG)
    }
}

