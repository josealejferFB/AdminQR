package com.example.escanqradmin.domain.model

/**
 * Payload de configuración que la App Admin envía a la App de Usuario
 * via Bluetooth Classic (RFCOMM/SPP) durante el proceso de provisionamiento.
 *
 * La App de Usuario usa un contador de llaves {} para detectar el fin del objeto JSON,
 * por lo que NO se necesita ningún carácter de terminación (\n).
 */
data class ProvisioningPayload(
    /** URL base del API backend (ej: "http://172.17.12.119:8059") */
    val endpoint: String,
    /** Dirección MAC física del ESP32 de apertura (ej: "E0:5A:1B:31:29:6E") */
    val target_mac: String,
    /** Token de seguridad fijo que la App de Usuario valida */
    val token: String
)

/**
 * Serializa el payload a un JSON plano sin librería externa.
 * El resultado se envía directamente al socket Bluetooth.
 */
fun ProvisioningPayload.toJson(): String =
    """{"endpoint":"$endpoint","target_mac":"$target_mac","token":"$token"}"""
