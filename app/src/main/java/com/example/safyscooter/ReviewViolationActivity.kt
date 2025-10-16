package com.example.safyscooter


import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.safyscooter.databinding.ActivityReviewBinding  // ← ВАЖНО: другой импорт
import android.content.Intent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReviewViolationActivity : ComponentActivity() {

    private lateinit var binding: ActivityReviewBinding   // ← ВАЖНО: другой тип
    private var videoPath: String? = null
    private var tsFromStart: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewBinding.inflate(layoutInflater)  // ← ВАЖНО
        setContentView(binding.root)

        videoPath = intent.getStringExtra("VIDEO_PATH")
        tsFromStart = intent.getLongExtra("TS", System.currentTimeMillis())

        val (thumb, durationSec) = extractMeta(videoPath)
        if (thumb != null) binding.ivThumb.setImageBitmap(thumb)
        binding.tvDuration.text = "Длительность: ${durationSec}s"
        binding.tvDate.text = formatTs(tsFromStart)

        binding.btnSend.setOnClickListener {
            val nextIndex = ViolationStore.items.size + 1
            ViolationStore.items.add(
                0, ViolationUi(id = tsFromStart, title = "нарушение $nextIndex", timestamp = tsFromStart)
            )
            goToPersonal()
        }

        binding.btnDelete.setOnClickListener {
            videoPath?.let { File(it).delete() }
            goToPersonal()
        }
    }

    private fun goToPersonal() {
        startActivity(Intent(this, PersonalActivity::class.java))
        finish()
    }

    private fun extractMeta(path: String?): Pair<Bitmap?, Int> { /* без изменений */
        if (path.isNullOrEmpty()) return Pair(null, 0)
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val bmp = r.getFrameAtTime(0)
            Pair(bmp, (durMs / 1000).toInt())
        } catch (_: Throwable) { Pair(null, 0) } finally { r.release() }
    }

    private fun formatTs(ts: Long): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ts))
}
