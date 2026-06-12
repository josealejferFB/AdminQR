package com.example.escanqradmin.data.repository

import com.example.escanqradmin.data.network.ApiConstants
import com.example.escanqradmin.data.network.model.GateListResponse
import com.example.escanqradmin.domain.model.GateInfo
import com.example.escanqradmin.domain.repository.GateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

class GateRepositoryImpl @Inject constructor(
    private val client: OkHttpClient
) : GateRepository {

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    override suspend fun getGates(): Result<List<GateInfo>> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", "call")
                put("params", buildJsonObject {})
            }.toString()

            val request = Request.Builder()
                .url(ApiConstants.Endpoints.GATES_LIST)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: throw Exception("Empty body")
                    val jsonElement = json.parseToJsonElement(bodyString)
                    val jsonObject = jsonElement.jsonObject

                    if (jsonObject.containsKey("error")) {
                        val errObj = jsonObject["error"]
                        Result.failure(Exception("Odoo Error: $errObj"))
                    } else {
                        val resultElement = jsonObject["result"] ?: throw Exception("Missing result in response")
                        val gateResponse = json.decodeFromJsonElement<GateListResponse>(resultElement)

                        if (gateResponse.isSuccess && gateResponse.gates != null) {
                            val gateInfos = gateResponse.gates.map { dto ->
                                GateInfo(
                                    id = dto.id,
                                    name = dto.name,
                                    macAddress = dto.macAddress,
                                    ipAddress = dto.ipAddress,
                                    isOnline = dto.isOnline,
                                    btName = dto.btName,
                                    isOdooRegistered = dto.id != null
                                )
                            }
                            Result.success(gateInfos)
                        } else {
                            Result.failure(Exception(gateResponse.message ?: "Error desconocido"))
                        }
                    }
                } else {
                    Result.failure(Exception("Error ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateGateName(gateId: Int, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", "call")
                put("params", buildJsonObject {
                    put("gate_id", gateId)
                    put("name", newName)
                })
            }.toString()

            val request = Request.Builder()
                .url(ApiConstants.Endpoints.GATE_UPDATE)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: throw Exception("Empty body")
                    val jsonElement = json.parseToJsonElement(bodyString)
                    val jsonObject = jsonElement.jsonObject

                    if (jsonObject.containsKey("error")) {
                        val errObj = jsonObject["error"]
                        Result.failure(Exception("Odoo Error: $errObj"))
                    } else {
                        val resultObj = jsonObject["result"]?.jsonObject ?: throw Exception("Missing result in response")
                        val status = resultObj["status"]?.jsonPrimitive?.content ?: ""
                        if (status == "success") {
                            Result.success(Unit)
                        } else {
                            val msg = resultObj["message"]?.jsonPrimitive?.content ?: "Error desconocido"
                            Result.failure(Exception(msg))
                        }
                    }
                } else {
                    Result.failure(Exception("Error ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
