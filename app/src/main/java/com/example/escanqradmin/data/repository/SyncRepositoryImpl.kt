package com.example.escanqradmin.data.repository

import com.example.escanqradmin.data.network.model.ConductoresResponse
import com.example.escanqradmin.domain.model.QrContent
import com.example.escanqradmin.domain.repository.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
    private val refreshUsers = "http://172.17.2.178:8059/api/get_conductores"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    override suspend fun syncEntry(data: QrContent): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = buildJsonObject {
                put("nombre", data.userName)
                put("cedula", data.cedula)
                put("placas", data.plate)
            }.toString()

            val request = Request.Builder()
                .url(baseUrl)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Error ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchEntries(): Result<List<QrContent>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(baseUrl)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(emptyList<QrContent>())
                } else {
                    Result.failure(Exception("Error ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshConductores(): Result<List<QrContent>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(refreshUsers)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: throw Exception("Empty body")
                    val conductoresResponse = json.decodeFromString<ConductoresResponse>(bodyString)
                    
                    if (conductoresResponse.success) {
                        val qrContents = conductoresResponse.data.map { dto ->
                            QrContent(
                                androidId = dto.id?.toString() ?: "",
                                userName = dto.nombre ?: "",
                                cedula = dto.cedula ?: "",
                                plate = dto.placas ?: ""
                            )
                        }
                        Result.success(qrContents)
                    } else {
                        Result.failure(Exception(conductoresResponse.message))
                    }
                } else {
                    Result.failure(Exception("Error ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteEntry(cedula: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = baseUrl.toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("cedula", cedula)
                ?.build() ?: throw Exception("Invalid URL")

            val request = Request.Builder()
                .url(url)
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Error ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateEntry(data: QrContent): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = buildJsonObject {
                put("nombre", data.userName)
                put("cedula", data.cedula)
                put("placas", data.plate)
            }.toString()

            val request = Request.Builder()
                .url(baseUrl)
                .addHeader("Content-Type", "application/json")
                .put(jsonBody.toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Error ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
