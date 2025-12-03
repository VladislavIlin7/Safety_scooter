package com.example.safyscooter.network

import com.example.safyscooter.data.ViolationStore
import com.example.safyscooter.data.ViolationUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object UploadManager {

    // Внутреннее состояние загрузки
    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)

    // Внешний read-only поток для подписки в UI
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    // Скоуп для фоновых корутин (IO-поток)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Флажок, чтобы не запустить несколько загрузок одновременно
    private var isUploading = AtomicBoolean(false)

    // HTTP-клиент с увеличенными таймаутами для больших видео
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    sealed class UploadState {
        object Idle : UploadState()

        data class Uploading(
            val progress: Int,        // прогресс в процентах
            val mbUploaded: Double,   // сколько МБ уже отправили
            val mbTotal: Double       // общий размер файла в МБ
        ) : UploadState()

        object Success : UploadState()
        data class Error(val message: String) : UploadState()
    }


    fun startUpload(
        accessToken: String,
        videoPath: String,
        timestamp: Long,
        locations: List<Pair<Double, Double>>
    ) {
        /**
         * Запускаем загрузку видео.
         *
         * @param accessToken токен авторизации
         * @param videoPath путь к видеофайлу
         * @param timestamp время записи (в секундах)
         * @param locations список координат (широта, долгота) по секундам
         */
        // Если уже идёт загрузка – выходим, вторую не запускаем
        if (isUploading.getAndSet(true)) return

        scope.launch {
            try {
                val videoFile = File(videoPath)
                if (!videoFile.exists()) {
                    throw Exception("Файл не найден")
                }

                // Собираем GPS в JSON-массив строк "lat,lon"
                val gpsJsonArray = JSONArray().apply {
                    locations.forEach { location ->
                        put("${location.first},${location.second}")
                    }
                }

                // Формируем multipart-запрос:
                // - gps: массив координат
                // - time: время записи
                // - file: само видео с обёрткой ProgressRequestBody
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("gps", gpsJsonArray.toString())
                    .addFormDataPart("time", timestamp.toString())
                    .addFormDataPart(
                        "file",
                        "video_${timestamp}.mp4",
                        ProgressRequestBody(
                            file = videoFile,
                            contentType = "application/octet-stream".toMediaTypeOrNull()
                        ) { bytesWritten, contentLength ->
                            // Колбэк прогресса – считаем проценты и МБ
                            val progress = (100.0 * bytesWritten / contentLength).toInt()
                            val uploadedMB = bytesWritten / (1024.0 * 1024.0)
                            val totalMB = contentLength / (1024.0 * 1024.0)

                            _uploadState.value =
                                UploadState.Uploading(progress, uploadedMB, totalMB)
                        }
                    )
                    .build()

                // Собираем HTTP-запрос
                val request = Request.Builder()
                    .url("https://safetyscooter.ru/video/upload")
                    .post(requestBody)
                    .header("Authorization", accessToken)
                    .build()

                // Выполняем запрос
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        // Если сервер ответил OK — добавляем запись в локальную историю
                        val nextIndex = ViolationStore.items.size + 1
                        ViolationStore.items.add(
                            0,
                            ViolationUi(
                                id = timestamp,
                                title = "нарушение $nextIndex",
                                timestamp = timestamp
                            )
                        )
                        _uploadState.value = UploadState.Success
                    } else {
                        // Любая ошибка от сервера
                        throw Exception("Ошибка сервера: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                // Любая ошибка при формировании/отправке/ответе
                _uploadState.value = UploadState.Error(e.message ?: "Неизвестная ошибка")
            } finally {
                // В любом случае – загрузка больше не считается активной
                isUploading.set(false)
                if (_uploadState.value is UploadState.Success) {
                    delay(4000)
                    _uploadState.value = UploadState.Idle
                }
            }
        }
    }

    fun resetState() {
        // Не даём сбросить состояние, если загрузка всё ещё идёт
        if (!isUploading.get()) {
            _uploadState.value = UploadState.Idle
        }
    }

    /**
     * Обёртка над RequestBody, которая считает прогресс загрузки.
     * Каждый раз, когда часть файла записана в сеть, вызываем колбэк.
     */
    private class ProgressRequestBody(
        private val file: File,
        private val contentType: MediaType?,
        private val progressListener: (bytesWritten: Long, contentLength: Long) -> Unit
    ) : RequestBody() {

        // Тип контента – video/octet-stream
        override fun contentType() = contentType

        // Общий размер файла
        override fun contentLength() = file.length()

        // Реальная запись файла побайтно
        override fun writeTo(sink: BufferedSink) {
            val fileLength = file.length()
            val buffer = ByteArray(2048)
            val inputStream = file.inputStream()
            var uploaded: Long = 0

            try {
                var read: Int
                // Читаем кусками по 2 КБ и отправляем в сеть
                while (inputStream.read(buffer).also { read = it } != -1) {
                    uploaded += read
                    sink.write(buffer, 0, read)
                    // Сообщаем, сколько уже отправили
                    progressListener(uploaded, fileLength)
                }
            } finally {
                inputStream.close()
            }
        }
    }
}
