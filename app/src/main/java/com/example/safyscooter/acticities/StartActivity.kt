package com.example.safyscooter.acticities

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
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
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.safyscooter.R
import com.example.safyscooter.network.UploadManager
import com.example.safyscooter.databinding.ActivityStartBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
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
    private var pulseAnimator: ObjectAnimator? = null
    private var locationSettingsShownInThisSession = false
    private var returningFromSettings = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationList = mutableListOf<Pair<Double, Double>>()
    private var isTrackingLocation = false
    private var locationServiceRequested = false

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

    private enum class StopReason { NONE, USER, TIMER }
    private var stopReason: StopReason = StopReason.NONE

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            }
            checkAndRequestLocationPermission()
        }

    private val requestCameraOnlyLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
                checkAllPermissionsAndStartRecording()
            }
        }

    private val requestLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                if (!isLocationEnabled() && !locationServiceRequested) {
                    locationServiceRequested = true
                    checkLocationServicesAndEnable()
                }
            }
        }

    private val requestLocationOnlyLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                if (!isLocationEnabled()) {
                    locationServiceRequested = false
                    checkLocationServicesAndEnable()
                } else {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            startRecording()
                        }, 200)
                    }
                }
            }
        }

    private val locationSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        }

    override fun onResume() {
        super.onResume()

        if (returningFromSettings) {
            returningFromSettings = false

            Handler(Looper.getMainLooper()).postDelayed({
                if (isLocationEnabled()) {
                    locationSettingsShownInThisSession = false
                }
            }, 300)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            returningFromSettings = true
        }

        binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            UploadManager.uploadState.collect { state ->
                when (state) {
                    is UploadManager.UploadState.Idle -> {
                        binding.uploadStatusCard.visibility = View.GONE
                    }
                    is UploadManager.UploadState.Uploading -> {
                        binding.uploadStatusCard.visibility = View.VISIBLE
                        binding.uploadSpinner.visibility = View.VISIBLE
                        binding.uploadIcon.visibility = View.GONE
                        binding.tvUploadStatus.text = "Загрузка видео..."
                        binding.tvUploadPercent.text = "${state.progress}%"
                        binding.tvUploadPercent.setTextColor(ContextCompat.getColor(this@StartActivity, R.color.primary))
                        binding.uploadProgressBar.progress = state.progress
                        binding.uploadProgressBar.progressTintList = ColorStateList.valueOf(
                            ContextCompat.getColor(this@StartActivity, R.color.primary)
                        )

                        binding.tvUploadDetails.text = String.format(
                            "%d%% • %.1f MB из %.1f MB",
                            state.progress, state.mbUploaded, state.mbTotal
                        )
                    }
                    is UploadManager.UploadState.Success -> {
                        binding.uploadStatusCard.visibility = View.VISIBLE
                        binding.uploadSpinner.visibility = View.GONE
                        binding.uploadIcon.visibility = View.VISIBLE
                        binding.uploadIcon.setImageResource(android.R.drawable.checkbox_on_background)
                        binding.uploadIcon.setColorFilter(ContextCompat.getColor(this@StartActivity, R.color.success))

                        binding.tvUploadStatus.text = "Загрузка завершена"
                        binding.tvUploadDetails.text = "Видео успешно отправлено"
                        binding.tvUploadPercent.text = "✓"
                        binding.tvUploadPercent.setTextColor(ContextCompat.getColor(this@StartActivity, R.color.success))
                        binding.uploadProgressBar.progress = 100
                        binding.uploadProgressBar.progressTintList = ColorStateList.valueOf(
                            ContextCompat.getColor(this@StartActivity, R.color.success)
                        )
                    }
                    is UploadManager.UploadState.Error -> {
                        binding.uploadStatusCard.visibility = View.VISIBLE
                        binding.uploadSpinner.visibility = View.GONE
                        binding.uploadIcon.visibility = View.VISIBLE
                        binding.uploadIcon.setImageResource(android.R.drawable.ic_delete)
                        binding.uploadIcon.setColorFilter(ContextCompat.getColor(this@StartActivity, R.color.error))

                        binding.tvUploadStatus.text = "Ошибка загрузки"
                        binding.tvUploadDetails.text = state.message
                        binding.tvUploadPercent.text = "!"
                        binding.tvUploadPercent.setTextColor(ContextCompat.getColor(this@StartActivity, R.color.error))
                        binding.uploadProgressBar.progress = 0
                        binding.uploadProgressBar.progressTintList = ColorStateList.valueOf(
                            ContextCompat.getColor(this@StartActivity, R.color.error)
                        )

                        binding.uploadStatusCard.postDelayed({
                            UploadManager.resetState()
                        }, 5000)
                    }
                }
            }
        }

        binding.btnCloseUpload.setOnClickListener {
            UploadManager.resetState()
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (!returningFromSettings) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startCamera()
                checkAndRequestLocationPermission()
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startCamera()
            }
        }

        binding.btnRec.setOnClickListener {
            if (!isRecording) {
                checkAllPermissionsAndStartRecording()
            } else {
                stopReason = StopReason.USER
                stopRecording()
            }
        }

        binding.bottomNavigation.selectedItemId = R.id.nav_home
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    finish()
                    true
                }
                R.id.nav_violations -> {
                    if (!isRecording) {
                        val intent = Intent(this, PersonalActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(intent, ActivityOptionsCompat.makeCustomAnimation(
                            this, 0, 0
                        ).toBundle())
                        finish()
                    }
                    true
                }
                R.id.nav_profile -> {
                    if (!isRecording) {
                        val intent = Intent(this, ProfileActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(intent, ActivityOptionsCompat.makeCustomAnimation(
                            this, 0, 0
                        ).toBundle())
                        finish()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun checkAllPermissionsAndStartRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraOnlyLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        if (!hasLocationPermission()) {
            requestLocationOnlyLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        if (!isLocationEnabled()) {
            // Сбрасываем флаг для нового шанса при нажатии кнопки
            locationSettingsShownInThisSession = false
            checkLocationServicesAndEnable()
            return
        }

        startRecording()
    }

    private fun checkAndRequestLocationPermission() {
        if (!hasLocationPermission()) {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else if (!isLocationEnabled() && !locationServiceRequested) {
            locationServiceRequested = true
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
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun checkLocationServicesAndEnable() {
        if (!isLocationEnabled() && !locationSettingsShownInThisSession) {
            locationSettingsShownInThisSession = true
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            locationSettingsLauncher.launch(intent)
        } else if (!isLocationEnabled()) {
            return
        } else {
            return
        }
    }

    private fun updateCurrentLocation(location: Location) {
        currentLocation = location

        if (isRecording) {
            locationList.add(Pair(location.latitude, location.longitude))
        }
    }

    private fun startLocationTracking() {
        if (!isTrackingLocation && hasLocationPermission() && isLocationEnabled()) {
            try {
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

    private fun ensureLocationForSecond(second: Int) {
        val expectedSize = second + 1

        if (locationList.size < expectedSize) {
            currentLocation?.let { location ->
                locationList.add(Pair(location.latitude, location.longitude))
            } ?: run {
                if (hasLocationPermission()) {
                    try {
                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            if (location != null && isRecording) {
                                locationList.add(Pair(location.latitude, location.longitude))
                            }
                        }
                    } catch (securityException: SecurityException) {
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

        if (hasLocationPermission()) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null && isRecording) {
                        locationList.add(Pair(location.latitude, location.longitude))
                    }
                }
            } catch (securityException: SecurityException) {
            }
        }

        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        recording = vc.output
            .prepareRecording(this, outputOptions)
            .start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        isRecording = true
                        animateRecordingStart()
                        binding.timerCard.isVisible = true
                        binding.timer.isVisible = true
                        startCountdownTimer()
                    }
                    is VideoRecordEvent.Finalize -> {
                        stopLocationTracking()

                        val file = lastVideoFile
                        isRecording = false
                        animateRecordingStop()
                        binding.timerCard.isVisible = false
                        binding.timer.isVisible = false
                        countDownTimer?.cancel()

                        if (!event.hasError()) {
                            when (stopReason) {
                                StopReason.USER, StopReason.TIMER -> {
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
                                }
                            }
                        }
                        stopReason = StopReason.NONE
                    }
                }
            }
    }

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

    private fun animateRecordingStart() {
        binding.recButtonInner.clearAnimation()
        binding.recButtonCard.clearAnimation()
        binding.recButtonInner.animate().cancel()
        binding.recButtonCard.animate().cancel()

        binding.recButtonInner.scaleX = 1f
        binding.recButtonInner.scaleY = 1f
        binding.recButtonCard.scaleX = 1f
        binding.recButtonCard.scaleY = 1f

        binding.recButtonInner.setBackgroundResource(R.drawable.rec_button_recording_modern)

        binding.recButtonInner.animate()
            .scaleX(0.5f)
            .scaleY(0.5f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()

        binding.recButtonCard.animate()
            .scaleX(1.08f)
            .scaleY(1.08f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()

        binding.pulseRing.visibility = View.VISIBLE
        val pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse_animation)
        binding.pulseRing.startAnimation(pulseAnimation)

        binding.tvHint.animate()
            .alpha(0f)
            .setDuration(200)
            .start()
    }

    private fun animateRecordingStop() {
        binding.recButtonInner.clearAnimation()
        binding.recButtonCard.clearAnimation()
        binding.recButtonInner.animate().cancel()
        binding.recButtonCard.animate().cancel()

        binding.recButtonInner.setBackgroundResource(R.drawable.rec_button_idle_modern)

        binding.recButtonInner.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()

        binding.recButtonCard.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()

        binding.pulseRing.clearAnimation()
        binding.pulseRing.visibility = View.GONE

        binding.tvHint.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationTracking()
        locationExecutor.shutdown()
        guaranteedLocationTimer?.cancel()
        pulseAnimator?.cancel()
        binding.pulseRing.clearAnimation()
    }
}