package com.example.safyscooter.acticities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.example.safyscooter.models.AuthResponse
import com.example.safyscooter.R
import com.example.safyscooter.models.User
import com.example.safyscooter.utils.Validators
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class RegistrationActivity : Activity() {

    private val client = OkHttpClient() // http-клиент
    private val gson = Gson() // для JSON

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration) // показываем экран регистрации

        val userPhone: EditText = findViewById(R.id.user_phone) // поле телефона
        val userPass: EditText = findViewById(R.id.user_pass) // поле пароля
        val btnReg: Button = findViewById(R.id.btn_register) // кнопка регистрации
        val cardLogin: View = findViewById(R.id.cardLogin) // кнопка "Уже есть аккаунт"

        userPhone.setText("+7") // ставим +7 по умолчанию
        userPhone.setSelection(userPhone.text.length) // ставим курсор в конец строки

        cardLogin.setOnClickListener {
            // переход на экран авторизации
            val intent = Intent(this, AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }

        btnReg.setOnClickListener {
            val phoneNumber = userPhone.text.toString().trim() // читаем телефон
            val password = userPass.text.toString().trim() // читаем пароль

            if (phoneNumber.isEmpty() || password.isEmpty()) {
                // если пустые поля
                Toast.makeText(this, "Не все поля заполнены", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (!Validators.validateRussianPhone(phoneNumber)) {
                // проверяем длину телефона
                Toast.makeText(
                    this,
                    "Номер телефона должен содержать 10 цифр (не включая +7)",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            if (!Validators.validatePassword(password)) {
                // проверяем пароль
                Toast.makeText(
                    this,
                    "Пароль должен состоять из 8-16 символов и включать только цифры и латиницу",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val formattedPhone = "$phoneNumber" // формируем строку телефона
            val user = User(formattedPhone, password) // создаём объект пользователя

            CoroutineScope(Dispatchers.IO).launch {
                // выполняем сетевой запрос в фоне
                registerUser(user)
            }
        }
    }

    private suspend fun registerUser(user: User) {
        withContext(Dispatchers.IO) {
            try {
                val jsonBody = gson.toJson(user) // превращаем user в JSON
                val requestBody =
                    jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()) // создаём тело запроса

                val request = Request.Builder()
                    .url("https://safetyscooter.ru//registration") // URL регистрации
                    .post(requestBody) // POST-запрос
                    .build()

                client.newCall(request).execute().use { response ->

                    if (response.code == 200) {
                        // регистрация успешна
                        val responseBody = response.body?.string()
                        val authResponse =
                            gson.fromJson(responseBody, AuthResponse::class.java) // разбираем ответ
                        val accessToken = authResponse.access_token // достаем токен

                        saveAccessToken(accessToken) // сохраняем токен

                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@RegistrationActivity,
                                "Регистрация успешна!",
                                Toast.LENGTH_LONG
                            ).show()

                            // переход в главное меню
                            val intent = Intent(this@RegistrationActivity, StartActivity::class.java)
                            intent.flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }

                    } else if (response.code == 409) {
                        // пользователь уже существует
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@RegistrationActivity,
                                "Такой пользователь уже есть",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                    } else {
                        // другие ошибки
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@RegistrationActivity,
                                "Ошибка регистрации: ${response.code}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

            } catch (e: Exception) {
                // ошибка сети
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@RegistrationActivity,
                        "Сетевая ошибка: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun saveAccessToken(token: String) {
        // сохраняем токен в SharedPreferences
        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("access_token", token)
            apply() // сохраняем асинхронно
        }
    }
}
