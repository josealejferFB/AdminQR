package com.example.escanqradmin.domain.model

object SecurityConstants {
    // Esta llave DEBE ser la misma en la app del Usuario y del Administrador
    const val SHARED_AES_KEY = "GabyQrSecureKey12345678901234567"

    // Token de provisionamiento: la App de Usuario lo valida antes de aceptar la configuración
    const val PROVISIONING_TOKEN = "ALCARAVAN_2025"
}
