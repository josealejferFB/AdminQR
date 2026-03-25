package com.example.escanqradmin.domain.repository

import com.example.escanqradmin.domain.model.BluetoothDeviceDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface BluetoothRepository {
    val scannedDevices: StateFlow<List<BluetoothDeviceDomain>>
    val pairedDevices: StateFlow<List<BluetoothDeviceDomain>>
    val isScanning: StateFlow<Boolean>
    val connectionState: StateFlow<BluetoothConnectionState>
    val messages: Flow<String>

    fun startDiscovery()
    fun stopDiscovery()
    fun connectToDevice(address: String)
    fun disconnect()
    suspend fun sendMessage(message: String): Boolean
}

sealed class BluetoothConnectionState {
    object Idle : BluetoothConnectionState()
    object Connecting : BluetoothConnectionState()
    data class Connected(val deviceAddress: String) : BluetoothConnectionState()
    data class Error(val message: String) : BluetoothConnectionState()
}
