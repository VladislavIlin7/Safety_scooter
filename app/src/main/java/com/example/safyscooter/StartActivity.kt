package com.example.safyscooter

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class StartActivity : Activity() {
    private val REQUEST_VIDEO_CAPTURE = 1
    private val REQUEST_CAMERA_PERMISSION = 100
    private var videoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        val recordButton: ImageButton = findViewById(R.id.record_button)

        // Проверяем разрешение при создании активности
        if (hasCameraPermission()) {
            setupCameraButton(recordButton)
        } else {
            requestCameraPermission()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.CAMERA),
            REQUEST_CAMERA_PERMISSION
        )
    }

    private fun setupCameraButton(recordButton: ImageButton) {
        recordButton.setOnClickListener {
            val takeVideoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            if (takeVideoIntent.resolveActivity(packageManager) != null) {
                startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE)
            }
        }
    }

    // Обработка результата запроса разрешения
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Разрешение получено, настраиваем кнопку
                    val recordButton: ImageButton = findViewById(R.id.record_button)
                    setupCameraButton(recordButton)
                    Toast.makeText(this, "Разрешение на камеру получено!", Toast.LENGTH_SHORT).show()
                } else {
                    // Разрешение не получено
                    Toast.makeText(this, "Для записи видео необходимо разрешение на камеру", Toast.LENGTH_LONG).show()
                    // Можно закрыть активность или оставить кнопку неактивной
                }
            }
        }
    }

    @Deprecated("Use registerForActivityResult in new projects")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_VIDEO_CAPTURE && resultCode == Activity.RESULT_OK) {
            videoUri = data?.data
            // здесь в videoUri путь к записанному видео
            Toast.makeText(this, "Видео записано!", Toast.LENGTH_SHORT).show()
        }
    }
}