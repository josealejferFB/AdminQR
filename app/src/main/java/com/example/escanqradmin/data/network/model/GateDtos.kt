package com.example.escanqradmin.data.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class EspConfigResponse(
    val status: String,
    val message: String? = null,
    @SerialName("mac_address") val macAddress: String? = null
)

@Serializable
data class GateRegisterRequest(
    val name: String,
    @SerialName("mac_address") val macAddress: String
)

@Serializable
data class GateRegisterResponse(
    val success: JsonElement = JsonPrimitive(""),
    @SerialName("gate_id") val gateId: Int? = null,
    val message: String? = null
) {
    val isSuccess: Boolean get() = success.jsonPrimitive.let { it.content == "success" || it.booleanOrNull == true }
}

@Serializable
data class GateListResponse(
    val success: JsonElement = JsonPrimitive(""),
    val data: List<GateDto>? = null,
    val message: String? = null
) {
    val isSuccess: Boolean get() = success.jsonPrimitive.let { it.content == "success" || it.booleanOrNull == true }
}

@Serializable
data class GateDto(
    val id: Int,
    val name: String,
    @SerialName("mac_address") val macAddress: String,
    @SerialName("ip_address") val ipAddress: String? = null,
    @SerialName("is_online") val isOnline: Boolean = false,
    val hostname: String = "",
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class GateNameUpdateRequest(
    @SerialName("gate_id") val gateId: Int,
    val name: String
)
