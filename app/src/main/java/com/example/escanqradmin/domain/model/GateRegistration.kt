package com.example.escanqradmin.domain.model

data class GateRegistration(
    val name: String,
    val macAddress: String,
    val description: String = ""
)
