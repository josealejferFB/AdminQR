package com.example.escanqradmin.domain.model

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class ProvisioningPayload(
    val endpoint: String,
    val target_mac: String,
    val token: String
)

fun ProvisioningPayload.toJson(): String =
    buildJsonObject {
        put("endpoint", endpoint)
        put("target_mac", target_mac)
        put("token", token)
    }.toString()
