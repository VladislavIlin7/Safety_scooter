package com.example.safyscooter

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.safyscooter.databinding.ActivityReviewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import com.google.gson.Gson
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

class ReviewViolationActivity : ComponentActivity() {

    private lateinit var binding: ActivityReviewBinding
    private var videoPath: String? = null
    private var startTimestamp: Long = 0L
    private var locations: List<Pair<Double, Double>> = emptyList()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        videoPath = intent.getStringExtra("VIDEO_PATH")
        startTimestamp = intent.getLongExtra("START_TIMESTAMP", System.currentTimeMillis())

        val locationsArray = intent.getSerializableExtra("LOCATIONS") as? Array<DoubleArray>
        locations = locationsArray?.map {
            Pair(it[0], it[1])
        } ?: emptyList()

        val (thumb, durationSec) = extractMeta(videoPath)
        if (thumb != null) binding.ivThumb.setImageBitmap(thumb)
        binding.tvDuration.text = "Длительность: ${durationSec}s"
        binding.tvDate.text = formatTs(startTimestamp * 1000)

        binding.btnSend.setOnClickListener {
            binding.btnSend.isEnabled = false
            binding.btnDelete.isEnabled = false

            lifecycleScope.launch {
                try {
                    uploadViolation()
                    withContext(Dispatchers.Main) {
                        val nextIndex = ViolationStore.items.size + 1
                        ViolationStore.items.add(
                            0, ViolationUi(id = startTimestamp, title = "нарушение $nextIndex", timestamp = startTimestamp)
                        )
                        Toast.makeText(this@ReviewViolationActivity, "Успешно отправлено", Toast.LENGTH_LONG).show()
                        goToPersonal()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.btnSend.isEnabled = true
                        binding.btnDelete.isEnabled = true
                        Toast.makeText(this@ReviewViolationActivity, e.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        binding.btnDelete.setOnClickListener {
            videoPath?.let { File(it).delete() }
            goToPersonal()
        }
    }

    private suspend fun uploadViolation() = withContext(Dispatchers.IO) {
        val videoFile = File(videoPath ?: throw Exception("Ошибка: видео не найдено"))
        if (!videoFile.exists()) {
            throw Exception("Ошибка: файл видео не существует")
        }

        val accessToken = getAccessToken()
        if (accessToken.isBlank()) {
            throw Exception("Ошибка: пользователь не авторизован")
        }

        val videoBytes = videoFile.readBytes()
        val base64Video = android.util.Base64.encodeToString(videoBytes, android.util.Base64.DEFAULT)

        val uploadData = mapOf(
            "gps" to locations.map { "${it.first},${it.second}" },
            "time" to startTimestamp,
            "file" to base64Video
        )

        val jsonBody = Gson().toJson(uploadData)
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://safetyscooter.ru//video//upload")
            .post(requestBody)
            .header("Authorization", accessToken)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            when (response.code) {
                200 -> {
                    Toast.makeText(this@ReviewViolationActivity, "Успешно",
                        Toast.LENGTH_LONG).show()
                }
                401 -> throw Exception("Ошибка авторизации: 401")
                422 -> throw Exception("Ошибка валидации: 422")
//                500 -> throw Exception("Внутренняя ошибка сервера: 500")
                500 -> throw Exception("500: ${responseBody}")
                else -> throw Exception("Ошибка сервера: ${response.code}")
            }
        }
    }

    private fun getAccessToken(): String {
        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        return sharedPref.getString("access_token", "") ?: ""
    }

    private fun goToPersonal() {
        startActivity(Intent(this, PersonalActivity::class.java))
        finish()
    }

    private fun extractMeta(path: String?): Pair<Bitmap?, Int> {
        if (path.isNullOrEmpty()) return Pair(null, 0)
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val bmp = r.getFrameAtTime(0)
            Pair(bmp, (durMs / 1000).toInt())
        } catch (_: Throwable) {
            Pair(null, 0)
        } finally {
            r.release()
        }
    }

    private fun formatTs(ts: Long): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ts))
}