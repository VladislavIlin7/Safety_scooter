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
import com.example.safyscooter.databinding.ActivityPersonalBinding
import kotlinx.coroutines.Dispatchers
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

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.btnAdd.setOnClickListener {
            startActivity(Intent(this, StartActivity::class.java))
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadApplications()
        }

        binding.bottomNavigation.selectedItemId = R.id.nav_violations
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    val intent = Intent(this, StartActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent, ActivityOptionsCompat.makeCustomAnimation(
                        this, 0, 0
                    ).toBundle())
                    finish()
                    true
                }
                R.id.nav_violations -> true
                R.id.nav_profile -> {
                    val intent = Intent(this, ProfileActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent, ActivityOptionsCompat.makeCustomAnimation(
                        this, 0, 0
                    ).toBundle())
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ApplicationAdapter { application ->
            val intent = Intent(this, ViolationDetailsActivity::class.java).apply {
                putExtra("APPLICATION_ID", application.id)
                putExtra("LOCAL_NUMBER", application.localNumber)
            }
            startActivity(intent)
        }

        binding.rvViolations.layoutManager = LinearLayoutManager(this)
        binding.rvViolations.adapter = adapter
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

                withContext(Dispatchers.Main) {
                    adapter.submitList(applicationsWithLocalNumbers)
                    binding.swipeRefresh.isRefreshing = false

                    if (applications.isEmpty()) {
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