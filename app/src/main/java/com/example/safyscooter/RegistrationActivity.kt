package com.example.safyscooter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
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
import okhttp3.ResponseBody.Companion.toResponseBody

class RegistrationActivity : Activity() {
    private val client = OkHttpClient()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        val userPhone: EditText = findViewById(R.id.user_phone)
        val userPass: EditText = findViewById(R.id.user_pass)
        val btnReg: Button = findViewById(R.id.btn_register)
        val linkToAuth: TextView = findViewById(R.id.link_auth)

        userPhone.setText("+7")
        userPhone.setSelection(userPhone.text.length)

        linkToAuth.setOnClickListener {
            val intent = Intent(this, AuthActivity::class.java)
            startActivity(intent)
        }

        btnReg.setOnClickListener {
            val phoneNumber = userPhone.text.toString().trim()
            val password = userPass.text.toString().trim()

            if (phoneNumber.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Не все поля заполнены", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (!Validators.validateRussinPhone(phoneNumber)) {
                Toast.makeText(this, "Номер телефона должен содержать 10 цифр (не включая +7)",
                    Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if(!Validators.validatePassword(password)) {
                Toast.makeText(this, "Пароль должен состоять из 8-16 символов и включать только цифры и латиницу",
                    Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val formattedPhone = "+7$phoneNumber"
            val user = User(formattedPhone, password)

            CoroutineScope(Dispatchers.IO).launch {
                registerUser(user)
            }
        }
    }

    private suspend fun registerUser(user: User) {
        withContext(Dispatchers.IO) {
            try {
                val jsonBody = gson.toJson(user)
                val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url("http://89.169.177.162/registration")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use() { response ->
                    if (response.code == 200) {
                        val responseBody = response.body?.string()
                        val authResponse = gson.fromJson(responseBody, AuthResponse::class.java)
                        val accessToken = authResponse.auth_access

                        saveAccessToken(accessToken)

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@RegistrationActivity, "Регистрация успешна!",
                                Toast.LENGTH_SHORT).show()
                            val intent = Intent(this@RegistrationActivity, StartActivity::class.java)
                            startActivity(intent)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@RegistrationActivity, "Ошибка регистрации: ${response.code}",
                                Toast.LENGTH_LONG).show()

                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RegistrationActivity, "Сетевая ошибка: ${e.message}",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveAccessToken(token: String) {
        val sharedPref = getSharedPreferences("apps_prefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("access_token", token)
            apply()
        }
    }

    private fun getAccessToken(): String? {
        val sharedPref = getSharedPreferences("apps_prefs", MODE_PRIVATE)
        return sharedPref.getString("access_token", null)
    }

    private fun clearAccessToken() {
        val sharedPref = getSharedPreferences("apps_prefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            remove("access_token")
            apply()
        }
    }
}
