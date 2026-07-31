package com.example.escanqradmin.domain.model

object SecurityConstants {
    var SHARED_AES_KEY: String = "GabyQrSecureKey12345678901234567"
        private set
    var PROVISIONING_TOKEN: String = "ALCARAVAN_2025"
        private set

    fun init(aesKey: String, provisioningToken: String) {
        SHARED_AES_KEY = aesKey
        PROVISIONING_TOKEN = provisioningToken
    }
}
