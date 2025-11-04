package com.example.safyscooter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.safyscooter.databinding.ActivityStartBinding
import java.io.File


class StartActivity : ComponentActivity() {

    private lateinit var binding: ActivityStartBinding
    private var recording: Recording? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var countDownTimer: CountDownTimer? = null
    private var isRecording = false
    private var lastVideoFile: File? = null
    private var recordingStartTime: Long = 0

    private enum class StopReason { NONE, USER, TIMER }
    private var stopReason: StopReason = StopReason.NONE

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) startCamera()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Разрешение камеры
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Кнопка REC / STOP
        binding.btnRec.setOnClickListener {
            if (!isRecording) {
                startRecording()
            } else {
                stopReason = StopReason.USER
                stopRecording()
            }
        }

        // Кнопка профиля
        binding.btnProfile.setOnClickListener {
            if (isRecording) {
                stopReason = StopReason.USER
                stopRecording()
            } else {
                openPersonal()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startRecording() {
        val vc = videoCapture ?: return
        val videoFile = File(externalCacheDir, "video_${System.currentTimeMillis()}.mp4")
        lastVideoFile = videoFile
        stopReason = StopReason.NONE
        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        recording = vc.output
            .prepareRecording(this, outputOptions)
            .start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        isRecording = true
                        binding.btnRec.text = "STOP"
                        binding.timer.isVisible = true
                        recordingStartTime = System.currentTimeMillis() / 1000
                        startCountdownTimer()
                    }
                    is VideoRecordEvent.Finalize -> {
                        val file = lastVideoFile
                        isRecording = false
                        binding.btnRec.text = "REC"
                        binding.timer.isVisible = false
                        countDownTimer?.cancel()

                        if (!event.hasError()) {
                            when (stopReason) {
                                StopReason.USER -> {
                                    // -> экран предпросмотра (без добавления в список)
                                    if (file != null) {
                                        startActivity(
                                            Intent(this, ReviewViolationActivity::class.java)
                                                .putExtra("VIDEO_PATH", file.absolutePath)
                                                .putExtra("START_TIMESTAMP", recordingStartTime)
                                        )
                                    } else {
                                        // на всякий случай, если файл не создался
                                        startActivity(Intent(this, PersonalActivity::class.java))
                                    }
                                }
                                StopReason.TIMER, StopReason.NONE -> {
                                    // автостоп — остаемся на камере (по вашему текущему ТЗ)
                                    // можно показать Toast, если нужно
                                }
                            }
                        }
                        stopReason = StopReason.NONE
                    }
                }
            }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
        countDownTimer?.cancel()
        // Остальное (UI/навигация) делаем в Finalize по stopReason
    }

    /** Обратный отсчёт 20→0, затем автостоп */
    private fun startCountdownTimer() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(20_000, 1_000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.timer.text = (millisUntilFinished / 1000).toString()
            }
            override fun onFinish() {
                stopReason = StopReason.TIMER
                stopRecording()
            }
        }.start()
    }

    private fun openPersonal() {
        startActivity(Intent(this, PersonalActivity::class.java))
    }
}
