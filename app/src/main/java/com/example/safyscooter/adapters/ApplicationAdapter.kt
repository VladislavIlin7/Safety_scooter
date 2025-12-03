package com.example.safyscooter.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.safyscooter.R
import com.example.safyscooter.models.Application

class ApplicationAdapter(
    private val onItemClick: (Application) -> Unit
) : ListAdapter<Application, ApplicationAdapter.ViewHolder>(ApplicationDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_violation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val application = getItem(position)
        holder.bind(application)
        holder.itemView.setOnClickListener { onItemClick(application) }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val statusIndicator: View = itemView.findViewById(R.id.statusIndicator)

        fun bind(application: Application) {
            val context = itemView.context

            tvTitle.text = if (application.verdicts.isNotEmpty()) {
                "Заявка #${application.localNumber} • ${application.verdicts.size} вердикт(а/ов)"
            } else {
                "Заявка #${application.localNumber}"
            }

            tvDate.text = application.getFormattedDate()

            tvStatus.text = application.status

            val statusColor = ContextCompat.getColor(context, application.getStatusColor())
            tvStatus.setTextColor(statusColor)
            statusIndicator.setBackgroundColor(statusColor)
        }
    }

    object ApplicationDiffCallback : DiffUtil.ItemCallback<Application>() {
        override fun areItemsTheSame(oldItem: Application, newItem: Application): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Application, newItem: Application): Boolean {
            return oldItem == newItem
        }
    }
}