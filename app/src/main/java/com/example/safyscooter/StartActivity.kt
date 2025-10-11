package com.example.safyscooter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
import com.example.safyscooter.databinding.ActivityStartBinding
import java.io.File

class StartActivity : ComponentActivity() {

    private lateinit var binding: ActivityStartBinding
    private var recording: Recording? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var isRecording = false

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) startCamera()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Таймер справа
        binding.timer.base = SystemClock.elapsedRealtime()
        binding.timer.stop()
        binding.timer.isVisible = false

        // Запрос разрешений
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.btnRec.setOnClickListener {
            if (!isRecording) {
                startRecording()
            } else {
                stopRecording()
            }
        }

        binding.btnProfile.setOnClickListener {
            // Личный кабинет
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

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, videoCapture
                )
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }


    private fun startRecording() {
        val videoCapture = this.videoCapture ?: return

        val videoFile = File(externalCacheDir, "video_${System.currentTimeMillis()}.mp4")

        val outputOptions = FileOutputOptions.Builder(videoFile).build()

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
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!event.hasError()) {
                            val intent = Intent(this, SendVideoActivity::class.java).apply {
                                putExtra("VIDEO_PATH", videoFile.absolutePath)
                            }
                            startActivity(intent)
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
}