package com.example.escanqradmin.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkUtil {
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun mapException(e: Exception): String {
        val msg = e.message ?: e.javaClass.simpleName
        return when {
            msg.contains("Unable to resolve host", ignoreCase = true) ->
                "No se pudo conectar al servidor. Verifica la IP y que el servidor esté encendido."
            msg.contains("timeout", ignoreCase = true) ->
                "El servidor no respondió a tiempo. Verifica que esté accesible."
            msg.contains("Connection refused", ignoreCase = true) ->
                "Conexión rechazada. Verifica que el servidor Odoo esté corriendo."
            msg.contains("Network is unreachable", ignoreCase = true) ->
                "No hay conexión de red. Verifica tu conexión WiFi o datos."
            msg.contains("Failed to connect", ignoreCase = true) ->
                "No se pudo conectar al servidor. Verifica la IP y puerto."
            msg.contains("Socket closed", ignoreCase = true) ->
                "La conexión se interrumpió."
            msg.contains("Empty body", ignoreCase = true) ->
                "El servidor respondió vacío."
            msg.contains("Missing result", ignoreCase = true) ->
                "Respuesta inesperada del servidor."
            msg.contains("Odoo Error", ignoreCase = true) ->
                msg
            else -> msg
        }
    }
}
