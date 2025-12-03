package com.example.safyscooter.acticities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.safyscooter.R
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Достаём сохранённый access_token
        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val accessToken = sharedPref.getString("access_token", null)

        // Если токен есть — сразу кидаем пользователя в StartActivity
        if (!accessToken.isNullOrEmpty()) {
            val intent = Intent(this, StartActivity::class.java)
            startActivity(intent)
            finish() // закрываем MainActivity, чтобы не вернуться назад
            return
        }

        // Если токена нет — показываем приветственный экран
        setContentView(R.layout.activity_main)

        // Находим кнопку "Начать работу"
        val btnStarted = findViewById<MaterialButton>(R.id.btnStarted)

        // При нажатии ведём на экран авторизации
        btnStarted.setOnClickListener {
            val intent = Intent(this, AuthActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
