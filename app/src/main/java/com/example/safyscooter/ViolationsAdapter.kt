package com.example.safyscooter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

import com.example.safyscooter.databinding.ItemViolationBinding
import java.io.File

class ViolationsAdapter(
    private val onItemClick: (ViolationsRepository.Violation) -> Unit = {}
) : ListAdapter<ViolationsRepository.Violation, ViolationsAdapter.ViolationVH>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViolationVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemViolationBinding.inflate(inflater, parent, false)
        return ViolationVH(binding)
    }

    override fun onBindViewHolder(holder: ViolationVH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViolationVH(private val b: ItemViolationBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(item: ViolationsRepository.Violation) {
            b.tvFileName.text = File(item.path).name
            b.tvStatus.text = item.status

            // При клике, можно открыть просмотр видео / детали
            b.root.setOnClickListener { onItemClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ViolationsRepository.Violation>() {
        override fun areItemsTheSame(
            oldItem: ViolationsRepository.Violation,
            newItem: ViolationsRepository.Violation
        ): Boolean = oldItem.path == newItem.path

        override fun areContentsTheSame(
            oldItem: ViolationsRepository.Violation,
            newItem: ViolationsRepository.Violation
        ): Boolean = oldItem.path == newItem.path && oldItem.status == newItem.status
    }
}
