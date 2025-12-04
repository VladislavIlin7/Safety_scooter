package com.example.safyscooter.acticities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import com.example.safyscooter.R
import com.example.safyscooter.databinding.ActivityProfileBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding // привязка к layout
    private val client = OkHttpClient() // http-клиент

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root) // показываем экран

        loadUserData() // загружаем данные профиля

        // ставим выбранной вкладку профиля
        binding.bottomNavigation.selectedItemId = R.id.nav_profile

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {

                R.id.nav_home -> {
                    // переход на главный экран
                    val intent = Intent(this, StartActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent, ActivityOptionsCompat.makeCustomAnimation(this, 0, 0).toBundle())
                    finish()
                    true
                }

                R.id.nav_violations -> {
                    // переход в список нарушений
                    val intent = Intent(this, ViolationsActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent, ActivityOptionsCompat.makeCustomAnimation(this, 0, 0).toBundle())
                    finish()
                    true
                }

                R.id.nav_profile -> true // уже здесь

                else -> false
            }
        }

        binding.btnLogout.setOnClickListener {
            logout() // выход из аккаунта
        }
    }

    private fun loadUserData() {
        // достаём токен
        val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("access_token", null)

        if (token.isNullOrEmpty()) {
            // если токена нет — показываем пустые данные
            showDefaultData()
            Toast.makeText(this, "Пожалуйста, авторизуйтесь", Toast.LENGTH_SHORT).show()
        } else {
            // иначе грузим профиль с сервера
            fetchProfileData(token)
        }
    }

    private fun fetchProfileData(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // создаём GET-запрос к профилю
                val request = Request.Builder()
                    .url("https://safetyscooter.ru/profile")
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", "Bearer $token") // токен
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val responseCode = response.code
                val responseBody = response.body?.string()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && responseBody != null) {
                        // успешно — парсим JSON и показываем
                        parseAndDisplayProfileData(responseBody)
                    } else {
                        // если 401 — пробуем другой формат токена
                        if (responseCode == 401) {
                            tryOtherTokenFormats(token)
                        } else {
                            // иначе показываем дефолтные данные
                            showDefaultData()
                            Toast.makeText(
                                this@ProfileActivity,
                                "Ошибка загрузки профиля: $responseCode",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: IOException) {
                // ошибка сети
                withContext(Dispatchers.Main) {
                    showDefaultData()
                    Toast.makeText(this@ProfileActivity, "Ошибка соединения", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun tryOtherTokenFormats(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // пробуем разные названия заголовков
                val formats = listOf(
                    "Authorization" to token,
                    "X-Auth-Token" to token,
                    "X-Access-Token" to token,
                    "Token" to token
                )

                for ((headerName, headerValue) in formats) {
                    try {
                        val request = Request.Builder()
                            .url("https://safetyscooter.ru/profile")
                            .addHeader("accept", "application/json")
                            .addHeader(headerName, headerValue)
                            .get()
                            .build()

                        val response = client.newCall(request).execute()

                        if (response.isSuccessful) {
                            // нашли рабочий формат
                            val responseBody = response.body?.string()
                            withContext(Dispatchers.Main) {
                                if (responseBody != null) {
                                    parseAndDisplayProfileData(responseBody)
                                }
                            }
                            return@launch
                        }
                    } catch (_: Exception) {}
                }

                // если все форматы не подошли — ошибка
                withContext(Dispatchers.Main) {
                    showDefaultData()
                    Toast.makeText(
                        this@ProfileActivity,
                        "Ошибка авторизации. Пожалуйста, войдите снова",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showDefaultData()
                }
            }
        }
    }

    private fun parseAndDisplayProfileData(jsonResponse: String) {
        try {
            // читаем JSON вручную
            val jsonObject = JSONObject(jsonResponse)

            val phoneNumber = jsonObject.optString("phone_number", "")
            val balance = jsonObject.optDouble("balance", 0.0)
            val totalApplications = jsonObject.optInt("total_applications", 0)
            val violationsFound = jsonObject.optInt("violations_found", 0)

            // выводим данные на экран
            binding.tvPhone.text = formatPhoneNumber(phoneNumber)
            binding.tvBalance.text = String.format("%.2f ₽", balance)
            binding.tvViolationsCount.text = violationsFound.toString()
            binding.tvSentCount.text = totalApplications.toString()

            // сохраняем профиль локально
            val sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
            with(sharedPreferences.edit()) {
                putString("phone", phoneNumber)
                putFloat("balance", balance.toFloat())
                putInt("total_applications", totalApplications)
                putInt("violations_found", violationsFound)
                apply()
            }

            Toast.makeText(this, "Данные профиля обновлены", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            // если что-то сломалось при парсинге
            showDefaultData()
            Toast.makeText(this, "Ошибка обработки данных профиля", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDefaultData() {
        // берём сохранённые или дефолтные данные
        val sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val phone = sharedPreferences.getString("phone", "+7 (XXX) XXX-XX-XX") ?: "+7 (XXX) XXX-XX-XX"
        val balance = sharedPreferences.getFloat("balance", 0f)
        val totalApplications = sharedPreferences.getInt("total_applications", 0)
        val violationsFound = sharedPreferences.getInt("violations_found", 0)

        // выводим на экран
        binding.tvPhone.text = phone
        binding.tvBalance.text = String.format("%.2f ₽", balance)
        binding.tvViolationsCount.text = violationsFound.toString()
        binding.tvSentCount.text = totalApplications.toString()
    }

    private fun formatPhoneNumber(phone: String): String {
        // форматируем номер +7 (999) 123-45-67
        return if (phone.isNotEmpty()) {
            val digits = phone.replace("\\D".toRegex(), "")

            when {
                // формат если 11 цифр и начинается с 7
                digits.length == 11 && digits.startsWith("7") ->
                    "+7 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7, 9)}-${digits.substring(9)}"

                // если только 10 цифр
                digits.length == 10 ->
                    "+7 (${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6, 8)}-${digits.substring(8)}"

                else -> phone
            }
        } else "+7 (XXX) XXX-XX-XX"
    }

    private fun logout() {
        // очищаем сохранённые данные
        val sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()

        val appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        appPrefs.edit().clear().apply()

        // переходим на экран авторизации
        val intent = Intent(this, AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
