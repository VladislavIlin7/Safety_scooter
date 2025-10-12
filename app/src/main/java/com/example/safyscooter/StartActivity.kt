package com.example.safyscooter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Chronometer
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.core.Preview
import androidx.camera.video.*
import androidx.camera.video.FileOutputOptions
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.safyscooter.databinding.ActivityStartBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File


class StartActivity : ComponentActivity() {

    private lateinit var binding: ActivityStartBinding
    private var recording: Recording? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var isRecording = false
    private var videoFile: File? = null
    private val maxDurationMs = 20_000L // 20 секунд

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) startCamera()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.timer.base = SystemClock.elapsedRealtime()
        binding.timer.stop()
        binding.timer.isVisible = false

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.btnRec.setOnClickListener {
            if (!isRecording) startRecording() else stopRecording()
        }

        binding.btnProfile.setOnClickListener {
            // переход в личный кабинет
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startRecording() {
        val videoCapture = this.videoCapture ?: return

        videoFile = File(externalCacheDir, "video_${System.currentTimeMillis()}.mp4")
        val outputOptions = FileOutputOptions.Builder(videoFile!!).build()

        recording = videoCapture.output
            .prepareRecording(this, outputOptions)
            .start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        isRecording = true
                        binding.timer.base = SystemClock.elapsedRealtime()
                        binding.timer.isVisible = true
                        binding.timer.start()
                        binding.btnRec.text = "STOP"

                        // Автоостановка через 20 секунд
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (isRecording) stopRecording()
                        }, maxDurationMs)
                    }

                    is VideoRecordEvent.Finalize -> {
                        if (!event.hasError()) {
                            val path = videoFile?.absolutePath ?: return@start

                            // Переход на экран со списком нарушений
                            val intent = Intent(this, ViolationsListActivity::class.java)
                            intent.putExtra("VIDEO_PATH", path)
                            intent.putExtra("STATUS", "Видео отправляется...")
                            startActivity(intent)

                            // Асинхронная загрузка видео
                            uploadVideoAsync(path)
                        }
                    }
                }
            }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
        isRecording = false
        binding.timer.stop()
        binding.timer.isVisible = false
        binding.btnRec.text = "REC"
    }

    private fun uploadVideoAsync(filePath: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(filePath)
                val requestBody = file.asRequestBody("video/mp4".toMediaType())
                val multipart = MultipartBody.Part.createFormData("video", file.name, requestBody)

                // Пример запроса через Retrofit
                val api = Retrofit.Builder()
                    .baseUrl("https://your-server.com/api/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(VideoApi::class.java)

                val response = api.uploadVideo(multipart)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        ViolationsRepository.updateStatus(filePath, "Видео успешно отправлено")
                    } else {
                        ViolationsRepository.updateStatus(filePath, "Ошибка отправки")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    ViolationsRepository.updateStatus(filePath, "Ошибка отправки")
                }
            }
        }
  }

}
