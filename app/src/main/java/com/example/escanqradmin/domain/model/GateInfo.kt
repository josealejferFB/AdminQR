package com.example.escanqradmin.domain.model

data class GateInfo(
    val id: Int,
    val name: String,
    val macAddress: String,
    val ipAddress: String? = null,
    val isOnline: Boolean = false,
    val btName: String = "ESP32_Seguro"
)
