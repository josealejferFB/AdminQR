package com.example.escanqradmin.data.repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.example.escanqradmin.domain.model.BluetoothDeviceDomain
import com.example.escanqradmin.domain.repository.BluetoothConnectionState
import com.example.escanqradmin.domain.repository.BluetoothRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.io.InputStream
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BluetoothRepository {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _scannedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    override val scannedDevices: StateFlow<List<BluetoothDeviceDomain>> = _scannedDevices.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    override val pairedDevices: StateFlow<List<BluetoothDeviceDomain>> = _pairedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectionState = MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Idle)
    override val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    override val messages: Flow<String> = _messages.asSharedFlow()

    private var socket: BluetoothSocket? = null
    private var connectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let {
                        val domainDevice = BluetoothDeviceDomain(it.name, it.address)
                        if (!_scannedDevices.value.any { d -> d.address == it.address }) {
                            _scannedDevices.update { list -> list + domainDevice }
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> _isScanning.value = true
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> _isScanning.value = false
            }
        }
    }

    init {
        updatePairedDevices()
    }

    @SuppressLint("MissingPermission")
    private fun updatePairedDevices() {
        try {
            bluetoothAdapter?.bondedDevices?.let { devices ->
                _pairedDevices.value = devices.map { BluetoothDeviceDomain(it.name, it.address, isPaired = true) }
            }
        } catch (s: SecurityException) {
            _pairedDevices.value = emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    override fun startDiscovery() {
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }
        _scannedDevices.value = emptyList()
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND).apply {
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        bluetoothAdapter?.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    override fun stopDiscovery() {
        bluetoothAdapter?.cancelDiscovery()
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
        _isScanning.value = false
    }

    @SuppressLint("MissingPermission")
    override fun connectToDevice(address: String) {
        disconnect()
        connectionJob = scope.launch {
            _connectionState.value = BluetoothConnectionState.Connecting
            val device = bluetoothAdapter?.getRemoteDevice(address) ?: return@launch
            try {
                socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                bluetoothAdapter.cancelDiscovery()
                socket?.connect()
                _connectionState.value = BluetoothConnectionState.Connected
                listenForMessages()
            } catch (e: IOException) {
                _connectionState.value = BluetoothConnectionState.Error(e.message ?: "Connection failed")
                socket?.close()
            }
        }
    }

    override fun disconnect() {
        connectionJob?.cancel()
        socket?.close()
        socket = null
        _connectionState.value = BluetoothConnectionState.Idle
    }

    override suspend fun sendMessage(message: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                socket?.outputStream?.write(message.toByteArray())
                true
            } catch (e: IOException) {
                false
            }
        }
    }

    private suspend fun listenForMessages() {
        withContext(Dispatchers.IO) {
            val inputStream: InputStream = socket?.inputStream ?: return@withContext
            val buffer = ByteArray(1024)
            while (isActive && socket?.isConnected == true) {
                try {
                    val bytes = inputStream.read(buffer)
                    if (bytes > 0) {
                        val message = String(buffer, 0, bytes)
                        _messages.emit(message)
                    }
                } catch (e: IOException) {
                    _connectionState.value = BluetoothConnectionState.Error("Disconnected")
                    break
                }
            }
        }
    }
}
