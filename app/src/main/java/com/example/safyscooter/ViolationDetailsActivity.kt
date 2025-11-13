package com.example.safyscooter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.safyscooter.databinding.ActivityViolationDetailsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ViolationDetailsActivity : ComponentActivity() {

    private lateinit var binding: ActivityViolationDetailsBinding
    private lateinit var vm: ViolationDetailsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViolationDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val applicationId = intent.getLongExtra("APPLICATION_ID", -1L)
        require(applicationId > 0) { "APPLICATION_ID is required" }

        vm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ViolationDetailsViewModel(applicationId) as T
            }
        })[ViolationDetailsViewModel::class.java]

        lifecycleScope.launch {
            vm.ui.collectLatest { ui ->
                binding.tvTitle.text = ui.title
                binding.tvDateTime.text = ui.dateTime
                binding.tvStatus.text = ui.status
            }
        }

        vm.refresh()

        binding.btnBackToList.setOnClickListener {
            startActivity(Intent(this, PersonalActivity::class.java))
            finish()
        }

        binding.btnRetry.setOnClickListener {
            vm.refresh()
        }
    }
}
