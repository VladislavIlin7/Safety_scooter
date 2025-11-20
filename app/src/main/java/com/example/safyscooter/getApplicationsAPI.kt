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
                        
                        val verdictsList = mutableListOf<Verdict>()
                        if (appJson.has("verdicts") && !appJson.isNull("verdicts")) {
                            val verdictsArray = appJson.getJSONArray("verdicts")
                            for (j in 0 until verdictsArray.length()) {
                                val verdictJson = verdictsArray.getJSONObject(j)
                                
                                val coordinatesList = mutableListOf<Double>()
                                if (verdictJson.has("coordinates") && !verdictJson.isNull("coordinates")) {
                                    val coordArray = verdictJson.getJSONArray("coordinates")
                                    for (k in 0 until coordArray.length()) {
                                        coordinatesList.add(coordArray.getDouble(k))
                                    }
                                }
                                
                                verdictsList.add(Verdict(
                                    id = verdictJson.getLong("id"),
                                    type = if (verdictJson.has("type") && !verdictJson.isNull("type")) 
                                        verdictJson.getString("type") else "",
                                    scooterType = if (verdictJson.has("scooter_type") && !verdictJson.isNull("scooter_type")) 
                                        verdictJson.getString("scooter_type") else null,
                                    objectId = if (verdictJson.has("object_id") && !verdictJson.isNull("object_id")) 
                                        verdictJson.getString("object_id") else null,
                                    timestamp = if (verdictJson.has("timestamp") && !verdictJson.isNull("timestamp"))
                                        verdictJson.getDouble("timestamp") else 0.0,
                                    coordinates = coordinatesList,
                                    createdAt = if (verdictJson.has("created_at") && !verdictJson.isNull("created_at")) 
                                        verdictJson.getString("created_at") else null
                                ))
                            }
                        }
                        
                        applications.add(Application(
                            id = appJson.getLong("id"),
                            status = appJson.getString("status"),
                            key = appJson.getString("key"),
                            recordTime = if (appJson.has("record_time") && !appJson.isNull("record_time")) 
                                appJson.getString("record_time") else null,
                            lastChange = if (appJson.has("last_change") && !appJson.isNull("last_change")) 
                                appJson.getString("last_change") else null,
                            verdicts = verdictsList
                        ))
                    }
                    applications
                }
                else -> throw Exception("Ошибка сервера: ${response.code}")
            }
        }
    }
}