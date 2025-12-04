package com.example.safyscooter.acticities

import android.R
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.safyscooter.network.UploadManager
import com.example.safyscooter.databinding.ActivityReviewBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ReviewViolationActivity : ComponentActivity() {

    private lateinit var binding: ActivityReviewBinding // привязываем layout
    private var videoPath: String? = null // путь к видеофайлу
    private var startTimestamp: Long = 0L // время начала записи
    private var locations: List<Pair<Double, Double>> = emptyList() // список координат
    private var isVideoPlaying = false // флаг проигрывания видео

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(binding.root) // показываем экран

        binding.toolbar.setNavigationOnClickListener {
            finish() // кнопка назад
        }

        videoPath = intent.getStringExtra("VIDEO_PATH") // путь видео из Intent
        startTimestamp = intent.getLongExtra("START_TIMESTAMP", System.currentTimeMillis()) // время старта

        @Suppress("DEPRECATION")
        val locationsArray = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // получаем массив координат для новых API
            intent.getSerializableExtra("LOCATIONS", Array<DoubleArray>::class.java)
        } else {
            // для старых API
            intent.getSerializableExtra("LOCATIONS") as? Array<DoubleArray>
        }

        locations = locationsArray?.map { Pair(it[0], it[1]) } ?: emptyList() // переводим в пары

        val (thumb, durationSec) = extractMeta(videoPath) // берём превью и длительность
        if (thumb != null) binding.ivThumb.setImageBitmap(thumb) // показываем превью

        binding.tvDuration.text = "${durationSec} сек" // показываем длительность
        binding.tvDate.text = formatTs(startTimestamp * 1000) // дата записи

        // показываем размер файла
        val videoFile = File(videoPath ?: "")
        if (videoFile.exists()) {
            val fileSizeInMB = videoFile.length() / (1024.0 * 1024.0)
            binding.tvFileSize.text = String.format("%.2f MB", fileSizeInMB)
        } else {
            binding.tvFileSize.text = "Н/Д" // если файл не найден
        }

        setupVideoPlayer() // настраиваем просмотр видео

        binding.btnSend.setOnClickListener {
            // отправка видео
            val accessToken = getAccessToken()
            if (accessToken.isNotBlank() && videoPath != null) {
                UploadManager.startUpload(accessToken, videoPath!!, startTimestamp, locations)
                Toast.makeText(this, "Загрузка началась в фоне", Toast.LENGTH_SHORT).show()

                val intent = Intent(this, StartActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Ошибка данных", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDelete.setOnClickListener {
            // удаление файла
            videoPath?.let { File(it).delete() }
            val intent = Intent(this, StartActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    private fun getAccessToken(): String {
        // достаём сохранённый токен
        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        return sharedPref.getString("access_token", "") ?: ""
    }

    private fun goToPersonal() {
        // переход в список заявок
        startActivity(Intent(this, ViolationsActivity::class.java))
        finish()
    }

    private fun extractMeta(path: String?): Pair<Bitmap?, Int> {
        // получаем превью и длительность видео
        if (path.isNullOrEmpty()) return Pair(null, 0)
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val bmp = r.getFrameAtTime(0) // получаем первый кадр
            Pair(bmp, (durMs / 1000).toInt())
        } catch (_: Throwable) {
            Pair(null, 0)
        } finally {
            r.release()
        }
    }

    private fun formatTs(ts: Long): String =
        // переводим timestamp в понятную дату
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ts))

    private fun setupVideoPlayer() {
        // обработка кликов по области превью
        binding.videoPreviewCard.setOnClickListener {
            if (!isVideoPlaying) playVideo() else pauseVideo()
        }

        binding.playButton.setOnClickListener {
            if (!isVideoPlaying) playVideo() else pauseVideo()
        }
    }

    private fun playVideo() {
        // запускаем воспроизведение видео
        videoPath?.let { path ->
            binding.videoView.setVideoPath(path)
            binding.videoView.visibility = View.VISIBLE
            binding.ivThumb.visibility = View.GONE
            binding.overlayView.visibility = View.GONE
            binding.playButton.visibility = View.GONE

            binding.videoView.setOnPreparedListener { mediaPlayer ->
                mediaPlayer.isLooping = true // зацикливаем
                binding.videoView.start()
                isVideoPlaying = true
            }

            binding.videoView.setOnCompletionListener {
                // на случай, если проигрывание завершится
                if (!isVideoPlaying) pauseVideo()
            }

            binding.videoView.setOnErrorListener { _, _, _ ->
                Toast.makeText(this, "Ошибка воспроизведения видео", Toast.LENGTH_SHORT).show()
                pauseVideo()
                true
            }
        }
    }

    private fun pauseVideo() {
        // ставим видео на паузу
        if (binding.videoView.isPlaying) {
            binding.videoView.pause()
        }
        binding.videoView.visibility = View.GONE
        binding.ivThumb.visibility = View.VISIBLE
        binding.overlayView.visibility = View.VISIBLE
        binding.playButton.visibility = View.VISIBLE
        binding.playButton.setImageResource(R.drawable.ic_media_play)
        isVideoPlaying = false
    }

    override fun onPause() {
        super.onPause()
        if (isVideoPlaying) pauseVideo() // если свернули — ставим паузу
    }

    override fun onDestroy() {
        super.onDestroy()
        if (binding.videoView.isPlaying) {
            binding.videoView.stopPlayback() // останавливаем проигрывание
        }
    }
}
