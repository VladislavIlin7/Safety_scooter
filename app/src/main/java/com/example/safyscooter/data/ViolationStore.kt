package com.example.safyscooter.data

// Модель одного нарушения, которое мы храним локально
data class ViolationUi(
    val id: Long,              // уникальный ID (обычно timestamp)
    val title: String,         // название: "нарушение 1"
    val timestamp: Long,       // время записи
    val status: String = "статус в разработке"  // начальный статус
)

// Простое локальное хранилище нарушений
object ViolationStore {
    // Список всех нарушений (меняемый) — новые добавляем сверху
    val items: MutableList<ViolationUi> = mutableListOf()
}

