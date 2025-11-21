package com.example.safyscooter

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.safyscooter.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding

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
                    true
                }
                R.id.nav_violations -> {
                    val intent = Intent(this, PersonalActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent, androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(
                        this, 0, 0
                    ).toBundle())
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
        val sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val phone = sharedPreferences.getString("phone", "+7 (XXX) XXX-XX-XX")
        
        binding.tvPhone.text = phone
        binding.tvBalance.text = "0 ₽"
        binding.tvViolationsCount.text = "0"
        binding.tvSentCount.text = "0"
    }

    private fun logout() {
        // Очищаем оба SharedPreferences
        val sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        sharedPreferences.edit().clear().commit()
        
        val appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        appPrefs.edit().clear().commit()

        val intent = Intent(this, AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

