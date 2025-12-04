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

class AuthActivity : Activity() {
    private val client = OkHttpClient() // создаём HTTP-клиент
    private val gson = Gson() // для превращения объектов в JSON

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login) // показываем экран логина

        val userPhoneAuth: EditText = findViewById(R.id.user_phone_auth) // поле ввода телефона
        val userPassAuth: EditText = findViewById(R.id.user_pass_auth) // поле ввода пароля
        val btnAuth: Button = findViewById(R.id.btn_auth) // кнопка входа
        val cardRegister: View = findViewById(R.id.cardRegister) // кнопка перейти к регистрации

        userPhoneAuth.setText("+7") // ставим +7 по умолчанию
        userPhoneAuth.setSelection(userPhoneAuth.text.length) // ставим курсор в конец

        cardRegister.setOnClickListener {
            // переход на экран регистрации
            val intent = Intent(this, RegistrationActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }

        btnAuth.setOnClickListener {
            val phoneNumber = userPhoneAuth.text.toString().trim() // телефон из поля
            val password = userPassAuth.text.toString().trim() // пароль из поля

            if (phoneNumber.isEmpty() || password.isEmpty()) {
                // если пустые поля
                Toast.makeText(this, "Не все поля заполнены", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (!Validators.validateRussianPhone(phoneNumber)) {
                // проверка правильного формата телефона
                Toast.makeText(
                    this,
                    "Номер телефона должен содержать 10 цифр (не включая +7)",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            if (!Validators.validatePassword(password)) {
                // проверка пароля
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
                // запускаем авторизацию в отдельном потоке
                AuthUser(user)
            }
        }
    }

    private suspend fun AuthUser(user: User) {
        withContext(Dispatchers.IO) {
            try {
                val jsonBody = gson.toJson(user) // превращаем user в JSON
                val requestBody =
                    jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()) // создаём тело запроса

                val request = Request.Builder()
                    .url("https://safetyscooter.ru//login") // URL авторизации
                    .post(requestBody) // POST-запрос
                    .build()

                client.newCall(request).execute().use { response ->
                    // выполняем запрос

                    if (response.code == 200) {
                        // успешная авторизация
                        val responseBody = response.body?.string()
                        val authResponse =
                            gson.fromJson(responseBody, AuthResponse::class.java) // парсим ответ
                        val accessToken = authResponse.access_token // достаём токен

                        saveAccessToken(accessToken) // сохраняем токен

                        withContext(Dispatchers.Main) {
                            // возвращаемся в UI-поток
                            Toast.makeText(
                                this@AuthActivity,
                                "Авторизация успешна!",
                                Toast.LENGTH_LONG
                            ).show()

                            val intent =
                                Intent(this@AuthActivity, StartActivity::class.java) // переход в главное меню
                            intent.flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }

                    } else if (response.code == 404) {
                        // неверный номер
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@AuthActivity,
                                "Неверный номер",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                    } else if (response.code == 403) {
                        // неверный пароль
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@AuthActivity,
                                "Неверный пароль",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                    } else {
                        // любая другая ошибка
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@AuthActivity,
                                "Ошибка авторизации: ${response.code}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

            } catch (e: Exception) {
                // ошибка сети
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AuthActivity,
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
