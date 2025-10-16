package com.example.safyscooter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.safyscooter.databinding.ActivityPersonalBinding
import com.example.safyscooter.databinding.ItemViolationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PersonalActivity : ComponentActivity() {

    private lateinit var binding: ActivityPersonalBinding
    private lateinit var adapter: ViolationsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1) Создаём адаптер с обработчиком клика
        adapter = ViolationsAdapter { item ->
            startActivity(
                Intent(this, ViolationDetailsActivity::class.java)
                    .putExtra("VIOLATION_ID", item.id)
            )
        }

        binding.rvViolations.layoutManager = LinearLayoutManager(this)
        binding.rvViolations.adapter = adapter

        // 2) Первичная отрисовка (список может быть пуст)
        adapter.submitList(ViolationStore.items.toList())

        // 3) Кнопка "Записать нарушение" → обратно на StartActivity
        binding.btnAdd.setOnClickListener {
            startActivity(Intent(this, StartActivity::class.java))
            finish()
        }
    }

    // 4) Обновляем список при возврате на экран (после добавления/удаления)
    override fun onResume() {
        super.onResume()
        adapter.submitList(ViolationStore.items.toList())
    }
}

/** Адаптер с DiffUtil, форматированием времени и кликом по item */
private class ViolationsAdapter(
    private val onItemClick: (ViolationUi) -> Unit
) : ListAdapter<ViolationUi, ViolationsVH>(Diff) {

    object Diff : DiffUtil.ItemCallback<ViolationUi>() {
        override fun areItemsTheSame(old: ViolationUi, new: ViolationUi) = old.id == new.id
        override fun areContentsTheSame(old: ViolationUi, new: ViolationUi) = old == new
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViolationsVH {
        val binding = ItemViolationBinding.inflate(
            android.view.LayoutInflater.from(parent.context), parent, false
        )
        return ViolationsVH(binding)
    }

    override fun onBindViewHolder(holder: ViolationsVH, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }
}

private class ViolationsVH(
    private val binding: ItemViolationBinding
) : RecyclerView.ViewHolder(binding.root) {

    private val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    fun bind(item: ViolationUi, onClick: (ViolationUi) -> Unit) {
        binding.tvTitle.text = item.title
        binding.tvDate.text = formatter.format(Date(item.timestamp))
        binding.root.setOnClickListener { onClick(item) }
    }
}
