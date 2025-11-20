package com.example.safyscooter

import android.R
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.safyscooter.databinding.ActivityReviewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import kotlin.math.roundToInt

class ReviewViolationActivity : ComponentActivity() {

    private lateinit var binding: ActivityReviewBinding
    private var videoPath: String? = null
    private var startTimestamp: Long = 0L
    private var locations: List<Pair<Double, Double>> = emptyList()
    private var isVideoPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        videoPath = intent.getStringExtra("VIDEO_PATH")
        startTimestamp = intent.getLongExtra("START_TIMESTAMP", System.currentTimeMillis())

        @Suppress("DEPRECATION")
        val locationsArray = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("LOCATIONS", Array<DoubleArray>::class.java)
        } else {
            intent.getSerializableExtra("LOCATIONS") as? Array<DoubleArray>
        }
        locations = locationsArray?.map {
            Pair(it[0], it[1])
        } ?: emptyList()

        val (thumb, durationSec) = extractMeta(videoPath)
        if (thumb != null) binding.ivThumb.setImageBitmap(thumb)
        binding.tvDuration.text = "${durationSec} сек"
        binding.tvDate.text = formatTs(startTimestamp * 1000)
        
        // Показываем реальный размер файла
        val videoFile = File(videoPath ?: "")
        if (videoFile.exists()) {
            val fileSizeInMB = videoFile.length() / (1024.0 * 1024.0)
            binding.tvFileSize.text = String.format("%.2f MB", fileSizeInMB)
        } else {
            binding.tvFileSize.text = "Н/Д"
        }
        
        // Настройка просмотра видео
        setupVideoPlayer()

        binding.btnSend.setOnClickListener {
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
            videoPath?.let { File(it).delete() }
            val intent = Intent(this, StartActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    // Удаляем локальный uploadViolation и ProgressRequestBody - они теперь в UploadManager

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
    
    private fun setupVideoPlayer() {
        binding.videoPreviewCard.setOnClickListener {
            if (!isVideoPlaying) {
                playVideo()
            } else {
                pauseVideo()
            }
        }
        
        binding.playButton.setOnClickListener {
            if (!isVideoPlaying) {
                playVideo()
            } else {
                pauseVideo()
            }
        }
    }
    
    private fun playVideo() {
        videoPath?.let { path ->
            binding.videoView.setVideoPath(path)
            binding.videoView.visibility = View.VISIBLE
            binding.ivThumb.visibility = View.GONE
            binding.overlayView.visibility = View.GONE
            binding.playButton.visibility = View.GONE
            
            binding.videoView.setOnPreparedListener { mediaPlayer ->
                mediaPlayer.isLooping = true
                binding.videoView.start()
                isVideoPlaying = true
            }
            
            binding.videoView.setOnCompletionListener {
                // Видео зациклено, но на случай если что-то пойдет не так
                if (!isVideoPlaying) {
                    pauseVideo()
                }
            }
            
            binding.videoView.setOnErrorListener { _, _, _ ->
                Toast.makeText(this, "Ошибка воспроизведения видео", Toast.LENGTH_SHORT).show()
                pauseVideo()
                true
            }
        }
    }
    
    private fun pauseVideo() {
        if (binding.videoView.isPlaying) {
            binding.videoView.pause()
        }
        binding.videoView.visibility = View.GONE
        binding.ivThumb.visibility = View.VISIBLE
        binding.overlayView.visibility = View.VISIBLE
        binding.playButton.visibility = View.VISIBLE
        binding.playButton.setImageResource(android.R.drawable.ic_media_play)
        isVideoPlaying = false
    }
    
    override fun onPause() {
        super.onPause()
        if (isVideoPlaying) {
            pauseVideo()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (binding.videoView.isPlaying) {
            binding.videoView.stopPlayback()
        }
    }
}