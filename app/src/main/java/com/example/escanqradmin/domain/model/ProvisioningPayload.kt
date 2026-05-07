package com.example.escanqradmin.domain.model

data class ProvisioningPayload(
    val endpoint: String,
    val target_mac: String,
    val token: String
)

fun ProvisioningPayload.toJson(): String =
    """{"endpoint":"$endpoint","target_mac":"$target_mac","token":"$token"}"""
