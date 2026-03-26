package com.example.escanqradmin.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class ConductoresResponse(
    val success: Boolean,
    val message: String,
    val data: List<ConductorDto>
)

@Serializable
data class ConductorDto(
    val id: Int? = null,
    val nombre: String? = null,
    val cedula: String? = null,
    val placas: String? = null,
    val estado: String? = null
)
