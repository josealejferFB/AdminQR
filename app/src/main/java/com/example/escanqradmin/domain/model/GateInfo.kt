package com.example.escanqradmin.domain.model

data class GateInfo(
    val id: Int? = null,
    val name: String,
    val macAddress: String,
    val ipAddress: String? = null,
    val isOnline: Boolean = false,
    val btName: String = "ESP32_Seguro",
    val hostname: String = "",
    val isOdooRegistered: Boolean = false
)
