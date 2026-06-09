package com.example.escanqradmin.data.network

import android.content.Context
import android.content.SharedPreferences

object ApiConstants {
    private var baseProtocol: String = "http"
    private var baseHost: String = "172.17.12.119"
    private var basePort: String = "8059"
    private var endpointSync: String = "/api/control_acceso"
    private var endpointConductores: String = "/api/get_conductores"
    private var endpointRegisterGate: String = "/api/v1/gates/register"
    private var endpointGatesList: String = "/api/v1/gates/list"
    private var endpointGateUpdate: String = "/api/v1/gates/update"

    private const val PREFS_NAME = "api_config_prefs"
    private const val KEY_PROTOCOL = "base_protocol"
    private const val KEY_HOST = "base_host"
    private const val KEY_PORT = "base_port"
    private const val KEY_ENDPOINT_SYNC = "endpoint_sync"
    private const val KEY_ENDPOINT_CONDUCTORES = "endpoint_conductores"
    private const val KEY_ENDPOINT_REGISTER_GATE = "endpoint_register_gate"
    private const val KEY_ENDPOINT_GATES_LIST = "endpoint_gates_list"
    private const val KEY_ENDPOINT_GATE_UPDATE = "endpoint_gate_update"

    fun init(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        baseProtocol = prefs.getString(KEY_PROTOCOL, "http") ?: "http"
        baseHost = prefs.getString(KEY_HOST, "172.17.12.119") ?: "172.17.12.119"
        basePort = prefs.getString(KEY_PORT, "8059") ?: "8059"
        endpointSync = prefs.getString(KEY_ENDPOINT_SYNC, "/api/control_acceso") ?: "/api/control_acceso"
        endpointConductores = prefs.getString(KEY_ENDPOINT_CONDUCTORES, "/api/get_conductores") ?: "/api/get_conductores"
        endpointRegisterGate = prefs.getString(KEY_ENDPOINT_REGISTER_GATE, "/api/v1/gates/register") ?: "/api/v1/gates/register"
        endpointGatesList = prefs.getString(KEY_ENDPOINT_GATES_LIST, "/api/v1/gates/list") ?: "/api/v1/gates/list"
        endpointGateUpdate = prefs.getString(KEY_ENDPOINT_GATE_UPDATE, "/api/v1/gates/update") ?: "/api/v1/gates/update"
    }

    fun saveConfig(
        context: Context,
        protocol: String,
        host: String,
        port: String,
        syncPath: String = "/api/control_acceso",
        conductoresPath: String = "/api/get_conductores",
        registerGatePath: String = "/api/v1/gates/register",
        gatesListPath: String = "/api/v1/gates/list",
        gateUpdatePath: String = "/api/v1/gates/update"
    ) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Sanitize host: remove protocol if exists, and trailing slashes
        val cleanHost = host.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .removeSuffix("/")

        // Ensure paths start with /
        val cleanSync = if (syncPath.startsWith("/")) syncPath else "/$syncPath"
        val cleanConductores = if (conductoresPath.startsWith("/")) conductoresPath else "/$conductoresPath"
        val cleanRegister = if (registerGatePath.startsWith("/")) registerGatePath else "/$registerGatePath"
        val cleanGatesList = if (gatesListPath.startsWith("/")) gatesListPath else "/$gatesListPath"
        val cleanGateUpdate = if (gateUpdatePath.startsWith("/")) gateUpdatePath else "/$gateUpdatePath"

        prefs.edit()
            .putString(KEY_PROTOCOL, protocol)
            .putString(KEY_HOST, cleanHost)
            .putString(KEY_PORT, port)
            .putString(KEY_ENDPOINT_SYNC, cleanSync)
            .putString(KEY_ENDPOINT_CONDUCTORES, cleanConductores)
            .putString(KEY_ENDPOINT_REGISTER_GATE, cleanRegister)
            .putString(KEY_ENDPOINT_GATES_LIST, cleanGatesList)
            .putString(KEY_ENDPOINT_GATE_UPDATE, cleanGateUpdate)
            .apply()

        baseProtocol = protocol
        baseHost = cleanHost
        basePort = port
        endpointSync = cleanSync
        endpointConductores = cleanConductores
        endpointRegisterGate = cleanRegister
        endpointGatesList = cleanGatesList
        endpointGateUpdate = cleanGateUpdate
    }

    fun getProtocol(): String = baseProtocol
    fun getHost(): String = baseHost
    fun getPort(): String = basePort
    fun getEndpointSync(): String = endpointSync
    fun getEndpointConductores(): String = endpointConductores
    fun getEndpointRegisterGate(): String = endpointRegisterGate
    fun getEndpointGatesList(): String = endpointGatesList
    fun getEndpointGateUpdate(): String = endpointGateUpdate

    val BASE_URL: String
        get() {
            val portSuffix = if (basePort.isBlank()) "" else ":$basePort"
            return "$baseProtocol://$baseHost$portSuffix"
        }

    object Endpoints {
        val SYNC_VEHICULAR: String
            get() = "$BASE_URL${endpointSync}"
        val GET_CONDUCTORES: String
            get() = "$BASE_URL${endpointConductores}"
        val REGISTER_GATE: String
            get() = "$BASE_URL${endpointRegisterGate}"
        val GATES_LIST: String
            get() = "$BASE_URL${endpointGatesList}"
        val GATE_UPDATE: String
            get() = "$BASE_URL${endpointGateUpdate}"
    }
}
