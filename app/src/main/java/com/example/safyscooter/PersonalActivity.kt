package com.example.safyscooter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safyscooter.databinding.ActivityPersonalBinding
import com.example.safyscooter.databinding.ItemViolationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PersonalActivity : ComponentActivity() {

    private lateinit var binding: ActivityPersonalBinding
    private val adapter = ViolationsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvViolations.layoutManager = LinearLayoutManager(this)
        binding.rvViolations.adapter = adapter

        // 1) Если пришла метка времени — добавим новый элемент в начало (стек)
        intent.getLongExtra("NEW_VIOLATION_TS", -1L).takeIf { it > 0L }?.let { ts ->
            val nextIndex = ViolationStore.items.size + 1 // "нарушение 1" для первого
            val item = ViolationUi(
                id = ts,                       // можно использовать ts как уникальный id
                title = "нарушение $nextIndex",
                timestamp = ts
            )
            ViolationStore.items.add(0, item) // в начало списка
        }

        // 2) Отобразим текущее состояние списка (может быть пустым)
        adapter.submitList(ViolationStore.items.toList())

        // 3) Кнопка "Записать нарушение" → обратно на StartActivity
        binding.btnAdd.setOnClickListener {
            startActivity(Intent(this, StartActivity::class.java))
            finish()
        }
    }
}

/** Адаптер с DiffUtil и форматированием даты/времени */
private class ViolationsAdapter :
    ListAdapter<ViolationUi, ViolationsVH>(Diff) {

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
        holder.bind(getItem(position))
    }
}

private class ViolationsVH(
    private val binding: ItemViolationBinding
) : RecyclerView.ViewHolder(binding.root) {

    private val formatter =
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    fun bind(item: ViolationUi) {
        binding.tvTitle.text = item.title
        binding.tvDate.text = formatter.format(Date(item.timestamp))
    }
}
