package com.example.escanqradmin.data.network

import android.content.Context
import android.content.SharedPreferences

object ApiConstants {
    private var baseProtocol: String = "http"
    private var baseHost: String = "172.17.12.119"
    private var basePort: String = "8059"
    private var endpointSync: String = "/api/control_acceso"
    private var endpointConductores: String = "/api/get_conductores"

    private const val PREFS_NAME = "api_config_prefs"
    private const val KEY_PROTOCOL = "base_protocol"
    private const val KEY_HOST = "base_host"
    private const val KEY_PORT = "base_port"
    private const val KEY_ENDPOINT_SYNC = "endpoint_sync"
    private const val KEY_ENDPOINT_CONDUCTORES = "endpoint_conductores"

    fun init(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        baseProtocol = prefs.getString(KEY_PROTOCOL, "http") ?: "http"
        baseHost = prefs.getString(KEY_HOST, "172.17.12.119") ?: "172.17.12.119"
        basePort = prefs.getString(KEY_PORT, "8059") ?: "8059"
        endpointSync = prefs.getString(KEY_ENDPOINT_SYNC, "/api/control_acceso") ?: "/api/control_acceso"
        endpointConductores = prefs.getString(KEY_ENDPOINT_CONDUCTORES, "/api/get_conductores") ?: "/api/get_conductores"
    }

    fun saveConfig(
        context: Context,
        protocol: String,
        host: String,
        port: String,
        syncPath: String = "/api/control_acceso",
        conductoresPath: String = "/api/get_conductores"
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

        prefs.edit()
            .putString(KEY_PROTOCOL, protocol)
            .putString(KEY_HOST, cleanHost)
            .putString(KEY_PORT, port)
            .putString(KEY_ENDPOINT_SYNC, cleanSync)
            .putString(KEY_ENDPOINT_CONDUCTORES, cleanConductores)
            .apply()

        baseProtocol = protocol
        baseHost = cleanHost
        basePort = port
        endpointSync = cleanSync
        endpointConductores = cleanConductores
    }

    fun getProtocol(): String = baseProtocol
    fun getHost(): String = baseHost
    fun getPort(): String = basePort
    fun getEndpointSync(): String = endpointSync
    fun getEndpointConductores(): String = endpointConductores

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
    }
}
