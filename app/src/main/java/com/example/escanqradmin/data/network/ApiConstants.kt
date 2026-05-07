package com.example.escanqradmin.data.network

import android.content.Context
import android.content.SharedPreferences

object ApiConstants {
    private var baseHost: String = "172.17.12.119"
    private var basePort: String = "8059"

    private const val PREFS_NAME = "api_config_prefs"
    private const val KEY_HOST = "base_host"
    private const val KEY_PORT = "base_port"

    fun init(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        baseHost = prefs.getString(KEY_HOST, "172.17.12.119") ?: "172.17.12.119"
        basePort = prefs.getString(KEY_PORT, "8059") ?: "8059"
    }

    fun saveConfig(context: Context, host: String, port: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_HOST, host).putString(KEY_PORT, port).apply()
        baseHost = host
        basePort = port
    }

    fun getHost(): String = baseHost
    fun getPort(): String = basePort

    val BASE_URL: String
        get() = "http://$baseHost:$basePort"

    object Endpoints {
        val SYNC_VEHICULAR: String
            get() = "$BASE_URL/api/sync_vehicular"
        val GET_CONDUCTORES: String
            get() = "$BASE_URL/api/get_conductores"
    }
}