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
    @SerialName("mac_address") val macAddress: String,
    val description: String = ""
)

@Serializable
data class GateRegisterResponse(
    val success: Boolean,
    @SerialName("gate_id") val gateId: Int? = null,
    val message: String? = null
)
