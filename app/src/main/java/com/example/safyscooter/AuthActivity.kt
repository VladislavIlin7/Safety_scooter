package com.example.safyscooter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient


class AuthActivity : Activity() {
    private val client = OkHttpClient()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val userPhoneAuth: EditText = findViewById(R.id.user_phone_auth)
        val userPassAuth: EditText = findViewById(R.id.user_pass_auth)
        val btnAuth: Button = findViewById(R.id.btn_auth)
        val cardRegister: View = findViewById(R.id.cardRegister)

        userPhoneAuth.setText("+7")
        userPhoneAuth.setSelection(userPhoneAuth.text.length)

        cardRegister.setOnClickListener {
            val intent = Intent(this, RegistrationActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }

        btnAuth.setOnClickListener {
            val phoneNumber = userPhoneAuth.text.toString().trim()
            val password = userPassAuth.text.toString().trim()

            if (phoneNumber.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Не все поля заполнены", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (!Validators.validateRussianPhone(phoneNumber)) {
                Toast.makeText(this, "Номер телефона должен содержать 10 цифр (не включая +7)",
                    Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if(!Validators.validatePassword(password)) {
                Toast.makeText(this, "Пароль должен состоять из 8-16 символов и включать только цифры и латиницу",
                    Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val formattedPhone = "$phoneNumber"
            val user = User(formattedPhone, password)

            CoroutineScope(Dispatchers.IO).launch {
                AuthUser(user)
            }
        }
    }

    private suspend fun AuthUser(user: User) {
        withContext(Dispatchers.IO) {
            try {
                val jsonBody = gson.toJson(user)
                val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url("https://safetyscooter.ru//login")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use() { response ->
                    if (response.code == 200) {
                        val responseBody = response.body?.string()
                        val authResponse = gson.fromJson(responseBody, AuthResponse::class.java)
                        val accessToken = authResponse.access_token

                        saveAccessToken(accessToken)

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@AuthActivity, "Авторизация успешна!",
                                Toast.LENGTH_LONG).show()
                            val intent = Intent(this@AuthActivity, StartActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                    } else if (response.code == 404) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@AuthActivity, "Неверный номер",
                                Toast.LENGTH_LONG).show()

                        }
                    } else if (response.code == 403) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@AuthActivity, "Неверный пароль",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@AuthActivity, "Ошибка авторизации: ${response.code}",
                                Toast.LENGTH_LONG).show()

                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AuthActivity, "Сетевая ошибка: ${e.message}",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveAccessToken(token: String) {
        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("access_token", token)
            apply()
        }
    }
}
