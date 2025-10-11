package com.example.safyscooter

import android.os.Bundle
import android.content.Intent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val sharedPref = getSharedPreferences("apps_prefs", MODE_PRIVATE)
        val rememberMe = sharedPref.getBoolean("remember_me", false)
        val accessToken = sharedPref.getString("access_token", null)

        if (rememberMe && !accessToken.isNullOrEmpty()) {
            val intent = Intent(this, StartActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}