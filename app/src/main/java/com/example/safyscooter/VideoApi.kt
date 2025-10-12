package com.example.safyscooter

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface VideoApi {
    @Multipart
    @POST("upload")
    suspend fun uploadVideo(
        @Part video: MultipartBody.Part
    ): Response<Unit> // ✅ Правильно - используем Response от Retrofit
}