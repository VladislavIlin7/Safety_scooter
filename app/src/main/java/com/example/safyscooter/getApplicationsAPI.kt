package com.example.safyscooter

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun getApplications(accessToken: String): List<Application> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://safetyscooter.ru/applications")
            .get()
            .header("Authorization", accessToken)
            .build()

        client.newCall(request).execute().use { response ->
            when (response.code) {
                200 -> {
                    val responseBody = response.body?.string()
                    val json = JSONObject(responseBody ?: "{}")
                    val applicationsArray = json.getJSONArray("applications")
                    val applications = mutableListOf<Application>()

                    for (i in 0 until applicationsArray.length()) {
                        val appJson = applicationsArray.getJSONObject(i)
                        applications.add(Application(
                            id = appJson.getLong("id"),
                            status = appJson.getString("status"),
                            key = appJson.getString("key"),
                            address = appJson.getString("address"),
                            recordTime = appJson.getString("record_time"),
                            lastChange = appJson.getString("last_change")
                        ))
                    }
                    applications
                }
                else -> throw Exception("Ошибка сервера: ${response.code}")
            }
        }
    }
}