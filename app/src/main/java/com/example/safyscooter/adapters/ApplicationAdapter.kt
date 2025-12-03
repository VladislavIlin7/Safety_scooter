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

/**
 * Адаптер для списка заявок (RecyclerView).
 * Он получает список Application и отображает каждую заявку как карточку.
 */
class ApplicationAdapter(
    private val onItemClick: (Application) -> Unit // что делать при клике
) : ListAdapter<Application, ApplicationAdapter.ViewHolder>(ApplicationDiffCallback) {

    // Создаём ViewHolder — объект, который хранит ссылки на элементы разметки
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_violation, parent, false) // берём разметку одной карточки
        return ViewHolder(view)
    }

    // Привязываем данные (Application) к ViewHolder
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val application = getItem(position)
        holder.bind(application) // отображаем данные
        holder.itemView.setOnClickListener { onItemClick(application) } // обработка клика
    }

    /**
     * ViewHolder — объект, который хранит ссылки на элементы интерфейса карточки
     * Здесь мы просто задаём текст: номер заявки, дата, статус, цвет статуса
     */
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        // Находим элементы по id из item_violation.xml
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val statusIndicator: View = itemView.findViewById(R.id.statusIndicator)

        // Привязка данных одной заявки к UI
        fun bind(application: Application) {
            val context = itemView.context

            tvTitle.text = if (application.verdicts.isNotEmpty()) {
                "Заявка #${application.localNumber} • ${application.verdicts.size} вердикт(а/ов)"
            } else {
                "Заявка #${application.localNumber}"
            }

            // Форматированная дата (метод внутри Application)
            tvDate.text = application.getFormattedDate()

            // Отображаем статус
            tvStatus.text = application.status

            // Определяем цвет статуса (метод getStatusColor внутри Application)
            val statusColor = ContextCompat.getColor(context, application.getStatusColor())

            tvStatus.setTextColor(statusColor)
            statusIndicator.setBackgroundColor(statusColor)
        }
    }

    /**
     * DiffUtil — механизм для оптимизации списка.
     * Он сравнивает старые и новые элементы и обновляет только изменившиеся.
     */
    object ApplicationDiffCallback : DiffUtil.ItemCallback<Application>() {

        // Считаем элементы одинаковыми, если совпадает их id
        override fun areItemsTheSame(oldItem: Application, newItem: Application): Boolean {
            return oldItem.id == newItem.id
        }

        // Содержимое одинаково, если объекты полностью равны
        override fun areContentsTheSame(oldItem: Application, newItem: Application): Boolean {
            return oldItem == newItem
        }
    }
}
