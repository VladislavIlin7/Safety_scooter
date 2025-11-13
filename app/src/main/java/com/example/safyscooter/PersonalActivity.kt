package com.example.safyscooter

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.safyscooter.databinding.ActivityPersonalBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PersonalActivity : ComponentActivity() {

    private lateinit var binding: ActivityPersonalBinding
    private lateinit var adapter: ApplicationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadApplications()

        binding.btnRecord.setOnClickListener {
            startActivity(Intent(this, StartActivity::class.java))
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadApplications()
        }
    }

    private fun setupRecyclerView() {
        adapter = ApplicationAdapter { application ->
            val intent = Intent(this, ViolationDetailsActivity::class.java).apply {
                putExtra("APPLICATION_ID", application.id)
            }
            startActivity(intent)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun loadApplications() {
        lifecycleScope.launch {
            try {
                binding.swipeRefresh.isRefreshing = true
                val accessToken = getAccessToken()
                var applications = ApiService.getApplications(accessToken)

                applications = applications.sortedByDescending { it.recordTime }

                val totalApplications = applications.size
                val applicationsWithLocalNumbers = applications.mapIndexed { index, app ->
                    app.copy(localNumber = totalApplications - index)
                }

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    adapter.submitList(applicationsWithLocalNumbers)
                    binding.swipeRefresh.isRefreshing = false

                    if (applications.isEmpty()) {
                        binding.emptyState.visibility = View.VISIBLE
                        binding.recyclerView.visibility = View.GONE
                    } else {
                        binding.emptyState.visibility = View.GONE
                        binding.recyclerView.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    binding.swipeRefresh.isRefreshing = false
                    Toast.makeText(
                        this@PersonalActivity,
                        "Ошибка загрузки заявок: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun getAccessToken(): String {
        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        return sharedPref.getString("access_token", "") ?: ""
    }
}