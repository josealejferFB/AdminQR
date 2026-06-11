package com.example.escanqradmin.data.repository

import com.example.escanqradmin.data.network.ApiConstants
import com.example.escanqradmin.data.network.ApiConstants.Endpoints.GET_CONDUCTORES
import com.example.escanqradmin.data.network.ApiConstants.Endpoints.SYNC_VEHICULAR
import com.example.escanqradmin.data.network.model.ConductoresResponse
import com.example.escanqradmin.data.network.model.GateRegisterResponse
import com.example.escanqradmin.domain.model.QrContent
import com.example.escanqradmin.domain.repository.SyncRepository
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

class SyncRepositoryImpl @Inject constructor(
    private val client: OkHttpClient
) : SyncRepository {

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    override suspend fun syncEntry(data: QrContent): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", "call")
                put("params", buildJsonObject {
                    put("action", "create")
                    put("cedula", data.cedula)
                    put("nombre", data.userName)
                    put("placas", data.plate)
                })
            }.toString()

            val request = Request.Builder()
                .url(SYNC_VEHICULAR)
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
                        if (status == "success" || status == "pending") {
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

    override suspend fun refreshConductores(): Result<List<QrContent>> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", "call")
                put("params", buildJsonObject {})
            }.toString()

            val request = Request.Builder()
                .url(GET_CONDUCTORES)
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
                        val conductoresResponse = json.decodeFromJsonElement<ConductoresResponse>(resultElement)

                        if (conductoresResponse.success) {
                            val qrContents = conductoresResponse.data.map { dto ->
                                QrContent(
                                    androidId = dto.id?.toString() ?: "",
                                    userName = dto.nombre ?: "",
                                    cedula = dto.cedula ?: "",
                                    plate = dto.placas ?: "",
                                    authorizedGates = dto.puertasAutorizadas?.map { it.macAddress } ?: emptyList()
                                )
                            }
                            Result.success(qrContents)
                        } else {
                            Result.failure(Exception(conductoresResponse.message))
                        }
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
            val jsonBody = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", "call")
                put("params", buildJsonObject {
                    put("action", "delete")
                    put("cedula", cedula)
                })
            }.toString()

            val request = Request.Builder()
                .url(SYNC_VEHICULAR)
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

    override suspend fun updateEntry(data: QrContent): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", "call")
                put("params", buildJsonObject {
                    put("action", "update")
                    put("cedula", data.cedula)
                    put("nombre", data.userName)
                    put("placas", data.plate)
                })
            }.toString()

            val request = Request.Builder()
                .url(SYNC_VEHICULAR)
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

    override suspend fun registerGate(name: String, macAddress: String): Result<GateRegisterResponse> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = buildJsonObject {
                put("jsonrpc", "2.0")
                put("params", buildJsonObject {
                    put("name", name)
                    put("mac_address", macAddress)
                })
            }.toString()

            val request = Request.Builder()
                .url(ApiConstants.Endpoints.REGISTER_GATE)
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
                        val gateResponse = json.decodeFromJsonElement<GateRegisterResponse>(resultElement)

                        if (gateResponse.success) {
                            Result.success(gateResponse)
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

    override suspend fun getGateUsers(gateId: Int): Result<List<QrContent>> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", "call")
                put("params", buildJsonObject {
                    put("gate_id", gateId)
                })
            }.toString()

            val request = Request.Builder()
                .url(ApiConstants.Endpoints.GATE_USERS(gateId))
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
                        val resultObj = jsonObject["result"] ?: throw Exception("Missing result in response")
                        val conductoresResponse = json.decodeFromJsonElement<ConductoresResponse>(resultObj)

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
                    }
                } else {
                    Result.failure(Exception("Error ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
