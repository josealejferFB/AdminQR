package com.example.escanqradmin.data.network

object ApiConstants {
    private const val BASE_HOST = "172.17.12.119"
    private const val BASE_PORT = "8059"
    const val BASE_URL = "http://$BASE_HOST:$BASE_PORT"

    object Endpoints {
        const val SYNC_VEHICULAR = "$BASE_URL/api/sync_vehicular"
        const val GET_CONDUCTORES = "$BASE_URL/api/get_conductores"
    }
}
