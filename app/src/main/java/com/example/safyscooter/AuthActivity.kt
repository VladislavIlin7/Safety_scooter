package com.example.safyscooter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class AuthActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val userPhoneAuth: EditText = findViewById(R.id.user_phone_auth)
        val userPassAuth: EditText = findViewById(R.id.user_pass_auth)
        val btnAuth: Button = findViewById(R.id.btn_auth)
        val linkToReg: TextView = findViewById(R.id.link_reg)

        linkToReg.setOnClickListener {
            val intent = Intent(this, RegistrationActivity::class.java)
            startActivity(intent)
        }

        btnAuth.setOnClickListener {
            val user_phone = userPhoneAuth.text.toString().trim()
            val pass = userPassAuth.text.toString().trim()


<<<<<<< HEAD
            if (user_phone == "" || pass == "")
=======
            if (login == "" || pass == "")
>>>>>>> ea4c2da6bfb752bac1637dd0425aa28e2002e61f
                Toast.makeText(this, "Не все поля заполнены", Toast.LENGTH_LONG).show()
            else {

                val intent = Intent(this, StartActivity::class.java)
                startActivity(intent)
            }

        }
    }
}
