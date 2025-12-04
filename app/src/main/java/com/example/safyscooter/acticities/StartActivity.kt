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

    private lateinit var binding: ActivityStartBinding   // биндинг для доступа к вью
    private var recording: Recording? = null             // текущая запись видео
    private var videoCapture: VideoCapture<Recorder>? = null  // объект для записи видео
    private var countDownTimer: CountDownTimer? = null   // таймер обратного отсчёта 20 сек
    private var isRecording = false                      // флаг: сейчас идёт запись или нет
    private var lastVideoFile: File? = null              // последний записанный видеофайл
    private var recordingStartTime: Long = 0             // время начала записи (в секундах)
    private var pulseAnimator: ObjectAnimator? = null    // анимация пульса круговой обводки
    private var locationSettingsShownInThisSession = false // показывали ли экран настроек гео
    private var returningFromSettings = false            // вернулись ли мы из настроек

    private lateinit var fusedLocationClient: FusedLocationProviderClient // клиент геолокации
    private val locationList = mutableListOf<Pair<Double, Double>>()      // список координат (lat, lon)
    private var isTrackingLocation = false             // флаг: сейчас слушаем обновления гео
    private var locationServiceRequested = false       // флаг: уже просили включить гео

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, 100          // частые обновления, высокая точность
    ).setMinUpdateIntervalMillis(50).build()

    private val locationCallback = object : LocationCallback() { // колбэк на новые координаты
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                updateCurrentLocation(location)        // обновляем текущую локацию
            }
        }
    }

    private var currentLocation: Location? = null      // последняя известная локация
    private var locationUpdatesStopped = false         // флаг: остановили ли обновление гео
    private val locationExecutor = Executors.newSingleThreadScheduledExecutor() // отдельный поток
    private var guaranteedLocationTimer: CountDownTimer? = null // таймер для гарантированного набора координат

    // причина остановки записи
    private enum class StopReason { NONE, USER, TIMER }
    private var stopReason: StopReason = StopReason.NONE

    // запрос разрешения камеры (при первом запуске)
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()                          // если дали камеру — запускаем предпросмотр
            }
            checkAndRequestLocationPermission()        // после этого проверяем геолокацию
        }

    // запрос только камеры, когда уже в активности
    private val requestCameraOnlyLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()                          // запускаем камеру
                checkAllPermissionsAndStartRecording() // и пробуем начать запись
            }
        }

    // запрос разрешения на геолокацию (общий)
    private val requestLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                if (!isLocationEnabled() && !locationServiceRequested) {
                    locationServiceRequested = true
                    checkLocationServicesAndEnable()   // просим включить геолокацию в настройках
                }
            }
        }

    // запрос только геолокации перед записью
    private val requestLocationOnlyLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                if (!isLocationEnabled()) {
                    locationServiceRequested = false
                    checkLocationServicesAndEnable()   // открываем настройки гео
                } else {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        // небольшая задержка и старт записи
                        Handler(Looper.getMainLooper()).postDelayed({
                            startRecording()
                        }, 200)
                    }
                }
            }
        }

    // лаунчер открытия настроек геолокации
    private val locationSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // результат нам тут не важен, просто возвращаемся
        }

    override fun onResume() {
        super.onResume()

        if (returningFromSettings) { // если возвращаемся из настроек
            returningFromSettings = false

            Handler(Looper.getMainLooper()).postDelayed({
                if (isLocationEnabled()) {
                    // если гео включили — сбрасываем флаг показа настроек
                    locationSettingsShownInThisSession = false
                }
            }, 300)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            returningFromSettings = true   // помечаем, что активити пересоздавалась (могли быть настройки)
        }

        binding = ActivityStartBinding.inflate(layoutInflater) // инициализация биндинга
        setContentView(binding.root)                           // устанавливаем разметку

        // слушаем состояние загрузки видео из UploadManager
        lifecycleScope.launch {
            UploadManager.uploadState.collect { state ->
                when (state) {
                    is UploadManager.UploadState.Idle -> {
                        binding.uploadStatusCard.visibility = View.GONE // блок загрузки скрыт
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

                        // через 5 секунд сбрасываем состояние загрузки
                        binding.uploadStatusCard.postDelayed({
                            UploadManager.resetState()
                        }, 5000)
                    }
                }
            }
        }

        binding.btnCloseUpload.setOnClickListener {
            UploadManager.resetState() // вручную свернуть/очистить блок загрузки
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this) // инициализация клиента гео

        // логика старта камеры и гео при запуске экрана
        if (!returningFromSettings) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startCamera()                       // если есть камера — стартуем предпросмотр
                checkAndRequestLocationPermission() // и запрашиваем гео
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA) // просим разрешение камеры
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startCamera()
            }
        }

        // обработка нажатия на большую кнопку записи
        binding.btnRec.setOnClickListener {
            if (!isRecording) {                     // если сейчас не пишем
                checkAllPermissionsAndStartRecording() // проверяем все разрешения и стартуем
            } else {
                stopReason = StopReason.USER        // пользователь сам остановил запись
                stopRecording()                     // останавливаем
            }
        }

        // нижняя навигация — текущий экран "домой"
        binding.bottomNavigation.selectedItemId = R.id.nav_home
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    finish()                        // если снова "домой" — просто закрываем активити
                    true
                }
                R.id.nav_violations -> {
                    if (!isRecording) {             // нельзя уйти пока идёт запись
                        val intent = Intent(this, ViolationsActivity::class.java)
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

    // проверяем все разрешения и только потом стартуем запись
    private fun checkAllPermissionsAndStartRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraOnlyLauncher.launch(Manifest.permission.CAMERA) // просим камеру
            return
        }

        if (!hasLocationPermission()) {
            requestLocationOnlyLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) // просим гео
            return
        }

        if (!isLocationEnabled()) {
            // сбрасываем флаг, чтобы снова показать настройки гео
            locationSettingsShownInThisSession = false
            checkLocationServicesAndEnable() // открываем настройки
            return
        }

        startRecording() // если всё ок — начинаем запись
    }

    // проверяем разрешение на геолокацию и при необходимости просим
    private fun checkAndRequestLocationPermission() {
        if (!hasLocationPermission()) {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else if (!isLocationEnabled() && !locationServiceRequested) {
            locationServiceRequested = true
            checkLocationServicesAndEnable() // просим включить гео в настройках
        }
    }

    // есть ли у приложения разрешение на гео
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // включены ли вообще службы геолокации на устройстве
    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    // проверяем и при необходимости открываем настройки геолокации
    private fun checkLocationServicesAndEnable() {
        if (!isLocationEnabled() && !locationSettingsShownInThisSession) {
            locationSettingsShownInThisSession = true
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS) // экран настроек гео
            locationSettingsLauncher.launch(intent)
        } else if (!isLocationEnabled()) {
            return // гео всё ещё выключено — просто выходим
        } else {
            return // гео уже включено — ничего не делаем
        }
    }

    // обновляем текущую локацию и добавляем её в список при записи
    private fun updateCurrentLocation(location: Location) {
        currentLocation = location

        if (isRecording) {
            locationList.add(Pair(location.latitude, location.longitude))
        }
    }

    // запускаем отслеживание геолокации
    private fun startLocationTracking() {
        if (!isTrackingLocation && hasLocationPermission() && isLocationEnabled()) {
            try {
                if (hasLocationPermission()) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        location?.let {
                            updateCurrentLocation(it) // сразу сохраняем последнюю известную точку
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
                checkAndRequestLocationPermission() // если ошибка — снова проверяем разрешения
            }
        }
    }

    // таймер, который во время записи следит, чтобы на каждый секунды была координата
    private fun startGuaranteedLocationCollection() {
        guaranteedLocationTimer?.cancel()

        guaranteedLocationTimer = object : CountDownTimer(20000, 1000) { // 20 секунд, шаг 1 секунда
            override fun onTick(millisUntilFinished: Long) {
                if (isRecording) {
                    val secondsPassed = (20000 - millisUntilFinished) / 1000
                    ensureLocationForSecond(secondsPassed.toInt()) // проверяем координаты на текущую секунду
                }
            }

            override fun onFinish() {
                if (isRecording) {
                    ensureLocationForSecond(20) // на всякий случай проверяем 20-ю секунду
                }
            }
        }.start()
    }

    // гарантируем, что для указанной секунды есть хотя бы одна координата
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
                        // игнорируем ошибку безопасности
                    }
                }
            }
        }
    }

    // останавливаем получение координат
    private fun stopLocationTracking() {
        if (isTrackingLocation) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isTrackingLocation = false
            locationUpdatesStopped = true
            guaranteedLocationTimer?.cancel()
        }
    }

    // подготавливаем и запускаем камеру (предпросмотр)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider) // связываем с виджетом превью
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD)) // качество HD
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA // используем заднюю камеру

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
        }, ContextCompat.getMainExecutor(this))
    }

    // старт записи видео
    private fun startRecording() {
        val vc = videoCapture ?: return
        val videoFile = File(externalCacheDir, "video_${System.currentTimeMillis()}.mp4") // создаём файл
        lastVideoFile = videoFile
        stopReason = StopReason.NONE

        recordingStartTime = System.currentTimeMillis() / 1000 // фиксируем время начала записи (секунды)

        locationList.clear()      // очищаем список координат
        currentLocation = null    // сбрасываем текущую локацию

        startLocationTracking()   // запускаем отслеживание гео
        startGuaranteedLocationCollection() // включаем таймер гарантированных координат

        if (hasLocationPermission()) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null && isRecording) {
                        locationList.add(Pair(location.latitude, location.longitude))
                    }
                }
            } catch (securityException: SecurityException) {
                // игнорируем ошибку
            }
        }

        val outputOptions = FileOutputOptions.Builder(videoFile).build() // куда записывать видео

        recording = vc.output
            .prepareRecording(this, outputOptions)
            .start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        isRecording = true
                        animateRecordingStart()          // визуальная анимация старта
                        binding.timerCard.isVisible = true
                        binding.timer.isVisible = true
                        startCountdownTimer()            // запускаем таймер 20 секунд
                    }
                    is VideoRecordEvent.Finalize -> {
                        stopLocationTracking()           // остановить гео после записи

                        val file = lastVideoFile
                        isRecording = false
                        animateRecordingStop()           // анимация окончания записи
                        binding.timerCard.isVisible = false
                        binding.timer.isVisible = false
                        countDownTimer?.cancel()

                        if (!event.hasError()) {         // если запись прошла без ошибки
                            when (stopReason) {
                                StopReason.USER, StopReason.TIMER -> {
                                    if (file != null) {
                                        val recordedSeconds = (System.currentTimeMillis() / 1000 - recordingStartTime).toInt()
                                        ensureMinimumLocations(recordedSeconds) // дозаполняем координаты при необходимости

                                        // запускаем экран проверки нарушения и передаём видео + координаты
                                        startActivity(
                                            Intent(this, ReviewViolationActivity::class.java)
                                                .putExtra("VIDEO_PATH", file.absolutePath)
                                                .putExtra("START_TIMESTAMP", recordingStartTime)
                                                .putExtra("LOCATIONS",
                                                    locationList.map { doubleArrayOf(it.first, it.second) }.toTypedArray()
                                                )
                                        )
                                    } else {
                                        // если файла нет — возвращаемся к списку нарушений
                                        startActivity(Intent(this, ViolationsActivity::class.java))
                                    }
                                }
                                StopReason.TIMER, StopReason.NONE -> {
                                    // тут логика не выполняется, оставлено как есть
                                }
                            }
                        }
                        stopReason = StopReason.NONE
                    }
                }
            }
    }

    // если координат меньше, чем секунд записи — дублируем последнюю точку
    private fun ensureMinimumLocations(expectedSeconds: Int) {
        while (locationList.size < expectedSeconds && currentLocation != null) {
            locationList.add(Pair(currentLocation!!.latitude, currentLocation!!.longitude))
        }
    }

    // остановка записи видео
    private fun stopRecording() {
        recording?.stop()
        recording = null
        countDownTimer?.cancel()
    }

    // запускаем таймер 20 секунд, который показывает оставшееся время на экране
    private fun startCountdownTimer() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(20_000, 1_000) { // 20 секунд по 1
            override fun onTick(millisUntilFinished: Long) {
                binding.timer.text = (millisUntilFinished / 1000).toString() // обновляем текст таймера
            }
            override fun onFinish() {
                stopReason = StopReason.TIMER   // запись закончилась по таймеру
                stopRecording()                 // останавливаем запись
            }
        }.start()
    }

    // анимация при старте записи (кнопка меняется на квадрат и пульсирующее кольцо)
    private fun animateRecordingStart() {
        binding.recButtonInner.clearAnimation()
        binding.recButtonCard.clearAnimation()
        binding.recButtonInner.animate().cancel()
        binding.recButtonCard.animate().cancel()

        binding.recButtonInner.scaleX = 1f
        binding.recButtonInner.scaleY = 1f
        binding.recButtonCard.scaleX = 1f
        binding.recButtonCard.scaleY = 1f

        binding.recButtonInner.setBackgroundResource(R.drawable.rec_button_recording_modern) // стиль "запись"

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
        binding.pulseRing.startAnimation(pulseAnimation) // запускаем анимацию пульса вокруг кнопки

        binding.tvHint.animate()
            .alpha(0f)            // подсказка исчезает при начале записи
            .setDuration(200)
            .start()
    }

    // анимация при остановке записи (кнопка возвращается в исходное состояние)
    private fun animateRecordingStop() {
        binding.recButtonInner.clearAnimation()
        binding.recButtonCard.clearAnimation()
        binding.recButtonInner.animate().cancel()
        binding.recButtonCard.animate().cancel()

        binding.recButtonInner.setBackgroundResource(R.drawable.rec_button_idle_modern) // стиль "ожидание"

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
        binding.pulseRing.visibility = View.GONE        // убираем пульсирующее кольцо

        binding.tvHint.animate()
            .alpha(1f)            // возвращаем подсказку
            .setDuration(200)
            .start()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationTracking()          // на всякий случай выключаем гео
        locationExecutor.shutdown()     // останавливаем отдельный executor
        guaranteedLocationTimer?.cancel() // отменяем таймер координат
        pulseAnimator?.cancel()         // отменяем анимацию пульса (если вдруг была)
        binding.pulseRing.clearAnimation() // чистим анимации у вью
    }
}
