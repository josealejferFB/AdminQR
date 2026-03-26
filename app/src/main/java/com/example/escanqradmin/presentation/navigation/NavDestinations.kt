package com.example.escanqradmin.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
object Home

@Serializable
object Scanner

@Serializable
data class Result(
    val androidId: String,
    val userName: String,
    val cedula: String,
    val plate: String
)

@Serializable
object ESPConfig
