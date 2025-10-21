package com.example.safyscooter


import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Хранит потоки статусов по id нарушения и умеет "обновлять" их с сервера.
 * Сейчас сервер имитируется (delay + фейковый ответ).
 * Подключение Retrofit ниже — см. комментарий.
 */
object ViolationRepository {
    private val statusFlows: MutableMap<Long, MutableStateFlow<String>> = mutableMapOf()
    private val io: CoroutineDispatcher = Dispatchers.IO

    fun statusFlow(id: Long): StateFlow<String> {
        val initial = ViolationStore.getById(id)?.status ?: "статус в разработке"
        return statusFlows.getOrPut(id) { MutableStateFlow(initial) }
    }

    /**
     * Имитация запроса статуса с бэкенда и распространение в Flow.
     * Вместо имитации подключишь Retrofit и проставишь реальное значение.
     */
    suspend fun refreshStatus(id: Long) = withContext(io) {
        // TODO: ЗАМЕНИТЬ на реальный вызов Retrofit:
        // val dto = api.getStatus(id)  // например, {"status":"принято"}
        // val newStatus = dto.status
        delay(1200) // имитация сетевой задержки
        val newStatus = "статус в разработке" // пока оставим так или подставь другое

        // Обновим in-memory и flow:
        ViolationStore.updateStatus(id, newStatus)
        statusFlows[id]?.update { newStatus }
    }
}

/* Пример будущего Retrofit API:
interface ViolationApi {
    @GET("violations/{id}/status")
    suspend fun getStatus(@Path("id") id: Long): StatusDto
}
data class StatusDto(val status: String)
*/
