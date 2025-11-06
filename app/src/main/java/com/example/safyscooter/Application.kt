package com.example.safyscooter

import java.text.SimpleDateFormat
import java.util.*

data class Application(
    val id: Long,
    val status: String,
    val key: String,
    val address: String,
    val recordTime: String,
    val lastChange: String,
    val localNumber: Int = 0
) {
    fun getFormattedDate(): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val outputFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            outputFormat.timeZone = TimeZone.getDefault()
            val date = inputFormat.parse(recordTime)
            outputFormat.format(date)
        } catch (e: Exception) {
            recordTime
        }
    }
}
