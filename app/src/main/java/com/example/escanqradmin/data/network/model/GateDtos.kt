package com.example.escanqradmin.data.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val success: String = "",
    @SerialName("gate_id") val gateId: Int? = null,
    val message: String? = null
) {
    val isSuccess: Boolean get() = success == "success" || success == "true"
}

@Serializable
data class GateListResponse(
    val success: String = "",
    val gates: List<GateDto>? = null,
    val message: String? = null
) {
    val isSuccess: Boolean get() = success == "success" || success == "true"
}

@Serializable
data class GateDto(
    val id: Int,
    val name: String,
    @SerialName("mac_address") val macAddress: String,
    @SerialName("ip_address") val ipAddress: String? = null,
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("bt_name") val btName: String = "ESP32_Seguro"
)

@Serializable
data class GateNameUpdateRequest(
    @SerialName("gate_id") val gateId: Int,
    val name: String
)
