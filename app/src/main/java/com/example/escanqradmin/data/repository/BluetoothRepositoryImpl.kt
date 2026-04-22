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
    private val readBuffer = StringBuilder()

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
                        val isAlreadyPaired = _pairedDevices.value.any { d -> d.address == it.address }
                        val isAlreadyScanned = _scannedDevices.value.any { d -> d.address == it.address }
                        
                        if (!isAlreadyPaired && !isAlreadyScanned) {
                            val domainDevice = BluetoothDeviceDomain(it.name, it.address)
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
        updatePairedDevices()
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
        // Cancelamos job anterior sin cambiar el estado (evita snackbar falso)
        connectionJob?.cancel()
        socket?.close()
        socket = null
        readBuffer.clear()

        connectionJob = scope.launch {
            _connectionState.value = BluetoothConnectionState.Connecting
            val device = bluetoothAdapter?.getRemoteDevice(address) ?: run {
                _connectionState.value = BluetoothConnectionState.Error("Dispositivo no encontrado")
                return@launch
            }
            
            // Cancelar discovery mejora la velocidad de conexión
            bluetoothAdapter.cancelDiscovery()

            try {
                // Método estándar con UUID SPP
                socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                withContext(Dispatchers.IO) { socket?.connect() }
                
                _connectionState.value = BluetoothConnectionState.Connected(address)
                listenForMessages()
                
            } catch (e: IOException) {
                // FALLBACK: Conexión por reflexión (canal 1)
                try {
                    socket?.close()
                    socket = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                        .invoke(device, 1) as BluetoothSocket
                    withContext(Dispatchers.IO) { socket?.connect() }
                    
                    _connectionState.value = BluetoothConnectionState.Connected(address)
                    listenForMessages()
                    
                } catch (e2: Exception) {
                    _connectionState.value = BluetoothConnectionState.Error("Fallo de conexión: ${e2.message}")
                    socket?.close()
                    socket = null
                }
            }
        }
    }

    override fun disconnect() {
        connectionJob?.cancel()
        socket?.close()
        socket = null
        readBuffer.clear()
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
            while (isActive) {
                try {
                    val bytes = inputStream.read(buffer)
                    if (bytes > 0) {
                        // Añadimos al buffer para manejar mensajes parciales
                        readBuffer.append(String(buffer, 0, bytes))
                        // Procesamos cada línea completa (el ESP32 termina con \n via println)
                        var newlineIdx = readBuffer.indexOf('\n')
                        while (newlineIdx != -1) {
                            val line = readBuffer.substring(0, newlineIdx).trim()
                            readBuffer.delete(0, newlineIdx + 1)
                            if (line.isNotEmpty()) {
                                _messages.emit(line)
                            }
                            newlineIdx = readBuffer.indexOf('\n')
                        }
                    } else if (bytes == -1) {
                        // El extremo remoto cerró la conexión limpiamente
                        break
                    }
                } catch (e: IOException) {
                    // El ESP32 cerró la conexión (timeout u otro motivo)
                    break
                }
            }
            // Si el job sigue activo, significa que el cierre fue remoto (ESP32 timeout)
            if (isActive) {
                _connectionState.value = BluetoothConnectionState.Error("Desconectado")
            }
        }
    }
}
