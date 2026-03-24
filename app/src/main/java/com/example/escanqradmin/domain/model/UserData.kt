package com.example.escanqradmin.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UserData(
    val u: String,   // Nombre
    val c: String,   // Cédula
    val p: List<String>, // Placas
    val aid: String  // Android ID cifrado (contiene IV + cifrado)
)
