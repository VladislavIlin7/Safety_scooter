package com.example.safyscooter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Looper
import android.provider.Settings
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.File
import java.util.concurrent.Executors

class StartActivity : ComponentActivity() {

    private lateinit var binding: ActivityStartBinding
    private var recording: Recording? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var countDownTimer: CountDownTimer? = null
    private var isRecording = false
    private var lastVideoFile: File? = null
    private var recordingStartTime: Long = 0

    // === ДОБАВЛЕНО ДЛЯ ГЕОЛОКАЦИИ ===
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationList = mutableListOf<Pair<Double, Double>>()
    private var isTrackingLocation = false

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, 100
    ).setMinUpdateIntervalMillis(50).build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                updateCurrentLocation(location)
            }
        }
    }

    private var currentLocation: Location? = null
    private var locationUpdatesStopped = false
    private val locationExecutor = Executors.newSingleThreadScheduledExecutor()
    private var guaranteedLocationTimer: CountDownTimer? = null

    // === КОНЕЦ ДОБАВЛЕНИЯ ===

    private enum class StopReason { NONE, USER, TIMER }
    private var stopReason: StopReason = StopReason.NONE

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
                checkAndRequestLocationPermission()
            }
        }

    private val requestLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                checkLocationServicesAndEnable()
            }
        }

    private val locationSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            checkLocationServicesAndEnable()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
            checkAndRequestLocationPermission()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.btnRec.setOnClickListener {
            if (!isRecording) {
                if (hasLocationPermission() && isLocationEnabled()) {
                    startRecording()
                } else {
                    checkAndRequestLocationPermission()
                }
            } else {
                stopReason = StopReason.USER
                stopRecording()
            }
        }

        binding.btnProfile.setOnClickListener {
            if (isRecording) {
                stopReason = StopReason.USER
                stopRecording()
            } else {
                openPersonal()
            }
        }
    }

    private fun checkAndRequestLocationPermission() {
        if (!hasLocationPermission()) {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            checkLocationServicesAndEnable()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isLocationEnabled(): Boolean {
        val locationMode = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.LOCATION_MODE,
            Settings.Secure.LOCATION_MODE_OFF
        )
        return locationMode != Settings.Secure.LOCATION_MODE_OFF
    }

    private fun checkLocationServicesAndEnable() {
        if (!isLocationEnabled()) {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            locationSettingsLauncher.launch(intent)
        }
    }

    // === ОБНОВЛЕННЫЙ МЕТОД ДЛЯ ГЕОЛОКАЦИИ ===
    private fun updateCurrentLocation(location: Location) {
        currentLocation = location

        if (isRecording) {
            locationList.add(Pair(location.latitude, location.longitude))
        }
    }

    private fun startLocationTracking() {
        if (!isTrackingLocation && hasLocationPermission() && isLocationEnabled()) {
            try {
                // === ИСПРАВЛЕНИЕ: Добавляем проверку разрешения ===
                if (hasLocationPermission()) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        location?.let {
                            updateCurrentLocation(it)
                        }
                    }
                }

                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
                isTrackingLocation = true
                locationUpdatesStopped = false

            } catch (securityException: SecurityException) {
                checkAndRequestLocationPermission()
            }
        }
    }

    // === НОВЫЙ МЕТОД: Гарантированный сбор координат ===
    private fun startGuaranteedLocationCollection() {
        guaranteedLocationTimer?.cancel()

        guaranteedLocationTimer = object : CountDownTimer(20000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (isRecording) {
                    val secondsPassed = (20000 - millisUntilFinished) / 1000
                    ensureLocationForSecond(secondsPassed.toInt())
                }
            }

            override fun onFinish() {
                if (isRecording) {
                    ensureLocationForSecond(20)
                }
            }
        }.start()
    }

    // === НОВЫЙ МЕТОД: Гарантирует координату для конкретной секунды ===
    private fun ensureLocationForSecond(second: Int) {
        val expectedSize = second + 1

        if (locationList.size < expectedSize) {
            currentLocation?.let { location ->
                locationList.add(Pair(location.latitude, location.longitude))
            } ?: run {
                // === ИСПРАВЛЕНИЕ: Добавляем проверку разрешения ===
                if (hasLocationPermission()) {
                    try {
                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            if (location != null && isRecording) {
                                locationList.add(Pair(location.latitude, location.longitude))
                            }
                        }
                    } catch (securityException: SecurityException) {
                        // Игнорируем или логируем ошибку
                    }
                }
            }
        }
    }

    private fun stopLocationTracking() {
        if (isTrackingLocation) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isTrackingLocation = false
            locationUpdatesStopped = true
            guaranteedLocationTimer?.cancel()
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

        recordingStartTime = System.currentTimeMillis() / 1000

        locationList.clear()
        currentLocation = null

        startLocationTracking()
        startGuaranteedLocationCollection()

        // === ИСПРАВЛЕНИЕ: Добавляем проверку разрешения ===
        if (hasLocationPermission()) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null && isRecording) {
                        locationList.add(Pair(location.latitude, location.longitude))
                    }
                }
            } catch (securityException: SecurityException) {
                // Игнорируем или логируем ошибку
            }
        }

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
                        stopLocationTracking()

                        val file = lastVideoFile
                        isRecording = false
                        binding.btnRec.text = "REC"
                        binding.timer.isVisible = false
                        countDownTimer?.cancel()

                        if (!event.hasError()) {
                            when (stopReason) {
                                StopReason.USER -> {
                                    if (file != null) {
                                        val recordedSeconds = (System.currentTimeMillis() / 1000 - recordingStartTime).toInt()
                                        ensureMinimumLocations(recordedSeconds)

                                        startActivity(
                                            Intent(this, ReviewViolationActivity::class.java)
                                                .putExtra("VIDEO_PATH", file.absolutePath)
                                                .putExtra("START_TIMESTAMP", recordingStartTime)
                                                .putExtra("LOCATIONS",
                                                    locationList.map { doubleArrayOf(it.first, it.second) }.toTypedArray()
                                                )
                                        )
                                    } else {
                                        startActivity(Intent(this, PersonalActivity::class.java))
                                    }
                                }
                                StopReason.TIMER, StopReason.NONE -> {
                                    // автостоп
                                }
                            }
                        }
                        stopReason = StopReason.NONE
                    }
                }
            }
    }

    // === НОВЫЙ МЕТОД: Гарантирует минимальное количество координат ===
    private fun ensureMinimumLocations(expectedSeconds: Int) {
        while (locationList.size < expectedSeconds && currentLocation != null) {
            locationList.add(Pair(currentLocation!!.latitude, currentLocation!!.longitude))
        }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
        countDownTimer?.cancel()
    }

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

    override fun onDestroy() {
        super.onDestroy()
        stopLocationTracking()
        locationExecutor.shutdown()
        guaranteedLocationTimer?.cancel()
    }
}