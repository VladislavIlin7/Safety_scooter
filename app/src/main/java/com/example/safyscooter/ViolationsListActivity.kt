package com.example.safyscooter


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager

import com.example.safyscooter.databinding.ActivityViolationsListBinding

class ViolationsListActivity : ComponentActivity() {

    private lateinit var binding: ActivityViolationsListBinding
    private lateinit var adapter: ViolationsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViolationsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ViolationsAdapter { violation ->
            // можно открыть просмотр ролика или детали
        }

        binding.rvViolations.layoutManager = LinearLayoutManager(this)
        binding.rvViolations.adapter = adapter

        // Если стартовали с интентом отправки нового видео
        intent.getStringExtra("VIDEO_PATH")?.let { path ->
            val status = intent.getStringExtra("STATUS") ?: "Видео отправляется..."
            ViolationsRepository.addViolation(path, status)
        }

        ViolationsRepository.violations.observe(this, Observer { list ->
            adapter.submitList(list)
        })
    }
}

