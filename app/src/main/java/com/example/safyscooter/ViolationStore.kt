package com.example.safyscooter

data class ViolationUi(
    val id: Long,           // уникальный id (можно = timestamp)
    val title: String,      // "нарушение 1", "нарушение 2", ...
    val timestamp: Long     // когда была запись (для отображения в item)
)

object ViolationStore {
    /** Самые новые сверху (стек) */
    val items: MutableList<ViolationUi> = mutableListOf()
}

