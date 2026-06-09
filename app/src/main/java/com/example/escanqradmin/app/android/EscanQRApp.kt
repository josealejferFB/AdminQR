package com.example.escanqradmin.app.android

import android.app.Application
import com.example.escanqradmin.BuildConfig
import com.example.escanqradmin.data.network.ApiConstants
import com.example.escanqradmin.domain.model.SecurityConstants
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EscanQRApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiConstants.init(this)
        SecurityConstants.init(
            aesKey = BuildConfig.SHARED_AES_KEY,
            provisioningToken = BuildConfig.PROVISIONING_TOKEN
        )
    }
}