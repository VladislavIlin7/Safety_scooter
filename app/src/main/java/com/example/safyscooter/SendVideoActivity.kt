//package com.example.safyscooter
//
//import android.net.Uri
//import android.os.Bundle
//import android.widget.VideoView
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.net.toUri
//
//class SendVideoActivity : AppCompatActivity() {
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_send_video)
//
//        val videoPath = intent.getStringExtra("VIDEO_PATH")
//        val videoView: VideoView = findViewById(R.id.videoView)
//
//        if (videoPath != null) {
//            videoView.setVideoURI(videoPath.toUri())
//            videoView.start()
//        }
//    }
//}
