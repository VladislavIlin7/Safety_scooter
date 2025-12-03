package com.example.safyscooter.data

data class ViolationUi(
    val id: Long,             // уникальный id (ts)
    val title: String,        // "нарушение 1", ...
    val timestamp: Long,      // время записи
    val status: String = "статус в разработке" // начальный статус
)

object ViolationStore {
    /** новые сверху */
    val items: MutableList<ViolationUi> = mutableListOf()

    fun getById(id: Long): ViolationUi? = items.firstOrNull { it.id == id }

    fun updateStatus(id: Long, newStatus: String) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) items[idx] = items[idx].copy(status = newStatus)
    }
}

