package com.example.safyscooter

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object UploadManager {
    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isUploading = AtomicBoolean(false)

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    sealed class UploadState {
        object Idle : UploadState()
        data class Uploading(val progress: Int, val mbUploaded: Double, val mbTotal: Double) : UploadState()
        object Success : UploadState()
        data class Error(val message: String) : UploadState()
    }

    fun startUpload(
        accessToken: String,
        videoPath: String,
        timestamp: Long,
        locations: List<Pair<Double, Double>>
    ) {
        if (isUploading.getAndSet(true)) return // Уже идет загрузка

        scope.launch {
            try {
                val videoFile = File(videoPath)
                if (!videoFile.exists()) {
                    throw Exception("Файл не найден")
                }

                val gpsJsonArray = JSONArray().apply {
                    locations.forEach { location ->
                        put("${location.first},${location.second}")
                    }
                }

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("gps", gpsJsonArray.toString())
                    .addFormDataPart("time", timestamp.toString())
                    .addFormDataPart(
                        "file",
                        "video_${timestamp}.mp4",
                        ProgressRequestBody(videoFile, "application/octet-stream".toMediaTypeOrNull()) { bytesWritten, contentLength ->
                            val progress = (100.0 * bytesWritten / contentLength).toInt()
                            val uploadedMB = bytesWritten / (1024.0 * 1024.0)
                            val totalMB = contentLength / (1024.0 * 1024.0)
                            
                            _uploadState.value = UploadState.Uploading(progress, uploadedMB, totalMB)
                        }
                    )
                    .build()

                val request = Request.Builder()
                    .url("https://safetyscooter.ru/video/upload")
                    .post(requestBody)
                    .header("Authorization", accessToken)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        // Добавляем локально в историю
                        val nextIndex = ViolationStore.items.size + 1
                        ViolationStore.items.add(
                            0, ViolationUi(id = timestamp, title = "нарушение $nextIndex", timestamp = timestamp)
                        )
                        _uploadState.value = UploadState.Success
                    } else {
                        throw Exception("Ошибка сервера: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                _uploadState.value = UploadState.Error(e.message ?: "Неизвестная ошибка")
            } finally {
                isUploading.set(false)
                // Сбрасываем статус Success через пару секунд, чтобы скрыть плашку
                if (_uploadState.value is UploadState.Success) {
                    kotlinx.coroutines.delay(4000)
                    _uploadState.value = UploadState.Idle
                }
            }
        }
    }
    
    fun resetState() {
        if (!isUploading.get()) {
            _uploadState.value = UploadState.Idle
        }
    }

    private class ProgressRequestBody(
        private val file: File,
        private val contentType: okhttp3.MediaType?,
        private val progressListener: (bytesWritten: Long, contentLength: Long) -> Unit
    ) : RequestBody() {
        override fun contentType() = contentType
        override fun contentLength() = file.length()
        override fun writeTo(sink: BufferedSink) {
            val fileLength = file.length()
            val buffer = ByteArray(2048)
            val inputStream = file.inputStream()
            var uploaded: Long = 0
            try {
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    uploaded += read
                    sink.write(buffer, 0, read)
                    progressListener(uploaded, fileLength)
                }
            } finally {
                inputStream.close()
            }
        }
    }
}



