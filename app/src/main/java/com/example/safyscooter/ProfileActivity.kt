package com.example.safyscooter

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var binding: ActivityProfileBinding
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadUserData()

        binding.bottomNavigation.selectedItemId = R.id.nav_profile
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    val intent = Intent(this, StartActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent, androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(
                        this, 0, 0
                    ).toBundle())
                    finish()
                    true
                }
                R.id.nav_violations -> {
                    val intent = Intent(this, PersonalActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent, androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(
                        this, 0, 0
                    ).toBundle())
                    finish()
                    true
                }
                R.id.nav_profile -> true
                else -> false
            }
        }

        binding.btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun loadUserData() {
        val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("access_token", null)

        if (token.isNullOrEmpty()) {
            showDefaultData()
            Toast.makeText(this, "Пожалуйста, авторизуйтесь", Toast.LENGTH_SHORT).show()
        } else {
            fetchProfileData(token)
        }
    }

    private fun fetchProfileData(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("https://safetyscooter.ru/profile")
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()


                val response = client.newCall(request).execute()
                val responseCode = response.code
                val responseBody = response.body?.string()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && responseBody != null) {
                        parseAndDisplayProfileData(responseBody)
                    } else {
                        if (responseCode == 401) {
                            tryOtherTokenFormats(token)
                        } else {
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
                            val responseBody = response.body?.string()
                            withContext(Dispatchers.Main) {
                                if (responseBody != null) {
                                    parseAndDisplayProfileData(responseBody)
                                }
                            }
                            return@launch
                        }
                    } catch (e: Exception) { }
                }

                withContext(Dispatchers.Main) {
                    showDefaultData()
                    Toast.makeText(
                        this@ProfileActivity,
                        "Ошибка авторизации. Пожалуйста, войдите снова",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showDefaultData()
                }
            }
        }
    }

    private fun parseAndDisplayProfileData(jsonResponse: String) {
        try {
            val jsonObject = JSONObject(jsonResponse)

            val phoneNumber = jsonObject.optString("phone_number", "")
            val balance = jsonObject.optDouble("balance", 0.0)
            val totalApplications = jsonObject.optInt("total_applications", 0)
            val violationsFound = jsonObject.optInt("violations_found", 0)

            binding.tvPhone.text = formatPhoneNumber(phoneNumber)
            binding.tvBalance.text = String.format("%.2f ₽", balance)
            binding.tvViolationsCount.text = violationsFound.toString()
            binding.tvSentCount.text = totalApplications.toString()

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
            showDefaultData()
            Toast.makeText(this, "Ошибка обработки данных профиля", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDefaultData() {
        val sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val phone = sharedPreferences.getString("phone", "+7 (XXX) XXX-XX-XX") ?: "+7 (XXX) XXX-XX-XX"
        val balance = sharedPreferences.getFloat("balance", 0f)
        val totalApplications = sharedPreferences.getInt("total_applications", 0)
        val violationsFound = sharedPreferences.getInt("violations_found", 0)

        binding.tvPhone.text = phone
        binding.tvBalance.text = String.format("%.2f ₽", balance)
        binding.tvViolationsCount.text = violationsFound.toString()
        binding.tvSentCount.text = totalApplications.toString()
    }

    private fun formatPhoneNumber(phone: String): String {
        return if (phone.isNotEmpty()) {
            val digits = phone.replace("\\D".toRegex(), "")

            when {
                digits.length == 11 && digits.startsWith("7") -> {
                    "+7 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7, 9)}-${digits.substring(9)}"
                }
                digits.length == 10 -> {
                    "+7 (${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6, 8)}-${digits.substring(8)}"
                }
                else -> phone
            }
        } else {
            "+7 (XXX) XXX-XX-XX"
        }
    }

    private fun logout() {
        val sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()

        val appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        appPrefs.edit().clear().apply()

        val intent = Intent(this, AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
