package com.example.escanqradmin.data.repository

import com.example.escanqradmin.domain.model.QrContent
import com.example.escanqradmin.domain.repository.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

class SyncRepositoryImpl @Inject constructor(
    private val client: OkHttpClient
) : SyncRepository {

    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = "http://172.17.2.178:8059/api/sync_vehicular"

    override suspend fun syncEntry(data: QrContent): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = buildJsonObject {
                putJsonObject("params") {
                    put("nombre", data.userName)
                    put("cedula", data.cedula)
                    put("telefono", "0")
                    put("placas", data.plate)
                }
            }.toString()

            val request = Request.Builder()
                .url(baseUrl)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Error ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchEntries(): Result<List<QrContent>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(baseUrl) // Same URL for now, assuming server handles GET/POST differently
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: "[]"
                    // Provisory empty list if no parsing logic yet, 
                    // but the infrastructure is ready.
                    Result.success(emptyList<QrContent>())
                } else {
                    Result.failure(Exception("Error ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
