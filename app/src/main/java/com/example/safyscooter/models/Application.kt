package com.example.safyscooter.models

import com.example.safyscooter.R
import java.text.SimpleDateFormat
import java.util.*

data class Verdict(
    val id: Long,
    val type: String,
    val scooterType: String?,
    val objectId: String?,
    val timestamp: Double,
    val coordinates: List<Double>,
    val createdAt: String?
)

data class Application(
    val id: Long,
    val status: String,
    val key: String,
    val recordTime: String?,
    val lastChange: String?,
    val verdicts: List<Verdict> = emptyList(),
    val localNumber: Int = 0
) {
    fun getFormattedDate(): String {
        return try {
            if (recordTime.isNullOrEmpty()) return "Нет данных"
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val outputFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            outputFormat.timeZone = TimeZone.getDefault()
            val date = inputFormat.parse(recordTime)
            date?.let { outputFormat.format(it) } ?: "Нет данных"
        } catch (e: Exception) {
            recordTime ?: "Нет данных"
        }
    }
    
    fun getStatusColor(): Int {
        return when (status.lowercase()) {
            "нарушение обнаружено", "отправлено", "успешно отправлено" -> R.color.success
            "проверяется", "в обработке", "на рассмотрении" -> R.color.info
            "нуждается в ручной проверке" -> R.color.warning
            "нарушение не обнаружено", "отклонено", "ошибка" -> R.color.error
            "черновик", "не отправлено" -> R.color.text_tertiary
            else -> R.color.secondary
        }
    }
}
