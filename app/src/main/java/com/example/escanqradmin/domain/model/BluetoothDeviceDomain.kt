package com.example.escanqradmin.domain.model

data class BluetoothDeviceDomain(
    val name: String?,
    val address: String,
    val isConnected: Boolean = false,
    val isPaired: Boolean = false
)
