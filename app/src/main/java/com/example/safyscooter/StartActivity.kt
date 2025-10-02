package com.example.safyscooter

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class StartActivity : AppCompatActivity() {

    private val REQUEST_VIDEO_CAPTURE = 1
    private val CAMERA_PERMISSION_CODE = 100
    private var videoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        val recordButton: ImageButton = findViewById(R.id.record_button)

        recordButton.setOnClickListener {
            // При КАЖДОМ нажатии проверяем разрешение
            checkCameraPermission()
        }
    }

    private fun checkCameraPermission() {
        // Проверяем, есть ли уже разрешение
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Разрешение есть - запускаем камеру
            launchCamera()
        } else {
            // Разрешения нет - запрашиваем его ПРИ КАЖДОМ нажатии
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }
    }

    // Этот метод вызывается после того как пользователь ответил на запрос разрешения
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            CAMERA_PERMISSION_CODE -> {
                // Проверяем, дал ли пользователь разрешение
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Разрешение дано - запускаем камеру
                    launchCamera()
                }
                // Если разрешение не дано - ничего не делаем, но при следующем нажатии снова запросим
            }
        }
    }

    private fun launchCamera() {
        val takeVideoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        if (takeVideoIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE)
        }
    }

    @Deprecated("Use registerForActivityResult in new projects")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_VIDEO_CAPTURE && resultCode == Activity.RESULT_OK) {
            videoUri = data?.data
            // здесь в videoUri путь к записанному видео
        }
    }
}