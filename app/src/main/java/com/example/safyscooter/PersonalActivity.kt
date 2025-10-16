package com.example.safyscooter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.safyscooter.databinding.ActivityPersonalBinding
import com.google.android.material.card.MaterialCardView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView

data class Violation(val id: Int, val title: String)

class PersonalActivity : ComponentActivity() {

    private lateinit var binding: ActivityPersonalBinding
    private val adapter = ViolationsAdapter(
        listOf(
            Violation(1, "нарушение 1"),
            Violation(2, "нарушение 2"),
            Violation(3, "нарушение 3")
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Настройка списка
        binding.rvViolations.layoutManager = LinearLayoutManager(this)
        binding.rvViolations.adapter = adapter

        // Переход обратно на экран камеры (StartActivity)
        binding.btnAdd.setOnClickListener {
            val intent = Intent(this, StartActivity::class.java)
            startActivity(intent)
            finish() // закрываем PersonalActivity, чтобы не накапливались в стеке
        }
    }
}

class ViolationsAdapter(private val items: List<Violation>) :
    androidx.recyclerview.widget.RecyclerView.Adapter<ViolationsAdapter.ViewHolder>() {

    inner class ViewHolder(val card: MaterialCardView) : androidx.recyclerview.widget.RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val card = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_violation, parent, false) as MaterialCardView
        return ViewHolder(card)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val tvTitle = holder.card.findViewById<TextView>(R.id.tvTitle)
        tvTitle.text = item.title
    }

    override fun getItemCount() = items.size
}
