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


        val userLogin: EditText = findViewById(R.id.user_login)
        val userEmail: EditText = findViewById(R.id.user_email)
        val userPass: EditText = findViewById(R.id.user_pass)
        val btnReg: Button = findViewById(R.id.btn_register)

        val linkToAuth: TextView = findViewById(R.id.link_auth)

        linkToAuth.setOnClickListener {
            val intent = Intent(this, AuthActivity::class.java)
            startActivity(intent)
        }


        btnReg.setOnClickListener{
            val login = userLogin.text.toString().trim()
            val email = userEmail.text.toString().trim()
            val pass = userPass.text.toString().trim()


            if(login == "" || email == "" || pass == "")
                Toast.makeText(this, "Не все полоя заполнены", Toast.LENGTH_LONG).show()
            else{
                val user = User(login, email,pass)

                val intent = Intent(this, StartActivity::class.java)
                startActivity(intent)
            }


        }

    }

}
