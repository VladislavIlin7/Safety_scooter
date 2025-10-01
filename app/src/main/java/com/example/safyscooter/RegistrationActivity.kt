package com.example.safyscooter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class RegistrationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        val userPhone: EditText = findViewById(R.id.user_phone)
        val userPass: EditText = findViewById(R.id.user_pass)
        val btnReg: Button = findViewById(R.id.btn_register)
        val linkToAuth: TextView = findViewById(R.id.link_auth)

        print(1)

        linkToAuth.setOnClickListener {
            val intent = Intent(this, AuthActivity::class.java)
            startActivity(intent)
        }

        btnReg.setOnClickListener {
            val phone_number = userPhone.text.toString().trim()
            val pass = userPass.text.toString().trim()

            if (phone_number == "" || pass == "")
                Toast.makeText(this, "Не все поля заполнены", Toast.LENGTH_LONG).show()
            else {
                val user = User(phone_number, pass)

                val intent = Intent(this, StartActivity::class.java)
                startActivity(intent)
            }
        }
    }
}
