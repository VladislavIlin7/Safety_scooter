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
import androidx.camera.video.*
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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.btnRec.setOnClickListener {
            if (!isRecording) {
                startRecording()
            } else {
                // пользователь остановил запись
                stopReason = StopReason.USER
                stopRecording()
            }
        }

        binding.btnProfile.setOnClickListener {
            if (isRecording) {
                // сперва корректно завершим запись, затем откроем личный кабинет
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
                                    // вручную остановили → открываем личный кабинет
                                    openPersonal()
                                }
                                StopReason.TIMER, StopReason.NONE -> {
                                    // автостоп по таймеру → как раньше, на отправку видео
                                    if (file != null) {
                                        startActivity(
                                            Intent(this, SendVideoActivity::class.java)
                                                .putExtra("VIDEO_PATH", file.absolutePath)
                                        )
                                    }
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
        // Остальное (UI/навигация) произойдёт в Finalize по stopReason
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
