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
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class BluetoothRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scope: CoroutineScope
) : BluetoothRepository {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _scannedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    override val scannedDevices: StateFlow<List<BluetoothDeviceDomain>> = _scannedDevices.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    override val pairedDevices: StateFlow<List<BluetoothDeviceDomain>> = _pairedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectionState = MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Idle)
    override val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 5,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val messages: Flow<String> = _messages.asSharedFlow()

    private val socketMutex = Mutex()
    private var socket: BluetoothSocket? = null
    private var connectionJob: Job? = null
    private val readBuffer = StringBuilder()
    private var isReceiverRegistered = false

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
        if (bluetoothAdapter == null) {
            _scannedDevices.value = emptyList()
            return
        }
        try {
            updatePairedDevices()
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            _scannedDevices.value = emptyList()

            if (isReceiverRegistered) {
                try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
                isReceiverRegistered = false
            }

            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND).apply {
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            isReceiverRegistered = true
            bluetoothAdapter.startDiscovery()
        } catch (s: SecurityException) {
            _scannedDevices.value = emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopDiscovery() {
        bluetoothAdapter?.cancelDiscovery()
        if (isReceiverRegistered) {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        _isScanning.value = false
    }

    @SuppressLint("MissingPermission")
    override fun connectToDevice(address: String) {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            socketMutex.withLock {
                socket?.close()
                socket = null
                readBuffer.clear()
                _messages.resetReplayCache()
            }

            if (bluetoothAdapter == null) {
                _connectionState.value = BluetoothConnectionState.Error(
                    "Bluetooth no disponible en este dispositivo")
                return@launch
            }
            _connectionState.value = BluetoothConnectionState.Connecting(address)
            try {
                val device = bluetoothAdapter.getRemoteDevice(address) ?: run {
                    _connectionState.value = BluetoothConnectionState.Error("Dispositivo no encontrado")
                    return@launch
                }
                
                // Cancelar discovery mejora la velocidad de conexión
                bluetoothAdapter.cancelDiscovery()
                
                // [CRITICAL FIX 3] Dar tiempo a la radio Bluetooth de Android para que termine de cancelar el escaneo
                // antes de intentar abrir un socket RFCOMM. Si se hace de inmediato, muchos teléfonos (ej. Xiaomi/Samsung)
                // rechazan el socket con "read failed, socket might closed or timeout".
                delay(500)

                try {
                    withTimeout(20000) {
                        try {
                            // INTENTO 1: Conexión Insegura (Mucho más fiable con ESP32 sin emparejamiento estricto)
                            val insecureSocket = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                            socketMutex.withLock { socket = insecureSocket }
                            var connectJob = launch(Dispatchers.IO) { insecureSocket.connect() }
                            connectJob.join()
                        } catch (e1: Exception) {
                            if (e1 is CancellationException) throw e1
                            
                            try {
                                // INTENTO 2: Método estándar Seguro
                                socketMutex.withLock { socket?.close() }
                                val secureSocket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                                socketMutex.withLock { socket = secureSocket }
                                var connectJob = launch(Dispatchers.IO) { secureSocket.connect() }
                                connectJob.join()
                            } catch (e2: Exception) {
                                if (e2 is CancellationException) throw e2
                                
                                // INTENTO 3: FALLBACK por reflexión (canal 1)
                                socketMutex.withLock {
                                    socket?.close()
                                    val fallbackSocket = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                                        .invoke(device, 1) as BluetoothSocket
                                    socket = fallbackSocket
                                    var connectJob = launch(Dispatchers.IO) { fallbackSocket.connect() }
                                    connectJob.join()
                                }
                            }
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    socketMutex.withLock { socket?.close() } // This forcefully interrupts the native connect()!
                    throw e
                }
                
                _connectionState.value = BluetoothConnectionState.Connected(address)
                listenForMessages()
                
            } catch (e: SecurityException) {
                Log.e("BluetoothRepository", "SecurityException al conectar", e)
                _connectionState.value = BluetoothConnectionState.Error("Faltan permisos de Bluetooth")
                socketMutex.withLock { 
                    socket?.close()
                    socket = null
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("BluetoothRepository", "TimeoutCancellationException al conectar", e)
                _connectionState.value = BluetoothConnectionState.Error("Tiempo de espera agotado al conectar")
                socketMutex.withLock { 
                    socket?.close()
                    socket = null
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("BluetoothRepository", "Exception al conectar", e)
                _connectionState.value = BluetoothConnectionState.Error("Fallo de conexión: ${e.message}")
                socketMutex.withLock { 
                    socket?.close()
                    socket = null
                }
            }
        }
    }

    override fun disconnect() {
        connectionJob?.cancel()
        scope.launch {
            socketMutex.withLock {
                socket?.close()
                socket = null
                readBuffer.clear()
            }
        }
        _connectionState.value = BluetoothConnectionState.Idle
    }

    @SuppressLint("MissingPermission")
    override fun unpairDevice(address: String) {
        disconnect()
        try {
            val device = bluetoothAdapter?.getRemoteDevice(address) ?: return
            device.javaClass.getMethod("removeBond").invoke(device)
            updatePairedDevices()
        } catch (_: Exception) { }
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

    override suspend fun sendMessageAndWaitForReply(message: String, timeoutMs: Long): String? {
        return withContext(Dispatchers.IO) {
            try {
                socket?.outputStream?.write("$message\n".toByteArray())
                kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
                    _messages.first { msg -> 
                        msg.isNotBlank() && !msg.contains("\"status\":\"processing\"")
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun listenForMessages() {
        withContext(Dispatchers.IO) {
            val inputStream: InputStream = socketMutex.withLock { socket?.inputStream } ?: return@withContext
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
                        // El extremo remoto cerró la conexión limpiamente (Ej. Autodisconnect tras configurar)
                        break
                    }
                } catch (e: IOException) {
                    // El ESP32 cerró la conexión
                    break
                }
            }
            // Si el job sigue activo, significa que el cierre fue remoto (ESP32 auto-disconnect)
            // Tratamos la desconexión remota como transición a Idle en lugar de Error
            if (isActive) {
                _connectionState.value = BluetoothConnectionState.Idle
            }
        }
    }
}
