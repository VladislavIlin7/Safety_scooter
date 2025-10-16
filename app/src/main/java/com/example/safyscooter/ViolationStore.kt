package com.example.safyscooter

data class ViolationUi(
    val id: Long,          // уникальный id (можно = timestamp)
    val title: String,     // "нарушение 1", "нарушение 2", ...
    val timestamp: Long    // время записи видео
)

object ViolationStore {
    /** Новейшие элементы в начале */
    val items: MutableList<ViolationUi> = mutableListOf()
}
