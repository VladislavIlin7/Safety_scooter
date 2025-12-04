package com.example.safyscooter.acticities

import android.graphics.Typeface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.safyscooter.network.ApiService
import com.example.safyscooter.models.Application
import com.example.safyscooter.R
import com.example.safyscooter.models.Verdict
import com.example.safyscooter.databinding.ActivityViolationDetailsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class ViolationDetailsActivity : ComponentActivity() {

    private lateinit var binding: ActivityViolationDetailsBinding // привязка к layout
    private var isVideoPlaying = false // играет ли сейчас видео
    private var localNumber: Int = 0 // локальный номер заявки в списке

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViolationDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root) // показываем экран

        binding.toolbar.setNavigationOnClickListener {
            finish() // кнопка назад
        }

        val applicationId = intent.getLongExtra("APPLICATION_ID", -1L) // id заявки из списка
        localNumber = intent.getIntExtra("LOCAL_NUMBER", 0) // её локальный номер
        require(applicationId > 0) { "APPLICATION_ID is required" } // проверяем, что id передан

        loadApplicationDetails(applicationId) // загружаем данные заявки
        setupVideoPlayer(applicationId) // настраиваем плеер видео
    }

    private fun loadApplicationDetails(applicationId: Long) {
        lifecycleScope.launch {
            try {
                val accessToken = getAccessToken() // берём токен
                val applications = ApiService.getApplications(accessToken) // загружаем все заявки
                val application = applications.find { it.id == applicationId } // ищем нужную

                withContext(Dispatchers.Main) {
                    if (application != null) {
                        displayApplication(application) // показываем данные заявки
                    } else {
                        binding.tvStatus.text = "Заявка не найдена"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = "Ошибка загрузки: ${e.message}"
                }
            }
        }
    }

    private fun displayApplication(app: Application) {
        // выбираем номер: из интента или из самой заявки
        val displayNumber = localNumber.takeIf { it > 0 } ?: app.localNumber
        binding.tvTitle.text = "Заявка #$displayNumber"
        binding.tvDateTime.text = app.getFormattedDate() // форматированная дата
        binding.tvStatus.text = app.status // статус заявки
        binding.tvViolationId.text = "#${app.id}" // ID заявки

        // форматируем дату последнего изменения
        val lastChange = app.lastChange?.let {
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                val date = inputFormat.parse(it)
                date?.let { d -> outputFormat.format(d) } ?: it
            } catch (e: Exception) {
                it
            }
        } ?: "Нет данных"
        binding.tvLastChange.text = lastChange

        val statusColor = ContextCompat.getColor(this, app.getStatusColor()) // цвет статуса
        binding.tvStatus.setTextColor(statusColor)

        // проверяем статус текста на наличие "обнаружено / не обнаружено"
        val isViolationNotFound = app.status.lowercase().contains("не обнаружено")
        val isViolationFound = app.status.lowercase().contains("обнаружено") && !isViolationNotFound

        when {
            isViolationNotFound -> {
                // если нарушение не найдено
                binding.tvStatusIcon.text = "✕"
                binding.tvStatusIcon.setTextColor(ContextCompat.getColor(this, R.color.error))
                binding.statusCard.setCardBackgroundColor(
                    ContextCompat.getColor(this, R.color.error_light)
                )
                binding.tvStatusDescription.text = "Нарушение не было обнаружено при проверке"
            }
            isViolationFound -> {
                // если нарушение найдено
                binding.tvStatusIcon.text = "✓"
                binding.tvStatusIcon.setTextColor(ContextCompat.getColor(this, R.color.success))
                binding.statusCard.setCardBackgroundColor(
                    ContextCompat.getColor(this, R.color.success_light)
                )
                binding.tvStatusDescription.text =
                    "Нарушение успешно обнаружено и зафиксировано"
            }
            app.status.lowercase().contains("проверяется") -> {
                // если статус "проверяется"
                binding.tvStatusIcon.text = "⏱"
                binding.tvStatusIcon.setTextColor(ContextCompat.getColor(this, R.color.info))
                binding.statusCard.setCardBackgroundColor(
                    ContextCompat.getColor(this, R.color.info_light)
                )
                binding.tvStatusDescription.text = "Ваша заявка находится на рассмотрении"
            }
            else -> {
                // все остальные статусы
                binding.tvStatusIcon.text = "ℹ"
                binding.tvStatusIcon.setTextColor(
                    ContextCompat.getColor(this, R.color.secondary)
                )
                binding.statusCard.setCardBackgroundColor(
                    ContextCompat.getColor(this, R.color.surface_variant)
                )
                binding.tvStatusDescription.text = "Статус заявки: ${app.status}"
            }
        }

        // показываем вердикты, если они есть
        if (app.verdicts.isNotEmpty()) {
            binding.tvNoVerdicts.visibility = View.GONE
            binding.verdictsContainer.removeAllViews()

            app.verdicts.forEachIndexed { index, verdict ->
                val verdictView = createVerdictView(verdict, index + 1) // создаём блок вердикта
                binding.verdictsContainer.addView(verdictView)
            }
        } else {
            binding.tvNoVerdicts.visibility = View.VISIBLE
        }

        updateInfoMessage(app.status) // обновляем текст подсказки
    }

    private fun updateInfoMessage(status: String) {
        // текстовая подсказка под статусом
        val infoText = when (status.lowercase()) {
            "нарушение обнаружено" ->
                "✓ Нарушение успешно обнаружено и зафиксировано в системе."
            "нарушение не обнаружено" ->
                "✕ Нарушение не было обнаружено при проверке."
            "проверяется" ->
                "Ваше нарушение находится на рассмотрении. Вы получите уведомление о результатах."
            "нуждается в ручной проверке" ->
                "Заявка требует дополнительной ручной проверки модератором."
            else ->
                "Статус заявки: $status"
        }

        binding.tvInfoMessage.text = infoText
    }

    private fun createVerdictView(verdict: Verdict, index: Int): View {
        // создаём карточку одного вердикта
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 1) topMargin = 16.dpToPx() // отступы между вердиктами
            }
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            setBackgroundColor(
                ContextCompat.getColor(this@ViolationDetailsActivity, R.color.surface_variant)
            )
        }

        // заголовок "Вердикт #N"
        val titleView = TextView(this).apply {
            text = "Вердикт #$index"
            textSize = 16f
            setTextColor(
                ContextCompat.getColor(this@ViolationDetailsActivity, R.color.text_primary)
            )
            setTypeface(null, Typeface.BOLD)
        }
        layout.addView(titleView)

        // строка "Тип"
        addVerdictRow(layout, "Тип", verdict.type)
        // строка "Тип самоката", если есть
        verdict.scooterType?.let { addVerdictRow(layout, "Тип самоката", it) }

        // если есть координаты — добавляем их
        if (verdict.coordinates.isNotEmpty()) {
            val coords = verdict.coordinates.joinToString(", ") { "%.6f".format(it) }
            addVerdictRow(layout, "Координаты", coords)
        }

        return layout
    }

    private fun addVerdictRow(parent: LinearLayout, label: String, value: String) {
        // создаём одну строку вида "Название: Значение"
        val rowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dpToPx()
            }
        }

        val labelView = TextView(this).apply {
            text = "$label:"
            textSize = 14f
            setTextColor(
                ContextCompat.getColor(this@ViolationDetailsActivity, R.color.text_secondary)
            )
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        rowLayout.addView(labelView)

        val valueView = TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(
                ContextCompat.getColor(this@ViolationDetailsActivity, R.color.text_primary)
            )
            setTypeface(null, Typeface.BOLD)
        }
        rowLayout.addView(valueView)

        parent.addView(rowLayout)
    }

    private fun Int.dpToPx(): Int {
        // конвертация dp в пиксели
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun getAccessToken(): String {
        // получаем access_token из SharedPreferences
        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        return sharedPref.getString("access_token", "") ?: ""
    }

    private suspend fun checkVideoAvailability(videoUrl: String, accessToken: String): String {
        // проверяем доступность видео и получаем финальную ссылку (S3)
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val request = Request.Builder()
                .url(videoUrl)
                .header("Authorization", accessToken)
                .get()
                .build()

            val response = client.newCall(request).execute()

            Log.d("ViolationDetails", "Video check response code: ${response.code}")
            Log.d("ViolationDetails", "Content-Type: ${response.header("Content-Type")}")

            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            val responseBody =
                response.body?.string() ?: throw Exception("Пустой ответ от сервера")
            Log.d("ViolationDetails", "Response body: $responseBody")

            response.close()

            // разбираем JSON и достаём поле download_url
            try {
                val jsonObject = JSONObject(responseBody)
                val downloadUrl = jsonObject.getString("download_url")
                Log.d("ViolationDetails", "Extracted download URL: $downloadUrl")
                downloadUrl
            } catch (e: Exception) {
                Log.e("ViolationDetails", "Failed to parse JSON: ${e.message}")
                throw Exception("Не удалось получить ссылку на видео: ${e.message}")
            }
        }
    }

    private fun setupVideoPlayer(applicationId: Long) {
        // базовый URL для запроса ссылки на видео
        val videoUrl = "https://safetyscooter.ru/video/download/$applicationId"

        Log.d("ViolationDetails", "Loading video from: $videoUrl")

        binding.videoLoadingProgress.visibility = View.VISIBLE
        binding.playButton.visibility = View.GONE
        binding.tvVideoError.visibility = View.GONE

        // сначала получаем финальный URL с сервера
        lifecycleScope.launch {
            val finalVideoUrl = try {
                val accessToken = getAccessToken()
                checkVideoAvailability(videoUrl, accessToken)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("ViolationDetails", "Video check failed: ${e.message}", e)
                    binding.videoLoadingProgress.visibility = View.GONE
                    binding.tvVideoError.visibility = View.VISIBLE
                    binding.tvVideoError.text = when {
                        e.message?.contains("404") == true ->
                            "Видео не найдено на сервере"
                        e.message?.contains("403") == true ->
                            "Нет доступа к видео"
                        e.message?.contains("401") == true ->
                            "Требуется авторизация"
                        else ->
                            "Видео недоступно: ${e.message}"
                    }
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                try {
                    Log.d("ViolationDetails", "Loading video from S3: $finalVideoUrl")

                    // S3 ссылка уже подписана, заголовки авторизации не нужны
                    val uri = Uri.parse(finalVideoUrl)
                    binding.videoView.setVideoURI(uri)

                    binding.videoView.setOnPreparedListener { mediaPlayer ->
                        Log.d("ViolationDetails", "Video prepared successfully")
                        binding.videoLoadingProgress.visibility = View.GONE
                        binding.playButton.visibility = View.VISIBLE
                        mediaPlayer.isLooping = false // не зацикливаем
                    }

                    binding.videoView.setOnErrorListener { _, what, extra ->
                        // обработка ошибок плеера
                        val errorMsg = when (what) {
                            MediaPlayer.MEDIA_ERROR_UNKNOWN -> "Неизвестная ошибка"
                            MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "Ошибка сервера"
                            else -> "Ошибка воспроизведения"
                        }

                        val extraMsg = when (extra) {
                            MediaPlayer.MEDIA_ERROR_IO -> "Ошибка ввода/вывода"
                            MediaPlayer.MEDIA_ERROR_MALFORMED -> "Неверный формат"
                            MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "Неподдерживаемый формат"
                            MediaPlayer.MEDIA_ERROR_TIMED_OUT -> "Превышено время ожидания"
                            -2147483648 -> "Видео не найдено (404) или нет доступа"
                            else -> "Код: $extra"
                        }

                        Log.e(
                            "ViolationDetails",
                            "Video error: what=$what ($errorMsg), extra=$extra ($extraMsg)"
                        )
                        Log.e("ViolationDetails", "Video URL: $videoUrl")

                        binding.videoLoadingProgress.visibility = View.GONE
                        binding.playButton.visibility = View.GONE
                        binding.tvVideoError.visibility = View.VISIBLE

                        binding.tvVideoError.text = if (extra == -2147483648) {
                            "Видео недоступно\nВозможно, оно еще обрабатывается\nили было удалено"
                        } else {
                            "Не удалось загрузить видео\n$errorMsg: $extraMsg"
                        }
                        true
                    }

                    binding.videoView.setOnCompletionListener {
                        // когда видео закончилось
                        Log.d("ViolationDetails", "Video playback completed")
                        isVideoPlaying = false
                        binding.playButton.visibility = View.VISIBLE
                        binding.videoView.seekTo(0) // возвращаемся в начало
                    }

                    binding.playButton.setOnClickListener {
                        if (isVideoPlaying) pauseVideo() else playVideo()
                    }

                    binding.videoView.setOnClickListener {
                        if (isVideoPlaying) pauseVideo() else playVideo()
                    }

                } catch (e: Exception) {
                    Log.e("ViolationDetails", "Error setting up video: ${e.message}", e)
                    binding.videoLoadingProgress.visibility = View.GONE
                    binding.tvVideoError.visibility = View.VISIBLE
                    binding.tvVideoError.text = "Ошибка при загрузке видео:\n${e.message}"
                }
            }
        }
    }

    private fun playVideo() {
        // запуск воспроизведения
        binding.videoView.start()
        isVideoPlaying = true
        binding.playButton.visibility = View.GONE
    }

    private fun pauseVideo() {
        // пауза видео
        binding.videoView.pause()
        isVideoPlaying = false
        binding.playButton.visibility = View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        // при сворачивании ставим на паузу
        if (isVideoPlaying) {
            pauseVideo()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // полностью останавливаем плеер
        binding.videoView.stopPlayback()
    }
}
