package com.example.safyscooter.acticities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val accessToken = sharedPref.getString("access_token", null)

        if (!accessToken.isNullOrEmpty()) {
            val intent = Intent(this, StartActivity::class.java)
            startActivity(intent)
        } else {
            val intent = Intent(this, AuthActivity::class.java)
            startActivity(intent)
        }

        finish()
    }
}