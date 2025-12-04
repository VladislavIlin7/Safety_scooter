package com.example.safyscooter.acticities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.safyscooter.network.ApiService
import com.example.safyscooter.adapters.ApplicationAdapter
import com.example.safyscooter.R
import com.example.safyscooter.databinding.ActivityViolationsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViolationsActivity : ComponentActivity() {

    private lateinit var binding: ActivityViolationsBinding   // доступ к элементам разметки
    private lateinit var adapter: ApplicationAdapter        // адаптер для списка заявок

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViolationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()   // настраиваем список
        loadApplications()    // загружаем заявки с сервера

        binding.toolbar.setNavigationOnClickListener {
            finish()          // стрелка “назад” в тулбаре
        }

        binding.btnAdd.setOnClickListener {
            // переход на экран записи нарушения
            startActivity(Intent(this, StartActivity::class.java))
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadApplications() // обновление списка свайпом
        }

        // выбираем пункт нижнего меню “Нарушения”
        binding.bottomNavigation.selectedItemId = R.id.nav_violations
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // переход на экран камеры
                    val intent = Intent(this, StartActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(
                        intent,
                        ActivityOptionsCompat.makeCustomAnimation(
                            this, 0, 0
                        ).toBundle()
                    )
                    finish()
                    true
                }

                R.id.nav_violations -> true
                R.id.nav_profile -> {
                    // переход в профиль
                    val intent = Intent(this, ProfileActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(
                        intent,
                        ActivityOptionsCompat.makeCustomAnimation(
                            this, 0, 0
                        ).toBundle()
                    )
                    finish()
                    true
                }

                else -> false
            }
        }
    }

    // настройка RecyclerView
    private fun setupRecyclerView() {
        adapter = ApplicationAdapter { application ->
            // при клике открываем детали заявки
            val intent = Intent(this, ViolationDetailsActivity::class.java).apply {
                putExtra("APPLICATION_ID", application.id)
                putExtra("LOCAL_NUMBER", application.localNumber)
            }
            startActivity(intent)
        }

        binding.rvViolations.layoutManager = LinearLayoutManager(this) // вертикальный список
        binding.rvViolations.adapter = adapter
    }

    // загрузка заявок с сервера
    private fun loadApplications() {
        lifecycleScope.launch {
            try {
                binding.swipeRefresh.isRefreshing = true  // показываем спиннер обновления
                val accessToken = getAccessToken()        // берём токен из SharedPreferences
                var applications = ApiService.getApplications(accessToken) // запрос к API

                // сортируем по времени создания (новые сверху)
                applications = applications.sortedByDescending { it.recordTime }

                val totalApplications = applications.size
                // присваиваем локальный номер “1,2,3...” от нижней к верхней
                val applicationsWithLocalNumbers = applications.mapIndexed { index, app ->
                    app.copy(localNumber = totalApplications - index)
                }

                withContext(Dispatchers.Main) {
                    adapter.submitList(applicationsWithLocalNumbers) // передаём список адаптеру
                    binding.swipeRefresh.isRefreshing = false

                    if (applications.isEmpty()) {
                        // если заявок нет — показываем пустое состояние
                        binding.emptyState.visibility = View.VISIBLE
                        binding.rvViolations.visibility = View.GONE
                    } else {
                        binding.emptyState.visibility = View.GONE
                        binding.rvViolations.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.swipeRefresh.isRefreshing = false
                    // показываем ошибку загрузки
                    Toast.makeText(
                        this@ViolationsActivity,             // контекст этой активности
                        "Ошибка загрузки заявок: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // получаем токен доступа из SharedPreferences
    private fun getAccessToken(): String {
        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        return sharedPref.getString("access_token", "") ?: ""
    }
}
